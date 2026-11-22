package utar.model;

/**
 * Notification.java — A short message for one user, e.g. "Your booking
 * BK0100 was approved."
 *
 * The services create notifications when something happens that a user
 * should know about (see NotificationService).  Users see them the next
 * time they open the app; once seen they are marked as read.
 *
 * OOP concepts:
 *   • Encapsulation — only the read flag can change after creation.
 */
public class Notification {

    private final String notificationId;   // e.g. "NT0001"
    private final String userId;           // who it is for
    private final String createdAt;        // "dd/MM/yyyy HH:mm"
    private final String message;
    private boolean read;

    public Notification(String notificationId, String userId, String createdAt,
                        String message, boolean read) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.message = message;
        this.read = read;
    }

    public String getNotificationId() { return notificationId; }
    public String getUserId()         { return userId; }
    public String getCreatedAt()      { return createdAt; }
    public String getMessage()        { return message; }
    public boolean isRead()           { return read; }

    public void markRead() {
        this.read = true;
    }
}
