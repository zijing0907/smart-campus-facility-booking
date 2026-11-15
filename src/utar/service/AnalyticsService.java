package utar.service;

import utar.model.*;
import java.util.ArrayList;

/**
 * AnalyticsService.java — Generates the summary reports required by the
 * assignment specification:
 *   • printFacilityTypeReport — bookings grouped by facility type
 *   • printUtilizationReport  — bookings + maintenance per facility,
 *                                with monthly / semester / yearly filter
 *   • printPeakHoursReport    — busiest hours of the day (with a bar chart)
 *   • printMaintenanceSummary — counts + average repair time
 *   • printUserStats          — a single user's own booking stats
 *
 * All numbers come from DataStore, so the reports always reflect the
 * latest state of the system.
 */
public class AnalyticsService {

    // Single shared DataStore for all reads.
    private DataStore ds = DataStore.getInstance();

    // ─────────────────────────────────────────────────────────────────────────
    //  1) Facility-Type Usage Report
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * printFacilityTypeReport — for every facility type (LECTURE_HALL,
     * COMPUTER_LAB, etc.), count how many active bookings exist.
     * Cancelled and rejected bookings are not counted.
     */
    public void printFacilityTypeReport() {
        System.out.println("  Facility Type | Bookings");
        System.out.println("  " + "-".repeat(35));

        // For each facility type → count active bookings whose facility has that type
        for (Facility.FacilityType type : Facility.FacilityType.values()) {
            int count = 0;

            for (Booking b : ds.getAllBookings()) {
                // Skip cancelled / rejected — they don't represent real usage
                if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
                if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;

                Facility f = ds.getFacilityById(b.getFacilityId());
                if (f != null && f.getType() == type) {
                    count++;
                }
            }

            System.out.printf("  %-22s| %d%n", type.name(), count);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2) Facility Utilization Report (with period filter)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * printUtilizationReport — show booking count + maintenance count for
     * every facility, optionally filtered by a time period.
     *
     * @param period one of "ALL" / "MONTHLY" / "SEMESTER" / "YEARLY"
     * @param value  meaning depends on period:
     *                   MONTHLY  → "MM/yyyy"     (e.g. "04/2026")
     *                   SEMESTER → "SEM1/yyyy", "SEM2/yyyy", "SEM3/yyyy"
     *                   YEARLY   → "yyyy"        (e.g. "2026")
     *                   ALL      → ignored
     */
    public void printUtilizationReport(String period, String value) {
        System.out.println("  Facility | Bookings | Maint Issues");
        System.out.println("  " + "-".repeat(55));

        for (Facility f : ds.getAllFacilities()) {
            int bookCount = 0;

            // Count active bookings of this facility that fall within the period
            for (Booking b : ds.getBookingsByFacility(f.getFacilityId())) {
                if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
                if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;

                if (matchesPeriod(b.getDate(), period, value)) {
                    bookCount++;
                }
            }

            System.out.printf("  %-32s| %-9d| %d%n",
                              f.getName(), bookCount, f.getMaintenanceCount());
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
     * printPeakHoursReport — find which hours of the day are most popular.
     *
     * For each booking we add 1 to every hour it spans (e.g. 09:00-11:00
     * adds 1 to hour 9 and 1 to hour 10).  We then print a tiny ASCII bar
     * chart — each '#' equals one booking-hour.
     *
     * We display 08:00 → 21:00 because that's UTAR's typical operating window.
     */
    public void printPeakHoursReport() {
        int[] hourCount = new int[24];   // index = hour-of-day

        for (Booking b : ds.getAllBookings()) {
            if (b.getStatus() == Booking.BookingStatus.CANCELLED) continue;
            if (b.getStatus() == Booking.BookingStatus.REJECTED)  continue;

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

        // Print the bar chart
        System.out.println("  Hour   | Count | Bar");
        System.out.println("  " + "-".repeat(40));
        for (int h = 8; h <= 21; h++) {
            System.out.printf("  %02d:00  | %-6d| %s%n",
                              h, hourCount[h], "#".repeat(hourCount[h]));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4) Maintenance Summary (totals + average repair time)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * printMaintenanceSummary — summary of the maintenance side:
     *   • total / open / resolved counts
     *   • average repair time in days (RESOLVED reports only)
     *   • per-facility maintenance counts (only those with > 0 cases)
     */
    public void printMaintenanceSummary() {
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

        // ── Print the totals ────────────────────────────────────────────────
        System.out.printf("  Total Reports  : %d%n", total);
        System.out.printf("  Open           : %d%n", open);
        System.out.printf("  Resolved       : %d%n", resolved);

        // Show the average only when we have at least one resolved report
        if (repairCount > 0) {
            System.out.printf("  Avg Repair Time: %.1f day(s)%n",
                              (double) totalDays / repairCount);
        }

        // ── Per-facility breakdown ──────────────────────────────────────────
        System.out.println();
        System.out.println("  Maintenance cases per facility:");
        for (Facility f : ds.getAllFacilities()) {
            // Skip the facilities that have never had an issue
            if (f.getMaintenanceCount() > 0) {
                System.out.printf("  %-35s: %d case(s)%n",
                                  f.getName(), f.getMaintenanceCount());
            }
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
     * printUserStats — a single user's own booking stats.
     * Counts how many of their bookings are PENDING / APPROVED / REJECTED /
     * CANCELLED and prints a small report.
     */
    public void printUserStats(String userId) {
        ArrayList<Booking> list = ds.getBookingsByUser(userId);

        int pending = 0, approved = 0, rejected = 0, cancelled = 0;

        // Count by status using a switch — clearer than nested if/else
        for (Booking b : list) {
            switch (b.getStatus()) {
                case PENDING: pending++;
                break;
                case APPROVED:  approved++;
                break;
                case REJECTED:  rejected++;
                break;
                case CANCELLED: cancelled++;
                break;
            }
        }

        System.out.printf("  Total Bookings : %d%n", list.size());
        System.out.printf("  Approved       : %d%n", approved);
        System.out.printf("  Pending        : %d%n", pending);
        System.out.printf("  Rejected       : %d%n", rejected);
        System.out.printf("  Cancelled      : %d%n", cancelled);
    }
}
