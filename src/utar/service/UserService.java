package utar.service;

import utar.model.*;
import utar.util.PasswordHasher;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UserService.java — Business logic for user accounts.
 * This service owns four use-cases:
 *   1. register       — create a new account (Student / Staff)
 *   2. login          — verify email + password
 *   3. updateProfile  — change contact / faculty / name
 *   4. changePassword — change password (requires the old one)
 * Passwords are never stored or compared as plain text: they are saved
 * as salted hashes (see PasswordHasher) and checked with
 * PasswordHasher.verify().
 *
 * It does NOT touch the file system directly — instead it goes through
 * DataStore (a Singleton, see DataStore.java) which handles all file I/O.
 * Separating the logic from the storage keeps the code easier to test
 * and to maintain.
 */
public class UserService {

    // We grab the single shared DataStore once, on creation.
    private DataStore ds = DataStore.getInstance();
    private ActivityLogService activityLog = new ActivityLogService();

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

        // STEP 2 & 3 — build the right type of user.  Only a salted hash of
        // the password is kept, never the password itself.
        String passwordHash = PasswordHasher.hash(password);
        User u;
        if (role == User.UserRole.STUDENT) {
            // 'extra' = programme name for students
            u = new CampusUser(ds.nextStudentId(), name, email, passwordHash,
                               contact, faculty, User.UserRole.STUDENT, extra);
        } else if (role == User.UserRole.STAFF) {
            // 'extra' = department name for staff
            u = new CampusUser(ds.nextStaffId(), name, email, passwordHash,
                               contact, faculty, User.UserRole.STAFF, extra);
        } else {
            // Self-registration is only allowed for Student / Staff —
            // Admin and Maintenance accounts are seeded by the system.
            throw new IllegalArgumentException("Only Student or Staff can self-register.");
        }

        // STEP 4 — persist (DataStore writes to users.txt)
        ds.addUser(u);
        activityLog.record(u.getUserId(), LogEntry.Action.USER_REGISTERED,
                u.getName() + " (" + u.getRoleLabel() + ", " + u.getEmail() + ")");
        return u;
    }

    /** Wrong passwords in a row before an account is locked. */
    public static final int MAX_FAILED_LOGINS = 5;

    /** How long a locked account stays locked. */
    public static final int LOCK_MINUTES = 15;

    /**
     * login — return the matching User if the email + password are correct,
     * otherwise return null.  The UI uses null to mean "wrong credentials".
     *
     * Lock-out rule (stops someone guessing passwords forever):
     *   • every wrong password adds 1 to the account's failed-login count
     *   • on the MAX_FAILED_LOGINS-th wrong password in a row the account is
     *     locked for LOCK_MINUTES, even for the right password
     *   • a correct login sets the count back to 0
     * The count and lock time are saved in users.txt, so restarting the
     * program doesn't clear a lock.
     *
     * @throws IllegalStateException if the account is locked; the message
     *         says until when
     */
    public User login(String email, String password) {
        User u = ds.getUserByEmail(email);
        if (u == null) {
            // no such account — nothing to count or lock, but worth logging
            activityLog.record("-", LogEntry.Action.LOGIN_FAILED, "Unknown email: " + email);
            return null;
        }

        if (u.isLocked()) {
            activityLog.record(u.getUserId(), LogEntry.Action.LOGIN_FAILED, "Account is locked");
            throw new IllegalStateException(lockedMessage(u));
        }

        // Password doesn't match the stored hash
        if (!PasswordHasher.verify(password, u.getPassword())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= MAX_FAILED_LOGINS) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES).withNano(0));   // whole seconds keep users.txt tidy
                u.setFailedLogins(0);   // start counting again after the lock ends
                ds.addUser(u);
                activityLog.record(u.getUserId(), LogEntry.Action.ACCOUNT_LOCKED,
                        MAX_FAILED_LOGINS + " wrong passwords in a row; locked for " + LOCK_MINUTES + " minutes");
                throw new IllegalStateException(lockedMessage(u));
            }
            ds.addUser(u);
            activityLog.record(u.getUserId(), LogEntry.Action.LOGIN_FAILED,
                    "Wrong password (" + u.getFailedLogins() + " of " + MAX_FAILED_LOGINS + ")");
            return null;
        }

        // Correct password: clear any old failures (only save if something changed)
        if (u.getFailedLogins() != 0 || u.getLockedUntil() != null) {
            u.setFailedLogins(0);
            u.setLockedUntil(null);
            ds.addUser(u);
        }
        activityLog.record(u.getUserId(), LogEntry.Action.LOGIN, u.getName() + " (" + u.getRoleLabel() + ")");
        return u;
    }

    /**
     * attemptsLeft — how many more wrong passwords this account can take
     * before it's locked.  Used by the login screens after a wrong password.
     */
    public int attemptsLeft(String email) {
        User u = ds.getUserByEmail(email);
        return u == null ? 0 : MAX_FAILED_LOGINS - u.getFailedLogins();
    }

    /**
     * unlock — admin ends a lock-out early (e.g. a student asked for help).
     * @return true if the user exists
     */
    public boolean unlock(String adminId, String userId) {
        User u = ds.getUserById(userId);
        if (u == null) {
            return false;
        }
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        ds.addUser(u);
        activityLog.record(adminId, LogEntry.Action.ACCOUNT_UNLOCKED, u.getName() + " (" + userId + ")");
        return true;
    }

    private static String lockedMessage(User u) {
        return "Too many wrong passwords. This account is locked until "
             + u.getLockedUntil().format(DateTimeFormatter.ofPattern("HH:mm"))
             + ". Try again later or ask the admin office to unlock it.";
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
        activityLog.record(userId, LogEntry.Action.PROFILE_UPDATED, u.getName());
        return true;
    }

    /**
     * checkPassword — true if the password is correct for this user.
     * Lets a menu check the current password before asking for a new one.
     */
    public boolean checkPassword(String userId, String password) {
        User u = ds.getUserById(userId);
        return u != null && PasswordHasher.verify(password, u.getPassword());
    }

    /**
     * changePassword — verify the current password first, then update.
     * If the old password is wrong, returns false and nothing changes.
     */
    public boolean changePassword(String userId, String oldPass, String newPass)
    {
        User u = ds.getUserById(userId);

        // Reject if user not found OR if the old password doesn't match
        if (u == null || !PasswordHasher.verify(oldPass, u.getPassword())) {
            return false;
        }

        u.setPassword(PasswordHasher.hash(newPass));   // store the new hash
        ds.addUser(u);   // persist the change
        activityLog.record(userId, LogEntry.Action.PASSWORD_CHANGED, u.getName());
        return true;
    }
}
