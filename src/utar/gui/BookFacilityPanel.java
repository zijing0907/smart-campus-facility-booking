package utar.gui;

import utar.model.*;
import utar.service.BookingService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * BookFacilityPanel.java - Student / Staff tab: search for a free facility
 * and book it.
 *
 * GUI version of UserMenu options 1 and 2 (search + book), combined into
 * one screen:
 *   1. Pick a date, a time slot, how many people and (optionally) a type.
 *      Tick "Repeat every week" to book the same slot for several weeks.
 *   2. Search - BookingService lists the facilities that are free and big
 *      enough (for a weekly booking: free in EVERY one of those weeks).
 *   3. Pick one, choose a purpose, describe the booking and press Book.
 *      BookingService checks everything again before saving.
 *
 * The booking uses the details of the LAST SEARCH (shown above the table),
 * so the results on screen always match what gets booked.
 *
 * prefill() lets the Timetable tab jump here with a facility, date and
 * time already filled in when the user clicks an empty slot.
 */
class BookFacilityPanel extends JPanel {

    private final User user;
    private final BookingService bookingService = new BookingService();

    // -- Search fields -----------------------------------------------------
    private final JSpinner dateSpinner = GuiUtil.createDateSpinner(GuiUtil.tomorrow());
    // start: 08:00 .. 21:30, end: 08:30 .. 22:00
    private final JComboBox<String> startBox =
            GuiUtil.timeBox(0, GuiUtil.TIME_SLOTS.length - 2, "09:00");
    private final JComboBox<String> endBox =
            GuiUtil.timeBox(1, GuiUtil.TIME_SLOTS.length - 1, "11:00");
    private final JComboBox<String> typeBox = new JComboBox<>();
    private final JSpinner peopleSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
    private final JCheckBox repeatBox = new JCheckBox("Repeat every week for");
    private final JSpinner weeksSpinner =
            new JSpinner(new SpinnerNumberModel(6, 2, BookingService.MAX_WEEKS, 1));

    // -- Results -----------------------------------------------------------
    private final DefaultTableModel model =
            GuiUtil.readOnlyModel("ID", "Facility", "Type", "Capacity", "Location");
    private final JTable table = GuiUtil.createTable(model);
    private final JLabel resultLabel = new JLabel("Choose a date and time, then press Search.");

    // -- Booking fields ----------------------------------------------------
    private final JComboBox<String> purposeBox =
            new JComboBox<>(GuiUtil.prettyNames(Booking.BookingPurpose.values()));
    private final JTextField descriptionField = new JTextField(34);
    private final JButton bookButton = new JButton("Book selected facility");

    // The details the table was searched for (searchedDate is null until the first search)
    private String searchedDate, searchedStart, searchedEnd;
    private int searchedPeople, searchedWeeks;   // searchedWeeks 1 = a one-off booking

    BookFacilityPanel(User user) {
        super(new BorderLayout(0, 6));
        this.user = user;
        GuiUtil.padded(this, 10);

        typeBox.addItem("All types");
        for (String name : GuiUtil.prettyNames(Facility.FacilityType.values())) {
            typeBox.addItem(name);
        }

        // Moving the start time past the end time pushes the end time 1 hour later
        startBox.addActionListener(e -> keepEndAfterStart());

        weeksSpinner.setEnabled(false);
        repeatBox.addActionListener(e -> weeksSpinner.setEnabled(repeatBox.isSelected()));

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> search());

        JPanel searchRow = GuiUtil.row(new JLabel("Date"), dateSpinner,
                new JLabel("From"), startBox, new JLabel("To"), endBox,
                new JLabel("People"), peopleSpinner, new JLabel("Type"), typeBox);
        JPanel repeatRow = GuiUtil.row(repeatBox, weeksSpinner, new JLabel("weeks"),
                new JLabel("      "), searchButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Find a free facility"), BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(2, 1));
        rows.add(searchRow);
        rows.add(repeatRow);
        north.add(rows, BorderLayout.CENTER);
        north.add(resultLabel, BorderLayout.SOUTH);

        GuiUtil.setColumnWidths(table, 70, 300, 150, 80, 260);
        table.getSelectionModel().addListSelectionListener(e -> updateBookButton());

        bookButton.addActionListener(e -> book());
        bookButton.setEnabled(false);
        JPanel bookRow = GuiUtil.row(new JLabel("Purpose"), purposeBox,
                new JLabel("Description"), descriptionField, bookButton);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bookRow, BorderLayout.SOUTH);
    }

    private void keepEndAfterStart() {
        String start = (String) startBox.getSelectedItem();
        String end = (String) endBox.getSelectedItem();
        if (start != null && end != null && end.compareTo(start) <= 0) {
            int startIndex = Arrays.asList(GuiUtil.TIME_SLOTS).indexOf(start);
            int newEnd = Math.min(startIndex + 2, GuiUtil.TIME_SLOTS.length - 1);   // +1 hour
            endBox.setSelectedItem(GuiUtil.TIME_SLOTS[newEnd]);
        }
    }

    /**
     * prefill - called when the user clicks an empty slot on the Timetable
     * tab: fill in that facility's slot (1 hour, or less near closing time),
     * search, and select the facility so it's ready to book.
     */
    void prefill(String facilityId, LocalDate date, String startTime) {
        GuiUtil.setDate(dateSpinner, date.format(DateTimeFormatter.ofPattern(GuiUtil.DATE_PATTERN)));
        startBox.setSelectedItem(startTime);
        int startIndex = Arrays.asList(GuiUtil.TIME_SLOTS).indexOf(startTime);
        endBox.setSelectedItem(GuiUtil.TIME_SLOTS[Math.min(startIndex + 2, GuiUtil.TIME_SLOTS.length - 1)]);
        typeBox.setSelectedIndex(0);            // all types, so the facility can't be filtered out
        peopleSpinner.setValue(1);
        repeatBox.setSelected(false);
        weeksSpinner.setEnabled(false);

        search();
        GuiUtil.selectRowById(table, facilityId);
        if (!facilityId.equals(GuiUtil.selectedId(table)) && searchedDate != null) {
            Facility f = utar.service.DataStore.getInstance().getFacilityById(facilityId);
            GuiUtil.showInfo(this, f != null && f.getStatus() != Facility.FacilityStatus.AVAILABLE
                    ? f.getName() + " is " + GuiUtil.pretty(f.getStatus()).toLowerCase()
                      + ", so it can't be booked right now.\nPick another facility from the list."
                    : GuiUtil.facilityName(facilityId) + " isn't free for the whole hour."
                      + "\nTry a shorter time, or pick another facility from the list.");
        }
        descriptionField.requestFocusInWindow();
    }

    /** Check the slot, then list every free facility for it. */
    private void search() {
        String date  = GuiUtil.getDate(dateSpinner);
        String start = (String) startBox.getSelectedItem();
        String end   = (String) endBox.getSelectedItem();
        int people   = spinnerValue(peopleSpinner);
        int weeks    = repeatBox.isSelected() ? spinnerValue(weeksSpinner) : 1;

        String error = GuiUtil.checkSlot(date, start, end);
        if (error != null) {
            GuiUtil.showError(this, error);
            return;
        }

        // index 0 is "All types" -> null means no filter, like the console
        int t = typeBox.getSelectedIndex();
        Facility.FacilityType type = t == 0 ? null : Facility.FacilityType.values()[t - 1];

        ArrayList<Facility> free;
        try {
            free = weeks == 1
                    ? bookingService.searchAvailable(date, start, end, type, people)
                    : bookingService.searchAvailableWeekly(date, start, end, type, people, weeks);
        } catch (IllegalArgumentException ex) {
            GuiUtil.showError(this, ex.getMessage());   // e.g. a later week is too far ahead
            return;
        }

        model.setRowCount(0);
        for (Facility f : free) {
            model.addRow(new Object[] { f.getFacilityId(), f.getName(),
                    GuiUtil.pretty(f.getType()), f.getCapacity(), f.getLocation() });
        }

        searchedDate = date;
        searchedStart = start;
        searchedEnd = end;
        searchedPeople = people;
        searchedWeeks = weeks;

        String when = weeks == 1
                ? "on " + date
                : "every " + dayName(date) + " for " + weeks + " weeks from " + date;
        resultLabel.setText((free.isEmpty() ? "No facilities are" : free.size() + " facilities are")
                + " free " + when + ", " + start + " - " + end + ", for " + people
                + (people == 1 ? " person." : " people.")
                + (free.isEmpty() ? "" : " Select one to book it."));
        updateBookButton();
    }

    private void updateBookButton() {
        bookButton.setEnabled(table.getSelectedRow() >= 0);
    }

    /** Book the selected facility for the searched slot (one week or every week). */
    private void book() {
        String facilityId = GuiUtil.selectedId(table);
        if (facilityId == null) {
            return;
        }
        String description = descriptionField.getText().trim();
        if (description.isEmpty()) {
            GuiUtil.showError(this, "Please enter a short description of the booking.");
            descriptionField.requestFocusInWindow();
            return;
        }
        Booking.BookingPurpose purpose = Booking.BookingPurpose.values()[purposeBox.getSelectedIndex()];
        String name = GuiUtil.facilityName(facilityId);

        try {
            if (searchedWeeks == 1) {
                Booking b = bookingService.createBooking(user.getUserId(), facilityId,
                        searchedDate, searchedStart, searchedEnd, purpose, description, searchedPeople);
                GuiUtil.showInfo(this, "Booking " + b.getBookingId() + " submitted for " + name
                        + ".\nIt is now waiting for admin approval."
                        + "\nYou can follow it on the My Bookings tab.");
            } else {
                ArrayList<Booking> weeks = bookingService.createRecurringBooking(user.getUserId(),
                        facilityId, searchedDate, searchedStart, searchedEnd, searchedWeeks,
                        purpose, description, searchedPeople);
                GuiUtil.showInfo(this, weeks.size() + " weekly bookings submitted for " + name
                        + " (series " + weeks.get(0).getSeriesId() + "):\nevery " + dayName(searchedDate)
                        + ", " + searchedStart + " - " + searchedEnd + ", from " + searchedDate
                        + " to " + weeks.get(weeks.size() - 1).getDate()
                        + ".\nThey are now waiting for admin approval.");
            }
            descriptionField.setText("");
            search();   // run the search again so the booked facility drops off the list
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // e.g. someone else booked one of the weeks in the meantime
            GuiUtil.showError(this, ex.getMessage());
        }
    }

    /** The spinner's number, including one typed by hand. */
    private static int spinnerValue(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException e) {
            // not a number - keep the last valid value
        }
        return (Integer) spinner.getValue();
    }

    /** "25/11/2026" -> "Wednesday" */
    private static String dayName(String date) {
        return LocalDate.parse(date, DateTimeFormatter.ofPattern(GuiUtil.DATE_PATTERN))
                .getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
