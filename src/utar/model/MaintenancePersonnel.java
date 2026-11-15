package utar.model;

/**
 * MaintenancePersonnel.java — Represents a worker who fixes facility issues
 * reported by other users (e.g. damaged equipment, air-conditioning problems).
 *
 * OOP concepts:
 *   • Inheritance — extends User to reuse its fields.
 *   • Polymorphism — overrides getRoleLabel().
 *   • Encapsulation — the new "specialization" field is private and
 *     only accessible through getter/setter.
 */
public class MaintenancePersonnel extends User {

    // Extra attribute on top of the User fields — what type of repairs this
    // person specialises in (e.g. "Electrical", "HVAC", "Plumbing").
    private String specialization;

    /**
     * Constructor — calls super() to fill the inherited User fields,
     * then stores the specialization in this class's own field.
     */
    public MaintenancePersonnel(String userId, String name, String email, String password,
                                 String contactNumber, String faculty,
                                 String specialization)
    {
        // super(...) — Inheritance
        super(userId, name, email, password, contactNumber, faculty,
              UserRole.MAINTENANCE);
        this.specialization = specialization;
    }

    /**
     * Polymorphism — return the role name to display.
     */
    @Override
    public String getRoleLabel() {
        return "Maintenance Personnel";
    }

    // ── Specialization getter / setter ───────────────────────────────────────
    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String s) {
        this.specialization = s;
    }
}
