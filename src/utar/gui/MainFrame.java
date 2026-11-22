package utar.gui;

import utar.model.*;
import utar.service.BookingService;
import utar.service.MaintenanceService;
import utar.service.NotificationService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

/**
 * MainFrame.java - The main window after login, with one tab per feature.
 *
 * Which tabs appear depends on the user's role, the same way Main.java
 * opens a different console menu for each role:
 *   Student / Staff - Book a Facility, Timetable, My Bookings, Report an Issue, My Profile
 *   Admin           - Bookings, Timetable, Facilities, Maintenance, Reports, Users
 *   Maintenance     - My Tasks
 *
 * Every time a tab is opened, MainFrame calls its refresh() method (see
 * Refreshable) so it shows the latest data.
 *
 * The header also has a Notifications button showing how many new
 * messages the user has (see NotificationService).
 */
class MainFrame extends JFrame {

    private final User user;
    private final BookingService bookingService = new BookingService();
    private final MaintenanceService maintService = new MaintenanceService();
    private final NotificationService notifier = new NotificationService();
    private final JButton notificationsButton = new JButton();

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel alertLabel = new JLabel();   // admin only: frequent-issue alerts
    private final JLabel subtitleLabel = new JLabel(); // "Logged in as ..." in the header

    MainFrame(User user) {
        super("UTAR Smart Campus - " + portalName(user));
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // -- Header with the user's name and a Logout button ---------------
        updateHeader();
        JPanel header = GuiUtil.headerBand("UTAR Smart Campus \u2014 " + portalName(user), subtitleLabel);
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout());
        JPanel logoutHolder = new JPanel(new GridBagLayout());   // centres the button vertically
        logoutHolder.setOpaque(false);
        notificationsButton.addActionListener(e -> showNotifications());
        logoutHolder.add(notificationsButton);
        logoutHolder.add(Box.createHorizontalStrut(8));
        logoutHolder.add(logoutButton);
        header.add(logoutHolder, BorderLayout.EAST);

        alertLabel.setOpaque(true);
        alertLabel.setBackground(GuiUtil.ALERT_BG);
        alertLabel.setForeground(GuiUtil.BAD);
        alertLabel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        alertLabel.setVisible(false);

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(alertLabel, BorderLayout.SOUTH);

        // -- Tabs for this role (Polymorphism: same User type, different screens)
        if (user.getRole() == User.UserRole.ADMIN) {
            tabs.addTab("Bookings", new AdminBookingsPanel(user));
            tabs.addTab("Timetable", new TimetablePanel(user));
            tabs.addTab("Facilities", new FacilitiesPanel(user));
            tabs.addTab("Maintenance", new MaintenanceReportsPanel(user));
            tabs.addTab("Reports", new ReportsPanel(user));
            tabs.addTab("Users", new UsersPanel(user));
            tabs.addTab("Activity Log", new ActivityLogPanel());
        } else if (user.getRole() == User.UserRole.MAINTENANCE) {
            tabs.addTab("My Tasks", new MyTasksPanel(user));
        } else {
            BookFacilityPanel bookPanel = new BookFacilityPanel(user);
            TimetablePanel timetable = new TimetablePanel(user);
            // clicking an empty timetable slot jumps to the booking form, filled in
            timetable.setSlotListener((facilityId, date, startTime) -> {
                tabs.setSelectedComponent(bookPanel);
                bookPanel.prefill(facilityId, date, startTime);
            });
            tabs.addTab("Book a Facility", bookPanel);
            tabs.addTab("Timetable", timetable);
            tabs.addTab("My Bookings", new MyBookingsPanel(user));
            tabs.addTab("Report an Issue", new ReportIssuePanel(user));
            // this::updateHeader is passed in so a new name shows up straight away
            tabs.addTab("My Profile", new ProfilePanel(user, this::updateHeader));
        }
        tabs.addChangeListener(e -> refreshCurrentTab());
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // Students and staff see their reminders once the window is on screen
        if (user instanceof CampusUser) {
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    showReminders();
                }
            });
        }

        setSize(1120, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        refreshCurrentTab();
    }

    private static String portalName(User user) {
        switch (user.getRole()) {
            case ADMIN:       return "Admin Panel";
            case MAINTENANCE: return "Maintenance Portal";
            default:          return "User Portal";
        }
    }

    /** Show the user's current name, role and faculty in the header. */
    private void updateHeader() {
        String subtitle = "Logged in as " + user.getName() + " (" + user.getRoleLabel() + ")";
        if (user.getFaculty() != null && !user.getFaculty().isBlank()) {
            subtitle += "   \u00B7   " + user.getFaculty();
        }
        subtitleLabel.setText(subtitle);
    }

    /** Reload the open tab, and the admin alert banner. */
    private void refreshCurrentTab() {
        Component current = tabs.getSelectedComponent();
        if (current instanceof Refreshable) {
            ((Refreshable) current).refresh();
        }
        updateNotificationsButton();
        if (user.getRole() == User.UserRole.ADMIN) {
            updateAlerts();
        }
    }

    /** "Notifications (2 new)" in bold when there's something unread. */
    private void updateNotificationsButton() {
        int unread = notifier.unreadCount(user.getUserId());
        notificationsButton.setText(unread == 0 ? "Notifications" : "Notifications (" + unread + " new)");
        notificationsButton.setFont(notificationsButton.getFont().deriveFont(unread == 0 ? Font.PLAIN : Font.BOLD));
    }

    /** List the user's notifications (newest first, new ones in bold), then mark them read. */
    private void showNotifications() {
        java.util.ArrayList<Notification> list = notifier.getFor(user.getUserId());
        JPanel entries = new JPanel();
        entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
        if (list.isEmpty()) {
            entries.add(new JLabel("No notifications yet."));
        }
        Font plain = new JLabel().getFont();
        for (Notification n : list) {
            // new ones in bold; wrapped to fit the window (wrapHtml also escapes the text)
            String text = GuiUtil.wrapHtml(n.getMessage(), n.isRead() ? plain : plain.deriveFont(Font.BOLD), 430);
            JLabel entry = new JLabel("<html>" + (n.isRead() ? text : "<b>" + text + "</b>")
                    + "<br><font color='#6B7280'>" + n.getCreatedAt() + "</font></html>");
            entry.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE3, 0xE7, 0xED)),
                    BorderFactory.createEmptyBorder(6, 2, 6, 2)));
            entries.add(entry);
        }
        JScrollPane scroll = new JScrollPane(entries, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(500, Math.min(380, 40 + list.size() * 60)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JOptionPane.showMessageDialog(this, scroll, "Notifications", JOptionPane.PLAIN_MESSAGE);
        notifier.markAllRead(user.getUserId());
        updateNotificationsButton();
    }

    /** Show a red banner for facilities with too many unresolved issues. */
    private void updateAlerts() {
        ArrayList<String> alerts = maintService.getFrequentIssueAlerts();
        StringBuilder text = new StringBuilder("<html>");
        for (String alert : alerts) {
            text.append(GuiUtil.html(alert)).append("<br>");
        }
        alertLabel.setText(text.append("</html>").toString());
        alertLabel.setVisible(!alerts.isEmpty());
    }

    /** Pop up approved bookings that start within 2 hours (once per booking). */
    private void showReminders() {
        ArrayList<Booking> due = bookingService.getDueReminders(
                user.getUserId(), GuiUtil.today(), GuiUtil.nowTime());
        if (due.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Your booking starts soon:\n\n");
        for (Booking b : due) {
            message.append(GuiUtil.facilityName(b.getFacilityId()))
                   .append(" at ").append(b.getStartTime())
                   .append(" today (").append(b.getBookingId()).append(")\n");
        }
        JOptionPane.showMessageDialog(this, message.toString(), "Reminder",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Close this window and go back to the login screen. */
    private void logout() {
        dispose();
        LoginFrame.open();
    }
}
