package utar.model;

/**
 * Admin.java — Represents the facility-management admin staff.
 *
 * OOP concepts:
 *   • Inheritance  — extends User; reuses all the basic user fields
 *     so we don't have to declare them again.
 *   • Polymorphism — overrides the abstract getRoleLabel() method.
 *
 * Admin has no extra attributes of its own — its faculty is hard-coded to
 * "Facility Management Office" so the constructor is simpler than the
 * other user types.
 */
public class Admin extends User {

    /**
     * Constructor — admin always belongs to "Facility Management Office",
     * so we don't ask the caller for that. The role is fixed to ADMIN.
     */
    public Admin(String userId, String name, String email, String password, String contactNumber)
    {
        // super(...) sends the values up to the User parent constructor
        super(userId, name, email, password, contactNumber,
              "Facility Management Office", // fixed faculty
              UserRole.ADMIN);                // fixed role
    }

    /**
     * Polymorphism — returns "Admin" so the UI can display it.
     */
    @Override
    public String getRoleLabel() {
        return "Admin";
    }
}
