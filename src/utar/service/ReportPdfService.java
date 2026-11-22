package utar.service;

import utar.model.*;
import utar.util.PdfDocument;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * ReportPdfService.java - Builds the "Facility Usage & Maintenance Report"
 * as a PDF file for the admin.
 *
 *   Page 1: summary numbers, peak booking hours chart, bookings by
 *           facility type chart
 *   Page 2: facility utilization for the chosen period, maintenance
 *           totals and cases per facility (long tables continue onto
 *           more pages, with the header row repeated)
 *
 * Every number comes from AnalyticsService, the same methods the console
 * reports and the GUI Reports tab use, so all three always agree.
 * The drawing itself is done by utar.util.PdfDocument.
 */
public class ReportPdfService {

    // -- Layout, in points (A4 is about 595 x 842) --------------------------
    private static final float LEFT   = 40;
    private static final float RIGHT  = PdfDocument.WIDTH - 40;
    private static final float TOP    = 40;
    private static final float BOTTOM = PdfDocument.HEIGHT - 60;   // keep clear of the footer

    // -- Colours, matching the GUI --------------------------------------------
    private static final int DARK      = 0x1F3A68;
    private static final int TEXT      = 0x222222;
    private static final int MUTED     = 0x6B7280;
    private static final int BAR       = 0x2F6DB5;
    private static final int GRID      = 0xE5E8EE;
    private static final int BORDER    = 0xD5DBE3;
    private static final int HEADER_BG = 0xE8EEF7;
    private static final int ZEBRA     = 0xF5F7FA;

    private final AnalyticsService analytics = new AnalyticsService();
    private final BookingService bookingService = new BookingService();

    private PdfDocument pdf;   // the document being built
    private float y;           // where the next item starts on the current page

    /**
     * exportSummary - build the report and save it.
     *
     * @param period      "ALL", "MONTHLY", "SEMESTER" or "YEARLY" (as in AnalyticsService)
     * @param value       e.g. "04/2026", "SEM1/2026", "2026"; ignored for ALL
     * @param periodLabel the same period in words, e.g. "Apr 2026"
     * @param generatedBy name of the admin exporting it
     */
    public void exportSummary(File file, String period, String value,
                              String periodLabel, String generatedBy) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm"));

        pdf = new PdfDocument();
        pdf.setTitle("Facility Usage and Maintenance Report");
        pdf.setFooter("UTAR Smart Campus Facility System - report generated " + stamp);
        y = TOP;

        drawTitle(stamp, generatedBy, periodLabel);
        drawSummary();
        drawPeakHours();
        drawBookingsByType();

        pdf.newPage();
        y = TOP;
        drawUtilization(period, value, periodLabel);
        drawMaintenance();

        pdf.save(file);
    }

    // -- Sections ----------------------------------------------------------

    private void drawTitle(String stamp, String generatedBy, String periodLabel) {
        pdf.text("Facility Usage & Maintenance Report", LEFT, y + 20, 20, true, DARK);
        pdf.text("UTAR Smart Campus Facility System - Universiti Tunku Abdul Rahman, Sungai Long Campus",
                 LEFT, y + 38, 10, false, MUTED);
        pdf.text("Generated on " + stamp + " by " + generatedBy
                 + ".   Utilization period: " + periodLabel + ".", LEFT, y + 52, 10, false, MUTED);
        pdf.line(LEFT, y + 64, RIGHT, y + 64, BORDER, 1);
        y += 84;
    }

    /** Four boxes with the headline numbers, like the GUI's Overview cards. */
    private void drawSummary() {
        AnalyticsService.MaintenanceSummary m = analytics.getMaintenanceSummary();
        int active = 0;
        for (int count : analytics.getBookingsByType().values()) {
            active += count;
        }
        String[][] cards = {
            { "Active bookings",          String.valueOf(active) },
            { "Waiting for approval",     String.valueOf(bookingService.getPendingBookings().size()) },
            { "Open maintenance reports", String.valueOf(m.getOpen()) },
            { "Average repair time",      repairText(m) },
        };

        heading("Summary");
        float gap = 10, height = 56;
        float width = (RIGHT - LEFT - gap * (cards.length - 1)) / cards.length;
        for (int i = 0; i < cards.length; i++) {
            float x = LEFT + i * (width + gap);
            pdf.strokeRect(x, y, width, height, BORDER, 0.8f);
            pdf.text(cards[i][0], x + 10, y + 18, 8.5f, false, MUTED);
            pdf.text(cards[i][1], x + 10, y + 42, 18, true, DARK);
        }
        y += height + 24;
    }

    private void drawPeakHours() {
        int[] perHour = analytics.getBookingsPerHour();
        int hours = AnalyticsService.LAST_REPORT_HOUR - AnalyticsService.FIRST_REPORT_HOUR + 1;
        String[] labels = new String[hours];
        int[] values = new int[hours];
        for (int i = 0; i < hours; i++) {
            int hour = AnalyticsService.FIRST_REPORT_HOUR + i;
            labels[i] = String.format("%02d:00", hour);
            values[i] = perHour[hour];
        }
        heading("Peak booking hours");
        note("Number of active bookings that cover each hour of the day.");
        barChart(labels, values, 180);
    }

    private void drawBookingsByType() {
        LinkedHashMap<Facility.FacilityType, Integer> byType = analytics.getBookingsByType();
        String[] labels = new String[byType.size()];
        int[] values = new int[byType.size()];
        int i = 0;
        for (Facility.FacilityType type : byType.keySet()) {
            labels[i] = pretty(type);
            values[i] = byType.get(type);
            i++;
        }
        heading("Bookings by facility type");
        note("Active bookings only; cancelled and rejected bookings are not counted.");
        barChart(labels, values, 180);
    }

    /** Facilities with bookings in the period (or any reported issues), busiest first. */
    private void drawUtilization(String period, String value, String periodLabel) {
        heading("Facility utilization - " + periodLabel);

        LinkedHashMap<Facility, Integer> counts = analytics.getBookingsPerFacility(period, value);
        ArrayList<Facility> listed = new ArrayList<>();
        for (Facility f : counts.keySet()) {
            if (counts.get(f) > 0 || f.getMaintenanceCount() > 0) {
                listed.add(f);
            }
        }
        // most bookings first; ties broken by most issues
        listed.sort((a, b) -> {
            int byBookings = Integer.compare(counts.get(b), counts.get(a));
            return byBookings != 0 ? byBookings
                                   : Integer.compare(b.getMaintenanceCount(), a.getMaintenanceCount());
        });

        if (listed.isEmpty()) {
            note("No bookings or reported issues for this period.");
            return;
        }

        ArrayList<String[]> rows = new ArrayList<>();
        for (Facility f : listed) {
            rows.add(new String[] { f.getFacilityId(), f.getName(), pretty(f.getType()),
                    String.valueOf(counts.get(f)), String.valueOf(f.getMaintenanceCount()) });
        }
        table(new String[] { "ID", "Facility", "Type", "Bookings", "Issues (all time)" },
              new float[] { 50, 200, 110, 60, 0 },
              new boolean[] { false, false, false, true, true }, rows);

        int hidden = counts.size() - listed.size();
        if (hidden > 0) {
            note(hidden + " other facilities had no bookings in this period and no reported issues.");
        }
        y += 10;
    }

    private void drawMaintenance() {
        if (y + 120 > BOTTOM) {
            newPage();
        }
        AnalyticsService.MaintenanceSummary m = analytics.getMaintenanceSummary();
        heading("Maintenance");
        pdf.text("Total reports: " + m.getTotal() + "      Open: " + m.getOpen()
                 + "      Resolved: " + m.getResolved()
                 + "      Average repair time: " + repairText(m), LEFT, y + 10, 10, false, TEXT);
        y += 26;

        LinkedHashMap<Facility, Integer> cases = analytics.getMaintenanceCasesPerFacility();
        if (cases.isEmpty()) {
            note("No maintenance issues have been reported.");
            return;
        }
        ArrayList<Facility> sorted = new ArrayList<>(cases.keySet());
        sorted.sort((a, b) -> Integer.compare(cases.get(b), cases.get(a)));   // most cases first

        ArrayList<String[]> rows = new ArrayList<>();
        for (Facility f : sorted) {
            rows.add(new String[] { f.getFacilityId(), f.getName(), f.getLocation(),
                    String.valueOf(cases.get(f)) });
        }
        table(new String[] { "ID", "Facility", "Location", "Cases" },
              new float[] { 50, 215, 190, 0 },
              new boolean[] { false, false, false, true }, rows);
    }

    // -- Building blocks -----------------------------------------------------

    private void heading(String text) {
        pdf.text(text, LEFT, y + 12, 13, true, TEXT);
        y += 22;
    }

    private void note(String text) {
        pdf.text(text, LEFT, y + 8, 9, false, MUTED);
        y += 18;
    }

    private void newPage() {
        pdf.newPage();
        y = TOP;
    }

    /**
     * barChart - same idea as the GUI's BarChartPanel: gridlines with
     * whole-number steps, one bar per label, the value above each bar and
     * labels underneath (one word per line).
     */
    private void barChart(String[] labels, int[] values, float height) {
        float top = y;
        pdf.strokeRect(LEFT, top, RIGHT - LEFT, height, BORDER, 0.8f);

        int labelLines = 1;
        for (String label : labels) {
            labelLines = Math.max(labelLines, label.split(" ").length);
        }
        float plotLeft   = LEFT + 34;
        float plotRight  = RIGHT - 14;
        float plotTop    = top + 18;
        float plotBottom = top + height - 12 - labelLines * 10;
        float plotHeight = plotBottom - plotTop;

        int max = 0;
        for (int v : values) {
            max = Math.max(max, v);
        }
        int step = Math.max(1, (int) Math.ceil(max / 5.0));
        int axisMax = Math.max(step, ((max + step - 1) / step) * step);

        for (int v = 0; v <= axisMax; v += step) {
            float gy = plotBottom - v / (float) axisMax * plotHeight;
            pdf.line(plotLeft, gy, plotRight, gy, GRID, 0.6f);
            pdf.textRight(String.valueOf(v), plotLeft - 6, gy + 3, 8, false, MUTED);
        }

        float slot = (plotRight - plotLeft) / labels.length;
        float barWidth = Math.max(3, slot * 0.6f);
        for (int i = 0; i < labels.length; i++) {
            float centre = plotLeft + slot * i + slot / 2;
            float barHeight = values[i] / (float) axisMax * plotHeight;
            if (values[i] > 0) {
                pdf.fillRect(centre - barWidth / 2, plotBottom - barHeight, barWidth, barHeight, BAR);
                pdf.textCentred(String.valueOf(values[i]), centre, plotBottom - barHeight - 4, 8, false, TEXT);
            }
            String[] words = labels[i].split(" ");
            for (int w = 0; w < words.length; w++) {
                pdf.textCentred(words[w], centre, plotBottom + 12 + w * 10, 8, false, MUTED);
            }
        }
        pdf.line(plotLeft, plotBottom, plotRight, plotBottom, MUTED, 0.8f);

        if (max == 0) {
            pdf.textCentred("No bookings yet", (plotLeft + plotRight) / 2,
                            plotTop + plotHeight / 2, 9, false, MUTED);
        }
        y = top + height + 24;
    }

    /**
     * table - a simple table with a shaded header row and striped rows.
     * A width of 0 means "the rest of the line".  When a page fills up the
     * table continues on a new page and the header row is drawn again.
     */
    private void table(String[] headers, float[] widths, boolean[] numeric, ArrayList<String[]> rows) {
        float[] w = widths.clone();
        float used = 0;
        for (float width : w) {
            used += width;
        }
        for (int c = 0; c < w.length; c++) {
            if (w[c] == 0) {
                w[c] = (RIGHT - LEFT) - used;
            }
        }

        float rowHeight = 18;
        tableRow(headers, w, numeric, true, false);
        for (int r = 0; r < rows.size(); r++) {
            if (y + rowHeight > BOTTOM) {
                newPage();
                tableRow(headers, w, numeric, true, false);
            }
            tableRow(rows.get(r), w, numeric, false, r % 2 == 1);
        }
        pdf.line(LEFT, y, RIGHT, y, BORDER, 0.8f);
        y += 10;
    }

    private void tableRow(String[] cells, float[] widths, boolean[] numeric, boolean header, boolean striped) {
        float height = header ? 20 : 18;
        if (header || striped) {
            pdf.fillRect(LEFT, y, RIGHT - LEFT, height, header ? HEADER_BG : ZEBRA);
        }
        float x = LEFT;
        for (int c = 0; c < cells.length; c++) {
            String cell = PdfDocument.fit(cells[c], widths[c] - 12, 9, header);
            float baseline = y + (header ? 13.5f : 12.5f);
            int colour = header ? DARK : TEXT;
            if (numeric[c]) {
                pdf.textRight(cell, x + widths[c] - 6, baseline, 9, header, colour);
            } else {
                pdf.text(cell, x + 6, baseline, 9, header, colour);
            }
            x += widths[c];
        }
        y += height;
    }

    // -- Text helpers --------------------------------------------------------

    private static String repairText(AnalyticsService.MaintenanceSummary m) {
        return m.hasAverageRepairTime()
                ? String.format(java.util.Locale.ROOT, "%.1f days", m.getAverageRepairDays())
                : "-";
    }

    /** LECTURE_HALL -> "Lecture Hall". */
    private static String pretty(Enum<?> value) {
        StringBuilder out = new StringBuilder();
        for (String word : value.name().toLowerCase().split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
