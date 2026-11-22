package utar.ui;

import utar.model.*;
import utar.service.*;
import utar.util.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * UserMenu.java — Main menu for Student / Staff users (CampusUser).
 *
 * Shows a list of options grouped into three sections:
 *
 *   FACILITY BOOKING
 *     1. Search Available Facilities
 *     2. Book a Facility
 *     3. View My Bookings
 *     4. Modify a Booking
 *     5. Cancel a Booking
 *
 *   MAINTENANCE
 *     6. Report Facility Issue
 *
 *   ACCOUNT
 *     7. My Booking History & Stats
 *     8. Update My Profile
 *     9. Change Password
 *     0. Logout
 *
 * On every menu refresh, reminder notifications are shown for bookings
 * that start within the next 2 hours (see checkAndShowReminders).
 */
public class UserMenu {

    // ── Dependencies (one of each shared service) ────────────────────────────
    private Scanner            sc;                   // shared keyboard input
    private User               currentUser;          // who is logged in
    private BookingService     bookingService   = new BookingService();
    private NotificationService notifier        = new NotificationService();
    private MaintenanceService maintService     = new MaintenanceService();
    private AnalyticsService   analyticsService = new AnalyticsService();
    private UserService        userService      = new UserService();
    private DataStore          ds               = DataStore.getInstance();

    public UserMenu(Scanner sc, User user) {
        this.sc          = sc;
        this.currentUser = user;
    }

    /**
     * show — main loop for the user menu.  Keeps drawing the menu and
     * dispatching to the chosen action until the user picks 0 (Logout).
     * Polymorphism: each menu option calls a different private method.
     */
    public void show() {
        while (true) {
            // Re-paint the header + menu every iteration so it looks fresh
            ConsoleUI.clearScreen();
            ConsoleUI.printHeader("UTAR SMART CAMPUS — USER PORTAL");
            System.out.println();
            ConsoleUI.printField("Logged in as", currentUser.getName()
                    + " (" + currentUser.getRoleLabel() + ")");
            ConsoleUI.printField("Faculty", currentUser.getFaculty());
            System.out.println();

            // Show reminder notifications for bookings starting soon
            checkAndShowReminders();
            showNotifications(currentUser.getUserId());

            ConsoleUI.printSection("FACILITY BOOKING");
            ConsoleUI.printMenuItem(1, "Search Available Facilities");
            ConsoleUI.printMenuItem(2, "Book a Facility");
            ConsoleUI.printMenuItem(3, "View My Bookings");
            ConsoleUI.printMenuItem(4, "Modify a Booking");
            ConsoleUI.printMenuItem(5, "Cancel a Booking");

            ConsoleUI.printSection("MAINTENANCE");
            ConsoleUI.printMenuItem(6, "Report Facility Issue");

            ConsoleUI.printSection("ACCOUNT");
            ConsoleUI.printMenuItem(7, "My Booking History & Stats");
            ConsoleUI.printMenuItem(8, "Update My Profile");
            ConsoleUI.printMenuItem(9, "Change Password");
            ConsoleUI.printMenuItem(0, "Logout");
            System.out.println();

            int choice = ConsoleUI.promptInt(sc, "Select option", 0, 9);

            // Route the choice to the matching helper method
            switch (choice) {
                case 1: searchFacilities();    break;
                case 2: bookFacility();        break;
                case 3: viewMyBookings();      break;
                case 4: modifyBooking();       break;
                case 5: cancelBooking();       break;
                case 6: reportIssue();         break;
                case 7: viewHistoryStats();    break;
                case 8: updateProfile();       break;
                case 9: changePassword();      break;
                case 0:
                    // Logout — return to AuthMenu (handled by Main.java's outer loop)
                    ConsoleUI.printInfo("Logged out. Goodbye, " + currentUser.getName() + "!");
                    ConsoleUI.pressEnter(sc);
                    return;
            }
        }
    }

    /**
     * showNotifications — print any notifications this user hasn't seen
     * yet (e.g. "Your booking BK0100 was approved"), then mark them as read.
     */
    private void showNotifications(String userId) {
        for (Notification n : notifier.getUnread(userId)) {
            ConsoleUI.printAlert("NEW: " + n.getMessage());
        }
        notifier.markAllRead(userId);
    }

    /**
     * checkAndShowReminders — runs at the top of every menu refresh.
     * Asks BookingService for any approved bookings starting within the
     * next 2 hours and displays them.  Each booking's reminderSent flag
     * is set inside getDueReminders() so we don't show the same one twice.
     */
    private void checkAndShowReminders() {
        // Today's date and now's time, both formatted as the system stores them
        String today = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String now = java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        ArrayList<Booking> due = bookingService.getDueReminders(
                currentUser.getUserId(), today, now);
        for (Booking b : due) {
            Facility f = ds.getFacilityById(b.getFacilityId());
            ConsoleUI.printAlert("REMINDER: " + (f != null ? f.getName() : b.getFacilityId())
                + " booked at " + b.getStartTime() + " on " + b.getDate());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEARCH — option 1
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * searchFacilities — ask for a date + time slot (and optional type),
     * then call BookingService.searchAvailable() to find facilities that
     * are free.  This is read-only; it doesn't make any booking.
     */
    private void searchFacilities() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("SEARCH AVAILABLE FACILITIES");
        System.out.println();

        // Date prompt with retry loop
        String date;
        while (true) {
            date = ConsoleUI.prompt(sc, "Date (dd/MM/yyyy) — 0 to cancel");
            if (date.equals("0")) return;
            if (!InputValidator.isValidDate(date)) {
                ConsoleUI.printError("Invalid date. Use a real dd/MM/yyyy date in "
                        + InputValidator.currentYear() + " or " + InputValidator.maxBookingYear() + ".");
            } else if (InputValidator.isPastDate(date)) {
                ConsoleUI.printError("That date has already passed. Enter today or a later date.");
            } else {
                break;
            }
        }

        // Start time prompt with retry loop
        ConsoleUI.printInfo("Use 24-hour time, e.g. 14:00 for 2pm.");
        String start;
        while (true) {
            start = ConsoleUI.prompt(sc, "Start time (HH:mm) — 0 to cancel");
            if (start.equals("0")) return;
            if (InputValidator.isValidTime(start)) break;
            ConsoleUI.printError("Invalid time. Use HH:mm (e.g. 09:00).");
        }

        // End time prompt with retry loop (also checks start < end)
        String end;
        while (true) {
            end = ConsoleUI.prompt(sc, "End time   (HH:mm) — 0 to cancel");
            if (end.equals("0")) return;
            if (!InputValidator.isValidTime(end)) {
                ConsoleUI.printError("Invalid time. Use HH:mm (e.g. 11:00).");
                continue;
            }
            if (!InputValidator.isValidTimeRange(start, end)) {
                ConsoleUI.printError("End time must be after start time.");
                continue;
            }
            break;
        }
        if (InputValidator.hasStarted(date, start)) {
            ConsoleUI.printError(BookingService.PAST_SLOT_MESSAGE);
            ConsoleUI.pressEnter(sc);
            return;
        }
        if (!InputValidator.isWithinOperatingHours(start, end))
            ConsoleUI.printWarning("Note: Operating hours are 08:00 – 22:00.");

        System.out.println();
        ConsoleUI.printInfo("Facility Type:");
        System.out.println("  [0] All  [1] Lecture Hall  [2] Discussion Room  [3] Sports Court");
        System.out.println("  [4] Computer Lab  [5] Multipurpose Hall");
        int t = ConsoleUI.promptInt(sc, "Filter by type", 0, 5);
        Facility.FacilityType type = null;
        if (t > 0) type = Facility.FacilityType.values()[t - 1];
        int people = ConsoleUI.promptInt(sc, "Number of people (0 = any size)", 0, 1000);

        ArrayList<Facility> results = bookingService.searchAvailable(date, start, end, type, people);
        System.out.println();
        if (results.isEmpty()) {
            ConsoleUI.printWarning("No facilities available for selected criteria.");
        } else {
            ConsoleUI.printSuccess(results.size() + " facility(ies) found:");
            ConsoleUI.printDivider();
            for (Facility f : results) {
                System.out.printf("  %-8s %-35s Cap: %-5d %s%n",
                        f.getFacilityId(), ConsoleUI.fit(f.getName(), 35), f.getCapacity(), f.getLocation());
            }
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BOOK — option 2
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * bookFacility — full booking flow:
     *   1. Ask for date / start / end / type
     *   2. Show the available facilities (uses searchAvailable)
     *   3. Let the user pick one and choose a purpose
     *   4. Call BookingService.createBooking() — wrapped in try/catch so
     *      conflicts and "facility unavailable" errors show as friendly
     *      messages (Exception Handling).
     */
    private void bookFacility() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("BOOK A FACILITY");
        System.out.println();

        String date;
        while (true) {
            date = ConsoleUI.prompt(sc, "Date (dd/MM/yyyy) — 0 to cancel");
            if (date.equals("0")) return;
            if (!InputValidator.isValidDate(date)) {
                ConsoleUI.printError("Invalid date. Use a real dd/MM/yyyy date in "
                        + InputValidator.currentYear() + " or " + InputValidator.maxBookingYear() + ".");
            } else if (InputValidator.isPastDate(date)) {
                ConsoleUI.printError("That date has already passed. Enter today or a later date.");
            } else {
                break;
            }
        }

        ConsoleUI.printInfo("Use 24-hour time, e.g. 14:00 for 2pm.");
        String start;
        while (true) {
            start = ConsoleUI.prompt(sc, "Start time (HH:mm) — 0 to cancel");
            if (start.equals("0")) return;
            if (InputValidator.isValidTime(start)) break;
            ConsoleUI.printError("Invalid time. Use HH:mm (e.g. 09:00).");
        }

        String end;
        while (true) {
            end = ConsoleUI.prompt(sc, "End time   (HH:mm) — 0 to cancel");
            if (end.equals("0")) return;
            if (!InputValidator.isValidTime(end)) {
                ConsoleUI.printError("Invalid time. Use HH:mm (e.g. 11:00).");
                continue;
            }
            if (!InputValidator.isValidTimeRange(start, end)) {
                ConsoleUI.printError("End time must be after start time.");
                continue;
            }
            if (!InputValidator.hasMinimumDuration(start, end)) {
                ConsoleUI.printError("Booking must be at least 30 minutes long.");
                continue;
            }
            if (!InputValidator.isWithinOperatingHours(start, end)) {
                ConsoleUI.printError("Booking must be within operating hours (08:00 - 22:00).");
                continue;
            }
            break;
        }

        if (InputValidator.hasStarted(date, start)) {
            ConsoleUI.printError(BookingService.PAST_SLOT_MESSAGE);
            ConsoleUI.pressEnter(sc); return;
        }

        int people = ConsoleUI.promptInt(sc, "Number of people attending", 1, 1000);

        // only facilities that are free AND big enough
        ArrayList<Facility> available = bookingService.searchAvailable(date, start, end, null, people);
        if (available.isEmpty()) {
            ConsoleUI.printWarning("No facilities big enough are available for that slot.");
            ConsoleUI.pressEnter(sc); return;
        }

        System.out.println();
        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < available.size(); i++) {
            Facility f = available.get(i);
            System.out.printf("  %s[%d]%s %-35s Cap: %-5d %s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    ConsoleUI.fit(f.getName(), 35), f.getCapacity(), f.getLocation());
        }
        int sel = ConsoleUI.promptInt(sc, "Select facility", 0, available.size());
        if (sel == 0) return;
        Facility chosen = available.get(sel - 1);

        System.out.println();
        System.out.println("  [0] Back  [1] Academic  [2] Co-Curricular  [3] Official");
        int p = ConsoleUI.promptInt(sc, "Purpose", 0, 3);
        if (p == 0) return;
        Booking.BookingPurpose purpose = Booking.BookingPurpose.values()[p - 1];

        String desc;
        while (true) {
            desc = ConsoleUI.prompt(sc, "Brief description (0 to cancel)");
            if (desc.equals("0")) return;
            if (!desc.isBlank()) break;
            ConsoleUI.printError("Description cannot be empty.");
        }

        try {
            Booking b = bookingService.createBooking(currentUser.getUserId(),
                    chosen.getFacilityId(), date, start, end, purpose, desc, people);
            ConsoleUI.printSuccess("Booking " + b.getBookingId() + " submitted — awaiting approval.");
        } catch (Exception e) {
            ConsoleUI.printError(e.getMessage());
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW MY BOOKINGS — option 3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewMyBookings — list every booking made by the logged-in user,
     * regardless of status.  Read-only.
     */
    private void viewMyBookings() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MY BOOKINGS");

        ArrayList<Booking> list = bookingService.getUserBookings(currentUser.getUserId());
        if (list.isEmpty()) {
            ConsoleUI.printInfo("You have no bookings."); ConsoleUI.pressEnter(sc); return;
        }
        ConsoleUI.printDivider();
        for (Booking b : list) {
            Facility f = ds.getFacilityById(b.getFacilityId());
            String statusColor = b.getStatus() == Booking.BookingStatus.APPROVED ? ConsoleUI.GREEN
                    : b.getStatus() == Booking.BookingStatus.PENDING ? ConsoleUI.YELLOW
                    : ConsoleUI.RED;
            System.out.printf("  %s%-8s%s %-35s %s%-10s%s %s %s-%s%n",
                    ConsoleUI.CYAN, b.getBookingId(), ConsoleUI.RESET,
                    ConsoleUI.fit(f != null ? f.getName() : b.getFacilityId(), 35),
                    statusColor, b.getStatus(), ConsoleUI.RESET,
                    b.getDate(), b.getStartTime(), b.getEndTime());
            System.out.printf("           Purpose: %-15s  People: %-5s %s%n",
                    b.getPurpose(), b.getAttendees() > 0 ? String.valueOf(b.getAttendees()) : "-",
                    b.getDescription());
            if (b.getAdminRemark() != null)
                System.out.printf("           Remark: %s%n", b.getAdminRemark());
            ConsoleUI.printDivider();
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MODIFY BOOKING — option 4
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * modifyBooking — change date / time / description of an existing booking.
     * Only PENDING bookings (still waiting for admin) can be modified —
     * BookingService throws an IllegalStateException otherwise, which we
     * catch and show as an error message (Exception Handling).
     */
    private void modifyBooking() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MODIFY BOOKING");
        ConsoleUI.printInfo("Only PENDING bookings can be modified.");

        ArrayList<Booking> pending = new ArrayList<>();
        for (Booking b : bookingService.getUserBookings(currentUser.getUserId()))
            if (b.getStatus() == Booking.BookingStatus.PENDING) pending.add(b);

        if (pending.isEmpty()) {
            ConsoleUI.printWarning("No pending bookings to modify.");
            ConsoleUI.pressEnter(sc); return;
        }

        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < pending.size(); i++) {
            Booking b = pending.get(i);
            Facility f = ds.getFacilityById(b.getFacilityId());
            System.out.printf("  %s[%d]%s %s — %s — %s %s-%s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    b.getBookingId(), f != null ? f.getName() : b.getFacilityId(),
                    b.getDate(), b.getStartTime(), b.getEndTime());
        }
        int sel = ConsoleUI.promptInt(sc, "Select booking", 0, pending.size());
        if (sel == 0) return;
        Booking target = pending.get(sel - 1);

        System.out.println();
        ConsoleUI.printInfo("Press ENTER to keep current value.");

        String newDate = ConsoleUI.prompt(sc, "New date [" + target.getDate() + "]");
        if (newDate.isBlank()) newDate = target.getDate();
        else if (!InputValidator.isValidDate(newDate)) {
            ConsoleUI.printError("Invalid date."); ConsoleUI.pressEnter(sc); return;
        }

        String newStart = ConsoleUI.prompt(sc, "New start time [" + target.getStartTime() + "]");
        if (newStart.isBlank()) newStart = target.getStartTime();

        String newEnd = ConsoleUI.prompt(sc, "New end time [" + target.getEndTime() + "]");
        if (newEnd.isBlank()) newEnd = target.getEndTime();

        if (!InputValidator.isValidTime(newStart) || !InputValidator.isValidTime(newEnd)) {
            ConsoleUI.printError("Invalid time. Use HH:mm (e.g. 09:00)."); ConsoleUI.pressEnter(sc); return;
        }
        if (InputValidator.hasStarted(newDate, newStart)) {
            ConsoleUI.printError(BookingService.PAST_SLOT_MESSAGE); ConsoleUI.pressEnter(sc); return;
        }
        if (!InputValidator.isValidTimeRange(newStart, newEnd)
                || !InputValidator.hasMinimumDuration(newStart, newEnd)
                || !InputValidator.isWithinOperatingHours(newStart, newEnd)) {
            ConsoleUI.printError("Invalid time range (min 30 minutes, within 08:00-22:00)."); ConsoleUI.pressEnter(sc); return;
        }

        String newDesc = ConsoleUI.prompt(sc, "New description [" + target.getDescription() + "]");
        if (newDesc.isBlank()) newDesc = target.getDescription();

        // Number of people — ENTER keeps the current number
        String peopleText = ConsoleUI.prompt(sc, "New number of people ["
                + (target.getAttendees() > 0 ? target.getAttendees() : "-") + "]");
        int newPeople = 0;   // 0 = keep
        if (!peopleText.isBlank()) {
            try {
                newPeople = Integer.parseInt(peopleText);
            } catch (NumberFormatException e) {
                newPeople = -1;
            }
            if (newPeople < 1) {
                ConsoleUI.printError("Number of people must be a whole number, at least 1.");
                ConsoleUI.pressEnter(sc); return;
            }
        }

        // Check if anything actually changed
        boolean changed = !newDate.equals(target.getDate())
                       || !newStart.equals(target.getStartTime())
                       || !newEnd.equals(target.getEndTime())
                       || !newDesc.equals(target.getDescription())
                       || (newPeople > 0 && newPeople != target.getAttendees());

        if (!changed) {
            ConsoleUI.printInfo("No changes made.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        try {
            bookingService.modifyBooking(target.getBookingId(), currentUser.getUserId(),
                    newDate, newStart, newEnd, newDesc, newPeople);
            ConsoleUI.printSuccess("Booking updated successfully.");
        } catch (Exception e) {
            ConsoleUI.printError(e.getMessage());
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CANCEL BOOKING — option 5
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * cancelBooking — cancel one of the user's own bookings.
     * BookingService enforces the "no cancellation within 24 hours of
     * start time" rule (Exception Handling).
     */
    private void cancelBooking() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("CANCEL BOOKING");

        ArrayList<Booking> cancellable = new ArrayList<>();
        for (Booking b : bookingService.getUserBookings(currentUser.getUserId())) {
            if (b.getStatus() == Booking.BookingStatus.PENDING
                    || b.getStatus() == Booking.BookingStatus.APPROVED)
                cancellable.add(b);
        }
        if (cancellable.isEmpty()) {
            ConsoleUI.printWarning("No cancellable bookings."); ConsoleUI.pressEnter(sc); return;
        }

        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < cancellable.size(); i++) {
            Booking b = cancellable.get(i);
            Facility f = ds.getFacilityById(b.getFacilityId());
            System.out.printf("  %s[%d]%s %s — %s — %s [%s]%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    b.getBookingId(), f != null ? f.getName() : b.getFacilityId(),
                    b.getDate(), b.getStatus());
        }
        int sel = ConsoleUI.promptInt(sc, "Select booking to cancel", 0, cancellable.size());
        if (sel == 0) return;
        Booking target = cancellable.get(sel - 1);

        System.out.println();
        ConsoleUI.printWarning("Cancelling booking " + target.getBookingId() + ".");
        String confirm = ConsoleUI.prompt(sc, "Type 'yes' to confirm");
        if (!confirm.equalsIgnoreCase("yes")) {
            ConsoleUI.printInfo("Cancelled aborted."); ConsoleUI.pressEnter(sc); return;
        }

        try {
            bookingService.cancelBooking(target.getBookingId(), currentUser.getUserId());
            ConsoleUI.printSuccess("Booking " + target.getBookingId() + " cancelled.");
        } catch (Exception e) {
            ConsoleUI.printError(e.getMessage());
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REPORT ISSUE — option 6
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * reportIssue — submit a new MaintenanceReport for a broken/damaged
     * facility.  The user picks the facility and category, types a
     * description, and the report is saved with status = REPORTED.
     */
    private void reportIssue() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("REPORT FACILITY ISSUE");
        System.out.println();

        ArrayList<Facility> all = ds.getAllFacilities();
        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < all.size(); i++) {
            Facility f = all.get(i);
            System.out.printf("  %s[%d]%s %s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET, f.getName());
        }
        int sel = ConsoleUI.promptInt(sc, "Select facility", 0, all.size());
        if (sel == 0) return;
        Facility chosen = all.get(sel - 1);

        System.out.println();
        System.out.println("  Issue Category:");
        MaintenanceReport.IssueCategory[] cats = MaintenanceReport.IssueCategory.values();
        for (int i = 0; i < cats.length; i++)
            System.out.printf("  %s[%d]%s %s%n", ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET, cats[i]);
        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        int catSel = ConsoleUI.promptInt(sc, "Select category", 0, cats.length);
        if (catSel == 0) return;
        MaintenanceReport.IssueCategory category = cats[catSel - 1];

        String desc = ConsoleUI.prompt(sc, "Describe the issue");
        if (desc.isBlank()) {
            ConsoleUI.printError("Description cannot be empty."); ConsoleUI.pressEnter(sc); return;
        }

        String date = ConsoleUI.prompt(sc, "Today's date (dd/MM/yyyy)");
        if (!InputValidator.isValidDate(date)) {
            ConsoleUI.printError("Invalid date."); ConsoleUI.pressEnter(sc); return;
        }

        // Optional photo or PDF: the user types or pastes the file's full path
        File attachment = null;
        while (true) {
            String path = ConsoleUI.prompt(sc, "Attach a photo or PDF (full file path, ENTER to skip)");
            // Windows "Copy as path" puts quotes around the path, so remove them
            if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length() - 1);
            }
            if (path.isBlank()) break;
            try {
                MaintenanceService.checkAttachment(new File(path));   // type, size, contents
                attachment = new File(path);
                break;
            } catch (IllegalArgumentException e) {
                ConsoleUI.printError(e.getMessage() + " Try another file or press ENTER to skip.");
            }
        }

        try {
            MaintenanceReport r = maintService.reportIssue(chosen.getFacilityId(),
                    currentUser.getUserId(), desc, category, date, attachment);
            ConsoleUI.printSuccess("Report " + r.getReportId() + " submitted successfully"
                    + (r.hasAttachment() ? " with your attachment." : "."));
        } catch (Exception e) {
            ConsoleUI.printError(e.getMessage());
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HISTORY & STATS — option 7
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewHistoryStats — show the user's own booking statistics
     * (counts by status: PENDING / APPROVED / REJECTED / CANCELLED).
     * Delegates to AnalyticsService.printUserStats().
     */
    private void viewHistoryStats() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MY BOOKING HISTORY & STATS");
        System.out.println();
        analyticsService.printUserStats(currentUser.getUserId());
        System.out.println();
        ConsoleUI.printInfo("Full Booking History:");
        ConsoleUI.printDivider();
        for (Booking b : bookingService.getUserBookings(currentUser.getUserId())) {
            Facility f = ds.getFacilityById(b.getFacilityId());
            System.out.printf("  %-8s %-35s %-12s %s %s-%s%n",
                    b.getBookingId(), ConsoleUI.fit(f != null ? f.getName() : b.getFacilityId(), 35),
                    b.getStatus(), b.getDate(), b.getStartTime(), b.getEndTime());
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE PROFILE — option 8
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * updateProfile — let the user edit their contact number, faculty,
     * or display name.  Blank input means "keep the current value".
     * Delegates to UserService.updateProfile() which handles persistence.
     */
    private void updateProfile() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MY PROFILE");
        System.out.println();

        // Display all current details — Requirement b)
        ConsoleUI.printField("User ID",     currentUser.getUserId());
        ConsoleUI.printField("Name",        currentUser.getName());
        ConsoleUI.printField("Email",       currentUser.getEmail());
        ConsoleUI.printField("Contact",     currentUser.getContactNumber());
        ConsoleUI.printField("Faculty",     currentUser.getFaculty());
        ConsoleUI.printField("Role",        currentUser.getRoleLabel());
        if (currentUser instanceof CampusUser) {
            CampusUser cu = (CampusUser) currentUser;
            String label = cu.getRole() == User.UserRole.STAFF ? "Department" : "Programme";
            ConsoleUI.printField(label, cu.getExtra());
        }
        System.out.println();

        ConsoleUI.printInfo("Press ENTER to keep current value.");
        System.out.println();

        String newName    = ConsoleUI.prompt(sc, "New name");
        String newContact = ConsoleUI.prompt(sc, "New contact number");
        String newFaculty = ConsoleUI.prompt(sc, "New faculty/department");

        if (!newContact.isBlank() && !InputValidator.isValidPhone(newContact)) {
            ConsoleUI.printError("Invalid phone number."); ConsoleUI.pressEnter(sc); return;
        }

        userService.updateProfile(currentUser.getUserId(), newContact, newFaculty, newName);
        // Refresh local reference
        currentUser = DataStore.getInstance().getUserById(currentUser.getUserId());
        ConsoleUI.printSuccess("Profile updated successfully.");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CHANGE PASSWORD — option 9
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * changePassword — verify the user's current password, then set a
     * new one.  Both fields are validated by InputValidator and the
     * UserService refuses if the old password is wrong.
     */
    private void changePassword() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("CHANGE PASSWORD");
        System.out.println();

        String oldPass = ConsoleUI.prompt(sc, "Current password");

        // Verify current password FIRST — no point asking for new password if old is wrong
        if (!userService.checkPassword(currentUser.getUserId(), oldPass)) {
            ConsoleUI.printError("Current password is incorrect.");
            ConsoleUI.pressEnter(sc); return;
        }

        String newPass = ConsoleUI.prompt(sc, "New password (min 6 characters)");
        if (!InputValidator.isValidPassword(newPass)) {
            ConsoleUI.printError("Password must be at least 6 characters.");
            ConsoleUI.pressEnter(sc); return;
        }
        if (newPass.equals(oldPass)) {
            ConsoleUI.printError("New password must be different from current password.");
            ConsoleUI.pressEnter(sc); return;
        }
        String confirm = ConsoleUI.prompt(sc, "Confirm new password");
        if (!newPass.equals(confirm)) {
            ConsoleUI.printError("Passwords do not match."); ConsoleUI.pressEnter(sc); return;
        }

        userService.changePassword(currentUser.getUserId(), oldPass, newPass);
        ConsoleUI.printSuccess("Password changed successfully.");
        ConsoleUI.pressEnter(sc);
    }
}
