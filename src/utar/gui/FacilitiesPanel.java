package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.FacilityService;
import utar.service.MaintenanceService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.ParseException;

/**
 * FacilitiesPanel.java - Admin tab: every facility and its status.
 *
 * GUI version of AdminMenu option 3.  The admin can mark a facility as
 * Available, Under Maintenance or Closed through
 * MaintenanceService.setFacilityStatus().  Facilities that aren't
 * Available drop out of the students' search results automatically,
 * because BookingService.searchAvailable() skips them.
 *
 * The admin can also add new facilities and edit existing ones
 * (FacilityService checks the details).  There is no delete: bookings and
 * reports refer to facilities by ID, so a facility is retired by setting
 * it to Closed instead.
 *
 * The search box (GuiUtil.searchBox) filters the table as you type,
 * which is handy with 100+ facilities.
 */
class FacilitiesPanel extends JPanel implements Refreshable {

    private final User admin;   // who is making the changes (for the activity log)
    private final MaintenanceService maintService = new MaintenanceService();
    private final FacilityService facilityService = new FacilityService();
    private final DataStore ds = DataStore.getInstance();

    private final DefaultTableModel model = GuiUtil.readOnlyModel(
            "ID", "Facility", "Type", "Capacity", "Location", "Status", "Issues reported");
    private final JTable table = GuiUtil.createTable(model);
    private final JTextField searchField = GuiUtil.searchBox(table);
    private final JComboBox<String> statusBox =
            new JComboBox<>(GuiUtil.prettyNames(Facility.FacilityStatus.values()));
    private final JButton updateButton = new JButton("Update status");
    private final JButton editButton = new JButton("Edit...");

    FacilitiesPanel(User admin) {
        super(new BorderLayout(0, 6));
        this.admin = admin;
        GuiUtil.padded(this, 10);

        GuiUtil.setColumnWidths(table, 60, 280, 140, 70, 260, 130, 100);
        GuiUtil.fitText(table, 5, "Under Maintenance");
        GuiUtil.fitText(table, 6, "Issues reported");
        table.getSelectionModel().addListSelectionListener(e -> showSelection());

        JButton addButton = new JButton("Add facility...");
        addButton.addActionListener(e -> showFacilityForm(null));
        editButton.addActionListener(e -> showFacilityForm(ds.getFacilityById(GuiUtil.selectedId(table))));
        updateButton.addActionListener(e -> updateStatus());

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Facilities"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Search"), searchField,
                new JLabel("(name, ID, type, location or status)")), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(GuiUtil.row(addButton, editButton), BorderLayout.WEST);
        south.add(GuiUtil.row(new JLabel("Set selected facility to"), statusBox, updateButton),
                BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = GuiUtil.selectedId(table);

        model.setRowCount(0);
        for (Facility f : ds.getAllFacilities()) {
            model.addRow(new Object[] { f.getFacilityId(), f.getName(), GuiUtil.pretty(f.getType()),
                    f.getCapacity(), f.getLocation(), GuiUtil.pretty(f.getStatus()),
                    f.getMaintenanceCount() });
        }

        GuiUtil.selectRowById(table, selected);
        showSelection();
    }

    private void showSelection() {
        String id = GuiUtil.selectedId(table);
        updateButton.setEnabled(id != null);
        editButton.setEnabled(id != null);
        if (id != null) {
            statusBox.setSelectedIndex(ds.getFacilityById(id).getStatus().ordinal());
        }
    }

    private void updateStatus() {
        String id = GuiUtil.selectedId(table);
        if (id == null) {
            return;
        }
        Facility.FacilityStatus status = Facility.FacilityStatus.values()[statusBox.getSelectedIndex()];
        if (!maintService.setFacilityStatus(admin.getUserId(), id, status)) {
            GuiUtil.showError(this, "Facility " + id + " was not found.");
        }
        refresh();
    }

    /**
     * showFacilityForm - the Add / Edit dialog.  existing is null when
     * adding.  If FacilityService rejects the details, the error is shown
     * and the form opens again with everything the admin typed still in it.
     */
    private void showFacilityForm(Facility existing) {
        boolean adding = existing == null;

        JTextField nameField = new JTextField(adding ? "" : existing.getName(), 26);
        JComboBox<String> typeBox = new JComboBox<>(GuiUtil.prettyNames(Facility.FacilityType.values()));
        JSpinner capacitySpinner = new JSpinner(new SpinnerNumberModel(
                adding ? 40 : existing.getCapacity(), 1, FacilityService.MAX_CAPACITY, 1));
        JTextField locationField = new JTextField(adding ? "" : existing.getLocation(), 26);
        if (!adding) {
            typeBox.setSelectedIndex(existing.getType().ordinal());
        }

        JPanel form = GuiUtil.formPanel();
        GuiUtil.addFormRow(form, "Facility ID", new JLabel(adding
                ? ds.nextFacilityId() + " (given automatically)" : existing.getFacilityId()));
        GuiUtil.addFormRow(form, "Name", nameField);
        GuiUtil.addFormRow(form, "Type", typeBox);
        GuiUtil.addFormRow(form, "Capacity", capacitySpinner);
        GuiUtil.addFormRow(form, "Location", locationField);
        String title = adding ? "Add facility" : "Edit " + existing.getFacilityId();

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                capacitySpinner.commitEdit();   // pick up a number typed by hand
            } catch (ParseException e) {
                // not a number - the spinner keeps its last valid value
            }
            String name = nameField.getText();
            Facility.FacilityType type = Facility.FacilityType.values()[typeBox.getSelectedIndex()];
            int capacity = (Integer) capacitySpinner.getValue();
            String location = locationField.getText();

            try {
                String id;
                if (adding) {
                    id = facilityService.addFacility(admin.getUserId(), name, type, capacity, location)
                                        .getFacilityId();
                    GuiUtil.showInfo(this, name.trim() + " added as " + id + ".\nStudents can book it straight away.");
                } else {
                    id = existing.getFacilityId();
                    facilityService.updateFacility(admin.getUserId(), id, name, type, capacity, location);
                }
                searchField.setText("");   // make sure the row is visible
                refresh();
                GuiUtil.selectRowById(table, id);
                return;
            } catch (IllegalArgumentException ex) {
                GuiUtil.showError(this, ex.getMessage());   // then show the form again
            }
        }
    }
}
