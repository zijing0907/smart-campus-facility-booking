package utar.util;

/**
 * InputValidator.java — Static utility class for checking user input.
 *
 * Every method here returns a boolean (true = valid, false = invalid).
 * The actual prompting and re-asking is done by the menu classes;
 * this class only knows how to JUDGE a value, not how to ASK for it.
 *
 * Implementation notes:
 *   • No regular expressions used — all checks are written with simple
 *     String methods (charAt, indexOf, length, substring, endsWith).
 *   • Every method returns false on null input — so callers don't need
 *     to do their own null-check first.
 */
public class InputValidator {

    // Minimum number of characters in a password.
    private static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * isValidEmail — basic email format check.
     * Rules:
     *   • exactly one '@' (and not as the first character)
     *   • at least one '.' after the '@'
     *   • at least one character after the last '.'
     *   • no spaces anywhere
     *
     * Uses indexOf() and a simple charAt() loop — ch06 Strings.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty())
            return false;

        // (1) exactly one '@', not at the start
        int atIndex = email.indexOf('@');
        if (atIndex <= 0)
            return false;
        if (email.indexOf('@', atIndex + 1) != -1)
            return false;   // second '@' = bad

        // (2) at least one '.' after the '@'
        int dotIndex = email.indexOf('.', atIndex + 1);
        if (dotIndex == -1)
            return false;

        // (3) at least one character after the last dot
        if (dotIndex >= email.length() - 1)
            return false;

        // (4) no spaces
        for (int i = 0; i < email.length(); i++) {
            if (email.charAt(i) == ' ')
                return false;
        }

        return true;
    }

    /**
     * isUtarEmail — extra rule on top of isValidEmail():
     * the email must end with "@utar.edu.my" (case-insensitive).
     * Used during registration to enforce UTAR-only sign-ups.
     */
    public static boolean isUtarEmail(String email) {
        if (email == null)
            return false;

        return email.toLowerCase().endsWith("@utar.edu.my");
    }

    /** isValidPassword — at least MIN_PASSWORD_LENGTH (6) characters. */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * isValidName — letters and spaces only.  Walks the string with
     * charAt() and rejects digits, punctuation, etc.
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isBlank())
            return false;

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z')
                           || (c >= 'a' && c <= 'z')
                           || c == ' ';

            if (!allowed)
                return false;
        }
        return true;
    }

    /**
     * isValidPhone — 8 to 15 characters, must contain only:
     * digits, spaces, hyphens, '+', '(' or ')'.
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null)
            return false;
        int len = phone.length();

        if (len < 8 || len > 15)
            return false;

        for (int i = 0; i < len; i++) {
            char c = phone.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                           || c == '-' || c == '+' || c == '(' || c == ')' || c == ' ';

            if (!allowed)
                return false;
        }
        return true;
    }

    /**
     * BOOKING_YEAR — the only year accepted by isValidDate().
     * For this assignment we only allow bookings inside the current
     * booking year (2026).
     */
    public static final int BOOKING_YEAR = 2026;

    /**
     * isValidDate — accepts strings shaped like "dd/MM/yyyy".
     * Rules:
     *   • length must be exactly 10
     *   • positions 2 and 5 must be '/'
     *   • all other positions must be digits
     *   • day 1..31, month 1..12, year == BOOKING_YEAR
     */
    public static boolean isValidDate(String date) {
        if (date == null || date.length() != 10)
            return false;

        if (date.charAt(2) != '/' || date.charAt(5) != '/')
            return false;

        // every position other than the slashes must be a digit
        for (int i = 0; i < 10; i++) {
            if (i == 2 || i == 5)
                continue;     // skip the '/' positions

            if (date.charAt(i) < '0' || date.charAt(i) > '9')
                return false;
        }

        // pull out the numeric values and range-check them
        int day   = Integer.parseInt(date.substring(0, 2));
        int month = Integer.parseInt(date.substring(3, 5));
        int year  = Integer.parseInt(date.substring(6, 10));

        if (day   < 1 || day   > 31)
            return false;

        if (month < 1 || month > 12)
            return false;

        return year == BOOKING_YEAR;
    }

    /**
     * isValidTime — accepts strings shaped like "HH:mm" (24-hour clock).
     * Length 5, char[2] = ':', remaining chars are digits, hour 0..23, min 0..59.
     */
    public static boolean isValidTime(String time) {
        if (time == null || time.length() != 5)
            return false;

        if (time.charAt(2) != ':')
            return false;

        // every position other than the colon must be a digit
        for (int i = 0; i < 5; i++) {
            if (i == 2)
                continue;

            if (time.charAt(i) < '0' || time.charAt(i) > '9')
                return false;
        }

        int h = Integer.parseInt(time.substring(0, 2));
        int m = Integer.parseInt(time.substring(3, 5));
        return h >= 0 && h <= 23 && m >= 0 && m <= 59;
    }

    /**
     * isValidTimeRange — true if start is strictly before end.
     * Both strings are assumed to already be HH:mm.  Because that format
     * is fixed-width, lexicographic compareTo() gives the same result as
     * comparing real times, so we don't need to parse them into numbers.
     */
    public static boolean isValidTimeRange(String start, String end) {
        return start.compareTo(end) < 0;
    }

    /**
     * toMinutes — turn "HH:mm" into total minutes since midnight.
     * Example: "09:30" -> 570.  Used by hasMinimumDuration() below
     * and by BookingService.getDueReminders() when calculating how
     * many minutes from now the booking starts.
     */
    public static int toMinutes(String time) {
        return Integer.parseInt(time.substring(0, 2)) * 60
             + Integer.parseInt(time.substring(3, 5));
    }

    /** Minimum number of minutes a booking must last. */
    public static final int MIN_BOOKING_MINUTES = 30;

    /** hasMinimumDuration — true if (end - start) >= MIN_BOOKING_MINUTES. */
    public static boolean hasMinimumDuration(String start, String end) {
        return (toMinutes(end) - toMinutes(start)) >= MIN_BOOKING_MINUTES;
    }

    /**
     * isWithinOperatingHours — bookings must fall inside 08:00..22:00.
     * Uses string comparison again (same trick as isValidTimeRange).
     */
    public static boolean isWithinOperatingHours(String start, String end) {
        return start.compareTo("08:00") >= 0 && end.compareTo("22:00") <= 0;
    }
}
