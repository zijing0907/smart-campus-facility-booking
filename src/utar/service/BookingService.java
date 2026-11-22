package utar.service;

import utar.model.*;
import utar.util.InputValidator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;

/**
 * BookingService.java — Business logic for facility bookings.
 *
 * Handles every booking-related use-case:
 *   • search for available facilities at a given date/time
 *   • create a new booking
 *   • modify or cancel an existing booking
 *   • admin approve / reject (processBooking)
 *   • find conflicts when admin approves
 *   • collect bookings that need reminder notifications
 *   • weekly (recurring) bookings: the same slot for 2 to 14 weeks, booked
 *     all or nothing and approved / cancelled as one series
 *   • the number of people must fit the facility's capacity
 * Important actions are written to the activity log and the people
 * affected are notified (ActivityLogService, NotificationService).
 *
 * It uses DataStore (Singleton) for all reads/writes — this keeps file
 * handling out of the business logic and makes the code easier to read.
 *
 * Exception handling: when a rule is broken (e.g. cancelling within
 * 24 hours, time conflict) we throw IllegalStateException / IllegalArgumentException
 * so the calling UI can show a clear message to the user.
 */
public class BookingService {

    // Single shared DataStore — all reads and writes go through it.
    private DataStore ds = DataStore.getInstance();

    private NotificationService notifier = new NotificationService();
    private ActivityLogService activityLog = new ActivityLogService();

    /** Longest weekly booking: one 14-week trimester. */
    public static final int MAX_WEEKS = 14;

    /** Shown when someone tries to book or move a booking to a time that has passed. */
    public static final String PAST_SLOT_MESSAGE =
        "That time has already passed. Please choose a later date or time.";

    /**
     * overlaps — small helper that decides whether two time ranges on the
     * same date conflict with each other.
     * Returns false straight away if the dates don't even match.
     * Otherwise compares HH:mm strings — they sort the same way as numbers
     * because of the fixed format (e.g. "09:00" < "11:00").
     */
    private boolean overlaps(String date1, String start1, String end1,
                              String date2, String start2, String end2)
    {
        if (!date1.equals(date2)) {
            return false;   // different days → can't overlap
        }
        // Standard interval-overlap formula:  start1 < end2  AND  start2 < end1
        return start1.compareTo(end2) < 0
            && start2.compareTo(end1) < 0;
    }

    /**
     * searchAvailable — find every facility that is free in the given slot.
     * Steps for each facility:
     *   1. Skip if the user filtered by type and the type doesn't match.
     *   2. Skip if the facility is closed or under maintenance.
     *   3. Loop through the facility's existing bookings; if any of them
     *      overlap with the requested slot (and aren't cancelled/rejected),
     *      skip this facility.
     *   4. Otherwise, add it to the result list.
     */
    public ArrayList<Facility> searchAvailable(String date, String startTime,
                                                String endTime,
                                                Facility.FacilityType type)
    {
        return searchAvailable(date, startTime, endTime, type, 0);
    }

    /**
     * searchAvailable — same as above, but only facilities big enough for
     * the given number of people (0 = any size).
     */
    public ArrayList<Facility> searchAvailable(String date, String startTime,
                                                String endTime,
                                                Facility.FacilityType type,
                                                int people)
    {
        ArrayList<Facility> result = new ArrayList<>();

        for (Facility f : ds.getAllFacilities()) {

            // (1) facility-type filter
            if (type != null && f.getType() != type) {
                continue;
            }

            // (1b) must be big enough for everyone
            if (people > 0 && f.getCapacity() < people) {
                continue;
            }

            // (2) only AVAILABLE facilities can be booked
            if (f.getStatus() != Facility.FacilityStatus.AVAILABLE) {
                continue;
            }

            // (3) check for time-clash with existing bookings
            boolean conflict = false;
            for (Booking b : ds.getBookingsByFacility(f.getFacilityId())) {

                // cancelled / rejected bookings don't block anything
                if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
                if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;

                if (overlaps(date, startTime, endTime,
                             b.getDate(), b.getStartTime(), b.getEndTime())) {
                    conflict = true;
                    break;   // stop checking this facility — it's busy
                }
            }

            // (4) free → add to result
            if (!conflict) {
                result.add(f);
            }
        }

        return result;
    }

    /**
     * searchAvailableWeekly — facilities that are free in the same slot
     * EVERY week for the given number of weeks (e.g. every Monday 09:00-11:00
     * for 6 weeks).  A facility that is busy in even one of those weeks is
     * left out, so whatever this returns can be booked as a weekly series.
     * @throws IllegalArgumentException if a later week falls outside the
     *         dates that can be booked (see InputValidator.isValidDate)
     */
    public ArrayList<Facility> searchAvailableWeekly(String firstDate, String startTime,
                                                      String endTime,
                                                      Facility.FacilityType type,
                                                      int people, int weeks)
    {
        ArrayList<String> dates = seriesDates(firstDate, weeks);
        checkSeriesDates(dates);

        // start with the facilities free in week 1, then drop any that are
        // busy in a later week
        ArrayList<Facility> result = searchAvailable(firstDate, startTime, endTime, type, people);
        for (int i = 1; i < dates.size(); i++) {
            ArrayList<Facility> freeThatWeek = searchAvailable(dates.get(i), startTime, endTime, type, people);
            result.removeIf(f -> !freeThatWeek.contains(f));
        }
        return result;
    }

    /**
     * seriesDates — the dates of a weekly series: the first date, then
     * 7 days later, 14 days later, ... (weeks dates in total).
     */
    public static ArrayList<String> seriesDates(String firstDate, int weeks)
    {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate first = LocalDate.parse(firstDate, fmt);
        ArrayList<String> dates = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            dates.add(first.plusWeeks(w).format(fmt));
        }
        return dates;
    }

    /** Every week must be a date that can be booked (this year or next). */
    private void checkSeriesDates(ArrayList<String> dates)
    {
        for (String d : dates) {
            if (!InputValidator.isValidDate(d)) {
                throw new IllegalArgumentException("The weekly booking would run until " + d
                    + ", but bookings can only be made up to the end of "
                    + InputValidator.maxBookingYear() + ". Choose fewer weeks.");
            }
        }
    }

    /**
     * checkAttendees — the group must have at least 1 person and fit in the
     * facility (its capacity).
     */
    private void checkAttendees(Facility f, int attendees)
    {
        if (attendees < 1) {
            throw new IllegalArgumentException("Enter how many people will attend (at least 1).");
        }
        if (attendees > f.getCapacity()) {
            throw new IllegalArgumentException(f.getName() + " holds " + f.getCapacity()
                + " people, but the booking is for " + attendees + ".");
        }
    }

    /**
     * createBooking — make a new booking record.
     * Throws (Exception Handling, ch09):
     *   • IllegalArgumentException — if the slot has already started, the
     *                                facility ID doesn't exist, or the group
     *                                doesn't fit (attendees vs capacity)
     *   • IllegalStateException    — if the facility is not AVAILABLE
     *                                or if the slot conflicts with another booking
     */
    public Booking createBooking(String userId, String facilityId,
                                  String date, String startTime, String endTime,
                                  Booking.BookingPurpose purpose, String description,
                                  int attendees)
    {
        // (0) can't book a time that has already passed
        if (InputValidator.hasStarted(date, startTime)) {
            throw new IllegalArgumentException(PAST_SLOT_MESSAGE);
        }

        // (1) facility must exist
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }

        // (2) facility must be available
        if (f.getStatus() != Facility.FacilityStatus.AVAILABLE) {
            throw new IllegalStateException("Facility is not available.");
        }

        // (2b) everyone must fit
        checkAttendees(f, attendees);

        // (3) check for time-clash with existing bookings
        for (Booking b : ds.getBookingsByFacility(facilityId)) {
            if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
            if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;

            if (overlaps(date, startTime, endTime,
                         b.getDate(), b.getStartTime(), b.getEndTime())) {
                throw new IllegalStateException("Time slot conflict with existing booking.");
            }
        }

        // (4) all checks passed — create and save the booking
        String id = ds.nextBookingId();
        Booking booking = new Booking(id, userId, facilityId, date, startTime,
                                       endTime, purpose, description);
        booking.setAttendees(attendees);
        ds.addBooking(booking);

        activityLog.record(userId, LogEntry.Action.BOOKING_CREATED, describe(booking));
        notifier.notifyAdmins("New booking request " + id + " from " + userName(userId) + ": "
                + f.getName() + " on " + date + ", " + startTime + "-" + endTime
                + " for " + attendees + " people.");
        return booking;
    }

    /**
     * createRecurringBooking — book the same slot every week, e.g. every
     * Monday 09:00-11:00 for 6 weeks.  All the weeks share one series ID
     * (RS0001, ...) so they can be approved or cancelled together.
     *
     * All or nothing: every week is checked first, and if ANY week can't be
     * booked (clash, not available, too many people) nothing is saved and
     * the message lists the weeks that failed.
     * @return the new bookings, one per week, in date order
     */
    public ArrayList<Booking> createRecurringBooking(String userId, String facilityId,
                                                      String firstDate, String startTime,
                                                      String endTime, int weeks,
                                                      Booking.BookingPurpose purpose,
                                                      String description, int attendees)
    {
        if (weeks < 2 || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("A weekly booking can repeat for 2 to "
                + MAX_WEEKS + " weeks.");
        }
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }
        if (f.getStatus() != Facility.FacilityStatus.AVAILABLE) {
            throw new IllegalStateException("Facility is not available.");
        }
        checkAttendees(f, attendees);
        if (InputValidator.hasStarted(firstDate, startTime)) {
            throw new IllegalArgumentException(PAST_SLOT_MESSAGE);
        }
        ArrayList<String> dates = seriesDates(firstDate, weeks);
        checkSeriesDates(dates);

        // (1) check every week BEFORE saving anything
        ArrayList<String> busyWeeks = new ArrayList<>();
        for (String date : dates) {
            for (Booking b : ds.getBookingsByFacility(facilityId)) {
                if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
                if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;
                if (overlaps(date, startTime, endTime, b.getDate(), b.getStartTime(), b.getEndTime())) {
                    busyWeeks.add(date);
                    break;
                }
            }
        }
        if (!busyWeeks.isEmpty()) {
            throw new IllegalStateException("Nothing was booked: " + f.getName()
                + " is already taken at that time on " + String.join(", ", busyWeeks) + ".");
        }

        // (2) every week is free — create them all with the same series ID
        String seriesId = ds.nextSeriesId();
        ArrayList<Booking> created = new ArrayList<>();
        for (String date : dates) {
            Booking b = new Booking(ds.nextBookingId(), userId, facilityId, date,
                                    startTime, endTime, purpose, description);
            b.setAttendees(attendees);
            b.setSeriesId(seriesId);
            ds.addBooking(b);
            created.add(b);
        }

        String summary = seriesId + ": " + f.getName() + ", every " + dayName(firstDate) + " "
                + startTime + "-" + endTime + " for " + weeks + " weeks from " + firstDate;
        activityLog.record(userId, LogEntry.Action.SERIES_CREATED, summary);
        notifier.notifyAdmins("New weekly booking request " + summary + " (" + attendees
                + " people), from " + userName(userId) + ".");
        return created;
    }

    /**
     * modifyBooking — let the owner change the date/time/description.
     * Rules:
     *   • Only the booking's owner may modify it.
     *   • Only PENDING bookings can be modified
     *     (once approved/rejected the time slot is "locked").
     *   • New time slot must not have started yet.
     *   • New time slot must not clash with other active bookings.
     *   • The number of people must still fit (newAttendees 0 = keep it).
     */
    public boolean modifyBooking(String bookingId, String userId,
                                  String newDate, String newStart, String newEnd,
                                  String newDesc, int newAttendees)
    {
        Booking b = ds.getBookingById(bookingId);

        // not found or not the owner
        if (b == null || !b.getUserId().equals(userId)) {
            return false;
        }

        // only PENDING bookings can be modified — Exception Handling (ch09)
        if (b.getStatus() != Booking.BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be modified.");
        }

        // the new time slot must still be in the future
        if (InputValidator.hasStarted(newDate, newStart)) {
            throw new IllegalArgumentException(PAST_SLOT_MESSAGE);
        }

        // the group must still fit in the facility
        if (newAttendees > 0) {
            checkAttendees(ds.getFacilityById(b.getFacilityId()), newAttendees);
        }

        // make sure the new time slot doesn't clash with other bookings
        for (Booking other : ds.getBookingsByFacility(b.getFacilityId())) {
            if (other.getBookingId().equals(bookingId)) continue; // skip self
            if (other.getStatus() == Booking.BookingStatus.CANCELLED) continue;
            if (other.getStatus() == Booking.BookingStatus.REJECTED)  continue;

            if (overlaps(newDate, newStart, newEnd,
                         other.getDate(), other.getStartTime(), other.getEndTime())) {
                throw new IllegalStateException("Time slot conflict.");
            }
        }

        // save the new values
        b.setDate(newDate);
        b.setStartTime(newStart);
        b.setEndTime(newEnd);
        if (newDesc != null && !newDesc.isBlank()) {
            b.setDescription(newDesc);
        }
        if (newAttendees > 0) {
            b.setAttendees(newAttendees);
        }

        ds.addBooking(b);   // persist
        activityLog.record(userId, LogEntry.Action.BOOKING_CHANGED, describe(b));
        return true;
    }

    /**
     * cancelBooking — owner cancels their own booking.
     * Cancellation policy (Exception Handling, ch09):
     *   • Cannot cancel within 24 hours of the booking start time
     *     — encourages users to plan ahead.
     */
    public boolean cancelBooking(String bookingId, String userId)
    {
        Booking b = ds.getBookingById(bookingId);

        // not found or not the owner
        if (b == null || !b.getUserId().equals(userId)) {
            return false;
        }

        // already cancelled — block the duplicate action
        if (b.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new IllegalStateException("Already cancelled.");
        }

        // 24-hour rule
        if (isWithin24Hours(b.getDate(), b.getStartTime())) {
            throw new IllegalStateException(
                "Cannot cancel within 24 hours of the booking start time.");
        }

        // all good — flip status and save
        b.setStatus(Booking.BookingStatus.CANCELLED);
        ds.addBooking(b);
        activityLog.record(userId, LogEntry.Action.BOOKING_CANCELLED, describe(b));
        return true;
    }

    /**
     * cancelSeries — cancel every remaining week of a weekly booking.
     * The 24-hour rule still applies to each week, so weeks starting within
     * 24 hours (or already over) are kept.
     * @return how many weeks were cancelled
     */
    public int cancelSeries(String seriesId, String userId)
    {
        int cancelled = 0;
        for (Booking b : getSeries(seriesId)) {
            boolean active = b.getStatus() == Booking.BookingStatus.PENDING
                          || b.getStatus() == Booking.BookingStatus.APPROVED;
            if (b.getUserId().equals(userId) && active
                    && !isWithin24Hours(b.getDate(), b.getStartTime())) {
                b.setStatus(Booking.BookingStatus.CANCELLED);
                ds.addBooking(b);
                cancelled++;
            }
        }
        if (cancelled > 0) {
            activityLog.record(userId, LogEntry.Action.BOOKING_CANCELLED,
                    cancelled + " week(s) of series " + seriesId);
        }
        return cancelled;
    }

    /**
     * isWithin24Hours — helper that returns true when the booking's start
     * time is less than 24 hours from now.
     * It works by converting both timestamps into comparable yyyyMMddHHmm
     * strings and comparing them. Catches any parsing error and returns
     * false to be safe.
     */
    private boolean isWithin24Hours(String bookingDate, String startTime)
    {
        try {
            // Turn dd/MM/yyyy + HH:mm  →  yyyyMMddHHmm  (one big sortable number)
            String[] dp = bookingDate.split("/");
            String bookingKey = dp[2] + dp[1] + dp[0] + startTime.replace(":", "");

            // Compare against now+24h in the same format
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().plusHours(24);
            String cutoffKey = cutoff.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

            return bookingKey.compareTo(cutoffKey) <= 0;

        } catch (Exception e) {
            // Bad date/time format — be safe, treat as "not within 24h"
            return false;
        }
    }

    /**
     * processBooking — admin approves or rejects a pending booking.
     * Returns false if the booking doesn't exist or is no longer PENDING.
     * Otherwise sets the new status and stores the admin's remark.
     */
    public boolean processBooking(String adminId, String bookingId, boolean approve, String remark)
    {
        Booking b = ds.getBookingById(bookingId);

        // can only process bookings that are still pending
        if (b == null || b.getStatus() != Booking.BookingStatus.PENDING) {
            return false;
        }

        // approve  → APPROVED, otherwise REJECTED
        b.setStatus(approve ? Booking.BookingStatus.APPROVED
                            : Booking.BookingStatus.REJECTED);
        b.setAdminRemark(remark);

        ds.addBooking(b);
        activityLog.record(adminId, approve ? LogEntry.Action.BOOKING_APPROVED
                                            : LogEntry.Action.BOOKING_REJECTED, describe(b));
        notifier.notify(b.getUserId(), "Your booking " + b.getBookingId() + " for "
                + facilityName(b.getFacilityId()) + " on " + b.getDate() + ", "
                + b.getStartTime() + "-" + b.getEndTime() + " was "
                + (approve ? "approved" : "rejected") + "."
                + (remark == null ? "" : " Remark: " + remark));
        return true;
    }

    /**
     * processSeries — approve or reject every week of a weekly booking that
     * is still PENDING, in one go.  The person who booked gets one
     * notification for the whole series.
     * @return how many weeks were changed
     */
    public int processSeries(String adminId, String seriesId, boolean approve, String remark)
    {
        ArrayList<Booking> pending = new ArrayList<>();
        for (Booking b : getSeries(seriesId)) {
            if (b.getStatus() == Booking.BookingStatus.PENDING) {
                pending.add(b);
            }
        }
        if (pending.isEmpty()) {
            return 0;
        }
        for (Booking b : pending) {
            b.setStatus(approve ? Booking.BookingStatus.APPROVED : Booking.BookingStatus.REJECTED);
            b.setAdminRemark(remark);
            ds.addBooking(b);
        }
        Booking first = pending.get(0);
        String summary = pending.size() + " week(s) of series " + seriesId + " ("
                + facilityName(first.getFacilityId()) + ", every " + dayName(first.getDate()) + " "
                + first.getStartTime() + "-" + first.getEndTime() + ")";
        activityLog.record(adminId, approve ? LogEntry.Action.BOOKING_APPROVED
                                            : LogEntry.Action.BOOKING_REJECTED, summary);
        notifier.notify(first.getUserId(), "Your weekly booking " + summary + " was "
                + (approve ? "approved" : "rejected") + "."
                + (remark == null ? "" : " Remark: " + remark));
        return pending.size();
    }

    /** getSeries — every booking in a weekly series, in date order. */
    public ArrayList<Booking> getSeries(String seriesId)
    {
        ArrayList<Booking> result = new ArrayList<>();
        if (seriesId == null) {
            return result;
        }
        for (Booking b : ds.getAllBookings()) {
            if (seriesId.equals(b.getSeriesId())) {
                result.add(b);
            }
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        result.sort((a, c) -> LocalDate.parse(a.getDate(), fmt).compareTo(LocalDate.parse(c.getDate(), fmt)));
        return result;
    }

    // ── Small text helpers for log entries and notifications ──────────────────

    /** "BK0100 for Lecture Theatre KG01 on 25/11/2026 09:00-11:00" */
    private String describe(Booking b)
    {
        return b.getBookingId() + " for " + facilityName(b.getFacilityId()) + " on "
             + b.getDate() + " " + b.getStartTime() + "-" + b.getEndTime();
    }

    private String facilityName(String facilityId)
    {
        Facility f = ds.getFacilityById(facilityId);
        return f != null ? f.getName() : facilityId;
    }

    private String userName(String userId)
    {
        User u = ds.getUserById(userId);
        return u != null ? u.getName() : userId;
    }

    /** "25/11/2026" -> "Wednesday" */
    private static String dayName(String date)
    {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        .getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /**
     * getConflictingApproved — find all APPROVED bookings that overlap with
     * the given slot.  Used by the admin screen so the admin can see at a
     * glance whether approving a new booking would create a clash.
     * The excludeBookingId parameter lets us skip the booking we're
     * currently inspecting (so it doesn't appear to clash with itself).
     */
    public ArrayList<Booking> getConflictingApproved(String facilityId, String date,
                                                      String start, String end,
                                                      String excludeBookingId)
    {
        ArrayList<Booking> result = new ArrayList<>();

        for (Booking b : ds.getBookingsByFacility(facilityId)) {
            if (b.getBookingId().equals(excludeBookingId)) continue; // skip self
            if (b.getStatus() != Booking.BookingStatus.APPROVED)  continue;

            if (overlaps(date, start, end,
                         b.getDate(), b.getStartTime(), b.getEndTime())) {
                result.add(b);
            }
        }

        return result;
    }

    /**
     * getPendingBookings — return every booking that is still waiting for
     * admin approval. Used by the admin "process bookings" screen.
     */
    public ArrayList<Booking> getPendingBookings()
    {
        ArrayList<Booking> result = new ArrayList<>();
        for (Booking b : ds.getAllBookings()) {
            if (b.getStatus() == Booking.BookingStatus.PENDING) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * getActiveBookingsOn — a facility's PENDING and APPROVED bookings on
     * one date (dd/MM/yyyy), earliest first.  Cancelled and rejected
     * bookings don't hold the room, so they're left out.  Used by the
     * weekly timetable.
     */
    public ArrayList<Booking> getActiveBookingsOn(String facilityId, String date)
    {
        ArrayList<Booking> result = new ArrayList<>();
        for (Booking b : ds.getBookingsByFacility(facilityId)) {
            boolean active = b.getStatus() == Booking.BookingStatus.PENDING
                          || b.getStatus() == Booking.BookingStatus.APPROVED;
            if (active && b.getDate().equals(date)) {
                result.add(b);
            }
        }
        // HH:mm strings sort the same way as times
        result.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        return result;
    }

    /**
     * getUserBookings — return all bookings made by a specific user.
     * Convenience wrapper around DataStore.
     */
    public ArrayList<Booking> getUserBookings(String userId) {
        return ds.getBookingsByUser(userId);
    }

    /**
     * getDueReminders — find the given user's bookings that should trigger
     * a reminder right now.
     *
     * A booking is "due" when:
     *   • it belongs to the user
     *   • its status is APPROVED
     *   • its reminder hasn't been sent yet
     *   • its date is today
     *   • it starts within the next 2 hours
     * Once a reminder is shown, the booking's reminderSent flag is set so
     * the same reminder isn't shown twice.  Only the user's own bookings
     * are checked, so logging in never uses up someone else's reminder.
     */
    public ArrayList<Booking> getDueReminders(String userId, String currentDateStr,
                                              String currentTimeStr)
    {
        ArrayList<Booking> due = new ArrayList<>();

        for (Booking b : ds.getBookingsByUser(userId)) {
            if (b.getStatus() != Booking.BookingStatus.APPROVED) continue;
            if (b.isReminderSent())                              continue;
            if (!b.getDate().equals(currentDateStr))             continue;

            // Both times are HH:mm — convert to total minutes for easy math
            int bookingMins  = InputValidator.toMinutes(b.getStartTime());
            int currentMins  = InputValidator.toMinutes(currentTimeStr);
            int diff = bookingMins - currentMins;

            // 0..120 minutes from now → fire the reminder
            if (diff >= 0 && diff <= 120) {
                due.add(b);
                b.setReminderSent(true);   // mark it so we don't repeat
                ds.addBooking(b);
            }
        }

        return due;
    }
}
