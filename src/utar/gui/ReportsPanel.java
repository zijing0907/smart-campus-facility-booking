package utar.gui;

import utar.model.*;
import utar.service.AnalyticsService;
import utar.service.BookingService;
import utar.service.ReportPdfService;
import utar.util.InputValidator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;

/**
 * ReportsPanel.java - Admin tab: the analytics reports, with charts.
 *
 * GUI version of AdminMenu options 6 to 9.  All numbers come from the
 * getXxx() methods in AnalyticsService, the same ones the console
 * reports print, so both front ends always agree.
 *
 * Three sub-tabs:
 *   Overview             - summary cards + peak hours + bookings by type
 *   Facility Utilization - bookings per facility, filtered by period
 *   Maintenance          - open / resolved counts and cases per facility
 *
 * "Export PDF..." saves everything as one PDF report (see ReportPdfService),
 * using the period chosen on the Facility Utilization sub-tab.
 */
class ReportsPanel extends JPanel implements Refreshable {

    private static final String[] PERIODS = { "All time", "Month", "Semester", "Year" };
    private static final String[] MONTHS = { "01 Jan", "02 Feb", "03 Mar", "04 Apr", "05 May",
            "06 Jun", "07 Jul", "08 Aug", "09 Sep", "10 Oct", "11 Nov", "12 Dec" };
    private static final String[] SEMESTERS = { "SEM1 (Jan-Apr)", "SEM2 (May-Aug)", "SEM3 (Sep-Dec)" };
    private static final int FIRST_YEAR = 2020;   // same lower limit as the console report

    private final User admin;   // named in the PDF as the person who exported it
    private final AnalyticsService analytics = new AnalyticsService();
    private final BookingService bookingService = new BookingService();
    private final ReportPdfService pdfService = new ReportPdfService();

    // The period shown on the Facility Utilization sub-tab, also used for the PDF
    private String periodKey = "ALL", periodValue = "", periodLabel = "All time";
    private final JLabel exportNote = new JLabel();

    // -- Overview ----------------------------------------------------------
    private final JLabel activeCard  = new JLabel();
    private final JLabel pendingCard = new JLabel();
    private final JLabel openCard    = new JLabel();
    private final JLabel repairCard  = new JLabel();
    private final BarChartPanel peakChart =
            new BarChartPanel("Peak booking hours", "No bookings yet");
    private final BarChartPanel typeChart =
            new BarChartPanel("Bookings by facility type", "No bookings yet");

    // -- Utilization -------------------------------------------------------
    private final JComboBox<String> periodBox = new JComboBox<>(PERIODS);
    private final JComboBox<String> detailBox = new JComboBox<>();   // month or semester
    private final JComboBox<String> yearBox   = new JComboBox<>();
    private final DefaultTableModel usageModel =
            GuiUtil.readOnlyModel("ID", "Facility", "Type", "Bookings", "Issues reported");
    private final JTable usageTable = GuiUtil.createTable(usageModel);

    // -- Maintenance -------------------------------------------------------
    private final JLabel totalCard    = new JLabel();
    private final JLabel openCard2    = new JLabel();
    private final JLabel resolvedCard = new JLabel();
    private final JLabel repairCard2  = new JLabel();
    private final DefaultTableModel casesModel = GuiUtil.readOnlyModel("ID", "Facility", "Cases");
    private final JTable casesTable = GuiUtil.createTable(casesModel);

    ReportsPanel(User admin) {
        super(new BorderLayout());
        this.admin = admin;

        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Overview", buildOverview());
        subTabs.addTab("Facility Utilization", buildUtilization());
        subTabs.addTab("Maintenance", buildMaintenance());

        JButton exportButton = new JButton("Export PDF...");
        exportButton.addActionListener(e -> exportPdf());
        exportNote.setForeground(Color.GRAY);

        add(GuiUtil.row(exportButton, exportNote), BorderLayout.NORTH);
        add(subTabs, BorderLayout.CENTER);
        updateExportNote();
    }

    // -- Building the three sub-tabs ---------------------------------------

    private JPanel buildOverview() {
        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        cards.add(card("Active bookings", activeCard));
        cards.add(card("Waiting for approval", pendingCard));
        cards.add(card("Open maintenance reports", openCard));
        cards.add(card("Average repair time", repairCard));

        JPanel charts = new JPanel(new GridLayout(1, 2, 10, 0));
        charts.add(peakChart);
        charts.add(typeChart);

        JPanel panel = GuiUtil.padded(new JPanel(new BorderLayout(0, 10)), 10);
        panel.add(cards, BorderLayout.NORTH);
        panel.add(charts, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildUtilization() {
        for (int y = InputValidator.maxBookingYear(); y >= FIRST_YEAR; y--) {
            yearBox.addItem(String.valueOf(y));
        }
        yearBox.setSelectedItem(String.valueOf(InputValidator.currentYear()));
        periodBox.addActionListener(e -> { fillDetailBox(); refreshUtilization(); });
        detailBox.addActionListener(e -> refreshUtilization());
        yearBox.addActionListener(e -> refreshUtilization());
        fillDetailBox();

        GuiUtil.setColumnWidths(usageTable, 60, 320, 160, 90, 110);

        JPanel panel = GuiUtil.padded(new JPanel(new BorderLayout(0, 6)), 10);
        panel.add(GuiUtil.row(new JLabel("Period"), periodBox, detailBox, yearBox,
                new JLabel("  Click a column header to sort.")), BorderLayout.NORTH);
        panel.add(new JScrollPane(usageTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMaintenance() {
        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        cards.add(card("Total reports", totalCard));
        cards.add(card("Open", openCard2));
        cards.add(card("Resolved", resolvedCard));
        cards.add(card("Average repair time", repairCard2));

        GuiUtil.setColumnWidths(casesTable, 60, 360, 80);

        JPanel panel = GuiUtil.padded(new JPanel(new BorderLayout(0, 10)), 10);
        panel.add(cards, BorderLayout.NORTH);
        JPanel tableArea = new JPanel(new BorderLayout(0, 4));
        tableArea.add(new JLabel("Maintenance cases per facility"), BorderLayout.NORTH);
        tableArea.add(new JScrollPane(casesTable), BorderLayout.CENTER);
        panel.add(tableArea, BorderLayout.CENTER);
        return panel;
    }

    /** A white box with a small caption and a big number. */
    private static JPanel card(String caption, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD5, 0xDB, 0xE3)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setForeground(Color.GRAY);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 24f));
        valueLabel.setForeground(GuiUtil.HEADER_BG);
        card.add(captionLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // -- Refreshing --------------------------------------------------------

    @Override
    public void refresh() {
        refreshOverview();
        refreshUtilization();
        refreshMaintenance();
    }

    private void refreshOverview() {
        // Peak hours: one bar per hour in the operating window
        int[] perHour = analytics.getBookingsPerHour();
        int hours = AnalyticsService.LAST_REPORT_HOUR - AnalyticsService.FIRST_REPORT_HOUR + 1;
        String[] hourLabels = new String[hours];
        int[] hourValues = new int[hours];
        for (int i = 0; i < hours; i++) {
            int h = AnalyticsService.FIRST_REPORT_HOUR + i;
            hourLabels[i] = String.format("%02d", h);
            hourValues[i] = perHour[h];
        }
        peakChart.setData(hourLabels, hourValues);

        // Bookings per facility type
        LinkedHashMap<Facility.FacilityType, Integer> byType = analytics.getBookingsByType();
        String[] typeLabels = new String[byType.size()];
        int[] typeValues = new int[byType.size()];
        int i = 0, active = 0;
        for (Facility.FacilityType type : byType.keySet()) {
            typeLabels[i] = GuiUtil.pretty(type);
            typeValues[i] = byType.get(type);
            active += typeValues[i];
            i++;
        }
        typeChart.setData(typeLabels, typeValues);

        AnalyticsService.MaintenanceSummary s = analytics.getMaintenanceSummary();
        activeCard.setText(String.valueOf(active));
        pendingCard.setText(String.valueOf(bookingService.getPendingBookings().size()));
        openCard.setText(String.valueOf(s.getOpen()));
        repairCard.setText(repairText(s));
    }

    /** Show the month or semester box only for the periods that need it. */
    private void fillDetailBox() {
        String period = (String) periodBox.getSelectedItem();
        detailBox.removeAllItems();
        if (period.equals("Month")) {
            for (String m : MONTHS) detailBox.addItem(m);
            detailBox.setSelectedIndex(java.time.LocalDate.now().getMonthValue() - 1);
        } else if (period.equals("Semester")) {
            for (String sem : SEMESTERS) detailBox.addItem(sem);
        }
        detailBox.setVisible(period.equals("Month") || period.equals("Semester"));
        yearBox.setVisible(!period.equals("All time"));
    }

    /** Turn the drop-downs into the period / value strings AnalyticsService expects. */
    private void refreshUtilization() {
        String period = (String) periodBox.getSelectedItem();
        String detail = (String) detailBox.getSelectedItem();
        String year   = (String) yearBox.getSelectedItem();

        String key = "ALL", value = "";
        if (period.equals("Month") && detail != null) {
            key = "MONTHLY";
            value = detail.substring(0, 2) + "/" + year;     // e.g. "04/2026"
        } else if (period.equals("Semester") && detail != null) {
            key = "SEMESTER";
            value = detail.substring(0, 4) + "/" + year;     // e.g. "SEM1/2026"
        } else if (period.equals("Year")) {
            key = "YEARLY";
            value = year;                                    // e.g. "2026"
        } else if (!period.equals("All time")) {
            return;   // the detail box is being refilled - wait for the next event
        }

        // Remember the period (and describe it in words) for the PDF export
        periodKey = key;
        periodValue = value;
        if (key.equals("MONTHLY"))       periodLabel = detail.substring(3) + " " + year;     // "Apr 2026"
        else if (key.equals("SEMESTER")) periodLabel = detail.substring(0, 4) + " " + year
                                                       + " " + detail.substring(5);           // "SEM1 2026 (Jan-Apr)"
        else if (key.equals("YEARLY"))   periodLabel = "Year " + year;
        else                             periodLabel = "All time";
        updateExportNote();

        String selected = GuiUtil.selectedId(usageTable);
        usageModel.setRowCount(0);
        LinkedHashMap<Facility, Integer> counts = analytics.getBookingsPerFacility(key, value);
        for (Facility f : counts.keySet()) {
            usageModel.addRow(new Object[] { f.getFacilityId(), f.getName(),
                    GuiUtil.pretty(f.getType()), counts.get(f), f.getMaintenanceCount() });
        }
        GuiUtil.selectRowById(usageTable, selected);
    }

    private void updateExportNote() {
        exportNote.setText("Saves the summary, charts, facility utilization (" + periodLabel
                + ") and maintenance as one PDF.");
    }

    /**
     * exportPdf - ask where to save, write the report, then offer to open it.
     * File problems (e.g. the PDF is already open in another program on
     * Windows) are caught and shown as a message instead of crashing.
     */
    private void exportPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report as PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files (*.pdf)", "pdf"));
        chooser.setSelectedFile(new File("facility-report-" + LocalDate.now() + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;   // cancelled
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }
        if (file.exists() && !GuiUtil.confirm(this, file.getName() + " already exists. Replace it?")) {
            return;
        }

        try {
            pdfService.exportSummary(file, periodKey, periodValue, periodLabel, admin.getName());
        } catch (IOException ex) {
            GuiUtil.showError(this, "Could not save the PDF:\n" + ex.getMessage());
            return;
        }

        boolean canOpen = Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
        if (canOpen && GuiUtil.confirm(this, "Report saved to\n" + file.getAbsolutePath()
                + "\n\nOpen it now?")) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException ex) {
                GuiUtil.showError(this, "The report was saved, but could not be opened:\n" + ex.getMessage());
            }
        } else if (!canOpen) {
            GuiUtil.showInfo(this, "Report saved to\n" + file.getAbsolutePath());
        }
    }

    private void refreshMaintenance() {
        AnalyticsService.MaintenanceSummary s = analytics.getMaintenanceSummary();
        totalCard.setText(String.valueOf(s.getTotal()));
        openCard2.setText(String.valueOf(s.getOpen()));
        resolvedCard.setText(String.valueOf(s.getResolved()));
        repairCard2.setText(repairText(s));

        casesModel.setRowCount(0);
        LinkedHashMap<Facility, Integer> cases = analytics.getMaintenanceCasesPerFacility();
        for (Facility f : cases.keySet()) {
            casesModel.addRow(new Object[] { f.getFacilityId(), f.getName(), cases.get(f) });
        }
    }

    private static String repairText(AnalyticsService.MaintenanceSummary s) {
        return s.hasAverageRepairTime()
                ? String.format("%.1f days", s.getAverageRepairDays())
                : "-";
    }
}
