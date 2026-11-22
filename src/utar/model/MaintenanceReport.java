package utar.model;

/**
 * MaintenanceReport.java — Represents one issue reported by a user about
 * a facility (e.g. "broken chair in F001").
 *
 * OOP concepts:
 *   • Encapsulation — all fields are private.
 *   • Interface  — implements Reportable, so this class must
 *     supply getReportId() and getSummary().
 *   • Enum types — IssueCategory and ReportStatus restrict the values
 *     to a known list.
 */
public class MaintenanceReport implements Reportable {

    // ── Fields (Encapsulation: all private) ──────────────────────────────────
    private String reportId;            // unique ID, e.g. "RPT0001"
    private String facilityId;          // which facility has the issue
    private String reportedByUserId;    // user ID who reported it
    private String issueDescription;    // free-text description
    private IssueCategory category;            // type of problem
    private ReportStatus status;              // current state of the report
    private String assignedTo;          // userId of the maintenance personnel
    private String reportedDate;        // dd/MM/yyyy
    private String resolvedDate;        // dd/MM/yyyy (null if not done yet)
    private String resolutionNotes;     // notes added when resolving
    private String attachment;          // file name in data/attachments, e.g. "MR0007.jpg" (null = none)

    /**
     * IssueCategory — fixed list of issue types so the data is uniform.
     */
    public enum IssueCategory {
        EQUIPMENT_DAMAGE,
        AIR_CONDITIONING,
        FURNITURE,
        ELECTRICAL,
        OTHER
    }

    /**
     * ReportStatus — life cycle of a maintenance report.
     *   REPORTED    → just submitted, no one assigned yet
     *   ASSIGNED    → admin has assigned it to a maintenance worker
     *   IN_PROGRESS → the worker has started working on it
     *   RESOLVED    → fixed
     */
    public enum ReportStatus {
        REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED
    }

    /**
     * Constructor — creates a new report. The status always starts at
     * REPORTED; assignedTo / resolvedDate / resolutionNotes stay null
     * until the admin / worker fills them in later.
     */
    public MaintenanceReport(String reportId, String facilityId,
                              String reportedByUserId, String issueDescription,
                              IssueCategory category, String reportedDate)
    {
        this.reportId = reportId;
        this.facilityId = facilityId;
        this.reportedByUserId = reportedByUserId;
        this.issueDescription = issueDescription;
        this.category = category;
        this.status = ReportStatus.REPORTED;   // default
        this.reportedDate = reportedDate;
    }

    // ── Methods required by the Reportable interface  ──────────────────

    /** Returns the unique report ID — required by Reportable. */
    @Override
    public String getReportId() {
        return reportId;
    }

    /** One-line summary used when listing reports — required by Reportable. */
    @Override
    public String getSummary() {
        return reportId + " | " + facilityId + " | " + category + " [" + status + "]";
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getFacilityId() {
        return facilityId;
    }
    public String getReportedByUserId() {
        return reportedByUserId;
    }
    public String getIssueDescription()  {
        return issueDescription;
    }
    public IssueCategory getCategory() {
        return category;
    }
    public ReportStatus  getStatus() {
        return status;
    }
    public String getAssignedTo() {
        return assignedTo;
    }
    public String getReportedDate() {
        return reportedDate;
    }
    public String getResolvedDate() {
        return resolvedDate;
    }
    public String getResolutionNotes() {
        return resolutionNotes;
    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setStatus(ReportStatus s)  {
        this.status = s;
    }

    /**
     * setAssignedTo — also bumps the status forward to ASSIGNED automatically,
     * so callers don't have to remember to do both steps.
     */
    public void setAssignedTo(String id) {
        this.assignedTo = id;
        this.status = ReportStatus.ASSIGNED;
    }

    public void setResolvedDate(String d) {
        this.resolvedDate = d;
    }
    public void setResolutionNotes(String n) {
        this.resolutionNotes = n;
    }

    // ── Attachment (optional photo or PDF sent with the report) ──────────────
    public String getAttachment() {
        return attachment;
    }
    public void setAttachment(String fileName) {
        this.attachment = fileName;
    }
    public boolean hasAttachment() {
        return attachment != null;
    }

    /** Plain text form of the report — uses getSummary(). */
    @Override
    public String toString() {
        return getSummary();
    }
}
