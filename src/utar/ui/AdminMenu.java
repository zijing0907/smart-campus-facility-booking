package utar.ui;

import utar.model.*;
import utar.service.*;
import utar.util.*;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * AdminMenu.java — Main menu shown to ADMIN users after login.
 *
 * Admin users have privileges that ordinary users don't:
 *   BOOKING MANAGEMENT       — approve / reject bookings, manage facility status
 *   MAINTENANCE MANAGEMENT   — view all reports, assign workers
 *   ANALYTICS & REPORTS      — utilisation, peak hours, type usage, maintenance
 *   SYSTEM                   — view users, send reminders, see maintenance schedule
 *
 * On every refresh, "frequent issue" alerts are shown at the top so the
 * admin notices facilities that need attention.
 */
public class AdminMenu {

    // ── Dependencies ─────────────────────────────────────────────────────────
    private Scanner sc;                      // keyboard input
    private User adminUser;               // who is logged in
    private BookingService bookingService = new BookingService();
    private MaintenanceService maintService  = new MaintenanceService();
    private AnalyticsService analyticsService = new AnalyticsService();
    private DataStore ds  = DataStore.getInstance();

    public AdminMenu(Scanner sc, User admin) {
        this.sc        = sc;
        this.adminUser = admin;
    }

    /**
     * show — main loop for the admin panel.  Re-paints the menu every
     * iteration and dispatches to the chosen feature.  Stops only when
     * the admin picks 0 (Logout).
     */
    public void show() {
        while (true) {
            ConsoleUI.clearScreen();
            ConsoleUI.printHeader("UTAR SMART CAMPUS — ADMIN PANEL");
            System.out.println();

            ConsoleUI.printField("Admin", adminUser.getName());
            System.out.println();

            // Show "facility has too many issues" alerts at the top of the screen
            for (String alert : maintService.getFrequentIssueAlerts())
                ConsoleUI.printAlert(alert);

            ConsoleUI.printSection("BOOKING MANAGEMENT");
            ConsoleUI.printMenuItem(1, "Process Pending Bookings (Approve/Reject)");
            ConsoleUI.printMenuItem(2, "View All Bookings");
            ConsoleUI.printMenuItem(3, "Manage Facility Availability");

            ConsoleUI.printSection("MAINTENANCE MANAGEMENT");
            ConsoleUI.printMenuItem(4, "View All Maintenance Reports");
            ConsoleUI.printMenuItem(5, "Assign Maintenance Task");

            ConsoleUI.printSection("ANALYTICS & REPORTS");
            ConsoleUI.printMenuItem(6,  "Facility Utilization Report");
            ConsoleUI.printMenuItem(7,  "Peak Booking Hours");
            ConsoleUI.printMenuItem(8,  "Facility Type Usage Report");
            ConsoleUI.printMenuItem(9,  "Maintenance Summary Report");

            ConsoleUI.printSection("SYSTEM");
            ConsoleUI.printMenuItem(10, "View All Users");
            ConsoleUI.printMenuItem(11, "Send Booking Reminders");
            ConsoleUI.printMenuItem(12, "View Maintenance Schedule");
            ConsoleUI.printMenuItem(0,  "Logout");
            System.out.println();

            int choice = ConsoleUI.promptInt(sc, "Select option", 0, 12);

            // Route the choice to the matching helper method
            switch (choice) {
                case 1:  processPendingBookings();
                break;
                case 2:  viewAllBookings();
                break;
                case 3:  manageFacilityStatus();
                break;
                case 4:  viewMaintenanceReports();
                break;
                case 5:  assignMaintenanceTask();
                break;
                case 6:  utilizationReport();
                break;
                case 7:  peakHoursReport();
                break;
                case 8:  facilityTypeReport();
                break;
                case 9:  maintenanceSummary();
                break;
                case 10: viewAllUsers();
                break;
                case 11: sendReminders();
                break;
                case 12: viewMaintenanceSchedule();
                break;
                case 0:
                    // Logout — return to AuthMenu via Main's outer loop
                    ConsoleUI.printInfo("Logged out.");
                    ConsoleUI.pressEnter(sc);
                    return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PROCESS PENDING BOOKINGS — option 1
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * processPendingBookings — list every booking with status PENDING and
     * let the admin approve or reject them one at a time.  Before approving,
     * the system shows any conflicting APPROVED bookings so the admin can
     * see the implications of the decision.
     */
    private void processPendingBookings() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("PROCESS PENDING BOOKINGS");

        ArrayList<Booking> pending = bookingService.getPendingBookings();
        if (pending.isEmpty()) {
            ConsoleUI.printInfo("No pending bookings."); ConsoleUI.pressEnter(sc); return;
        }

        System.out.printf("  %s[0]%s Back%n%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < pending.size(); i++) {
            Booking b  = pending.get(i);
            User u     = ds.getUserById(b.getUserId());
            Facility f = ds.getFacilityById(b.getFacilityId());

            System.out.printf("  %s[%d]%s %-8s  User: %-20s  Facility: %s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    b.getBookingId(), u != null ? u.getName() : b.getUserId(),
                    f != null ? f.getName() : b.getFacilityId());

            System.out.printf("          Date: %s  %s-%s  Purpose: %s%n",
                    b.getDate(), b.getStartTime(), b.getEndTime(), b.getPurpose());

            System.out.printf("          Desc: %s%n", b.getDescription());
            ConsoleUI.printDivider();
        }

        int sel = ConsoleUI.promptInt(sc, "Select booking", 0, pending.size());
        if (sel == 0)
            return;
        Booking target = pending.get(sel - 1);

        // Booking conflict check — Admin requirement b)
        ArrayList<Booking> conflicts = bookingService.getConflictingApproved(
                target.getFacilityId(), target.getDate(),
                target.getStartTime(), target.getEndTime(), target.getBookingId());

        if (!conflicts.isEmpty()) {
            ConsoleUI.printWarning("Conflict detected! The following APPROVED booking(s) overlap:");

            for (Booking c : conflicts)
                System.out.printf("    %s  %s-%s  (User: %s)%n",
                        c.getDate(), c.getStartTime(), c.getEndTime(), c.getUserId());
        }

        System.out.println("  [0] Back   [1] Approve   [2] Reject");
        int dec = ConsoleUI.promptInt(sc, "Decision", 0, 2);

        if (dec == 0)
            return;

        String remark = ConsoleUI.prompt(sc, "Remark (optional, ENTER to skip)");
        bookingService.processBooking(target.getBookingId(), dec == 1,
                remark.isBlank() ? null : remark);
        ConsoleUI.printSuccess("Booking " + target.getBookingId()
                + (dec == 1 ? " APPROVED." : " REJECTED."));
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW ALL BOOKINGS — option 2
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewAllBookings — read-only list of every booking in the system,
     * grouped or filtered by status.  Useful for the admin to get an
     * overview of activity.
     */
    private void viewAllBookings() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("ALL BOOKINGS");

        System.out.println("  [0] Back  [1] All  [2] Pending  [3] Approved  [4] Rejected  [5] Cancelled");

        int filt = ConsoleUI.promptInt(sc, "Filter", 0, 5);
        if (filt == 0)
            return;

        Booking.BookingStatus filter = filt == 1 ? null : Booking.BookingStatus.values()[filt - 2];
        int count = 0;
        ConsoleUI.printDivider();

        for (Booking b : ds.getAllBookings()) {

            if (filter != null && b.getStatus() != filter)
                continue;

            User u     = ds.getUserById(b.getUserId());
            Facility f = ds.getFacilityById(b.getFacilityId());
            String col = b.getStatus() == Booking.BookingStatus.APPROVED ? ConsoleUI.GREEN
                    : b.getStatus() == Booking.BookingStatus.PENDING ? ConsoleUI.YELLOW
                    : ConsoleUI.RED;

            System.out.printf("  %-8s %-20s %-28s %s%-10s%s %s%n",
                    b.getBookingId(), u != null ? u.getName() : b.getUserId(),
                    f != null ? f.getName() : b.getFacilityId(),
                    col, b.getStatus(), ConsoleUI.RESET,
                    b.getDate() + " " + b.getStartTime() + "-" + b.getEndTime());
            count++;
        }
        System.out.println();
        ConsoleUI.printInfo("Total: " + count + " booking(s).");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MANAGE FACILITY STATUS — option 3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * manageFacilityStatus — let the admin change a facility's status
     * to AVAILABLE / UNDER_MAINTENANCE / CLOSED.  Used when a big repair
     * is happening or when a facility is permanently taken out of service.
     */
    private void manageFacilityStatus() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MANAGE FACILITY AVAILABILITY");

        ArrayList<Facility> all = ds.getAllFacilities();
        System.out.printf("  %s[0]%s Back%n%n", ConsoleUI.YELLOW, ConsoleUI.RESET);

        for (int i = 0; i < all.size(); i++) {
            Facility f = all.get(i);
            String col = f.getStatus() == Facility.FacilityStatus.AVAILABLE ? ConsoleUI.GREEN
                    : f.getStatus() == Facility.FacilityStatus.UNDER_MAINTENANCE ? ConsoleUI.YELLOW
                    : ConsoleUI.RED;
            System.out.printf("  %s[%2d]%s %-35s %s%-20s%s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET, f.getName(),
                    col, f.getStatus(), ConsoleUI.RESET);
        }
        int sel = ConsoleUI.promptInt(sc, "Select facility", 0, all.size());

        if (sel == 0)
            return;
        Facility chosen = all.get(sel - 1);

        System.out.println("\n  [0] Back  [1] AVAILABLE  [2] UNDER MAINTENANCE  [3] CLOSED");
        int s = ConsoleUI.promptInt(sc, "Set status", 0, 3);
        if (s == 0)
            return;

        Facility.FacilityStatus[] statuses = Facility.FacilityStatus.values();
        maintService.setFacilityStatus(chosen.getFacilityId(), statuses[s - 1]);
        ConsoleUI.printSuccess("Status updated to " + statuses[s - 1] + ".");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW MAINTENANCE REPORTS — option 4
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewMaintenanceReports — read-only list of every maintenance report
     * (REPORTED / ASSIGNED / IN_PROGRESS / RESOLVED).  Used by the admin
     * to track maintenance activity at a glance.
     */
    private void viewMaintenanceReports() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MAINTENANCE REPORTS");

        System.out.println("  [0] Back  [1] All  [2] Unresolved  [3] Resolved");

        int filt = ConsoleUI.promptInt(sc, "Filter", 0, 3);
        if (filt == 0)
            return;

        ArrayList<MaintenanceReport> list = filt == 3 ? new ArrayList<>()
                : filt == 2 ? maintService.getOpenReports()
                : maintService.getAllReports();

        if (filt == 3) {
            for (MaintenanceReport r : maintService.getAllReports())
                if (r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED) list.add(r);
        }

        if (list.isEmpty()) {
            ConsoleUI.printInfo("No reports found."); ConsoleUI.pressEnter(sc);
            return;
        }

        ConsoleUI.printDivider();
        for (MaintenanceReport r : list) {
            Facility f = ds.getFacilityById(r.getFacilityId());
            User reporter = ds.getUserById(r.getReportedByUserId());
            String col = r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED
                    ? ConsoleUI.GREEN : ConsoleUI.YELLOW;

            System.out.printf(" %-8s %-28s %s%-12s%s [%s]%n",
                    r.getReportId(), f != null ? f.getName() : r.getFacilityId(),
                    col, r.getStatus(), ConsoleUI.RESET, r.getCategory());

            System.out.printf(" By: %-20s  Issue: %s%n",
                    reporter != null ? reporter.getName() : r.getReportedByUserId(),
                    r.getIssueDescription());

            if (r.getAssignedTo() != null) {
                User assigned = ds.getUserById(r.getAssignedTo());
                System.out.printf(" Assigned to: %s%n",
                        assigned != null ? assigned.getName() : r.getAssignedTo());
            }
            ConsoleUI.printDivider();
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ASSIGN MAINTENANCE TASK — option 5
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * assignMaintenanceTask — admin picks an unresolved report and a
     * maintenance worker, then assigns the report to that worker.
     * MaintenanceService.assignReport() also flips the report status
     * from REPORTED to ASSIGNED automatically.
     */
    private void assignMaintenanceTask() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("ASSIGN MAINTENANCE TASK");

        ArrayList<MaintenanceReport> open = maintService.getOpenReports();
        if (open.isEmpty()) {
            ConsoleUI.printInfo("No unresolved reports."); ConsoleUI.pressEnter(sc);
            return;
        }

        System.out.printf("  %s[0]%s Back%n%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < open.size(); i++) {
            MaintenanceReport r = open.get(i);
            Facility f = ds.getFacilityById(r.getFacilityId());
            System.out.printf("  %s[%d]%s %-8s %-28s [%s] %s%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    r.getReportId(), f != null ? f.getName() : r.getFacilityId(),
                    r.getCategory(), r.getIssueDescription());
        }

        int sel = ConsoleUI.promptInt(sc, "Select report", 0, open.size());
        if (sel == 0)
            return;
        MaintenanceReport target = open.get(sel - 1);

        // Show maintenance personnel list for easy selection
        ArrayList<User> personnel = new ArrayList<>();
        for (User u : ds.getAllUsers())
            if (u.getRole() == User.UserRole.MAINTENANCE) personnel.add(u);

        String personnelId;
        if (!personnel.isEmpty()) {
            System.out.println();
            ConsoleUI.printInfo("Available Maintenance Personnel:");

            for (User u : personnel)
                ConsoleUI.printField("  " + u.getUserId(), u.getName()
                    + " (" + ((MaintenancePersonnel) u).getSpecialization() + ")");
        }
        // Validate personnel ID — must exist AND must be a MAINTENANCE user
        while (true) {
            personnelId = ConsoleUI.prompt(sc, "Enter personnel User ID to assign (0 to cancel)");

            if (personnelId.equals("0"))
                return;
            if (personnelId.isBlank()) {
                ConsoleUI.printError("User ID cannot be empty. Please try again.");
                continue;
            }

            User chosen = ds.getUserById(personnelId);
            if (chosen == null) {
                ConsoleUI.printError("User ID '" + personnelId + "' does not exist. Please try again.");
                continue;
            }
            if (chosen.getRole() != User.UserRole.MAINTENANCE) {
                ConsoleUI.printError("User '" + personnelId + "' is not a maintenance personnel. Please try again.");
                continue;
            }
            personnelId = chosen.getUserId(); // normalise to canonical casing (e.g. maint01 -> MAINT01)
            break;
        }

        if (maintService.assignReport(target.getReportId(), personnelId))
            ConsoleUI.printSuccess("Report " + target.getReportId() + " assigned to " + personnelId + ".");
        else
            ConsoleUI.printError("Assignment failed.");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ANALYTICS — options 6, 7, 8, 9
    //  Each method delegates to AnalyticsService which does the actual work.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * utilizationReport — show booking + maintenance counts per facility,
     * filtered by ALL / MONTHLY / SEMESTER / YEARLY (admin picks which).
     * The follow-up prompts read the period's "value" (e.g. "04/2026").
     */
    private void utilizationReport() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("FACILITY UTILIZATION REPORT");

        System.out.println("  [1] All  [2] Monthly  [3] Semester  [4] Yearly");

        int p = ConsoleUI.promptInt(sc, "Select period", 1, 4);
        String period = "ALL", value = "";
        if (p == 2) {
            while (true) {
                value = ConsoleUI.prompt(sc, "Enter month/year (MM/yyyy, e.g. 04/2026) — 0 to cancel");

                if (value.equals("0"))
                    return;

                if (isValidMonthYear(value))
                    break;
                ConsoleUI.printError("Invalid month/year. Format MM/yyyy, month 01-12, year " + MIN_YEAR + "-" + InputValidator.BOOKING_YEAR + ".");
            }
            period = "MONTHLY";
        } else if (p == 3) {
            while (true) {
                value = ConsoleUI.prompt(sc, "Enter semester/year (SEM1/2026, SEM2/2026, or SEM3/2026) — 0 to cancel");

                if (value.equals("0"))
                    return;

                if (isValidSemesterYear(value))
                    break;
                ConsoleUI.printError("Invalid semester/year. Must be SEM1/SEM2/SEM3 + /yyyy (" + MIN_YEAR + "-" + InputValidator.BOOKING_YEAR + ").");
            }
            period = "SEMESTER";
        } else if (p == 4) {
            while (true) {
                value = ConsoleUI.prompt(sc, "Enter year (e.g. 2026) — 0 to cancel");

                if (value.equals("0"))
                    return;

                if (isValidYear(value))
                    break;
                ConsoleUI.printError("Invalid year. Must be between " + MIN_YEAR + " and " + InputValidator.BOOKING_YEAR + ".");
            }
            period = "YEARLY";
        }
        System.out.println();
        analyticsService.printUtilizationReport(period, value);
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Input helpers — small private methods that ask for the "value"
    //  parameter of the utilization report (e.g. month, year, semester).
    // ─────────────────────────────────────────────────────────────────────────
    // Accepted year range: bookings only exist from MIN_YEAR up to the booking year
    // defined in InputValidator. Future years make no sense (no data) and are rejected.
    private static final int MIN_YEAR = 2020;

    // Valid year: exactly 4 digits AND within accepted range
    private boolean isValidYear(String s) {
        if (s == null || s.length() != 4)
            return false;

        for (int i = 0; i < 4; i++)
            if (s.charAt(i) < '0' || s.charAt(i) > '9')
                return false;
        int year = Integer.parseInt(s);
        return year >= MIN_YEAR && year <= InputValidator.BOOKING_YEAR;
    }

    // Valid month/year: "MM/yyyy" — 7 chars, '/' at position 2, month 01-12, year in range.
    // Delegates year check to isValidYear so range rules are applied consistently.
    private boolean isValidMonthYear(String s) {
        if (s == null || s.length() != 7)
            return false;

        if (s.charAt(2) != '/')
            return false;
        // Check month part (first 2 chars) is digits
        for (int i = 0; i < 2; i++)
            if (s.charAt(i) < '0' || s.charAt(i) > '9')
                return false;

        int month = Integer.parseInt(s.substring(0, 2));

        if (month < 1 || month > 12)
            return false;
        // Check year part (last 4 chars) using the shared year validator
        return isValidYear(s.substring(3));
    }

    // Valid semester/year: "SEM1/yyyy", "SEM2/yyyy", or "SEM3/yyyy" — case-insensitive
    private boolean isValidSemesterYear(String s) {
        if (s == null || s.length() != 9)
            return false;
        String prefix = s.substring(0, 4).toUpperCase();

        if (!prefix.equals("SEM1") && !prefix.equals("SEM2") && !prefix.equals("SEM3"))
            return false;

        if (s.charAt(4) != '/')
            return false;
        return isValidYear(s.substring(5));
    }

    /** peakHoursReport — small wrapper that prints the peak-hours bar chart. */
    private void peakHoursReport() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("PEAK BOOKING HOURS");
        System.out.println();

        analyticsService.printPeakHoursReport();
        ConsoleUI.pressEnter(sc);
    }

    /** facilityTypeReport — bookings counted by FacilityType. */
    private void facilityTypeReport() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("FACILITY TYPE USAGE REPORT");
        System.out.println();

        analyticsService.printFacilityTypeReport();
        ConsoleUI.pressEnter(sc);
    }

    /** maintenanceSummary — totals + average repair time (in days). */
    private void maintenanceSummary() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MAINTENANCE SUMMARY REPORT");

        System.out.println();
        analyticsService.printMaintenanceSummary();
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW ALL USERS — option 10
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewAllUsers — print every registered user as a table.  Read-only;
     * the admin uses this mainly to find a userId before assigning tasks.
     */
    private void viewAllUsers() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("ALL REGISTERED USERS");

        System.out.printf("  %-10s %-25s %-35s %-15s %-10s%n",
                "ID", "Name", "Email", "Contact", "Role");
        ConsoleUI.printDivider();

        for (User u : ds.getAllUsers()) {
            System.out.printf("  %-10s %-25s %-35s %-15s %-10s%n",
                    u.getUserId(), u.getName(), u.getEmail(),
                    u.getContactNumber(), u.getRoleLabel());
        }

        System.out.println();
        ConsoleUI.printInfo("Total: " + ds.getAllUsers().size() + " user(s).");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW MAINTENANCE SCHEDULE — option 12
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewMaintenanceSchedule — show every open (un-resolved) maintenance
     * report as a simple "schedule" so the admin can plan repairs.
     * Satisfies the assignment requirement to manage maintenance schedules.
     */
    private void viewMaintenanceSchedule() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MAINTENANCE SCHEDULE");
        System.out.println();

        ArrayList<MaintenanceReport> open = maintService.getOpenReports();
        if (open.isEmpty()) {
            ConsoleUI.printInfo("No active maintenance tasks.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        System.out.printf("  %-8s %-28s %-14s %-12s %-20s%n",
                "ID", "Facility", "Category", "Status", "Assigned To");
        ConsoleUI.printDivider();

        for (MaintenanceReport r : open) {
            Facility f  = ds.getFacilityById(r.getFacilityId());
            User assigned = r.getAssignedTo() != null ? ds.getUserById(r.getAssignedTo()) : null;
            System.out.printf("  %-8s %-28s %-14s %-12s %-20s%n",
                    r.getReportId(),
                    f != null ? f.getName() : r.getFacilityId(),
                    r.getCategory(),
                    r.getStatus(),
                    assigned != null ? assigned.getName() : "Unassigned");
        }
        System.out.println();
        ConsoleUI.printInfo("Total active tasks: " + open.size());
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND REMINDERS — option 11
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * sendReminders — manually trigger reminder notifications for any
     * approved bookings starting within the next 2 hours.  Normally this
     * also runs automatically every time a user opens UserMenu, but the
     * admin can also force it here.
     */
    private void sendReminders() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("SEND BOOKING REMINDERS");
        System.out.println();

        // Step 1: collect users who have upcoming APPROVED bookings
        ArrayList<User> usersWithBookings = new ArrayList<>();
        ArrayList<Integer> bookingCounts = new ArrayList<>();
        for (User u : ds.getAllUsers()) {
            int count = 0;

            for (Booking b : bookingService.getUserBookings(u.getUserId()))

                if (b.getStatus() == Booking.BookingStatus.APPROVED) count++;

            if (count > 0) {
                usersWithBookings.add(u);
                bookingCounts.add(count);
            }
        }

        if (usersWithBookings.isEmpty()) {
            ConsoleUI.printInfo("No users have upcoming approved bookings.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        // Step 2: show numbered list of users
        ConsoleUI.printInfo("Users with upcoming APPROVED bookings:");
        System.out.println();
        System.out.printf("  %s[0]%s Back%n", ConsoleUI.YELLOW, ConsoleUI.RESET);

        for (int i = 0; i < usersWithBookings.size(); i++) {
            User u = usersWithBookings.get(i);
            System.out.printf("  %s[%d]%s %-8s %-25s (%d booking%s)%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    u.getUserId(), u.getName(),
                    bookingCounts.get(i), bookingCounts.get(i) == 1 ? "" : "s");
        }

        // Step 3: pick one and show their approved bookings
        int sel = ConsoleUI.promptInt(sc, "Select user", 0, usersWithBookings.size());

        if (sel == 0)
            return;
        User chosen = usersWithBookings.get(sel - 1);

        System.out.println();
        ConsoleUI.printSuccess("Reminder(s) sent to " + chosen.getName() + ":");

        for (Booking b : bookingService.getUserBookings(chosen.getUserId())) {

            if (b.getStatus() != Booking.BookingStatus.APPROVED)
                continue;
            Facility f = ds.getFacilityById(b.getFacilityId());
            System.out.printf("    -> %s - %s at %s%n",
                    f != null ? f.getName() : b.getFacilityId(),
                    b.getDate(), b.getStartTime());
        }
        ConsoleUI.pressEnter(sc);
    }
}
