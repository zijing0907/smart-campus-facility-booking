package utar.service;

import utar.model.LogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * ActivityLogService.java — Records who did what, and when, so the admin
 * office can look back at decisions (accountability).
 *
 * The other services call record() whenever something important happens:
 * logins and lock-outs, bookings made / changed / cancelled / approved /
 * rejected, reports submitted / assigned / updated, and facility changes.
 * Each service method that an admin or worker uses takes that person's ID
 * as its first parameter, so the log always knows who acted.
 *
 * Entries are only ever added (see DataStore.appendLog), never edited.
 */
public class ActivityLogService {

    private DataStore ds = DataStore.getInstance();

    /** record — add one entry, stamped with the current date and time. */
    public void record(String userId, LogEntry.Action action, String details) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        ds.appendLog(new LogEntry(now, userId == null ? "-" : userId, action, details));
    }

    /** getAll — every entry, newest first. */
    public ArrayList<LogEntry> getAll() {
        ArrayList<LogEntry> list = ds.getActivityLog();
        Collections.reverse(list);
        return list;
    }
}
