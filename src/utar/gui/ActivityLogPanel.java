package utar.gui;

import utar.model.LogEntry;
import utar.service.ActivityLogService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * ActivityLogPanel.java - Admin tab: who did what, and when.
 *
 * Shows every entry from ActivityLogService, newest first: logins and
 * lock-outs, bookings made / changed / cancelled / approved / rejected,
 * maintenance reports, and facility changes.  The admin can narrow it to
 * one area and search it.  Entries can't be edited or deleted here.
 */
class ActivityLogPanel extends JPanel implements Refreshable {

    private static final String[] AREAS = { "Everything", "Bookings", "Maintenance", "Facilities", "Accounts" };

    private final ActivityLogService activityLog = new ActivityLogService();

    private final JComboBox<String> areaBox = new JComboBox<>(AREAS);
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel("Time", "User", "Action", "Details");
    private final JTable table = GuiUtil.createTable(model);
    private final JTextField searchField = GuiUtil.searchBox(table);

    ActivityLogPanel() {
        super(new BorderLayout(0, 6));
        GuiUtil.padded(this, 10);

        areaBox.addActionListener(e -> refresh());
        GuiUtil.setColumnWidths(table, 150, 190, 170, 620);
        GuiUtil.setUpDateColumn(table, 0);   // sorts by real date and time
        GuiUtil.fitText(table, 0, "00/00/0000 00:00:00");
        table.getRowSorter().addRowSorterListener(e -> updateCount());   // follow the search box

        JLabel note = new JLabel("Newest first. Entries are added automatically and can't be changed.");
        note.setForeground(Color.GRAY);

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Activity log"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Show"), areaBox, new JLabel("   Search"), searchField,
                countLabel), BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(GuiUtil.row(note), BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String area = (String) areaBox.getSelectedItem();
        model.setRowCount(0);
        for (LogEntry e : activityLog.getAll()) {
            if (!area.equals("Everything") && !area.equals(areaOf(e.getAction()))) {
                continue;
            }
            String who = e.getUserId().equals("-") ? "-"
                    : GuiUtil.userName(e.getUserId()) + " (" + e.getUserId() + ")";
            model.addRow(new Object[] { e.getTime(), who, GuiUtil.pretty(e.getAction()), e.getDetails() });
        }
        updateCount();
    }

    /** Which drop-down area an action belongs to. */
    private static String areaOf(LogEntry.Action action) {
        String name = action.name();
        if (name.startsWith("BOOKING_") || name.startsWith("SERIES_")) return "Bookings";
        if (name.startsWith("REPORT_"))   return "Maintenance";
        if (name.startsWith("FACILITY_")) return "Facilities";
        return "Accounts";
    }

    private void updateCount() {
        countLabel.setText("   " + table.getRowCount() + " entr" + (table.getRowCount() == 1 ? "y" : "ies"));
    }
}
