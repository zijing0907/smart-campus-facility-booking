package utar.service;

import utar.model.*;
import utar.util.InputValidator;
import java.util.ArrayList;

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
        ArrayList<Facility> result = new ArrayList<>();

        for (Facility f : ds.getAllFacilities()) {

            // (1) facility-type filter
            if (type != null && f.getType() != type) {
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
     * createBooking — make a new booking record.
     * Throws (Exception Handling, ch09):
     *   • IllegalArgumentException — if the facility ID doesn't exist
     *   • IllegalStateException    — if the facility is not AVAILABLE
     *                                or if the slot conflicts with another booking
     */
    public Booking createBooking(String userId, String facilityId,
                                  String date, String startTime, String endTime,
                                  Booking.BookingPurpose purpose, String description)
    {
        // (1) facility must exist
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }

        // (2) facility must be available
        if (f.getStatus() != Facility.FacilityStatus.AVAILABLE) {
            throw new IllegalStateException("Facility is not available.");
        }

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
        ds.addBooking(booking);
        return booking;
    }

    /**
     * modifyBooking — let the owner change the date/time/description.
     * Rules:
     *   • Only the booking's owner may modify it.
     *   • Only PENDING bookings can be modified
     *     (once approved/rejected the time slot is "locked").
     *   • New time slot must not clash with other active bookings.
     */
    public boolean modifyBooking(String bookingId, String userId,
                                  String newDate, String newStart, String newEnd,
                                  String newDesc)
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

        ds.addBooking(b);   // persist
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
        return true;
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
    public boolean processBooking(String bookingId, boolean approve, String remark)
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
        return true;
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
     * getUserBookings — return all bookings made by a specific user.
     * Convenience wrapper around DataStore.
     */
    public ArrayList<Booking> getUserBookings(String userId) {
        return ds.getBookingsByUser(userId);
    }

    /**
     * getDueReminders — find bookings that should trigger a reminder right now.
     *
     * A booking is "due" when:
     *   • its status is APPROVED
     *   • its reminder hasn't been sent yet
     *   • its date is today
     *   • it starts within the next 2 hours
     * Once a reminder is shown, the booking's reminderSent flag is set so
     * the same reminder isn't shown twice.
     */
    public ArrayList<Booking> getDueReminders(String currentDateStr, String currentTimeStr)
    {
        ArrayList<Booking> due = new ArrayList<>();

        for (Booking b : ds.getAllBookings()) {
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
