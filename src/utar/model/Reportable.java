package utar.model;

/**
 * Reportable.java — Java interface
 *
 * Any class that says "implements Reportable" must provide these two
 * methods. In our system, MaintenanceReport implements this interface,
 * so we can treat any future "report" type the same way (good for
 * extensibility).
 */
public interface Reportable {

    /** Returns the unique ID of the report (e.g. "RPT0003"). */
    String getReportId();

    /** Returns a one-line summary of the report — used when displaying a list. */
    String getSummary();
}
