package utar.gui;

import utar.model.Booking;
import utar.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * TimetableGrid.java - Draws one week (Monday to Sunday, 08:00 to 22:00)
 * of a facility's bookings as coloured blocks, like a calendar app.
 *
 *   - blue blocks are approved bookings, amber blocks are still pending
 *   - today's column is tinted and has a red line at the current time;
 *     days already gone are greyed
 *   - hovering over a block shows its details in a tooltip
 *
 * Privacy: the admin sees who made each booking.  Students and staff only
 * see "Booked" / "Requested" for other people's bookings, and "Your
 * booking" for their own.
 *
 * If a free-slot listener is set (students and staff), clicking an empty
 * slot that hasn't started yet reports its date and half-hour start time,
 * so the Book a Facility tab can be filled in for it.
 *
 * Like BarChartPanel, it extends JPanel and draws everything itself in
 * paintComponent() with Java2D.
 */
class TimetableGrid extends JPanel {

    private static final int FIRST_HOUR = 8;    // top of the grid
    private static final int LAST_HOUR  = 22;   // bottom of the grid

    // space for the hour labels on the left and the day names on top
    private static final int LEFT = 58, TOP = 46, RIGHT = 10, BOTTOM = 10;

    static final Color APPROVED_FILL = new Color(0xDC, 0xE8, 0xF7);
    static final Color APPROVED_EDGE = GuiUtil.BAR;
    static final Color PENDING_FILL  = new Color(0xFF, 0xF0, 0xD4);
    static final Color PENDING_EDGE  = GuiUtil.WAIT;
    private static final Color LINE      = new Color(0xE3, 0xE7, 0xED);
    private static final Color HALF_LINE = new Color(0xF1, 0xF3, 0xF6);
    private static final Color TODAY_BG  = new Color(0xF2, 0xF7, 0xFD);
    private static final Color PAST_BG   = new Color(0xF6, 0xF6, 0xF6);
    private static final Color TEXT      = new Color(0x22, 0x22, 0x22);

    private final User viewer;
    private final boolean showNames;   // only the admin sees who booked

    private LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
    private List<List<Booking>> days = new ArrayList<>();   // 7 lists, Monday first

    // Where each block was drawn last time, so getToolTipText() can find
    // the block under the mouse
    private final ArrayList<Rectangle> blockAreas = new ArrayList<>();
    private final ArrayList<String> blockTips = new ArrayList<>();

    // Called with (date, "HH:mm") when an empty slot is clicked; null = clicking does nothing
    private BiConsumer<LocalDate, String> freeSlotListener;

    TimetableGrid(User viewer) {
        this.viewer = viewer;
        this.showNames = viewer.getRole() == User.UserRole.ADMIN;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(0xD5, 0xDB, 0xE3)));
        setPreferredSize(new Dimension(900, 560));
        setToolTipText("");   // switches tooltips on; the text comes from getToolTipText(MouseEvent)

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isFreeSlot(e.getPoint())) {
                    freeSlotListener.accept(dateAt(e.getPoint()), timeAt(e.getPoint()));
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // a hand cursor shows that an empty slot can be clicked
                setCursor(isFreeSlot(e.getPoint()) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                                   : Cursor.getDefaultCursor());
            }
        });
    }

    /** Make empty slots clickable; the listener gets the slot's date and start time. */
    void setFreeSlotListener(BiConsumer<LocalDate, String> listener) {
        this.freeSlotListener = listener;
    }

    // -- Turning a mouse position into a day and a time -------------------

    private float columnWidth() {
        return (getWidth() - LEFT - RIGHT) / 7f;
    }

    private float hourHeight() {
        return (getHeight() - TOP - BOTTOM) / (float) (LAST_HOUR - FIRST_HOUR);
    }

    /** The date of the column under the point, or null outside the grid. */
    private LocalDate dateAt(Point p) {
        int day = (int) ((p.x - LEFT) / columnWidth());
        if (p.x < LEFT || day < 0 || day > 6 || p.y < TOP || p.y >= getHeight() - BOTTOM) {
            return null;
        }
        return monday.plusDays(day);
    }

    /** The half-hour slot under the point, e.g. "14:30" (21:30 at the latest). */
    private String timeAt(Point p) {
        int minutes = FIRST_HOUR * 60 + (int) ((p.y - TOP) / hourHeight() * 60);
        minutes = Math.min(minutes - minutes % 30, LAST_HOUR * 60 - 30);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** true if clicking here should start a booking: empty, inside the grid, not in the past. */
    private boolean isFreeSlot(Point p) {
        if (freeSlotListener == null || dateAt(p) == null) {
            return false;
        }
        for (Rectangle block : blockAreas) {
            if (block.contains(p)) {
                return false;
            }
        }
        LocalDate date = dateAt(p);
        String time = timeAt(p);
        java.time.LocalDateTime slotStart = date.atTime(Integer.parseInt(time.substring(0, 2)),
                                                        Integer.parseInt(time.substring(3)));
        return slotStart.isAfter(java.time.LocalDateTime.now());
    }

    /** Show a new week: monday plus 7 lists of bookings, one per day. */
    void setWeek(LocalDate monday, List<List<Booking>> days) {
        this.monday = monday;
        this.days = days;
        repaint();
    }

    /** How many bookings this week shows (handy for checking the grid). */
    int bookingCount() {
        int count = 0;
        for (List<Booking> day : days) {
            count += day.size();
        }
        return count;
    }

    /** The areas of the blocks drawn last time (for tests). */
    List<Rectangle> blockAreas() {
        return new ArrayList<>(blockAreas);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        for (int i = blockAreas.size() - 1; i >= 0; i--) {   // last drawn is on top
            if (blockAreas.get(i).contains(e.getPoint())) {
                return blockTips.get(i);
            }
        }
        if (isFreeSlot(e.getPoint())) {
            LocalDate date = dateAt(e.getPoint());
            return "<html>Click to book from <b>" + timeAt(e.getPoint()) + "</b> on "
                 + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                 + date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + "</html>";
        }
        return null;   // no tooltip over other empty space
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth(), height = getHeight();
        float columnWidth = (width - LEFT - RIGHT) / 7f;
        float hourHeight = (height - TOP - BOTTOM) / (float) (LAST_HOUR - FIRST_HOUR);
        LocalDate today = LocalDate.now();
        Font small = getFont().deriveFont(getFont().getSize2D() - 1f);
        Font bold = getFont().deriveFont(Font.BOLD);

        // -- Column backgrounds: today tinted, past days grey -------------
        for (int d = 0; d < 7; d++) {
            LocalDate date = monday.plusDays(d);
            if (date.equals(today) || date.isBefore(today)) {
                g2.setColor(date.equals(today) ? TODAY_BG : PAST_BG);
                g2.fillRect(Math.round(LEFT + d * columnWidth), TOP,
                            Math.round(columnWidth), height - TOP - BOTTOM);
            }
        }

        // -- Hour lines (with a lighter half-hour line) and hour labels ---
        g2.setFont(small);
        FontMetrics fm = g2.getFontMetrics();
        for (int h = FIRST_HOUR; h <= LAST_HOUR; h++) {
            int y = Math.round(TOP + (h - FIRST_HOUR) * hourHeight);
            g2.setColor(LINE);
            g2.drawLine(LEFT, y, width - RIGHT, y);
            if (h < LAST_HOUR) {
                int half = Math.round(y + hourHeight / 2);
                g2.setColor(HALF_LINE);
                g2.drawLine(LEFT, half, width - RIGHT, half);
            }
            String label = String.format("%02d:00", h);
            g2.setColor(Color.GRAY);
            g2.drawString(label, LEFT - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }

        // -- Day names, dates and column lines ----------------------------
        for (int d = 0; d <= 7; d++) {
            int x = Math.round(LEFT + d * columnWidth);
            g2.setColor(LINE);
            g2.drawLine(x, TOP, x, height - BOTTOM);
            if (d == 7) {
                break;
            }
            LocalDate date = monday.plusDays(d);
            int centre = Math.round(x + columnWidth / 2);
            String dayName = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            String dayDate = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
            g2.setColor(date.equals(today) ? GuiUtil.BAR : TEXT);
            g2.setFont(bold);
            g2.drawString(dayName, centre - g2.getFontMetrics().stringWidth(dayName) / 2, TOP - 24);
            g2.setFont(small);
            g2.setColor(date.equals(today) ? GuiUtil.BAR : Color.GRAY);
            g2.drawString(dayDate, centre - g2.getFontMetrics().stringWidth(dayDate) / 2, TOP - 8);
        }

        // -- Booking blocks -----------------------------------------------
        blockAreas.clear();
        blockTips.clear();
        for (int d = 0; d < days.size(); d++) {
            for (Booking b : days.get(d)) {
                drawBlock(g2, b, d, columnWidth, hourHeight, small, bold);
            }
        }

        // -- Red "now" line across today's column -------------------------
        int nowMinutes = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        long dayIndex = today.toEpochDay() - monday.toEpochDay();
        if (dayIndex >= 0 && dayIndex < 7 && nowMinutes >= FIRST_HOUR * 60 && nowMinutes <= LAST_HOUR * 60) {
            int y = Math.round(TOP + (nowMinutes - FIRST_HOUR * 60) * hourHeight / 60f);
            int x1 = Math.round(LEFT + dayIndex * columnWidth);
            int x2 = Math.round(LEFT + (dayIndex + 1) * columnWidth);
            g2.setColor(GuiUtil.BAD);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(x1, y, x2, y);
            g2.fillOval(x1 - 4, y - 4, 8, 8);
        }
        g2.dispose();
    }

    /** Draw one booking as a rounded block with its times and a label. */
    private void drawBlock(Graphics2D g2, Booking b, int dayIndex, float columnWidth,
                           float hourHeight, Font small, Font bold) {
        int start = Math.max(minutes(b.getStartTime()), FIRST_HOUR * 60);
        int end   = Math.min(minutes(b.getEndTime()), LAST_HOUR * 60);
        if (end <= start) {
            return;   // completely outside the grid
        }
        int x = Math.round(LEFT + dayIndex * columnWidth) + 3;
        int w = Math.round(columnWidth) - 6;
        int y = Math.round(TOP + (start - FIRST_HOUR * 60) * hourHeight / 60f) + 1;
        int h = Math.round((end - start) * hourHeight / 60f) - 2;

        boolean approved = b.getStatus() == Booking.BookingStatus.APPROVED;
        boolean mine = b.getUserId().equals(viewer.getUserId());
        Color fill = approved ? APPROVED_FILL : PENDING_FILL;
        Color edge = approved ? APPROVED_EDGE : PENDING_EDGE;

        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, 8, 8);
        g2.setColor(fill);
        g2.fill(shape);
        g2.setColor(edge);
        g2.fillRect(x, y + 1, 4, h - 2);                 // coloured bar on the left
        if (mine) {                                      // outline your own bookings
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(shape);
            g2.setStroke(new BasicStroke(1f));
        }

        // Text: the time in bold, then the other lines - but only as many
        // whole lines as fit, so a short block never shows half a line.
        // Clipping stops long text running past the block's right edge.
        Shape oldClip = g2.getClip();
        g2.clipRect(x, y, w, h);
        String[] lines = blockLines(b, approved, mine);
        Font timeFont = bold.deriveFont(small.getSize2D());
        FontMetrics timeMetrics = g2.getFontMetrics(timeFont);
        int lineHeight = g2.getFontMetrics(small).getHeight();
        int linesThatFit = Math.max(1, (h - 4) / lineHeight);

        int baseline = y + 3 + timeMetrics.getAscent();
        g2.setFont(timeFont);
        g2.setColor(TEXT);
        g2.drawString(lines[0], x + 9, baseline);
        g2.setFont(small);
        g2.setColor(new Color(0x44, 0x44, 0x44));
        if (linesThatFit == 1) {
            // short block (e.g. one hour): label on the same line as the time
            g2.drawString(lines[1], x + 9 + timeMetrics.stringWidth(lines[0]) + 8, baseline);
        } else {
            for (int i = 1; i < lines.length && i < linesThatFit; i++) {
                baseline += lineHeight;
                g2.drawString(lines[i], x + 9, baseline);
            }
        }
        g2.setClip(oldClip);

        blockAreas.add(new Rectangle(x, y, w, h));
        blockTips.add(tooltip(b, approved, mine));
    }

    /** The text inside a block: the time, then who / what it is. */
    private String[] blockLines(Booking b, boolean approved, boolean mine) {
        String time = b.getStartTime() + " - " + b.getEndTime();
        if (showNames) {
            return new String[] { time, GuiUtil.userName(b.getUserId()), approved ? "Approved" : "Pending" };
        }
        if (mine) {
            return new String[] { time, "Your booking", approved ? "Approved" : "Pending" };
        }
        return new String[] { time, approved ? "Booked" : "Requested" };
    }

    /** Tooltip text: more detail for the admin and for your own bookings. */
    private String tooltip(Booking b, boolean approved, boolean mine) {
        String status = approved ? "Approved" : "Waiting for approval";
        String when = LocalDate.parse(b.getDate(), DateTimeFormatter.ofPattern(GuiUtil.DATE_PATTERN))
                .getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                + " " + b.getDate() + ", " + b.getStartTime() + " - " + b.getEndTime();

        if (!showNames && !mine) {
            // someone else's booking: only say that the time is taken
            return "<html><b>" + (approved ? "Booked" : "Requested") + "</b><br>" + when + "</html>";
        }
        String who = showNames
                ? "Booked by " + GuiUtil.html(GuiUtil.userName(b.getUserId())) + " (" + b.getUserId() + ")"
                : "Your booking";
        String people = b.getAttendees() > 0 ? " for " + b.getAttendees() + " people" : "";
        String repeats = b.isRecurring() ? "<br>" + GuiUtil.seriesText(b) + " (" + b.getSeriesId() + ")" : "";
        return "<html><b>" + b.getBookingId() + " - " + status + "</b><br>" + when + people + "<br>" + who
             + "<br>" + GuiUtil.pretty(b.getPurpose()) + ": " + GuiUtil.html(b.getDescription())
             + repeats + "</html>";
    }

    /** "09:30" -> 570 minutes after midnight. */
    private static int minutes(String time) {
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
    }
}
