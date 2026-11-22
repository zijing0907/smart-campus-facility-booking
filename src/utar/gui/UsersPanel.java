package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.UserService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * UsersPanel.java - Admin tab: every registered account (read-only).
 *
 * GUI version of AdminMenu option 10.  The admin can narrow the list by
 * role and search by any column, e.g. to find a worker's user ID.
 * Passwords are never shown.
 *
 * The Status column shows accounts locked after too many wrong passwords
 * (see UserService.login); "Unlock account" ends the lock early.
 */
class UsersPanel extends JPanel implements Refreshable {

    // index 0 = all roles, the rest match User.UserRole in the same order
    private static final String[] ROLE_FILTERS = { "All roles", "Students", "Staff", "Maintenance", "Admins" };
    private static final User.UserRole[] ROLES = {
            null, User.UserRole.STUDENT, User.UserRole.STAFF, User.UserRole.MAINTENANCE, User.UserRole.ADMIN };

    private final User admin;   // who is unlocking (for the activity log)
    private final DataStore ds = DataStore.getInstance();
    private final UserService userService = new UserService();

    private final JComboBox<String> roleBox = new JComboBox<>(ROLE_FILTERS);
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel(
            "ID", "Name", "Email", "Contact", "Role", "Faculty", "Programme / Department", "Status");
    private final JTable table = GuiUtil.createTable(model);
    private final JButton unlockButton = new JButton("Unlock account");
    private final JTextField searchField = GuiUtil.searchBox(table);

    UsersPanel(User admin) {
        super(new BorderLayout(0, 6));
        this.admin = admin;
        GuiUtil.padded(this, 10);

        roleBox.addActionListener(e -> refresh());
        GuiUtil.setColumnWidths(table, 70, 180, 200, 110, 120, 260, 200, 150);
        GuiUtil.fitText(table, 3, "000-00000000");   // phone numbers in full
        GuiUtil.fitText(table, 7, "Locked until 00:00");
        table.getSelectionModel().addListSelectionListener(e -> updateUnlockButton());
        unlockButton.addActionListener(e -> unlockSelected());
        // keep the count right while the search box hides rows
        table.getRowSorter().addRowSorterListener(e -> updateCount());

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Registered users"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Show"), roleBox, new JLabel("   Search"), searchField,
                countLabel), BorderLayout.CENTER);

        JLabel lockNote = new JLabel("Accounts lock for " + UserService.LOCK_MINUTES + " minutes after "
                + UserService.MAX_FAILED_LOGINS + " wrong passwords in a row.");
        lockNote.setForeground(Color.GRAY);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(GuiUtil.row(unlockButton, lockNote), BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        User.UserRole role = ROLES[roleBox.getSelectedIndex()];

        model.setRowCount(0);
        for (User u : ds.getAllUsers()) {
            if (role != null && u.getRole() != role) {
                continue;
            }
            model.addRow(new Object[] { u.getUserId(), u.getName(), u.getEmail(),
                    u.getContactNumber(), u.getRoleLabel(), GuiUtil.orDash(u.getFaculty()),
                    extraDetail(u), statusText(u) });
        }
        updateCount();
        updateUnlockButton();
    }

    /** "Active", or "Locked until 13:05" while a lock-out is running. */
    private static String statusText(User u) {
        if (!u.isLocked()) {
            return "Active";
        }
        return "Locked until " + u.getLockedUntil().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void updateUnlockButton() {
        String id = GuiUtil.selectedId(table);
        User u = id == null ? null : ds.getUserById(id);
        unlockButton.setEnabled(u != null && u.isLocked());
    }

    private void unlockSelected() {
        String id = GuiUtil.selectedId(table);
        if (id != null && userService.unlock(admin.getUserId(), id)) {
            GuiUtil.showInfo(this, GuiUtil.userName(id) + " can log in again.");
        }
        String keep = id;
        refresh();
        GuiUtil.selectRowById(table, keep);
    }

    /** Programme (students), department (staff) or specialisation (maintenance). */
    private static String extraDetail(User u) {
        if (u instanceof CampusUser) {
            return GuiUtil.orDash(((CampusUser) u).getExtra());
        }
        if (u instanceof MaintenancePersonnel) {
            return GuiUtil.orDash(((MaintenancePersonnel) u).getSpecialization());
        }
        return "-";   // admins have neither
    }

    private void updateCount() {
        countLabel.setText("   " + table.getRowCount() + " user(s)");
    }
}
