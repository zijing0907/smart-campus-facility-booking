package utar.model;

/**
 * Booking.java — Represents one reservation made by a user for a facility.
 *
 * OOP concepts:
 *   • Encapsulation — every field is private; outside code reads or
 *     changes the data only through getters and setters.
 *   • Use of enum types — BookingPurpose and BookingStatus give us a
 *     fixed list of valid options so the data is always clean.
 */
public class Booking {

    // ── Fields (Encapsulation: all private) ──────────────────────────────────
    private String bookingId;     // unique ID, e.g. "BK0001"
    private String userId;        // who made the booking
    private String facilityId;    // which facility was booked
    private String date;          // format: dd/MM/yyyy
    private String startTime;     // format: HH:mm  (24-hour clock)
    private String endTime;       // format: HH:mm
    private BookingPurpose purpose;       // why the booking was made
    private String description;   // free-text description / notes
    private BookingStatus status;        // current state of the booking
    private String adminRemark;   // remark left by admin on approve/reject
    private boolean reminderSent;  // true once the reminder has been shown
    private int attendees;         // how many people are coming (0 = not recorded, older bookings)
    private String seriesId;       // weekly series this belongs to, e.g. "RS0001" (null = one-off)

    /**
     * BookingPurpose — fixed list of allowed reasons for a booking.
     */
    public enum BookingPurpose {
        ACADEMIC,        // class / lecture / lab
        CO_CURRICULAR,   // club / sports / event
        OFFICIAL         // staff meeting / official UTAR event
    }

    /**
     * BookingStatus — the booking life cycle.
     *   PENDING   → waiting for admin to approve
     *   APPROVED  → admin approved it
     *   REJECTED  → admin rejected it
     *   CANCELLED → the user cancelled before / after approval
     */
    public enum BookingStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    /**
     * Constructor — creates a brand-new booking. It always starts as
     * PENDING with no reminder sent yet.  adminRemark is left null until
     * an admin processes the booking.
     */
    public Booking(String bookingId, String userId, String facilityId,
                   String date, String startTime, String endTime,
                   BookingPurpose purpose, String description)
    {
        this.bookingId    = bookingId;
        this.userId       = userId;
        this.facilityId   = facilityId;
        this.date         = date;
        this.startTime    = startTime;
        this.endTime      = endTime;
        this.purpose      = purpose;
        this.description  = description;
        this.status       = BookingStatus.PENDING;   // default state
        this.reminderSent = false;
    }

    // ── Getters (read-only access) ───────────────────────────────────────────
    public String getBookingId() {
        return bookingId;
    }
    public String getUserId() {
        return userId;
    }
    public String getFacilityId() {
        return facilityId;
    }
    public String getDate() {
        return date;
    }
    public String getStartTime() {
        return startTime;
    }
    public String getEndTime() {
        return endTime;
    }
    public BookingPurpose getPurpose() {
        return purpose;
    }
    public String getDescription() {
        return description;
    }
    public BookingStatus getStatus() {
        return status;
    }
    public String getAdminRemark()  {
        return adminRemark;
    }
    public boolean isReminderSent()  {
        return reminderSent;
    }

    // ── Setters (only the fields that are allowed to change) ─────────────────
    // Note: bookingId, userId, facilityId never change once created.
    public void setDate(String d) {
        this.date = d;
    }
    public void setStartTime(String t) {
        this.startTime = t;
    }
    public void setEndTime(String t) {
        this.endTime = t;
    }
    public void setPurpose(BookingPurpose p) {
        this.purpose = p;
    }
    public void setDescription(String d) {
        this.description = d;
    }
    public void setStatus(BookingStatus s) {
        this.status = s;
    }
    public void setAdminRemark(String r) {
        this.adminRemark = r;
    }
    public void setReminderSent(boolean b) {
        this.reminderSent = b;
    }

    // ── Attendees and weekly series ──────────────────────────────────────────
    public int getAttendees() {
        return attendees;
    }
    public void setAttendees(int n) {
        this.attendees = n;
    }
    public String getSeriesId() {
        return seriesId;
    }
    public void setSeriesId(String id) {
        this.seriesId = id;
    }
    /** true if this booking is one week of a repeating (weekly) booking. */
    public boolean isRecurring() {
        return seriesId != null;
    }

    /**
     * Short summary of this booking — used when printing a list of
     * bookings on screen.  Example output:
     *     [BK0001] F001 on 12/05/2026 09:00-11:00 [APPROVED]
     */
    @Override
    public String toString() {
        return "[" + bookingId + "] " + facilityId
             + " on " + date + " " + startTime + "-" + endTime
             + " [" + status + "]";
    }
}
