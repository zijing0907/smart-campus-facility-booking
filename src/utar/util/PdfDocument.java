package utar.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/**
 * PdfDocument.java - A small PDF writer: A4 pages with text, filled
 * rectangles and lines, using the Helvetica font every PDF reader has.
 *
 * The project has no external libraries, so instead of a library such as
 * Apache PDFBox this class writes the PDF format directly.  A PDF file is
 * mostly plain text:
 *   - numbered "objects": the catalog, the list of pages, the fonts, and
 *     one page object + one content stream per page
 *   - each content stream is a list of drawing commands, for example
 *         BT /F1 12 Tf 40 800 Td (Hello) Tj ET      write "Hello"
 *         0.18 0.43 0.71 rg 40 600 100 20 re f      fill a blue rectangle
 *   - a cross-reference table with the byte position of every object,
 *     so a reader can jump straight to any of them
 *
 * Coordinates passed to this class start at the TOP-left corner of the
 * page and go DOWN, like Swing.  PDF measures from the bottom-left, so
 * every y value is flipped when it's written.  Units are points (1/72 inch).
 * Colours are 0xRRGGBB integers, e.g. 0x1F3A68.
 */
public final class PdfDocument {

    /** A4 page size in points. */
    public static final float WIDTH  = 595.28f;
    public static final float HEIGHT = 841.89f;

    // Width of each character 32..126 in Helvetica, in 1/1000 of the font
    // size (from the standard Helvetica font metrics).  Used to centre and
    // right-align text and to cut text that doesn't fit.
    private static final int[] HELVETICA_WIDTHS = {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,   // space .. /
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556,                                 // 0 .. 9
        278, 278, 584, 584, 584, 556, 1015,                                               // : .. @
        667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833,                  // A .. M
        722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611,                  // N .. Z
        278, 278, 278, 469, 556, 333,                                                     // [ .. `
        556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,                  // a .. m
        556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500,                  // n .. z
        334, 260, 334, 584                                                                // { .. ~
    };

    private final ArrayList<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;          // drawing commands of the current page
    private String title = "Report";
    private String footer = "";

    public PdfDocument() {
        newPage();
    }

    /** Title shown in the PDF reader's window and document properties. */
    public void setTitle(String title) {
        this.title = title;
    }

    /** Small text at the bottom-left of every page; "Page x of y" goes on the right. */
    public void setFooter(String footer) {
        this.footer = footer;
    }

    /** Start a new, empty page; later drawing goes onto it. */
    public void newPage() {
        page = new StringBuilder();
        pages.add(page);
    }

    public int getPageCount() {
        return pages.size();
    }

    // -- Drawing -----------------------------------------------------------

    /** Write text with its baseline at y. */
    public void text(String s, float x, float y, float size, boolean bold, int rgb) {
        page.append("BT /").append(bold ? "F2 " : "F1 ").append(num(size)).append(" Tf ")
            .append(colour(rgb)).append(" rg ")
            .append(num(x)).append(' ').append(num(HEIGHT - y)).append(" Td (")
            .append(escape(s)).append(") Tj ET\n");
    }

    /** Write text so that it ends at rightX (for numbers in a column). */
    public void textRight(String s, float rightX, float y, float size, boolean bold, int rgb) {
        text(s, rightX - textWidth(s, size, bold), y, size, bold, rgb);
    }

    /** Write text centred on centreX. */
    public void textCentred(String s, float centreX, float y, float size, boolean bold, int rgb) {
        text(s, centreX - textWidth(s, size, bold) / 2, y, size, bold, rgb);
    }

    /** Fill a rectangle whose top-left corner is (x, y). */
    public void fillRect(float x, float y, float w, float h, int rgb) {
        page.append(colour(rgb)).append(" rg ")
            .append(num(x)).append(' ').append(num(HEIGHT - y - h)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    /** Draw the outline of a rectangle whose top-left corner is (x, y). */
    public void strokeRect(float x, float y, float w, float h, int rgb, float lineWidth) {
        page.append(num(lineWidth)).append(" w ").append(colour(rgb)).append(" RG ")
            .append(num(x)).append(' ').append(num(HEIGHT - y - h)).append(' ')
            .append(num(w)).append(' ').append(num(h)).append(" re S\n");
    }

    /** Draw a straight line. */
    public void line(float x1, float y1, float x2, float y2, int rgb, float lineWidth) {
        page.append(num(lineWidth)).append(" w ").append(colour(rgb)).append(" RG ")
            .append(num(x1)).append(' ').append(num(HEIGHT - y1)).append(" m ")
            .append(num(x2)).append(' ').append(num(HEIGHT - y2)).append(" l S\n");
    }

    // -- Measuring text ----------------------------------------------------

    /**
     * How wide the text will be, in points.  Bold Helvetica is a little
     * wider than regular, so bold widths are an estimate (5% wider).
     */
    public static float textWidth(String s, float size, boolean bold) {
        float units = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            units += (c >= 32 && c <= 126) ? HELVETICA_WIDTHS[c - 32] : 556;
        }
        return units * size / 1000f * (bold ? 1.05f : 1f);
    }

    /** Shorten text with "..." so it fits in maxWidth points. */
    public static String fit(String s, float maxWidth, float size, boolean bold) {
        if (textWidth(s, size, bold) <= maxWidth) {
            return s;
        }
        String cut = s;
        while (cut.length() > 0 && textWidth(cut + "...", size, bold) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.trim() + "...";
    }

    // -- Saving --------------------------------------------------------------

    /** Write the finished PDF to a file. */
    public void save(File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(toBytes());
        }
    }

    /**
     * toBytes - build the whole file.  Object numbers:
     *   1 catalog, 2 page list, 3 Helvetica, 4 Helvetica-Bold, 5 info,
     *   then for each page: a page object and its content stream.
     */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArrayList<Integer> offsets = new ArrayList<>();   // byte position of each object

        write(out, "%PDF-1.4\n");
        // A comment with bytes above 127 tells tools the file contains binary data
        out.write(0x25); out.write(0xE2); out.write(0xE3); out.write(0xCF); out.write(0xD3); out.write('\n');

        int pageCount = pages.size();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(6 + i * 2).append(" 0 R ");
        }

        addObject(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        addObject(out, offsets, "<< /Type /Pages /Kids [ " + kids + "] /Count " + pageCount + " >>");
        addObject(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica"
                + " /Encoding /WinAnsiEncoding >>");
        addObject(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold"
                + " /Encoding /WinAnsiEncoding >>");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        addObject(out, offsets, "<< /Title (" + escape(title) + ") /Producer (UTAR Smart Campus)"
                + " /CreationDate (D:" + now + ") >>");

        for (int i = 0; i < pageCount; i++) {
            int contentsId = 7 + i * 2;
            addObject(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + num(WIDTH) + " " + num(HEIGHT) + "] /Resources << /Font << /F1 3 0 R"
                    + " /F2 4 0 R >> >> /Contents " + contentsId + " 0 R >>");

            String content = pages.get(i) + footerCommands(i + 1, pageCount);
            byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
            addObject(out, offsets, "<< /Length " + bytes.length + " >>\nstream\n"
                    + content + "\nendstream");
        }

        // Cross-reference table: every entry is exactly 20 bytes long
        int xrefStart = out.size();
        write(out, "xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R /Info 5 0 R >>\n"
                + "startxref\n" + xrefStart + "\n%%EOF\n");
        return out.toByteArray();
    }

    private String footerCommands(int pageNumber, int pageCount) {
        StringBuilder saved = page;
        page = new StringBuilder();                    // draw into a scratch buffer
        float y = HEIGHT - 24;
        line(40, y - 12, WIDTH - 40, y - 12, 0xD5DBE3, 0.5f);
        text(footer, 40, y, 8, false, 0x6B7280);
        textRight("Page " + pageNumber + " of " + pageCount, WIDTH - 40, y, 8, false, 0x6B7280);
        String commands = page.toString();
        page = saved;
        return commands;
    }

    private static void addObject(ByteArrayOutputStream out, ArrayList<Integer> offsets, String body) {
        offsets.add(out.size());
        write(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    // -- Small formatting helpers ------------------------------------------

    /** A number with at most 2 decimals and always a '.' (never a locale comma). */
    private static String num(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private static String colour(int rgb) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f",
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    /**
     * escape - make text safe inside ( ) in a PDF.  Backslash and brackets
     * get a backslash in front; letters outside plain ASCII are written as
     * octal codes (\351 = e-acute), and characters the font can't show are
     * replaced with a close match or '?'.
     */
    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u2014' || c == '\u2013' || c == '\u00B7') c = '-';
            if (c == '\u2018' || c == '\u2019') c = '\'';
            if (c == '\u201C' || c == '\u201D') c = '"';

            if (c == '(' || c == ')' || c == '\\') {
                out.append('\\').append(c);
            } else if (c >= 32 && c <= 126) {
                out.append(c);
            } else if (c >= 160 && c <= 255) {
                out.append('\\').append(Integer.toOctalString(c));
            } else if (c >= 32) {
                out.append('?');
            }
            // control characters (below 32) are dropped
        }
        return out.toString();
    }
}
