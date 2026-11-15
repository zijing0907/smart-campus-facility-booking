package utar.model;

/**
 * Facility.java — Represents a bookable place on campus, such as a lecture
 * hall, computer lab, or sports court.
 *
 * OOP concepts:
 *   • Encapsulation (ch03a) — all attributes are private, accessed only via
 *     getter / setter methods.
 *   • Enum types    — FacilityType and FacilityStatus give us a fixed list
 *     of valid choices, preventing typos in the data.
 */
public class Facility {

    // ── Fields (Encapsulation: all private) ──────────────────────────────────
    private String facilityId;        // unique ID, e.g. "F001"
    private String name;              // human-readable name
    private FacilityType type;              // kind of facility (lab, hall, etc.)
    private int capacity;          // max number of people
    private String location;          // building / floor location
    private FacilityStatus status;            // current availability
    private int maintenanceCount;  // how many issues have been reported

    /**
     * FacilityType — the five types of facility supported by the system.
     */
    public enum FacilityType {
        LECTURE_HALL,
        DISCUSSION_ROOM,
        SPORTS_COURT,
        COMPUTER_LAB,
        MULTIPURPOSE_HALL
    }

    /**
     * FacilityStatus — describes whether the facility can be booked right now.
     *   AVAILABLE         → ready to be booked
     *   UNDER_MAINTENANCE → temporarily blocked while being repaired
     *   CLOSED            → permanently unavailable
     */
    public enum FacilityStatus {
        AVAILABLE, UNDER_MAINTENANCE, CLOSED
    }

    /**
     * Constructor — builds a new facility. New facilities always start as
     * AVAILABLE with zero recorded maintenance issues.
     */
    public Facility(String facilityId, String name, FacilityType type,
                    int capacity, String location)
    {
        this.facilityId = facilityId;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
        this.location  = location;
        this.status = FacilityStatus.AVAILABLE;   // default
        this.maintenanceCount = 0;                // default
    }

    /**
     * Called every time a new maintenance issue is reported for this
     * facility. Used by analytics to find the most-broken facilities.
     */
    public void incrementMaintenanceCount() {
        maintenanceCount++;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getFacilityId()
    {
        return facilityId;
    }
    public String getName() {
        return name;
    }
    public FacilityType getType() {
        return type;
    }
    public int getCapacity() {
        return capacity;
    }
    public String getLocation()
    {
        return location;
    }
    public FacilityStatus getStatus() {
        return status;
    }
    public int getMaintenanceCount()  {
        return maintenanceCount;
    }

    // ── Setters (only fields the admin is allowed to change) ─────────────────
    public void setStatus(FacilityStatus s) {
        this.status = s;
    }
    public void setMaintenanceCount(int count) {
        this.maintenanceCount = count;
    }

    /**
     * Pretty-printed facility — used when listing facilities on screen.
     * Example: "[F001] Lecture Theatre KG01 (LECTURE_HALL) - AVAILABLE"
     */
    @Override
    public String toString() {
        return "[" + facilityId + "] " + name
             + " (" + type + ") - " + status;
    }
}
