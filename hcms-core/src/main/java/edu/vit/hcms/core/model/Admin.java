package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Role;

import java.util.Set;

/**
 * Represents an administrator who triages and assigns complaints.
 * Extends {@link User} with an admin-specific role designation.
 */
public class Admin extends User {

    private String adminRole;

    /** Default constructor. */
    public Admin() {
        setRole(Role.ADMIN);
    }

    /**
     * Full constructor for creating an Admin with all fields.
     *
     * @param userId       unique user identifier
     * @param name         admin's full name
     * @param email        email address
     * @param passwordHash BCrypt-hashed password
     * @param adminRole    descriptive admin role (e.g., "Hostel Warden", "Chief Warden")
     */
    public Admin(Long userId, String name, String email, String passwordHash, String adminRole) {
        super(userId, name, email, passwordHash, Role.ADMIN);
        this.adminRole = adminRole;
    }

    @Override
    public String getDashboardView() {
        return "admin-dashboard";
    }

    @Override
    public Set<String> getPermissions() {
        return Set.of(
                "complaint:read:all",
                "complaint:assign",
                "complaint:priority:update",
                "staff:read",
                "staff:create",
                "analytics:read",
                "export:csv",
                "notification:read",
                "comment:create"
        );
    }

    public String getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }
}
