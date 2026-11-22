package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.MaintenanceService;
import utar.util.InputValidator;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * GuiUtil.java - Small helpers shared by every Swing screen.
 *
 * The GUI plays the same role as the console menus in the ui package:
 * it shows data and collects input, and leaves every business rule to
 * the service classes.  This class keeps the repeated Swing code (tables,
 * forms, colours, date pickers, message boxes) in one place so each
 * screen only contains what is special about it.
 *
 * It's a static utility class, like ConsoleUI and InputValidator.
 */
final class GuiUtil {

    private GuiUtil() { }   // static helpers only, never instantiated

    // -- Colours -----------------------------------------------------------
    static final Color HEADER_BG  = new Color(0x1F, 0x3A, 0x68);   // dark blue band
    static final Color HEADER_SUB = new Color(0xC9, 0xD6, 0xEA);   // light text on the band
    static final Color GOOD       = new Color(0x1E, 0x7B, 0x34);   // green
    static final Color WAIT       = new Color(0xB2, 0x6A, 0x00);   // amber
    static final Color BAD        = new Color(0xB3, 0x26, 0x1E);   // red
    static final Color ALERT_BG   = new Color(0xFD, 0xEC, 0xEA);   // pale red
    static final Color BAR        = new Color(0x2F, 0x6D, 0xB5);   // chart bars

    static final String DATE_PATTERN = "dd/MM/yyyy";

    /** Every half hour from 08:00 to 22:00, the hours facilities can be booked. */
    static final String[] TIME_SLOTS = buildTimeSlots();

    private static String[] buildTimeSlots() {
        ArrayList<String> slots = new ArrayList<>();
        for (int mins = 8 * 60; mins <= 22 * 60; mins += 30) {
            slots.add(String.format("%02d:%02d", mins / 60, mins % 60));
        }
        return slots.toArray(new String[0]);
    }

    // -- Look and feel -----------------------------------------------------

    /** Switch to Nimbus (built into the JDK); keep the default if it's missing. */
    static void useNimbusLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if (info.getName().equals("Nimbus")) {
                    UIManager.setLookAndFeel(info.getClassName());
                    UIManager.getLookAndFeelDefaults()
                             .put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    return;
                }
            }
        } catch (Exception e) {
            // Nimbus not available - the default look and feel still works
        }
    }

    // -- Dates and times ---------------------------------------------------

    static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_PATTERN));
    }

    static String tomorrow() {
        return LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern(DATE_PATTERN));
    }

    static String nowTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** A date picker with up/down arrows, showing dates as dd/MM/yyyy. */
    static JSpinner createDateSpinner(String date) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, DATE_PATTERN);
        editor.getTextField().setColumns(8);
        spinner.setEditor(editor);
        setDate(spinner, date);
        return spinner;
    }

    static void setDate(JSpinner spinner, String date) {
        try {
            spinner.setValue(new SimpleDateFormat(DATE_PATTERN).parse(date));
        } catch (ParseException e) {
            // leave the spinner on its current date
        }
    }

    /** Read the spinner's date in the dd/MM/yyyy format the services use. */
    static String getDate(JSpinner spinner) {
        try {
            spinner.commitEdit();   // pick up a date typed by hand
        } catch (ParseException e) {
            // text isn't a date - fall back to the last valid value
        }
        return new SimpleDateFormat(DATE_PATTERN).format((Date) spinner.getValue());
    }

    /** A drop-down of half-hour times, from index first to last in TIME_SLOTS. */
    static JComboBox<String> timeBox(int first, int last, String selected) {
        JComboBox<String> box = new JComboBox<>();
        for (int i = first; i <= last; i++) {
            box.addItem(TIME_SLOTS[i]);
        }
        box.setSelectedItem(selected);
        return box;
    }

    /**
     * checkSlot - apply the same booking-time rules as the console menus.
     * @return an error message, or null if the slot is fine
     */
    static String checkSlot(String date, String start, String end) {
        if (!InputValidator.isValidDate(date)) {
            return "Pick a real date in " + InputValidator.currentYear()
                 + " or " + InputValidator.maxBookingYear() + ".";
        }
        if (InputValidator.isPastDate(date)) {
            return "That date has already passed. Pick today or a later date.";
        }
        if (InputValidator.hasStarted(date, start)) {
            return "That start time has already passed today. Pick a later time.";
        }
        if (!InputValidator.isValidTimeRange(start, end)) {
            return "End time must be after start time.";
        }
        if (!InputValidator.hasMinimumDuration(start, end)) {
            return "Booking must be at least " + InputValidator.MIN_BOOKING_MINUTES + " minutes long.";
        }
        if (!InputValidator.isWithinOperatingHours(start, end)) {
            return "Booking must be within operating hours (08:00 - 22:00).";
        }
        return null;
    }

    // -- Tables ------------------------------------------------------------

    /**
     * A table model the user can't type into.  getColumnClass() reports the
     * real type of each column, so numbers sort as numbers (2 < 10).
     */
    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (getRowCount() > 0 && getValueAt(0, column) != null) {
                    return getValueAt(0, column).getClass();
                }
                return Object.class;
            }
        };
    }

    /** A sortable, single-selection table that colours status words. */
    static JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);   // click a header to sort
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(String.class, new StatusCellRenderer());
        table.setDefaultRenderer(Object.class, new StatusCellRenderer());
        fitText(table, 0, "MR0000");   // column 0 is always the record ID
        return table;
    }

    static void setColumnWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /**
     * fitText - never let a column shrink below the width of its widest
     * expected value, e.g. "In Progress" for a status column.  Uses the
     * bold font because status words are drawn in bold.
     */
    static void fitText(JTable table, int column, String widest) {
        FontMetrics fm = table.getFontMetrics(table.getFont().deriveFont(Font.BOLD));
        table.getColumnModel().getColumn(column).setMinWidth(fm.stringWidth(widest) + 16);
    }

    /**
     * setUpDateColumn - for a dd/MM/yyyy column: sort it by real date
     * instead of alphabetically, and never let it shrink so far that the
     * date gets cut off.
     */
    @SuppressWarnings("unchecked")
    static void setUpDateColumn(JTable table, int column) {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
        sorter.setComparator(column, (a, b) -> dateKey(a).compareTo(dateKey(b)));
        table.getColumnModel().getColumn(column).setMinWidth(
                table.getFontMetrics(table.getFont()).stringWidth("00/00/0000") + 16);
    }

    // "25/11/2026" -> "20261125", which sorts in date order.  Anything after
    // the date (e.g. " 14:05:09") is kept, so date-and-time columns sort too.
    private static String dateKey(Object value) {
        String s = String.valueOf(value);
        if (s.length() < 10 || s.charAt(2) != '/' || s.charAt(5) != '/') {
            return s;   // e.g. "-" for no date
        }
        return s.substring(6, 10) + s.substring(3, 5) + s.substring(0, 2) + s.substring(10);
    }

    /**
     * searchBox - a text field that filters a table as you type: only rows
     * containing the text (in any column, ignoring case) stay visible.
     * Used by the Facilities and Users tabs.
     */
    static JTextField searchBox(JTable table) {
        JTextField field = new JTextField(22);
        // DocumentListener has three methods, so it can't be a lambda
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { applySearch(table, field); }
            @Override public void removeUpdate(DocumentEvent e)  { applySearch(table, field); }
            @Override public void changedUpdate(DocumentEvent e) { applySearch(table, field); }
        });
        return field;
    }

    @SuppressWarnings("unchecked")
    private static void applySearch(JTable table, JTextField field) {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
        String text = field.getText().trim();
        // Pattern.quote treats the text literally, so "(" or "." can't break the filter
        sorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    /** The ID (column 0) of the selected row, or null if nothing is selected. */
    static String selectedId(JTable table) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);   // the view may be sorted
        return (String) table.getModel().getValueAt(modelRow, 0);
    }

    /** Select the row whose ID is id (used to keep the selection after a refresh). */
    static void selectRowById(JTable table, String id) {
        if (id == null) {
            return;
        }
        for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (id.equals(table.getModel().getValueAt(modelRow, 0))) {
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    /**
     * StatusCellRenderer - draws status words in colour:
     * green for good, amber for waiting, red for stopped.
     * Inheritance: it extends Swing's DefaultTableCellRenderer and only
     * changes the colour and font.
     */
    static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            Color colour = statusColour(String.valueOf(value));
            if (!isSelected) {
                // set every time - the same renderer object draws every cell
                setForeground(colour != null ? colour : table.getForeground());
            }
            setFont(colour != null ? table.getFont().deriveFont(Font.BOLD) : table.getFont());

            // Hovering shows the full text, for cells too narrow to show all of it
            String text = String.valueOf(value);
            setToolTipText(text.isEmpty() || text.equals("-") ? null : text);
            return this;
        }
    }

    /** The colour for a status word, or null if the text isn't a status. */
    static Color statusColour(String text) {
        if (text.startsWith("Locked until")) {
            return BAD;
        }
        switch (text) {
            case "Approved": case "Available": case "Resolved":
                return GOOD;
            case "Pending": case "Reported": case "Assigned":
            case "In Progress": case "Under Maintenance":
                return WAIT;
            case "Rejected": case "Cancelled": case "Closed":
                return BAD;
            default:
                return null;
        }
    }

    // -- Forms and layout --------------------------------------------------

    /** The dark blue band at the top of each window. */
    static JPanel headerBand(String title, String subtitle) {
        return headerBand(title, new JLabel(subtitle));
    }

    /**
     * Same as above, but the caller keeps the subtitle label so it can
     * change the text later (MainFrame does this after a profile update).
     */
    static JPanel headerBand(String title, JLabel subLabel) {
        JPanel band = new JPanel(new BorderLayout());
        band.setBackground(HEADER_BG);
        band.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));

        subLabel.setForeground(HEADER_SUB);

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        text.add(titleLabel);
        text.add(subLabel);
        band.add(text, BorderLayout.CENTER);
        return band;
    }

    /** An empty two-column form; fill it with addFormRow(). */
    static JPanel formPanel() {
        return new JPanel(new GridBagLayout());
    }

    static void addFormRow(JPanel form, String label, JComponent field) {
        addFormRow(form, new JLabel(label), field);
    }

    /** Add one "label | field" row below the rows already in the form. */
    static void addFormRow(JPanel form, JLabel label, JComponent field) {
        int row = form.getComponentCount() / 2;   // two components per row
        // 2 spare pixels so the last letter is never clipped (e.g. on high-DPI screens)
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy  = row;
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        form.add(label, c);

        c.gridx   = 1;
        c.fill    = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
    }

    /** A left-aligned row of components, e.g. a toolbar of filters and buttons. */
    static JPanel row(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }

    static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    /** A red label for form errors.  Starts as a space so it keeps its height. */
    static JLabel errorLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(BAD);
        return label;
    }

    /** Empty space around a panel's edges. */
    static <T extends JComponent> T padded(T component, int size) {
        component.setBorder(BorderFactory.createEmptyBorder(size, size, size, size));
        return component;
    }

    // -- Bookings ----------------------------------------------------------

    /** "Weekly 2 of 6" for one week of a weekly booking, "-" for a one-off. */
    static String seriesText(Booking b) {
        if (!b.isRecurring()) {
            return "-";
        }
        ArrayList<Booking> series = new utar.service.BookingService().getSeries(b.getSeriesId());
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).getBookingId().equals(b.getBookingId())) {
                return "Weekly " + (i + 1) + " of " + series.size();
            }
        }
        return "Weekly";
    }

    /** The number of people, or "-" for old bookings that didn't record it. */
    static String peopleText(Booking b) {
        return b.getAttendees() > 0 ? String.valueOf(b.getAttendees()) : "-";
    }

    // -- Attachments (photos / PDFs on maintenance reports) -----------------

    /** "Photo", "PDF" or "-" for the Attachment column of a table. */
    static String attachmentKind(MaintenanceReport r) {
        if (!r.hasAttachment()) {
            return "-";
        }
        return MaintenanceService.isImage(r.getAttachment()) ? "Photo" : "PDF";
    }

    /**
     * showAttachment - show a report's photo in a window (shrunk to fit the
     * screen), or open its PDF in the computer's PDF reader.
     */
    static void showAttachment(Component parent, MaintenanceReport r) {
        if (r == null || !r.hasAttachment()) {
            return;
        }
        File file = DataStore.getInstance().getAttachmentFile(r.getAttachment());
        if (!file.isFile()) {
            showError(parent, "The attached file is missing:\n" + file.getAbsolutePath());
            return;
        }

        if (MaintenanceService.isImage(file.getName())) {
            ImageIcon picture = scaledImage(file, 900, 600);
            if (picture == null) {
                showError(parent, "This photo could not be opened.");
                return;
            }
            JOptionPane.showMessageDialog(parent, new JLabel(picture),
                    "Attachment for " + r.getReportId(), JOptionPane.PLAIN_MESSAGE);
            return;
        }

        // A PDF: let the computer's own PDF reader open it
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                Desktop.getDesktop().open(file);
                return;
            } catch (IOException e) {
                // no PDF reader set up - fall back to showing where the file is
            }
        }
        showInfo(parent, "The attached PDF is saved at:\n" + file.getAbsolutePath());
    }

    /**
     * scaledImage - load a photo, shrunk to fit inside maxWidth x maxHeight
     * (small photos are never enlarged).  Returns null if it can't be read.
     */
    static ImageIcon scaledImage(File file, int maxWidth, int maxHeight) {
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
        if (image == null) {
            return null;   // not a format ImageIO understands
        }
        double scale = Math.min(1.0, Math.min(maxWidth / (double) image.getWidth(),
                                              maxHeight / (double) image.getHeight()));
        if (scale >= 1.0) {
            return new ImageIcon(image);
        }
        return new ImageIcon(image.getScaledInstance((int) (image.getWidth() * scale),
                (int) (image.getHeight() * scale), Image.SCALE_SMOOTH));
    }

    // -- Message boxes -----------------------------------------------------

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // -- Text helpers ------------------------------------------------------

    /** LECTURE_HALL -> "Lecture Hall", IN_PROGRESS -> "In Progress". */
    static String pretty(Enum<?> value) {
        String[] words = value.name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    /** The pretty names of every value of an enum, for a drop-down list. */
    static String[] prettyNames(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = pretty(values[i]);
        }
        return names;
    }

    static String orDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    static String facilityName(String facilityId) {
        Facility f = DataStore.getInstance().getFacilityById(facilityId);
        return f != null ? f.getName() : facilityId;
    }

    static String userName(String userId) {
        if (userId == null) {
            return "-";
        }
        User u = DataStore.getInstance().getUserById(userId);
        return u != null ? u.getName() : userId;
    }

    /**
     * wrapHtml - break text into lines no wider than maxWidth pixels and
     * join them with <br>, for an HTML JLabel.  Swing's HTML "width" style
     * changes with the screen's DPI, so measuring the words ourselves is
     * the reliable way to make long messages wrap.  The text is escaped.
     */
    static String wrapHtml(String text, Font font, int maxWidth) {
        FontMetrics fm = new JLabel().getFontMetrics(font);
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String longer = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && fm.stringWidth(longer) > maxWidth) {
                out.append(html(line.toString())).append("<br>");   // line is full
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(longer);
            }
        }
        return out.append(html(line.toString())).toString();
    }

    /** Escape text before putting it inside an HTML label. */
    static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
