package utar.service;

import utar.model.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;

/**
 * AnalyticsService.java — Generates the summary reports required by the
 * assignment specification:
 *   • Facility type usage — bookings grouped by facility type
 *   • Facility utilization — bookings + maintenance per facility,
 *                            with monthly / semester / yearly filter
 *   • Peak booking hours  — busiest hours of the day
 *   • Maintenance summary — counts + average repair time
 *   • User stats          — a single user's own booking stats
 *
 * Each report has two methods:
 *   • getXxx()   — works out the numbers and RETURNS them. It never
 *                  prints, so any front end can use it: the console
 *                  menus, the Swing GUI, or a future web page.
 *   • printXxx() — calls getXxx() and prints the result as a text table
 *                  for the console menus.
 * Keeping the calculation separate from the display is the same layering
 * idea as the rest of the project: the service decides WHAT the numbers
 * are, the UI decides HOW they look.
 *
 * All numbers come from DataStore, so the reports always reflect the
 * latest state of the system.
 */
public class AnalyticsService {

    // Single shared DataStore for all reads.
    private DataStore ds = DataStore.getInstance();

    /** First and last hour shown in the peak-hours report (UTAR operating window). */
    public static final int FIRST_REPORT_HOUR = 8;
    public static final int LAST_REPORT_HOUR  = 21;

    // ─────────────────────────────────────────────────────────────────────────
    //  1) Facility-Type Usage Report
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * getBookingsByType — for every facility type (LECTURE_HALL,
     * COMPUTER_LAB, etc.), count how many active bookings exist.
     * Cancelled and rejected bookings are not counted.
     * A LinkedHashMap keeps the types in the same order as the enum.
     */
    public LinkedHashMap<Facility.FacilityType, Integer> getBookingsByType() {
        LinkedHashMap<Facility.FacilityType, Integer> result = new LinkedHashMap<>();

        // For each facility type → count active bookings whose facility has that type
        for (Facility.FacilityType type : Facility.FacilityType.values()) {
            int count = 0;

            for (Booking b : ds.getAllBookings()) {
                // Skip cancelled / rejected — they don't represent real usage
                if (!isActive(b)) continue;

                Facility f = ds.getFacilityById(b.getFacilityId());
                if (f != null && f.getType() == type) {
                    count++;
                }
            }

            result.put(type, count);
        }

        return result;
    }

    /** printFacilityTypeReport — console version of getBookingsByType(). */
    public void printFacilityTypeReport() {
        System.out.println("  Facility Type | Bookings");
        System.out.println("  " + "-".repeat(35));

        LinkedHashMap<Facility.FacilityType, Integer> counts = getBookingsByType();
        for (Facility.FacilityType type : counts.keySet()) {
            System.out.printf("  %-22s| %d%n", type.name(), counts.get(type));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2) Facility Utilization Report (with period filter)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * getBookingsPerFacility — count active bookings for every facility,
     * optionally filtered by a time period.  The maintenance count for
     * each facility is available from Facility.getMaintenanceCount().
     *
     * @param period one of "ALL" / "MONTHLY" / "SEMESTER" / "YEARLY"
     * @param value  meaning depends on period:
     *                   MONTHLY  → "MM/yyyy"     (e.g. "04/2026")
     *                   SEMESTER → "SEM1/yyyy", "SEM2/yyyy", "SEM3/yyyy"
     *                   YEARLY   → "yyyy"        (e.g. "2026")
     *                   ALL      → ignored
     */
    public LinkedHashMap<Facility, Integer> getBookingsPerFacility(String period, String value) {
        LinkedHashMap<Facility, Integer> result = new LinkedHashMap<>();

        for (Facility f : ds.getAllFacilities()) {
            int bookCount = 0;

            // Count active bookings of this facility that fall within the period
            for (Booking b : ds.getBookingsByFacility(f.getFacilityId())) {
                if (!isActive(b)) continue;

                if (matchesPeriod(b.getDate(), period, value)) {
                    bookCount++;
                }
            }

            result.put(f, bookCount);
        }

        return result;
    }

    /** printUtilizationReport — console version of getBookingsPerFacility(). */
    public void printUtilizationReport(String period, String value) {
        System.out.println("  Facility | Bookings | Maint Issues");
        System.out.println("  " + "-".repeat(55));

        LinkedHashMap<Facility, Integer> counts = getBookingsPerFacility(period, value);
        for (Facility f : counts.keySet()) {
            System.out.printf("  %-32s| %-9d| %d%n",
                              f.getName(), counts.get(f), f.getMaintenanceCount());
        }
    }

    /**
     * matchesPeriod — helper that decides whether a booking date falls
     * inside the requested filter.  Returns true if no filter is active.
     *
     * UTAR's three trimesters per year are handled here:
     *   SEM1 → Jan-Apr, SEM2 → May-Aug, SEM3 → Sep-Dec.
     *
     * Any parsing problem makes the filter fall through to "true" so a
     * single bad date can't break the whole report.
     */
    private boolean matchesPeriod(String date, String period, String value) {
        // No filter requested → keep everything
        if (period == null || period.equals("ALL")) {
            return true;
        }

        try {
            String[] dp = date.split("/"); // dp = [dd, MM, yyyy]

            if (period.equals("MONTHLY")) {
                // value = "MM/yyyy"
                String[] vp = value.split("/");
                return dp[1].equals(vp[0])     // same month
                    && dp[2].equals(vp[1]);    // same year
            }
            else if (period.equals("YEARLY")) {
                // value = "yyyy"
                return dp[2].equals(value);
            }
            else if (period.equals("SEMESTER")) {
                // value = "SEM1/yyyy"  (or SEM2 / SEM3)
                String[] vp = value.split("/");

                // year must match first
                if (!dp[2].equals(vp[1])) {
                    return false;
                }

                int month = Integer.parseInt(dp[1]);

                // UTAR trimester ranges
                if (vp[0].equals("SEM1")) return month >= 1 && month <=  4;
                if (vp[0].equals("SEM2")) return month >= 5 && month <=  8;
                if (vp[0].equals("SEM3")) return month >= 9 && month <= 12;

                return false;
            }
        } catch (Exception e) {
            // Bad date format — fall through and let the booking be counted
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3) Peak Booking Hours
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * getBookingsPerHour — find which hours of the day are most popular.
     *
     * For each booking we add 1 to every hour it spans (e.g. 09:00-11:00
     * adds 1 to hour 9 and 1 to hour 10).
     *
     * @return an array of 24 counts, where index = hour of the day
     */
    public int[] getBookingsPerHour() {
        int[] hourCount = new int[24];   // index = hour-of-day

        for (Booking b : ds.getAllBookings()) {
            if (!isActive(b)) continue;

            try {
                // Take only the "HH" part of the HH:mm strings
                int startH = Integer.parseInt(b.getStartTime().split(":")[0]);
                int endH   = Integer.parseInt(b.getEndTime().split(":")[0]);

                // Bump every hour the booking covers
                for (int h = startH; h < endH; h++) {
                    hourCount[h]++;
                }
            } catch (Exception ignored) {
                // Skip any malformed booking times — don't crash the report
            }
        }

        return hourCount;
    }

    /**
     * printPeakHoursReport — console version of getBookingsPerHour().
     * Prints a tiny ASCII bar chart — each '#' equals one booking-hour.
     * We display 08:00 → 21:00 because that's UTAR's typical operating window.
     */
    public void printPeakHoursReport() {
        int[] hourCount = getBookingsPerHour();

        System.out.println("  Hour   | Count | Bar");
        System.out.println("  " + "-".repeat(40));
        for (int h = FIRST_REPORT_HOUR; h <= LAST_REPORT_HOUR; h++) {
            System.out.printf("  %02d:00  | %-6d| %s%n",
                              h, hourCount[h], "#".repeat(hourCount[h]));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4) Maintenance Summary (totals + average repair time)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MaintenanceSummary — the numbers behind the maintenance summary
     * report, bundled into one small object so a method can return them
     * all at once.  Fields are private and read-only (Encapsulation).
     */
    public static class MaintenanceSummary {
        private final int total;
        private final int open;
        private final int resolved;
        private final int totalRepairDays;   // sum of days taken to fix
        private final int repairCount;       // reports included in that sum

        public MaintenanceSummary(int total, int open, int resolved,
                                  int totalRepairDays, int repairCount) {
            this.total           = total;
            this.open            = open;
            this.resolved        = resolved;
            this.totalRepairDays = totalRepairDays;
            this.repairCount     = repairCount;
        }

        public int getTotal()    { return total; }
        public int getOpen()     { return open; }
        public int getResolved() { return resolved; }

        /** true when at least one resolved report has valid dates. */
        public boolean hasAverageRepairTime() { return repairCount > 0; }

        /** Average days from report to fix; only meaningful if hasAverageRepairTime(). */
        public double getAverageRepairDays() {
            return repairCount == 0 ? 0 : (double) totalRepairDays / repairCount;
        }
    }

    /**
     * getMaintenanceSummary — total / open / resolved counts and the
     * average repair time in days (RESOLVED reports only).
     */
    public MaintenanceSummary getMaintenanceSummary() {
        ArrayList<MaintenanceReport> all = ds.getAllReports();

        int total = all.size();
        int open = 0, resolved = 0;
        int totalDays = 0, repairCount = 0;   // for the average

        for (MaintenanceReport r : all) {
            if (r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED) {
                resolved++;

                // Add the resolution time (in days) to the running average,
                // but only if both dates parse cleanly (>= 0).
                int days = calcDaysBetween(r.getReportedDate(), r.getResolvedDate());
                if (days >= 0) {
                    totalDays   += days;
                    repairCount++;
                }
            } else {
                open++;
            }
        }

        return new MaintenanceSummary(total, open, resolved, totalDays, repairCount);
    }

    /**
     * getMaintenanceCasesPerFacility — every facility that has had at
     * least one maintenance issue, with its issue count.
     */
    public LinkedHashMap<Facility, Integer> getMaintenanceCasesPerFacility() {
        LinkedHashMap<Facility, Integer> result = new LinkedHashMap<>();
        for (Facility f : ds.getAllFacilities()) {
            // Skip the facilities that have never had an issue
            if (f.getMaintenanceCount() > 0) {
                result.put(f, f.getMaintenanceCount());
            }
        }
        return result;
    }

    /** printMaintenanceSummary — console version of the two methods above. */
    public void printMaintenanceSummary() {
        MaintenanceSummary s = getMaintenanceSummary();

        // ── Print the totals ────────────────────────────────────────────────
        System.out.printf("  Total Reports  : %d%n", s.getTotal());
        System.out.printf("  Open           : %d%n", s.getOpen());
        System.out.printf("  Resolved       : %d%n", s.getResolved());

        // Show the average only when we have at least one resolved report
        if (s.hasAverageRepairTime()) {
            System.out.printf("  Avg Repair Time: %.1f day(s)%n", s.getAverageRepairDays());
        }

        // ── Per-facility breakdown ──────────────────────────────────────────
        System.out.println();
        System.out.println("  Maintenance cases per facility:");
        LinkedHashMap<Facility, Integer> cases = getMaintenanceCasesPerFacility();
        for (Facility f : cases.keySet()) {
            System.out.printf("  %-35s: %d case(s)%n", f.getName(), cases.get(f));
        }
    }

    /**
     * calcDaysBetween — number of days between two dd/MM/yyyy strings.
     * Returns -1 if either string can't be parsed (so the caller can skip it).
     */
    private int calcDaysBetween(String from, String to) {
        try {
            java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.LocalDate d1 = java.time.LocalDate.parse(from, fmt);
            java.time.LocalDate d2 = java.time.LocalDate.parse(to,   fmt);
            return (int) java.time.temporal.ChronoUnit.DAYS.between(d1, d2);
        } catch (Exception e) {
            return -1;     // bad date(s) — let the caller skip this record
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5) User Booking Stats
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * getUserBookingCounts — how many of one user's bookings are
     * PENDING / APPROVED / REJECTED / CANCELLED.  Every status is present
     * in the map, with 0 if the user has none.
     */
    public EnumMap<Booking.BookingStatus, Integer> getUserBookingCounts(String userId) {
        EnumMap<Booking.BookingStatus, Integer> counts = new EnumMap<>(Booking.BookingStatus.class);
        for (Booking.BookingStatus s : Booking.BookingStatus.values()) {
            counts.put(s, 0);
        }

        for (Booking b : ds.getBookingsByUser(userId)) {
            counts.put(b.getStatus(), counts.get(b.getStatus()) + 1);
        }
        return counts;
    }

    /** printUserStats — console version of getUserBookingCounts(). */
    public void printUserStats(String userId) {
        EnumMap<Booking.BookingStatus, Integer> c = getUserBookingCounts(userId);
        int total = ds.getBookingsByUser(userId).size();

        System.out.printf("  Total Bookings : %d%n", total);
        System.out.printf("  Approved       : %d%n", c.get(Booking.BookingStatus.APPROVED));
        System.out.printf("  Pending        : %d%n", c.get(Booking.BookingStatus.PENDING));
        System.out.printf("  Rejected       : %d%n", c.get(Booking.BookingStatus.REJECTED));
        System.out.printf("  Cancelled      : %d%n", c.get(Booking.BookingStatus.CANCELLED));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    /** isActive — cancelled and rejected bookings don't count as real usage. */
    private boolean isActive(Booking b) {
        return b.getStatus() != Booking.BookingStatus.CANCELLED
            && b.getStatus() != Booking.BookingStatus.REJECTED;
    }
}
