package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.enums.Specialization;

import java.util.Set;

/**
 * Represents maintenance staff who resolve complaints.
 * Extends {@link User} with specialization and availability tracking.
 */
public class MaintenanceStaff extends User {

    private Specialization specialization;
    private boolean available;

    /** Default constructor. */
    public MaintenanceStaff() {
        setRole(Role.STAFF);
        this.available = true;
    }

    /**
     * Full constructor for creating MaintenanceStaff with all fields.
     *
     * @param userId         unique user identifier
     * @param name           staff member's full name
     * @param email          email address
     * @param passwordHash   BCrypt-hashed password
     * @param specialization area of expertise
     * @param available      whether currently available for assignment
     */
    public MaintenanceStaff(Long userId, String name, String email, String passwordHash,
                            Specialization specialization, boolean available) {
        super(userId, name, email, passwordHash, Role.STAFF);
        this.specialization = specialization;
        this.available = available;
    }

    @Override
    public String getDashboardView() {
        return "staff-dashboard";
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "task:read:own",
                "task:accept",
                "task:update:status",
                "availability:update",
                "notification:read",
                "comment:create"
        );
    }

    public Specialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(Specialization specialization) {
        this.specialization = specialization;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
