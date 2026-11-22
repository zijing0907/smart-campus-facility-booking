package utar.service;

import utar.model.Facility;
import utar.model.LogEntry;

/**
 * FacilityService.java — Business logic for adding and editing facilities
 * (admin only).
 *
 * Rules:
 *   • name is required, at most MAX_NAME_LENGTH characters, and no two
 *     facilities can share a name (ignoring upper/lower case)
 *   • capacity is a whole number from 1 to MAX_CAPACITY
 *   • location is required
 *   • the facility ID is generated (F103, F104, ...) and never changes
 *
 * There is deliberately no "delete": bookings and maintenance reports refer
 * to a facility by its ID, so removing one would leave them pointing at
 * nothing.  To retire a facility, set it to CLOSED instead
 * (MaintenanceService.setFacilityStatus); closed facilities can't be
 * searched for or booked.
 *
 * Status changes stay in MaintenanceService, where they already were.
 * All file I/O goes through DataStore (Singleton).
 */
public class FacilityService {

    public static final int MAX_CAPACITY    = 1000;
    public static final int MAX_NAME_LENGTH = 60;

    private DataStore ds = DataStore.getInstance();
    private ActivityLogService activityLog = new ActivityLogService();

    /**
     * addFacility — create a new facility.  It starts as AVAILABLE with no
     * reported issues (see the Facility constructor).
     * @throws IllegalArgumentException with a message for the admin if a rule is broken
     */
    public Facility addFacility(String adminId, String name, Facility.FacilityType type,
                                int capacity, String location)
    {
        check(null, name, type, capacity, location);
        Facility f = new Facility(ds.nextFacilityId(), name.trim(), type,
                                  capacity, location.trim());
        ds.addFacility(f);   // saves facilities.txt
        activityLog.record(adminId, LogEntry.Action.FACILITY_ADDED,
                f.getFacilityId() + " " + f.getName() + " (" + type + ", " + capacity + " people, "
                + f.getLocation() + ")");
        return f;
    }

    /**
     * updateFacility — change the name, type, capacity or location.
     * Existing bookings and reports keep working because the ID stays the same.
     * @throws IllegalArgumentException if the facility doesn't exist or a rule is broken
     */
    public void updateFacility(String adminId, String facilityId, String name,
                               Facility.FacilityType type, int capacity, String location)
    {
        Facility f = ds.getFacilityById(facilityId);
        if (f == null) {
            throw new IllegalArgumentException("Facility not found.");
        }
        check(facilityId, name, type, capacity, location);

        f.setName(name.trim());
        f.setType(type);
        f.setCapacity(capacity);
        f.setLocation(location.trim());
        ds.addFacility(f);   // replaces the old record and saves
        activityLog.record(adminId, LogEntry.Action.FACILITY_EDITED,
                facilityId + " is now " + f.getName() + " (" + type + ", " + capacity + " people, "
                + f.getLocation() + ")");
    }

    /**
     * check — apply every rule.  ownId is the facility being edited (null
     * when adding), so a facility doesn't clash with its own name.
     */
    private void check(String ownId, String name, Facility.FacilityType type,
                       int capacity, String location)
    {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Choose a facility type.");
        }
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Capacity must be between 1 and " + MAX_CAPACITY + ".");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location cannot be empty.");
        }
        for (Facility other : ds.getAllFacilities()) {
            if (!other.getFacilityId().equals(ownId)
                    && other.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Another facility is already called \""
                        + other.getName() + "\" (" + other.getFacilityId() + ").");
            }
        }
    }
}
