package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.MaintenanceService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * MyTasksPanel.java - Maintenance tab: the repair jobs assigned to me.
 *
 * GUI version of MaintenanceMenu options 1 to 3.  The worker marks a task
 * In Progress, or Resolved with optional notes.  The resolved date is
 * today's date, which AnalyticsService uses for the average repair time.
 * If the reporter attached a photo or PDF, "View attachment" shows it.
 */
class MyTasksPanel extends JPanel implements Refreshable {

    private static final String[] FILTERS = { "Open tasks", "Completed tasks" };

    private final User worker;
    private final MaintenanceService maintService = new MaintenanceService();
    private final DataStore ds = DataStore.getInstance();

    private final JComboBox<String> filterBox = new JComboBox<>(FILTERS);
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel("ID", "Facility", "Location",
            "Category", "Issue", "Reported on", "Status", "Resolved on", "Notes");
    private final JTable table = GuiUtil.createTable(model);
    private final JButton startButton = new JButton("Mark In Progress");
    private final JButton resolveButton = new JButton("Mark Resolved...");
    private final JButton viewButton = new JButton("View attachment");

    MyTasksPanel(User worker) {
        super(new BorderLayout(0, 6));
        this.worker = worker;
        GuiUtil.padded(this, 10);

        filterBox.addActionListener(e -> refresh());
        GuiUtil.setColumnWidths(table, 70, 200, 200, 130, 240, 90, 90, 90, 200);
        GuiUtil.setUpDateColumn(table, 5);
        GuiUtil.setUpDateColumn(table, 7);
        GuiUtil.fitText(table, 3, "Equipment Damage");
        GuiUtil.fitText(table, 6, "In Progress");
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        startButton.addActionListener(e -> markInProgress());
        resolveButton.addActionListener(e -> markResolved());
        viewButton.addActionListener(e -> GuiUtil.showAttachment(this, selectedTask()));

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("My maintenance tasks"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Show"), filterBox, countLabel), BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(GuiUtil.row(startButton, resolveButton, new JLabel("      "), viewButton), BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = GuiUtil.selectedId(table);
        boolean showCompleted = filterBox.getSelectedIndex() == 1;

        model.setRowCount(0);
        for (MaintenanceReport r : ds.getReportsAssignedTo(worker.getUserId())) {
            boolean resolved = r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED;
            if (resolved != showCompleted) {
                continue;
            }
            Facility f = ds.getFacilityById(r.getFacilityId());
            model.addRow(new Object[] { r.getReportId(),
                    f != null ? f.getName() : r.getFacilityId(),
                    f != null ? f.getLocation() : "-",
                    GuiUtil.pretty(r.getCategory()), r.getIssueDescription(),
                    r.getReportedDate(), GuiUtil.pretty(r.getStatus()),
                    GuiUtil.orDash(r.getResolvedDate()), GuiUtil.orDash(r.getResolutionNotes()) });
        }
        countLabel.setText(model.getRowCount() + " task(s)");

        GuiUtil.selectRowById(table, selected);
        updateButtons();
    }

    private MaintenanceReport selectedTask() {
        String id = GuiUtil.selectedId(table);
        return id == null ? null : ds.getReportById(id);
    }

    private void updateButtons() {
        MaintenanceReport r = selectedTask();
        boolean open = r != null && r.getStatus() != MaintenanceReport.ReportStatus.RESOLVED;
        startButton.setEnabled(open && r.getStatus() != MaintenanceReport.ReportStatus.IN_PROGRESS);
        resolveButton.setEnabled(open);
        viewButton.setEnabled(r != null && r.hasAttachment());
    }

    private void markInProgress() {
        MaintenanceReport r = selectedTask();
        if (r == null) {
            return;
        }
        maintService.updateStatus(worker.getUserId(), r.getReportId(), MaintenanceReport.ReportStatus.IN_PROGRESS,
                null, null);
        refresh();
    }

    private void markResolved() {
        MaintenanceReport r = selectedTask();
        if (r == null) {
            return;
        }
        // showInputDialog returns null when the user presses Cancel
        String notes = JOptionPane.showInputDialog(this,
                "What did you do to fix it? (optional)", "Resolve " + r.getReportId(),
                JOptionPane.QUESTION_MESSAGE);
        if (notes == null) {
            return;
        }
        maintService.updateStatus(worker.getUserId(), r.getReportId(), MaintenanceReport.ReportStatus.RESOLVED,
                notes.trim(), GuiUtil.today());
        GuiUtil.showInfo(this, "Report " + r.getReportId() + " marked as resolved.");
        refresh();
    }
}
