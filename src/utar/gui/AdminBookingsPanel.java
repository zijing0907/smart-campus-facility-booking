package utar.gui;

import utar.model.*;
import utar.service.BookingService;
import utar.service.DataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

/**
 * AdminBookingsPanel.java - Admin tab: view bookings and approve or reject them.
 *
 * GUI version of AdminMenu options 1 and 2.  When a pending booking is
 * selected, BookingService.getConflictingApproved() checks whether it
 * overlaps a booking that is already approved, and a red warning shows
 * the clash before the admin decides.
 *
 * For one week of a weekly booking, Approve / Reject asks whether to
 * decide every pending week of the series at once
 * (BookingService.processSeries) or just the selected week.
 */
class AdminBookingsPanel extends JPanel implements Refreshable {

    private static final String[] FILTERS = { "Pending", "All", "Approved", "Rejected", "Cancelled" };

    private final User admin;   // who decides (for the activity log)
    private final BookingService bookingService = new BookingService();
    private final DataStore ds = DataStore.getInstance();

    private final JComboBox<String> filterBox = new JComboBox<>(FILTERS);
    private final JLabel countLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel("ID", "Booked by", "Facility",
            "Date", "Time", "People", "Purpose", "Repeats", "Status", "Description", "Admin remark");
    private final JTable table = GuiUtil.createTable(model);
    private final JLabel conflictLabel = GuiUtil.errorLabel();
    private final JTextField remarkField = new JTextField(30);
    private final JButton approveButton = new JButton("Approve");
    private final JButton rejectButton = new JButton("Reject");

    AdminBookingsPanel(User admin) {
        super(new BorderLayout(0, 6));
        this.admin = admin;
        GuiUtil.padded(this, 10);

        filterBox.addActionListener(e -> refresh());
        GuiUtil.setColumnWidths(table, 70, 140, 190, 100, 110, 60, 110, 110, 90, 160, 130);
        GuiUtil.setUpDateColumn(table, 3);
        GuiUtil.fitText(table, 4, "00:00 - 00:00");
        GuiUtil.fitText(table, 5, "People");
        GuiUtil.fitText(table, 7, "Weekly 10 of 14");
        GuiUtil.fitText(table, 8, "Cancelled");
        table.getSelectionModel().addListSelectionListener(e -> showSelection());

        approveButton.addActionListener(e -> decide(true));
        rejectButton.addActionListener(e -> decide(false));

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Booking requests"), BorderLayout.NORTH);
        north.add(GuiUtil.row(new JLabel("Show"), filterBox, countLabel), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(GuiUtil.row(conflictLabel), BorderLayout.NORTH);
        south.add(GuiUtil.row(new JLabel("Remark (optional)"), remarkField,
                approveButton, rejectButton), BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = GuiUtil.selectedId(table);

        // "Pending" asks the service for pending bookings; the other choices
        // start from every booking and keep only the matching status ("All" keeps all)
        String filter = (String) filterBox.getSelectedItem();
        ArrayList<Booking> list = filter.equals("Pending")
                ? bookingService.getPendingBookings()
                : ds.getAllBookings();

        model.setRowCount(0);
        for (Booking b : list) {
            String status = GuiUtil.pretty(b.getStatus());
            if (!filter.equals("All") && !filter.equals(status)) {
                continue;
            }
            model.addRow(new Object[] { b.getBookingId(), GuiUtil.userName(b.getUserId()),
                    GuiUtil.facilityName(b.getFacilityId()), b.getDate(),
                    b.getStartTime() + " - " + b.getEndTime(), GuiUtil.peopleText(b),
                    GuiUtil.pretty(b.getPurpose()), GuiUtil.seriesText(b),
                    status, b.getDescription(), GuiUtil.orDash(b.getAdminRemark()) });
        }
        countLabel.setText(model.getRowCount() + " booking(s)");

        GuiUtil.selectRowById(table, selected);
        showSelection();
    }

    private Booking selectedBooking() {
        String id = GuiUtil.selectedId(table);
        return id == null ? null : ds.getBookingById(id);
    }

    /** Enable the buttons for pending bookings and warn about clashes. */
    private void showSelection() {
        Booking b = selectedBooking();
        boolean pending = b != null && b.getStatus() == Booking.BookingStatus.PENDING;
        approveButton.setEnabled(pending);
        rejectButton.setEnabled(pending);

        ArrayList<Booking> conflicts = pending ? findConflicts(b) : new ArrayList<>();
        if (conflicts.isEmpty()) {
            conflictLabel.setText(" ");
        } else {
            StringBuilder text = new StringBuilder("Warning: overlaps approved booking ");
            for (int i = 0; i < conflicts.size(); i++) {
                Booking c = conflicts.get(i);
                text.append(i == 0 ? "" : ", ").append(c.getBookingId()).append(" (")
                    .append(c.getStartTime()).append(" - ").append(c.getEndTime())
                    .append(", ").append(GuiUtil.userName(c.getUserId())).append(")");
            }
            conflictLabel.setText(text.toString());
        }
    }

    private ArrayList<Booking> findConflicts(Booking b) {
        return bookingService.getConflictingApproved(b.getFacilityId(), b.getDate(),
                b.getStartTime(), b.getEndTime(), b.getBookingId());
    }

    /** Approve or reject the selected booking (or its whole series), with the optional remark. */
    private void decide(boolean approve) {
        Booking b = selectedBooking();
        if (b == null) {
            return;
        }
        String remark = remarkField.getText().trim();
        if (b.isRecurring() && decideSeries(b, approve, remark.isEmpty() ? null : remark)) {
            return;   // the whole series was handled
        }
        if (approve && !findConflicts(b).isEmpty()
                && !GuiUtil.confirm(this, "This booking overlaps an approved booking. Approve it anyway?")) {
            return;
        }

        boolean done = bookingService.processBooking(admin.getUserId(), b.getBookingId(), approve,
                remark.isEmpty() ? null : remark);
        if (done) {
            remarkField.setText("");
        } else {
            GuiUtil.showError(this, "Booking " + b.getBookingId() + " is no longer pending.");
        }
        refresh();
    }

    /**
     * decideSeries - for a weekly booking, ask whether to approve / reject
     * every pending week or just this one.
     * @return true if the series was handled here (or the admin cancelled),
     *         false to carry on with just the selected week
     */
    private boolean decideSeries(Booking b, boolean approve, String remark) {
        int pendingWeeks = 0, clashingWeeks = 0;
        for (Booking week : bookingService.getSeries(b.getSeriesId())) {
            if (week.getStatus() == Booking.BookingStatus.PENDING) {
                pendingWeeks++;
                if (!findConflicts(week).isEmpty()) {
                    clashingWeeks++;
                }
            }
        }
        if (pendingWeeks <= 1) {
            return false;   // only this week is left - treat it like a one-off
        }

        String verb = approve ? "Approve" : "Reject";
        String[] options = { verb + " all " + pendingWeeks + " weeks", "Just this week", "Cancel" };
        int choice = JOptionPane.showOptionDialog(this,
                b.getBookingId() + " is part of weekly booking " + b.getSeriesId() + " ("
                + pendingWeeks + " weeks still pending).",
                verb + " weekly booking", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice == 1) {
            return false;   // just this week
        }
        if (choice != 0) {
            return true;    // closed or cancelled - do nothing
        }
        if (approve && clashingWeeks > 0 && !GuiUtil.confirm(this, clashingWeeks
                + " of these weeks overlap an approved booking. Approve them all anyway?")) {
            return true;
        }
        int done = bookingService.processSeries(admin.getUserId(), b.getSeriesId(), approve, remark);
        remarkField.setText("");
        GuiUtil.showInfo(this, done + " week(s) of " + b.getSeriesId() + (approve ? " approved." : " rejected."));
        refresh();
        return true;
    }
}
