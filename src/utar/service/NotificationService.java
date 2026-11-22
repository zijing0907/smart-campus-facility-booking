package utar.service;

import utar.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

/**
 * NotificationService.java — Short messages that tell users what happened
 * while they were away.
 *
 * The other services call notify() at the moment something happens, e.g.
 *   • a booking is approved or rejected        → the person who booked it
 *   • a new booking request or issue report    → every admin
 *   • a report is assigned / resolved          → the worker / the reporter
 *   • a facility is closed for maintenance     → people with upcoming bookings there
 * Because the rules live here in the service layer, notifications are sent
 * whether the action happened in the console or in the GUI.
 *
 * All file I/O goes through DataStore (Singleton).
 */
public class NotificationService {

    private DataStore ds = DataStore.getInstance();

    /** notify — send one message to one user. */
    public void notify(String userId, String message) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        ds.addNotification(new Notification(ds.nextNotificationId(), userId, now, message, false));
    }

    /** notifyAdmins — send the same message to every admin account. */
    public void notifyAdmins(String message) {
        for (User u : ds.getAllUsers()) {
            if (u.getRole() == User.UserRole.ADMIN) {
                notify(u.getUserId(), message);
            }
        }
    }

    /** getFor — a user's notifications, newest first. */
    public ArrayList<Notification> getFor(String userId) {
        ArrayList<Notification> list = ds.getNotificationsFor(userId);
        Collections.reverse(list);   // stored oldest first
        return list;
    }

    /** getUnread — the ones the user hasn't seen yet, newest first. */
    public ArrayList<Notification> getUnread(String userId) {
        ArrayList<Notification> unread = new ArrayList<>();
        for (Notification n : getFor(userId)) {
            if (!n.isRead()) {
                unread.add(n);
            }
        }
        return unread;
    }

    public int unreadCount(String userId) {
        return getUnread(userId).size();
    }

    /** markAllRead — called once the user has seen their notifications. */
    public void markAllRead(String userId) {
        boolean changed = false;
        for (Notification n : ds.getNotificationsFor(userId)) {
            if (!n.isRead()) {
                n.markRead();
                changed = true;
            }
        }
        if (changed) {
            ds.saveNotificationChanges();
        }
    }
}
