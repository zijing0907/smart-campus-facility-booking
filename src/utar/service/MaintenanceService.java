package utar.service;

import utar.model.*;
import java.util.ArrayList;

/**
 * MaintenanceService.java — Business logic for facility maintenance.
 * Handles every maintenance-related use-case:
 *   • reportIssue           — a user reports a broken/damaged facility
 *   • assignReport          — admin assigns a report to a maintenance worker
 *   • updateStatus          — worker updates the progress (in progress / resolved)
 *   • getOpenReports        — list reports that are not yet resolved
 *   • getFrequentIssueAlerts — find facilities with too many open issues
 *   • setFacilityStatus     — admin opens / closes / sets a facility under maintenance
 * All file I/O goes through DataStore (Singleton).
 */
public class MaintenanceService {

    // Single shared DataStore — handles all reads/writes to disk.
    private DataStore ds = DataStore.getInstance();

    // If a facility has this many or more *open* issues, the system raises
    // an alert to the admin.  Tweak this constant to change the rule.
    private static final int ALERT_THRESHOLD = 3;

    /**
     * reportIssue — user submits a new maintenance report.
     * Steps:
     *   1. Look up the facility so we can update its issue counter.
     *      If it doesn't exist, throw IllegalArgumentException (ch09).
     *   2. Build a new MaintenanceReport with a fresh ID.
     *   3. Bump the facility's maintenanceCount.
     *   4. Save both the facility and the new report.
     */
    public MaintenanceReport reportIssue(String facilityId, String reportedBy,
                                          String description,
                                          MaintenanceReport.IssueCategory category,
                                          String date)
    {
        // (1) facility must exist — Exception Handling (ch09)
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }

        // (2) create the report with a freshly-generated ID
        String id = ds.nextReportId();
        MaintenanceReport r = new MaintenanceReport(id, facilityId, reportedBy,
                                                     description, category, date);

        // (3) update the facility's issue counter
        f.incrementMaintenanceCount();

        // (4) save to disk
        ds.addFacility(f);
        ds.addReport(r);

        return r;
    }

    /**
     * assignReport — admin assigns the report to a maintenance worker.
     * The setAssignedTo() method on MaintenanceReport also flips the
     * status from REPORTED to ASSIGNED automatically.
     * @return true if the report exists, false otherwise
     */
    public boolean assignReport(String reportId, String personnelId)
    {
        MaintenanceReport r = ds.getReportById(reportId);
        if (r == null) {
            return false;
        }

        r.setAssignedTo(personnelId);   // also sets status = ASSIGNED
        ds.addReport(r);                // persist
        return true;
    }

    /**
     * updateStatus — maintenance worker (or admin) updates the report.
     * If the new status is RESOLVED, we also save the resolved date.
     * Resolution notes are optional — only saved if not blank.
     */
    public boolean updateStatus(String reportId,
                                 MaintenanceReport.ReportStatus newStatus,
                                 String notes, String resolvedDate)
    {
        MaintenanceReport r = ds.getReportById(reportId);
        if (r == null) {
            return false;
        }

        r.setStatus(newStatus);

        // optional notes
        if (notes != null && !notes.isBlank()) {
            r.setResolutionNotes(notes);
        }

        // record the resolved date only when actually resolving
        if (newStatus == MaintenanceReport.ReportStatus.RESOLVED) {
            r.setResolvedDate(resolvedDate);
        }

        ds.addReport(r);   // persist
        return true;
    }

    /**
     * getOpenReports — return every report that is NOT yet RESOLVED.
     * Used by the admin & maintenance personnel screens.
     */
    public ArrayList<MaintenanceReport> getOpenReports() {
        ArrayList<MaintenanceReport> result = new ArrayList<>();

        for (MaintenanceReport r : ds.getAllReports()) {
            if (r.getStatus() != MaintenanceReport.ReportStatus.RESOLVED) {
                result.add(r);
            }
        }

        return result;
    }

    /** getAllReports — convenience pass-through to DataStore. */
    public ArrayList<MaintenanceReport> getAllReports() {
        return ds.getAllReports();
    }

    /**
     * getFrequentIssueAlerts — scan every facility and produce alert
     * messages for those with too many open issues (>= ALERT_THRESHOLD).
     * Output example:
     *   "ALERT: Lecture Theatre KG01 has 4 open issues!"
     * Used by the admin dashboard to spotlight problem facilities.
     */
    public ArrayList<String> getFrequentIssueAlerts() {
        ArrayList<String> alerts = new ArrayList<>();

        // For each facility, count its open (un-resolved) reports
        for (Facility f : ds.getAllFacilities()) {
            int open = 0;

            for (MaintenanceReport r : ds.getReportsByFacility(f.getFacilityId())) {
                if (r.getStatus() != MaintenanceReport.ReportStatus.RESOLVED) {
                    open++;
                }
            }

            // Trigger an alert if the count crosses our threshold
            if (open >= ALERT_THRESHOLD) {
                alerts.add("ALERT: " + f.getName() + " has " + open + " open issues!");
            }
        }

        return alerts;
    }

    /**
     * setFacilityStatus — admin manually changes the facility's status
     * (e.g. mark it "UNDER_MAINTENANCE" while a big repair happens).
     * @return true on success, false if the facility ID doesn't exist
     */
    public boolean setFacilityStatus(String facilityId,
                                      Facility.FacilityStatus status)
    {
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            return false;
        }

        f.setStatus(status);
        ds.addFacility(f);   // persist
        return true;
    }
}
