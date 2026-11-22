package utar.gui;

import javax.swing.*;
import java.awt.*;

/**
 * BarChartPanel.java - A simple vertical bar chart drawn with Java2D.
 *
 * Used by the admin Reports tab for peak booking hours and bookings per
 * facility type.  It's drawn by hand in paintComponent() so the project
 * still needs no external libraries.
 *
 * OOP concepts:
 *   - Inheritance: extends JPanel and overrides paintComponent(), the
 *     method Swing calls whenever the panel needs to be drawn.
 *   - Encapsulation: the data can only be changed through setData(),
 *     which also asks Swing to redraw the chart.
 */
class BarChartPanel extends JPanel {

    private static final int MAX_GRID_LINES = 5;

    private final String title;
    private final String emptyMessage;
    private String[] labels = new String[0];
    private int[] values = new int[0];

    BarChartPanel(String title, String emptyMessage) {
        this.title = title;
        this.emptyMessage = emptyMessage;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(0xD5, 0xDB, 0xE3)));
        setPreferredSize(new Dimension(460, 300));
    }

    /** Replace the chart's data and redraw it.  labels and values must be the same length. */
    void setData(String[] labels, int[] values) {
        this.labels = labels;
        this.values = values;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getHeight();

        // -- Title ---------------------------------------------------------
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.setColor(new Color(0x22, 0x22, 0x22));
        g2.drawString(title, 16, 26);
        g2.setFont(getFont());

        // -- Work out the plot area ----------------------------------------
        int maxWordLines = 1;   // x labels can wrap onto several lines
        for (String label : labels) {
            maxWordLines = Math.max(maxWordLines, label.split(" ").length);
        }
        int left   = 44;
        int right  = getWidth() - 16;
        int top    = 48;
        int bottom = getHeight() - 14 - maxWordLines * lineHeight;
        int plotHeight = bottom - top;
        if (labels.length == 0 || plotHeight <= 0 || right <= left) {
            g2.dispose();
            return;
        }

        int max = 0;
        for (int v : values) {
            max = Math.max(max, v);
        }

        // -- Gridlines and y-axis numbers ------------------------------------
        // Pick a whole-number step so there are at most MAX_GRID_LINES lines.
        int step = Math.max(1, (int) Math.ceil(max / (double) MAX_GRID_LINES));
        int axisMax = Math.max(step, ((max + step - 1) / step) * step);

        for (int v = 0; v <= axisMax; v += step) {
            int y = bottom - (int) Math.round(v / (double) axisMax * plotHeight);
            g2.setColor(new Color(0xEC, 0xEF, 0xF3));
            g2.drawLine(left, y, right, y);
            g2.setColor(Color.GRAY);
            String text = String.valueOf(v);
            g2.drawString(text, left - 8 - fm.stringWidth(text), y + fm.getAscent() / 2 - 1);
        }

        // -- Bars, value labels and x labels -------------------------------
        double slot = (right - left) / (double) labels.length;
        int barWidth = Math.max(4, (int) (slot * 0.6));

        for (int i = 0; i < labels.length; i++) {
            int centreX = left + (int) (slot * i + slot / 2);
            int barHeight = (int) Math.round(values[i] / (double) axisMax * plotHeight);
            int x = centreX - barWidth / 2;

            g2.setColor(GuiUtil.BAR);
            g2.fillRect(x, bottom - barHeight, barWidth, barHeight);

            if (values[i] > 0) {
                g2.setColor(new Color(0x22, 0x22, 0x22));
                String text = String.valueOf(values[i]);
                g2.drawString(text, centreX - fm.stringWidth(text) / 2, bottom - barHeight - 4);
            }

            // one word per line so long names like "Multipurpose Hall" fit
            g2.setColor(new Color(0x44, 0x44, 0x44));
            String[] words = labels[i].split(" ");
            for (int w = 0; w < words.length; w++) {
                int y = bottom + 4 + fm.getAscent() + w * lineHeight;
                g2.drawString(words[w], centreX - fm.stringWidth(words[w]) / 2, y);
            }
        }

        // x-axis line
        g2.setColor(Color.GRAY);
        g2.drawLine(left, bottom, right, bottom);

        if (max == 0) {
            g2.setColor(Color.GRAY);
            g2.drawString(emptyMessage, (left + right - fm.stringWidth(emptyMessage)) / 2,
                          top + plotHeight / 2);
        }
        g2.dispose();
    }
}
