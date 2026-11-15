package utar.model;

/**
 * User.java — Abstract superclass for everyone who can log into the system.
 *
 * OOP concepts:
 *   • Encapsulation — every field is private and only reachable
 *     through getter / setter methods.
 *   • Abstraction — this class is declared "abstract" and has
 *     an abstract method getRoleLabel(). You cannot create a plain User —
 *     you must use one of the concrete subclasses below.
 *   • Inheritance — three concrete classes extend this one:
 *        - CampusUser            (Student or Staff)
 *        - Admin
 *        - MaintenancePersonnel
 *   • Polymorphism — each subclass overrides getRoleLabel()
 *     to print its own role name.
 */
public abstract class User {

    // ── Fields (Encapsulation: all private) ──────────────────────────────────
    private String userId;          // e.g. "STU001", "STAFF002", "ADMIN1"
    private String name;            // full name of the user
    private String email;           // login email — must be unique
    private String password;        // plain text password (kept simple for assignment)
    private String contactNumber;   // phone number, e.g. "012-3456789"
    private String faculty;         // faculty / department text
    private UserRole role;            // tells us what kind of user this is

    /**
     * UserRole — fixed list of role types the system supports.
     * Using an enum (instead of a String) prevents typing mistakes
     * like "Studant" and makes the code easier to read.
     */
    public enum UserRole {
        STUDENT,        // a UTAR student
        STAFF,          // a UTAR staff member
        ADMIN,          // facility-management admin staff
        MAINTENANCE     // maintenance personnel
    }

    /**
     * Constructor — called by every subclass via super(...).
     * Just copies the arguments into the private fields.
     */
    public User(String userId, String name, String email, String password,
                String contactNumber, String faculty, UserRole role)
    {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.contactNumber = contactNumber;
        this.faculty = faculty;
        this.role = role;
    }

    /**
     * Abstract method — every subclass MUST provide its own version.
     * Returns a friendly label such as "Student", "Admin", "Maintenance Personnel".
     */
    public abstract String getRoleLabel();

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getUserId() {
        return userId;
    }
    public String getName() {
        return name;
    }
    public String getEmail() {
        return email;
    }
    public String getPassword() {
        return password;
    }
    public String getContactNumber() {
        return contactNumber;
    }
    public String getFaculty() {
        return faculty;
    }
    public UserRole getRole() {
        return role;
    }

    // ── Setters (only the fields users are allowed to change) ────────────────
    public void setName(String n) {
        this.name = n;
    }
    public void setPassword(String p) {
        this.password = p;
    }
    public void setContactNumber(String c) {
        this.contactNumber = c;
    }
    public void setFaculty(String f) {
        this.faculty = f;
    }

    /**
     * Pretty-printed version of the user — handy when we need to show
     * who is logged in.  Calls getRoleLabel() which is polymorphic.
     */
    @Override
    public String toString() {
        return getRoleLabel() + " [" + userId + "] " + name;
    }
}
