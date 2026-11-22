package utar.gui;

import utar.model.*;
import utar.service.AnalyticsService;
import utar.service.BookingService;
import utar.service.DataStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.ParseException;
import java.util.EnumMap;

/**
 * MyBookingsPanel.java - Student / Staff tab: the user's own bookings.
 *
 * GUI version of UserMenu options 3, 4, 5 and 7 (view, change, cancel,
 * stats).  The service still enforces the rules:
 *   - only PENDING bookings can be changed  (BookingService.modifyBooking)
 *   - no cancelling within 24 hours of start (BookingService.cancelBooking)
 *   - the number of people must fit the facility
 * Those methods throw an exception with a message, and we show it.
 *
 * For one week of a weekly booking, Cancel asks whether to cancel just
 * that week or every remaining week (BookingService.cancelSeries).
 */
class MyBookingsPanel extends JPanel implements Refreshable {

    private final User user;
    private final BookingService bookingService = new BookingService();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final DataStore ds = DataStore.getInstance();

    private final JLabel statsLabel = new JLabel();
    private final DefaultTableModel model = GuiUtil.readOnlyModel(
            "ID", "Facility", "Date", "Time", "People", "Purpose", "Repeats", "Status",
            "Description", "Admin remark");
    private final JTable table = GuiUtil.createTable(model);
    private final JButton changeButton = new JButton("Change booking...");
    private final JButton cancelButton = new JButton("Cancel booking");

    MyBookingsPanel(User user) {
        super(new BorderLayout(0, 6));
        this.user = user;
        GuiUtil.padded(this, 10);

        GuiUtil.setColumnWidths(table, 70, 210, 100, 110, 60, 110, 110, 90, 180, 150);
        GuiUtil.setUpDateColumn(table, 2);
        GuiUtil.fitText(table, 3, "00:00 - 00:00");
        GuiUtil.fitText(table, 4, "People");
        GuiUtil.fitText(table, 6, "Weekly 10 of 14");
        GuiUtil.fitText(table, 7, "Cancelled");
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        changeButton.addActionListener(e -> changeSelected());
        cancelButton.addActionListener(e -> cancelSelected());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("My bookings"), BorderLayout.NORTH);
        north.add(statsLabel, BorderLayout.SOUTH);

        JLabel rules = new JLabel("Only pending bookings can be changed. "
                + "Bookings can't be cancelled within 24 hours of the start time.");
        rules.setForeground(Color.GRAY);

        JPanel south = new JPanel(new BorderLayout());
        south.add(GuiUtil.row(changeButton, cancelButton, refreshButton), BorderLayout.WEST);
        south.add(rules, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    @Override
    public void refresh() {
        String selected = GuiUtil.selectedId(table);

        model.setRowCount(0);
        for (Booking b : bookingService.getUserBookings(user.getUserId())) {
            model.addRow(new Object[] { b.getBookingId(), GuiUtil.facilityName(b.getFacilityId()),
                    b.getDate(), b.getStartTime() + " - " + b.getEndTime(), GuiUtil.peopleText(b),
                    GuiUtil.pretty(b.getPurpose()), GuiUtil.seriesText(b), GuiUtil.pretty(b.getStatus()),
                    b.getDescription(), GuiUtil.orDash(b.getAdminRemark()) });
        }

        // Booking counts by status, from the same method the console stats use
        EnumMap<Booking.BookingStatus, Integer> c = analyticsService.getUserBookingCounts(user.getUserId());
        statsLabel.setText("Total: " + model.getRowCount()
                + "     Approved: " + c.get(Booking.BookingStatus.APPROVED)
                + "     Pending: " + c.get(Booking.BookingStatus.PENDING)
                + "     Rejected: " + c.get(Booking.BookingStatus.REJECTED)
                + "     Cancelled: " + c.get(Booking.BookingStatus.CANCELLED));

        GuiUtil.selectRowById(table, selected);
        updateButtons();
    }

    private Booking selectedBooking() {
        String id = GuiUtil.selectedId(table);
        return id == null ? null : ds.getBookingById(id);
    }

    /** Enable each button only when it applies to the selected booking. */
    private void updateButtons() {
        Booking b = selectedBooking();
        changeButton.setEnabled(b != null && b.getStatus() == Booking.BookingStatus.PENDING);
        cancelButton.setEnabled(b != null && (b.getStatus() == Booking.BookingStatus.PENDING
                                           || b.getStatus() == Booking.BookingStatus.APPROVED));
    }

    /** Show a small form to change the date, time or description. */
    private void changeSelected() {
        Booking b = selectedBooking();
        if (b == null) {
            return;
        }

        JSpinner dateSpinner = GuiUtil.createDateSpinner(b.getDate());
        JComboBox<String> startBox = GuiUtil.timeBox(0, GuiUtil.TIME_SLOTS.length - 2, b.getStartTime());
        JComboBox<String> endBox = GuiUtil.timeBox(1, GuiUtil.TIME_SLOTS.length - 1, b.getEndTime());
        JTextField descField = new JTextField(b.getDescription(), 26);
        JSpinner peopleSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1, b.getAttendees()), 1, 1000, 1));

        JPanel form = GuiUtil.formPanel();
        GuiUtil.addFormRow(form, "Facility", new JLabel(GuiUtil.facilityName(b.getFacilityId())));
        GuiUtil.addFormRow(form, "Date", dateSpinner);
        GuiUtil.addFormRow(form, "From", startBox);
        GuiUtil.addFormRow(form, "To", endBox);
        GuiUtil.addFormRow(form, "People", peopleSpinner);
        GuiUtil.addFormRow(form, "Description", descField);

        int choice = JOptionPane.showConfirmDialog(this, form, "Change booking " + b.getBookingId(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        String date  = GuiUtil.getDate(dateSpinner);
        String start = (String) startBox.getSelectedItem();
        String end   = (String) endBox.getSelectedItem();
        String desc  = descField.getText().trim();
        try {
            peopleSpinner.commitEdit();
        } catch (ParseException e) {
            // not a number - keep the last valid value
        }
        int people = (Integer) peopleSpinner.getValue();

        String error = GuiUtil.checkSlot(date, start, end);
        if (error != null) {
            GuiUtil.showError(this, error);
            return;
        }
        if (date.equals(b.getDate()) && start.equals(b.getStartTime())
                && end.equals(b.getEndTime()) && (desc.isEmpty() || desc.equals(b.getDescription()))
                && people == b.getAttendees()) {
            GuiUtil.showInfo(this, "No changes made.");
            return;
        }

        try {
            bookingService.modifyBooking(b.getBookingId(), user.getUserId(), date, start, end, desc, people);
            GuiUtil.showInfo(this, "Booking " + b.getBookingId() + " updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            GuiUtil.showError(this, ex.getMessage());   // e.g. "Time slot conflict."
        }
        refresh();
    }

    /** Ask for confirmation, then cancel the selected booking (or its whole series). */
    private void cancelSelected() {
        Booking b = selectedBooking();
        if (b == null) {
            return;
        }
        if (b.isRecurring()) {
            cancelWeekly(b);
            return;
        }
        if (!GuiUtil.confirm(this, "Cancel booking " + b.getBookingId() + " for "
                + GuiUtil.facilityName(b.getFacilityId()) + " on " + b.getDate()
                + " at " + b.getStartTime() + "?")) {
            return;
        }

        try {
            bookingService.cancelBooking(b.getBookingId(), user.getUserId());
            GuiUtil.showInfo(this, "Booking " + b.getBookingId() + " cancelled.");
        } catch (IllegalStateException ex) {
            GuiUtil.showError(this, ex.getMessage());   // e.g. the 24-hour rule
        }
        refresh();
    }

    /** One week of a weekly booking: cancel just this week, or every remaining week. */
    private void cancelWeekly(Booking b) {
        String[] options = { "Just this week", "All remaining weeks", "Keep it" };
        int choice = JOptionPane.showOptionDialog(this,
                b.getBookingId() + " is " + GuiUtil.seriesText(b).replace("Weekly", "week") + " of a weekly booking for "
                + GuiUtil.facilityName(b.getFacilityId()) + ".\nWhat would you like to cancel?",
                "Cancel weekly booking", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[2]);

        try {
            if (choice == 0) {
                bookingService.cancelBooking(b.getBookingId(), user.getUserId());
                GuiUtil.showInfo(this, "Booking " + b.getBookingId() + " cancelled. The other weeks are kept.");
            } else if (choice == 1) {
                int stillActive = 0;
                for (Booking week : bookingService.getSeries(b.getSeriesId())) {
                    if (week.getStatus() == Booking.BookingStatus.PENDING
                            || week.getStatus() == Booking.BookingStatus.APPROVED) {
                        stillActive++;
                    }
                }
                int cancelled = bookingService.cancelSeries(b.getSeriesId(), user.getUserId());
                int kept = stillActive - cancelled;
                GuiUtil.showInfo(this, cancelled + " week(s) cancelled."
                        + (kept > 0 ? "\n" + kept + " week(s) start within 24 hours, so they were kept." : ""));
            }
        } catch (IllegalStateException ex) {
            GuiUtil.showError(this, ex.getMessage());   // e.g. the 24-hour rule for this week
        }
        refresh();
    }
}
