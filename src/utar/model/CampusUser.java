package utar.model;

/**
 * CampusUser.java — Concrete subclass of User that represents either a
 * Student or a Staff member.
 * Because they share the same set of features they were merged into one
 * OOP concepts:
 *   • Inheritance — extends User and reuses all its fields.
 *   • Polymorphism — overrides getRoleLabel() with its own version.
 */
public class CampusUser extends User {

    // 'extra' stores the programme name (when role = STUDENT) or the
    // department name (when role = STAFF).  Using one field keeps the
    // users.txt file format simple — only one extra column per row.
    private String extra;

    /**
     * Constructor — passes the standard fields up to the User class
     * and saves the role-specific value into 'extra'.
     */
    public CampusUser(String userId, String name, String email, String password,
                      String contactNumber, String faculty,
                      UserRole role, String extra)
    {
        // super(...) calls the User constructor (ch07a inheritance)
        super(userId, name, email, password, contactNumber, faculty, role);
        // null-safe: if the caller passes null we store an empty string
        this.extra = (extra == null) ? "" : extra;
    }

    /**
     * Polymorphism — return "Staff" for staff users,
     * otherwise return "Student".
     */
    @Override
    public String getRoleLabel() {
        return getRole() == UserRole.STAFF ? "Staff" : "Student";
    }

    // ── Accessors for the merged 'extra' field ───────────────────────────────

    /** Generic getter — returns whatever is stored (programme OR department). */
    public String getExtra() {
        return extra;
    }

    /** Generic setter — used when the user updates their profile. */
    public void setExtra(String value) {
        this.extra = value;
    }

    /**
     * Convenience method — readable name for student users.
     * Internally returns the same 'extra' field.
     */
    public String getProgramme() {
        return extra;
    }

    /**
     * Convenience method — readable name for staff users.
     * Internally returns the same 'extra' field.
     */
    public String getDepartment() {
        return extra;
    }
}
