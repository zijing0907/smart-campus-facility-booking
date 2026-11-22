package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.MaintenanceService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

/**
 * MaintenanceReportsPanel.java - Admin tab: maintenance reports and assigning them.
 *
 * GUI version of AdminMenu options 4, 5 and 12 (view reports, assign a
 * task, maintenance schedule).  MaintenanceService.assignReport() stores
 * the worker and moves the report to ASSIGNED.  If the reporter attached a
 * photo or PDF, "View attachment" shows it.
 */
class MaintenanceReportsPanel extends JPanel implements Refreshable {

    private static final String[] FILTERS = { "Unresolved", "All", "Resolved" };

    private final User admin;   // who is assigning (for the activity log)
    private final MaintenanceService maintService = new MaintenanceService();
    private final DataStore ds = DataStore.getInstance();

    private final JComboBox<String> filterBox = new JComboBox<>(FILTERS);
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel("ID", "Facility", "Category",
            "Issue", "Reported by", "Reported on", "Status", "Assigned to", "Resolved on", "Attachment");
    private final JTable table = GuiUtil.createTable(model);

    // Maintenance staff drop-down: the text shown, and the user at the same index
    private final ArrayList<User> personnel = new ArrayList<>();
    private final JComboBox<String> personnelBox = new JComboBox<>();
    private final JButton assignButton = new JButton("Assign");
    private final JButton viewButton = new JButton("View attachment");

    MaintenanceReportsPanel(User admin) {
        super(new BorderLayout(0, 6));
        this.admin = admin;
        GuiUtil.padded(this, 10);

        for (User u : ds.getAllUsers()) {
            if (u instanceof MaintenancePersonnel) {
                personnel.add(u);
                personnelBox.addItem(u.getUserId() + "  -  " + u.getName() + "  ("
                        + ((MaintenancePersonnel) u).getSpecialization() + ")");
            }
        }

        filterBox.addActionListener(e -> refresh());
        GuiUtil.setColumnWidths(table, 70, 190, 130, 220, 120, 90, 90, 120, 90, 90);
        GuiUtil.setUpDateColumn(table, 5);
        GuiUtil.setUpDateColumn(table, 8);
        GuiUtil.fitText(table, 2, "Equipment Damage");
        GuiUtil.fitText(table, 6, "In Progress");
        GuiUtil.fitText(table, 9, "Attachment");
        table.getSelectionModel().addListSelectionListener(e -> showSelection());
        assignButton.addActionListener(e -> assign());
        viewButton.addActionListener(e ->
                GuiUtil.showAttachment(this, ds.getReportById(GuiUtil.selectedId(table))));

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Maintenance reports"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Show"), filterBox, countLabel), BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(GuiUtil.row(new JLabel("Assign selected report to"), personnelBox, assignButton,
                new JLabel("      "), viewButton), BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = GuiUtil.selectedId(table);
        String filter = (String) filterBox.getSelectedItem();

        model.setRowCount(0);
        for (MaintenanceReport r : maintService.getAllReports()) {
            boolean resolved = r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED;
            if (filter.equals("Unresolved") && resolved) continue;
            if (filter.equals("Resolved") && !resolved)  continue;

            model.addRow(new Object[] { r.getReportId(), GuiUtil.facilityName(r.getFacilityId()),
                    GuiUtil.pretty(r.getCategory()), r.getIssueDescription(),
                    GuiUtil.userName(r.getReportedByUserId()), r.getReportedDate(),
                    GuiUtil.pretty(r.getStatus()),
                    r.getAssignedTo() == null ? "Unassigned" : GuiUtil.userName(r.getAssignedTo()),
                    GuiUtil.orDash(r.getResolvedDate()), GuiUtil.attachmentKind(r) });
        }
        countLabel.setText(model.getRowCount() + " report(s)");

        GuiUtil.selectRowById(table, selected);
        showSelection();
    }

    /** Only unresolved reports can be (re)assigned. */
    private void showSelection() {
        String id = GuiUtil.selectedId(table);
        MaintenanceReport r = id == null ? null : ds.getReportById(id);
        assignButton.setEnabled(r != null && !personnel.isEmpty()
                && r.getStatus() != MaintenanceReport.ReportStatus.RESOLVED);
        viewButton.setEnabled(r != null && r.hasAttachment());
    }

    private void assign() {
        String id = GuiUtil.selectedId(table);
        if (id == null || personnelBox.getSelectedIndex() < 0) {
            return;
        }
        User worker = personnel.get(personnelBox.getSelectedIndex());

        if (maintService.assignReport(admin.getUserId(), id, worker.getUserId())) {
            GuiUtil.showInfo(this, "Report " + id + " assigned to " + worker.getName() + ".");
        } else {
            GuiUtil.showError(this, "Report " + id + " was not found.");
        }
        refresh();
    }
}
