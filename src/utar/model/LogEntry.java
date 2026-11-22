package utar.model;

/**
 * LogEntry.java — One line of the activity log: who did what, and when.
 *
 * Entries are only ever added, never changed or removed, so every field
 * is final.  The admin reads them on the Activity Log tab.
 *
 * OOP concepts:
 *   • Encapsulation — read-only after creation.
 *   • Enum type     — Action is a fixed list of things that get logged.
 */
public class LogEntry {

    /** Everything the system records in the activity log. */
    public enum Action {
        LOGIN, LOGIN_FAILED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED,
        USER_REGISTERED, PROFILE_UPDATED, PASSWORD_CHANGED,
        BOOKING_CREATED, SERIES_CREATED, BOOKING_CHANGED, BOOKING_CANCELLED,
        BOOKING_APPROVED, BOOKING_REJECTED,
        REPORT_SUBMITTED, REPORT_ASSIGNED, REPORT_UPDATED,
        FACILITY_ADDED, FACILITY_EDITED, FACILITY_STATUS_CHANGED
    }

    private final String time;      // "dd/MM/yyyy HH:mm:ss"
    private final String userId;    // who did it ("-" if unknown, e.g. a mistyped email)
    private final Action action;
    private final String details;   // e.g. "BK0100 for Lecture Theatre KG01 on 25/11/2026"

    public LogEntry(String time, String userId, Action action, String details) {
        this.time = time;
        this.userId = userId;
        this.action = action;
        this.details = details;
    }

    public String getTime()    { return time; }
    public String getUserId()  { return userId; }
    public Action getAction()  { return action; }
    public String getDetails() { return details; }
}
