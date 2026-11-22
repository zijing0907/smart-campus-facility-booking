package utar.util;

import java.io.IOException;
import java.util.Scanner;

/**
 * ConsoleUI.java — small utility class with static helpers for formatted
 * console output and user input.
 *
 * Why a utility class?
 *   The same prompts, headers, dividers and coloured messages are needed
 *   in every menu screen.  By centralising them here:
 *     • every screen looks consistent
 *     • we don't repeat the same printf / colour codes everywhere
 *     • changing the look (e.g. colour scheme) only needs editing one file
 *
 * All methods are static so they can be called without creating an object,
 * e.g. ConsoleUI.printSuccess("...").
 *
 * The colour codes are ANSI escape sequences understood by most modern
 * terminals (including IntelliJ's run window).  If the terminal doesn't
 * support them, the user will see the escape codes as plain text — no
 * functional issue, just slightly uglier output.
 */
public class ConsoleUI {

    // ANSI colour codes for terminal output
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String CYAN = "\u001B[36m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String WHITE = "\u001B[37m";

    // Prints a large bordered header box with the given title centred inside
    public static void printHeader(String title) {
        String border = "=".repeat(60);
        System.out.println(CYAN + BOLD);
        System.out.println("+" + border + "+");

        int pad = (60 - title.length()) / 2;
        String line = " ".repeat(Math.max(0, pad)) + title;
        line = line + " ".repeat(Math.max(0, 60 - line.length()));

        System.out.println("|" + line + "|");
        System.out.println("+" + border + "+");
        System.out.print(RESET);
    }

    // Prints a section divider with the section title
    public static void printSection(String title) {
        System.out.println(CYAN + BOLD + "\n--- " + title + " "
                + "-".repeat(Math.max(0, 50 - title.length())) + RESET);
    }

    // Coloured message helpers — used throughout the UI for consistency
    public static void printSuccess(String msg) {
        System.out.println(GREEN  + "  [OK]  " + msg + RESET);
    }
    public static void printError(String msg) {
        System.out.println(RED    + "  [ERR] " + msg + RESET);
    }
    public static void printWarning(String msg) {
        System.out.println(YELLOW + "  [!]   " + msg + RESET);
    }
    public static void printInfo(String msg) {
        System.out.println(CYAN   + "  [i]   " + msg + RESET);
    }
    public static void printAlert(String msg) {
        System.out.println(RED + BOLD + "  [!!!] " + msg + RESET);
    }

    // Prints a numbered menu item in yellow
    public static void printMenuItem(int num, String label) {
        System.out.printf("  %s[%d]%s %s%n", YELLOW, num, RESET, label);
    }

    // Prints a label-value pair in aligned columns
    public static void printField(String label, String value) {
        System.out.printf("  %-25s %s%s%s%n", CYAN + label + ":" + RESET, WHITE, value, RESET);
    }

    /**
     * fit - keep a table cell inside its column.  Text longer than the column
     * is cut and ends with "..." so the next column still lines up.
     */
    public static String fit(String text, int width) {
        if (text == null) return "";
        return text.length() <= width ? text : text.substring(0, width - 3) + "...";
    }

    // Prints a horizontal divider line
    public static void printDivider() {
        System.out.println(CYAN + "  " + "-".repeat(55) + RESET);
    }

    // Clears any leftover newline in the input buffer before reading a new line
    public static void flushInput(Scanner sc) {
        try {
            if (System.in.available() > 0) sc.nextLine();
        }
        catch (IOException ignored) {

        }
    }

    // Reads one line of input. If the input stream has ended (the user
    // pressed Ctrl+D / Ctrl+Z, or piped input ran out) there is nothing
    // more to read, so we exit cleanly instead of crashing with
    // NoSuchElementException. All data is already saved after every change.
    private static String readLine(Scanner sc) {
        if (!sc.hasNextLine()) {
            System.out.println(CYAN + "\n\n  Input closed. Goodbye!\n" + RESET);
            System.exit(0);
        }
        return sc.nextLine();
    }

    // Pauses and waits for the user to press ENTER before continuing
    public static void pressEnter(Scanner sc) {
        System.out.print(YELLOW + "\n  Press ENTER to continue..." + RESET);
        readLine(sc);
    }

    // Displays a prompt label and returns the user's trimmed input
    public static String prompt(Scanner sc, String label) {
        System.out.print(YELLOW + "  " + label + ": " + RESET);
        return readLine(sc).trim();
    }

    // Prompts for an integer input within the given range, loops until valid
    // Catches NumberFormatException if the user types a non-number (ch09)
    public static int promptInt(Scanner sc, String label, int min, int max) {
        while (true) {
            System.out.print(YELLOW + "  " + label + " (" + min + "-" + max + "): " + RESET);
            try {
                int v = Integer.parseInt(readLine(sc).trim());

                if (v >= min && v <= max)
                    return v;
                printError("Enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                // User typed letters or symbols — ask again
                printError("Invalid input — enter a whole number.");
            }
        }
    }

    // Clears the terminal screen
    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
