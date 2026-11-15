package utar.service;

import utar.model.*;

/**
 * UserService.java — Business logic for user accounts.
 * This service owns four use-cases:
 *   1. register       — create a new account (Student / Staff)
 *   2. login          — verify email + password
 *   3. updateProfile  — change contact / faculty / name
 *   4. changePassword — change password (requires the old one)
 * It does NOT touch the file system directly — instead it goes through
 * DataStore (a Singleton, see DataStore.java) which handles all file I/O.
 * Separating the logic from the storage keeps the code easier to test
 * and to maintain.
 */
public class UserService {

    // We grab the single shared DataStore once, on creation.
    private DataStore ds = DataStore.getInstance();

    /**
     * register — create a new CampusUser account for a Student or Staff.
     * Steps:
     *   1. Make sure the email isn't already in use (unique-email rule)
     *   2. Generate the right kind of ID (STU... or STAFF...)
     *   3. Build the CampusUser object with the chosen role
     *   4. Save it through DataStore (which writes users.txt to disk)
     * @param extra programme name for students, department name for staff
     * @return      the newly-created User object
     * @throws IllegalArgumentException if the email is already taken
     */
    public User register(String name, String email, String password, String contact,
                         String faculty, User.UserRole role, String extra)
    {
        // STEP 1 — duplicate-email check
        if (ds.getUserByEmail(email) != null) {
            throw new IllegalArgumentException("Email already registered.");
        }

        // STEP 2 & 3 — build the right type of user
        User u;
        if (role == User.UserRole.STUDENT) {
            // 'extra' = programme name for students
            u = new CampusUser(ds.nextStudentId(), name, email, password,
                               contact, faculty, User.UserRole.STUDENT, extra);
        } else if (role == User.UserRole.STAFF) {
            // 'extra' = department name for staff
            u = new CampusUser(ds.nextStaffId(), name, email, password,
                               contact, faculty, User.UserRole.STAFF, extra);
        } else {
            // Self-registration is only allowed for Student / Staff —
            // Admin and Maintenance accounts are seeded by the system.
            throw new IllegalArgumentException("Only Student or Staff can self-register.");
        }

        // STEP 4 — persist (DataStore writes to users.txt)
        ds.addUser(u);
        return u;
    }

    /**
     * login — return the matching User if the email + password are correct,
     * otherwise return null.  The UI uses null to mean "wrong credentials".
     */
    public User login(String email, String password) {
        User u = ds.getUserByEmail(email);
        // Either the user doesn't exist, or the password is wrong.
        if (u == null || !u.getPassword().equals(password)) {
            return null;
        }
        return u;
    }

    /**
     * emailExists — quick yes/no check used by the registration UI to give
     * a friendlier message if the email is already taken.
     */
    public boolean emailExists(String email) {
        return ds.getUserByEmail(email) != null;
    }

    /**
     * updateProfile — let the user change their contact number, faculty,
     * or display name. If a field is left blank, it's ignored (we keep
     * the existing value).
     * @return true if the user was found and updated, false otherwise
     */
    public boolean updateProfile(String userId, String newContact,
                                  String newFaculty, String newName)
    {
        User u = ds.getUserById(userId);
        if (u == null) {
            return false;   // user not found — nothing to update
        }

        // Only update fields the user actually filled in
        if (newContact != null && !newContact.isBlank()) {
            u.setContactNumber(newContact);
        }
        if (newFaculty != null && !newFaculty.isBlank()) {
            u.setFaculty(newFaculty);
        }
        if (newName != null && !newName.isBlank()) {
            u.setName(newName);
        }

        ds.addUser(u);   // save the updated record back to disk
        return true;
    }

    /**
     * changePassword — verify the current password first, then update.
     * If the old password is wrong, returns false and nothing changes.
     */
    public boolean changePassword(String userId, String oldPass, String newPass)
    {
        User u = ds.getUserById(userId);

        // Reject if user not found OR if the old password doesn't match
        if (u == null || !u.getPassword().equals(oldPass)) {
            return false;
        }

        u.setPassword(newPass);
        ds.addUser(u);   // persist the change
        return true;
    }
}
