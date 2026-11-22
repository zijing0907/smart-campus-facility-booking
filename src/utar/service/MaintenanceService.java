package utar.service;

import utar.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * MaintenanceService.java — Business logic for facility maintenance.
 * Handles every maintenance-related use-case:
 *   • reportIssue           — a user reports a broken/damaged facility,
 *                             optionally with a photo or PDF attached
 *   • assignReport          — admin assigns a report to a maintenance worker
 *   • updateStatus          — worker updates the progress (in progress / resolved)
 *   • getOpenReports        — list reports that are not yet resolved
 *   • getFrequentIssueAlerts — find facilities with too many open issues
 *   • setFacilityStatus     — admin opens / closes / sets a facility under maintenance
 * Methods used by an admin or worker take that person's ID first, so the
 * activity log records who did it.  The people affected are notified.
 * All file I/O goes through DataStore (Singleton).
 */
public class MaintenanceService {

    // Single shared DataStore — handles all reads/writes to disk.
    private DataStore ds = DataStore.getInstance();
    private NotificationService notifier = new NotificationService();
    private ActivityLogService activityLog = new ActivityLogService();

    // If a facility has this many or more *open* issues, the system raises
    // an alert to the admin.  Tweak this constant to change the rule.
    private static final int ALERT_THRESHOLD = 3;

    /** Largest file that can be attached to a report: 5 MB. */
    public static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    /** Photo types that can be attached (a PDF can be attached too). */
    private static final String[] IMAGE_TYPES = { "jpg", "jpeg", "png", "gif", "bmp" };

    /**
     * reportIssue — user submits a new maintenance report with no attachment.
     */
    public MaintenanceReport reportIssue(String facilityId, String reportedBy,
                                          String description,
                                          MaintenanceReport.IssueCategory category,
                                          String date)
    {
        return reportIssue(facilityId, reportedBy, description, category, date, null);
    }

    /**
     * reportIssue — user submits a new maintenance report, optionally with
     * a photo or PDF (attachment may be null).
     * Steps:
     *   1. Look up the facility so we can update its issue counter.
     *      If it doesn't exist, throw IllegalArgumentException (ch09).
     *   2. Check the attachment BEFORE saving anything, so a bad file
     *      never leaves a half-saved report behind.
     *   3. Build a new MaintenanceReport with a fresh ID and copy the
     *      attachment into data/attachments.
     *   4. Bump the facility's maintenanceCount.
     *   5. Save both the facility and the new report.
     */
    public MaintenanceReport reportIssue(String facilityId, String reportedBy,
                                          String description,
                                          MaintenanceReport.IssueCategory category,
                                          String date, File attachment)
    {
        // (1) facility must exist — Exception Handling (ch09)
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }

        // (2) type, size and contents of the attachment
        String extension = attachment == null ? null : checkAttachment(attachment);

        // (3) create the report with a freshly-generated ID
        String id = ds.nextReportId();
        MaintenanceReport r = new MaintenanceReport(id, facilityId, reportedBy,
                                                     description, category, date);
        if (attachment != null) {
            try {
                r.setAttachment(ds.saveAttachment(id, attachment, extension));
            } catch (IOException e) {
                throw new IllegalStateException("Could not save the attachment: " + e.getMessage());
            }
        }

        // (4) update the facility's issue counter
        f.incrementMaintenanceCount();

        // (5) save to disk
        ds.addFacility(f);
        ds.addReport(r);

        activityLog.record(reportedBy, LogEntry.Action.REPORT_SUBMITTED,
                id + " for " + f.getName() + ": " + description);
        notifier.notifyAdmins("New maintenance report " + id + " for " + f.getName() + ": "
                + description + (r.hasAttachment() ? " (photo/file attached)" : ""));
        return r;
    }

    /**
     * checkAttachment — make sure a file is safe and reasonable to attach.
     * Rules:
     *   • it must be a photo (JPG, PNG, GIF, BMP) or a PDF
     *   • it must be 5 MB or smaller
     *   • its first bytes must match its type (every PNG starts with the
     *     same 8 bytes, every PDF with "%PDF", ...), so a program renamed
     *     to "photo.jpg" is refused.  The admin's computer opens these
     *     files, so only file types that can't run as programs are allowed.
     * @return the file's extension in lower case, e.g. "jpg"
     * @throws IllegalArgumentException with a message for the user if not
     */
    public static String checkAttachment(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("That file could not be found.");
        }
        String extension = extensionOf(file.getName());
        if (!isImageType(extension) && !extension.equals("pdf")) {
            throw new IllegalArgumentException(
                "Only photos (JPG, PNG, GIF, BMP) or PDF files can be attached.");
        }
        if (file.length() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException(String.format(
                "That file is %.1f MB. Files can be up to 5 MB.", file.length() / (1024.0 * 1024)));
        }
        if (!startsLikeItsType(file, extension)) {
            throw new IllegalArgumentException("That file doesn't look like a real "
                + extension.toUpperCase() + " file.");
        }
        return extension;
    }

    /** isImage — true if an attachment name is a photo (not a PDF). */
    public static boolean isImage(String fileName) {
        return fileName != null && isImageType(extensionOf(fileName));
    }

    private static boolean isImageType(String extension) {
        for (String type : IMAGE_TYPES) {
            if (type.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    /** "Broken Chair.JPG" -> "jpg";  no dot -> "". */
    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    /** Compare the file's first bytes with the signature its type always starts with. */
    private static boolean startsLikeItsType(File file, String extension) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = new FileInputStream(file)) {
            read = in.read(head);
        } catch (IOException e) {
            return false;
        }
        if (read < 4) {
            return false;   // too short to be any of these types
        }
        switch (extension) {
            case "jpg": case "jpeg":
                return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case "png":
                return (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "gif":
                return head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
            case "bmp":
                return head[0] == 'B' && head[1] == 'M';
            case "pdf":
                return head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            default:
                return false;
        }
    }

    /**
     * assignReport — admin assigns the report to a maintenance worker.
     * The setAssignedTo() method on MaintenanceReport also flips the
     * status from REPORTED to ASSIGNED automatically.
     * @return true if the report exists, false otherwise
     */
    public boolean assignReport(String adminId, String reportId, String personnelId)
    {
        MaintenanceReport r = ds.getReportById(reportId);
        if (r == null) {
            return false;
        }

        r.setAssignedTo(personnelId);   // also sets status = ASSIGNED
        ds.addReport(r);                // persist

        String where = facilityName(r.getFacilityId());
        activityLog.record(adminId, LogEntry.Action.REPORT_ASSIGNED,
                reportId + " (" + where + ") to " + userName(personnelId));
        notifier.notify(personnelId, "New task " + reportId + " at " + where + ": "
                + r.getIssueDescription());
        notifier.notify(r.getReportedByUserId(), "Your report " + reportId + " about " + where
                + " has been assigned to maintenance staff.");
        return true;
    }

    /**
     * updateStatus — maintenance worker (or admin) updates the report.
     * If the new status is RESOLVED, we also save the resolved date.
     * Resolution notes are optional — only saved if not blank.
     */
    public boolean updateStatus(String workerId, String reportId,
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

        String where = facilityName(r.getFacilityId());
        activityLog.record(workerId, LogEntry.Action.REPORT_UPDATED,
                reportId + " (" + where + ") is now " + newStatus);
        if (newStatus == MaintenanceReport.ReportStatus.RESOLVED) {
            notifier.notify(r.getReportedByUserId(), "Your report " + reportId + " about " + where
                    + " has been fixed." + (notes == null || notes.isBlank() ? "" : " Notes: " + notes));
        }
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
    public boolean setFacilityStatus(String adminId, String facilityId,
                                      Facility.FacilityStatus status)
    {
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            return false;
        }
        Facility.FacilityStatus old = f.getStatus();

        f.setStatus(status);
        ds.addFacility(f);   // persist

        if (old != status) {
            activityLog.record(adminId, LogEntry.Action.FACILITY_STATUS_CHANGED,
                    f.getName() + " (" + facilityId + "): " + old + " -> " + status);
            if (status != Facility.FacilityStatus.AVAILABLE) {
                warnUpcomingBookings(f, status);
            }
        }
        return true;
    }

    /**
     * warnUpcomingBookings — when a facility is closed or put under
     * maintenance, tell everyone with a pending or approved booking there
     * from today onwards, so they can make other plans.
     */
    private void warnUpcomingBookings(Facility f, Facility.FacilityStatus status)
    {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        String state = status == Facility.FacilityStatus.CLOSED ? "closed" : "under maintenance";

        for (Booking b : ds.getBookingsByFacility(f.getFacilityId())) {
            boolean active = b.getStatus() == Booking.BookingStatus.PENDING
                          || b.getStatus() == Booking.BookingStatus.APPROVED;
            boolean upcoming;
            try {
                upcoming = !java.time.LocalDate.parse(b.getDate(), fmt).isBefore(today);
            } catch (Exception e) {
                upcoming = false;   // unreadable date — skip it
            }
            if (active && upcoming) {
                notifier.notify(b.getUserId(), f.getName() + " is now " + state
                        + ". Your booking " + b.getBookingId() + " on " + b.getDate() + ", "
                        + b.getStartTime() + "-" + b.getEndTime()
                        + " may be affected - please contact the admin office.");
            }
        }
    }

    private String facilityName(String facilityId)
    {
        Facility f = ds.getFacilityById(facilityId);
        return f != null ? f.getName() : facilityId;
    }

    private String userName(String userId)
    {
        User u = ds.getUserById(userId);
        return u != null ? u.getName() : userId;
    }
}
