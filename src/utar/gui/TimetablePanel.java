package utar.gui;

import utar.model.*;
import utar.service.BookingService;
import utar.service.DataStore;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TimetablePanel.java - Tab for every role except maintenance: one
 * facility's bookings for a whole week, drawn by TimetableGrid.
 *
 * Pick a facility, then move between weeks.  The bookings for each day come
 * from BookingService.getActiveBookingsOn(), so the grid always matches
 * what the booking rules see.
 *
 * For students and staff, MainFrame sets a SlotListener: clicking an empty
 * slot opens the Book a Facility tab with that facility, day and time.
 */
class TimetablePanel extends JPanel implements Refreshable {

    /** Told which facility, day and start time an empty slot was clicked for. */
    interface SlotListener {
        void slotClicked(String facilityId, LocalDate date, String startTime);
    }

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final BookingService bookingService = new BookingService();
    private final DataStore ds = DataStore.getInstance();

    // Facility drop-down: the text shown, and the facility at the same index
    private final ArrayList<Facility> facilities = new ArrayList<>();
    private final JComboBox<String> facilityBox = new JComboBox<>();
    private boolean refilling = false;   // ignore the drop-down's events while it's being refilled

    private final JLabel weekLabel = new JLabel();
    private final JLabel facilityNote = new JLabel(" ");
    private final TimetableGrid grid;
    private LocalDate monday = thisMonday();

    TimetablePanel(User viewer) {
        super(new BorderLayout(0, 6));
        GuiUtil.padded(this, 10);
        grid = new TimetableGrid(viewer);

        facilityBox.setMaximumRowCount(15);
        facilityBox.addActionListener(e -> {
            if (!refilling) {
                loadWeek();
            }
        });

        JButton previous = new JButton("< Previous week");
        JButton current  = new JButton("This week");
        JButton next     = new JButton("Next week >");
        previous.addActionListener(e -> { monday = monday.minusWeeks(1); loadWeek(); });
        current.addActionListener(e -> showWeek(LocalDate.now()));
        next.addActionListener(e -> { monday = monday.plusWeeks(1); loadWeek(); });
        weekLabel.setFont(weekLabel.getFont().deriveFont(Font.BOLD));
        facilityNote.setForeground(GuiUtil.BAD);

        JPanel controls = new JPanel(new GridLayout(2, 1));
        controls.add(GuiUtil.row(new JLabel("Facility"), facilityBox, facilityNote));
        controls.add(GuiUtil.row(previous, current, next, new JLabel("   "), weekLabel));

        JPanel north = new JPanel(new BorderLayout());
        north.add(GuiUtil.heading("Weekly timetable"), BorderLayout.NORTH);
        north.add(controls, BorderLayout.CENTER);

        // Legend
        JLabel hint = new JLabel(viewer.getRole() == User.UserRole.ADMIN
                ? "   Hover over a booking for details."
                : "   Hover over a booking for details, or click an empty slot to book it.");
        hint.setForeground(Color.GRAY);
        JPanel legend = GuiUtil.row(
                legendItem("Approved", TimetableGrid.APPROVED_FILL, TimetableGrid.APPROVED_EDGE, false),
                legendItem("Waiting for approval", TimetableGrid.PENDING_FILL, TimetableGrid.PENDING_EDGE, false));
        if (viewer.getRole() != User.UserRole.ADMIN) {
            legend.add(legendItem("Your booking (outlined)", TimetableGrid.APPROVED_FILL,
                    TimetableGrid.APPROVED_EDGE, true));
        }
        legend.add(hint);

        add(north, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(legend, BorderLayout.SOUTH);
    }

    private static LocalDate thisMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    /** Reload the facility list (the admin may have added one) and the week. */
    @Override
    public void refresh() {
        int index = facilityBox.getSelectedIndex();
        String selectedId = index >= 0 ? facilities.get(index).getFacilityId() : null;

        refilling = true;
        facilities.clear();
        facilityBox.removeAllItems();
        for (Facility f : ds.getAllFacilities()) {
            facilities.add(f);
            facilityBox.addItem(f.getFacilityId() + "  -  " + f.getName());
        }
        showFacility(selectedId);
        refilling = false;
        loadWeek();
    }

    /** Make empty slots clickable (used for students and staff). */
    void setSlotListener(SlotListener listener) {
        grid.setFreeSlotListener((date, time) -> {
            int index = facilityBox.getSelectedIndex();
            if (index >= 0) {
                listener.slotClicked(facilities.get(index).getFacilityId(), date, time);
            }
        });
    }

    /** Select a facility by ID (the first one if the ID isn't found). */
    void showFacility(String facilityId) {
        int index = 0;
        for (int i = 0; i < facilities.size(); i++) {
            if (facilities.get(i).getFacilityId().equals(facilityId)) {
                index = i;
            }
        }
        if (!facilities.isEmpty()) {
            facilityBox.setSelectedIndex(index);
        }
    }

    /** Jump to the week (Monday to Sunday) that contains the given day. */
    void showWeek(LocalDate anyDay) {
        monday = anyDay.with(DayOfWeek.MONDAY);
        loadWeek();
    }

    /** Ask the service for each day's bookings and hand them to the grid. */
    private void loadWeek() {
        int index = facilityBox.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Facility facility = facilities.get(index);

        List<List<Booking>> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            String date = monday.plusDays(d).format(DateTimeFormatter.ofPattern(GuiUtil.DATE_PATTERN));
            days.add(bookingService.getActiveBookingsOn(facility.getFacilityId(), date));
        }
        grid.setWeek(monday, days);

        weekLabel.setText("Week of " + monday.format(DAY_MONTH) + " - "
                + monday.plusDays(6).format(DAY_MONTH_YEAR));
        facilityNote.setText(facility.getStatus() == Facility.FacilityStatus.AVAILABLE ? " "
                : "This facility is " + GuiUtil.pretty(facility.getStatus()).toLowerCase()
                  + ", so it can't be booked right now.");
    }

    /** A small coloured square followed by a label, for the legend. */
    private static JLabel legendItem(String text, Color fill, Color edge, boolean outlined) {
        Icon square = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(fill);
                g.fillRect(x, y, 16, 12);
                g.setColor(edge);
                g.fillRect(x, y, 3, 12);
                if (outlined) {
                    g.drawRect(x, y, 15, 11);
                }
            }
            @Override public int getIconWidth()  { return 16; }
            @Override public int getIconHeight() { return 12; }
        };
        JLabel label = new JLabel(text, square, SwingConstants.LEFT);
        label.setIconTextGap(6);
        return label;
    }
}
