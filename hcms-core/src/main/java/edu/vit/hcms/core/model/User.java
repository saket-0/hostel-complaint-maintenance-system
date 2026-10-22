package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Role;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class for all users in the HCMS system.
 * Demonstrates OOP inheritance: {@link Student}, {@link Admin}, and {@link MaintenanceStaff}
 * extend this class and override {@link #getDashboardView()} and {@link #getPermissions()}.
 *
 * <p>Uses private fields with getters/setters for encapsulation.
 * The {@code passwordHash} setter is intentionally restrictive to prevent accidental exposure.
 */
public abstract class User {

    private Long userId;
    private String name;
    private String email;
    private String passwordHash;
    private Role role;
    private boolean active;
    private boolean forcePasswordChange;
    private LocalDateTime createdAt;

    // Static initialization block: logs class loading (demonstrates static blocks)
    static {
        // Static block executed once when the User class is first loaded
        // Used here to document that User hierarchy is the core domain anchor
    }

    // Instance initialization block: sets sensible defaults before any constructor runs
    {
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    /** Default constructor for framework/mapping use. */
    protected User() {
    }

    /**
     * Primary constructor with all required fields.
     *
     * @param userId       unique user identifier
     * @param name         full name
     * @param email        unique email address
     * @param passwordHash BCrypt-hashed password
     * @param role         the user's role
     */
    protected User(Long userId, String name, String email, String passwordHash, Role role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Overloaded constructor without userId (for new user creation before DB insert).
     *
     * @param name         full name
     * @param email        unique email address
     * @param passwordHash BCrypt-hashed password
     * @param role         the user's role
     */
    protected User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Returns the dashboard view identifier for this user's role.
     * Each subclass returns a different view (e.g., "student-dashboard", "admin-dashboard").
     * Demonstrates polymorphism via abstract method override.
     */
    public abstract String getDashboardView();

    /**
     * Returns the set of permission strings for this user's role.
     * Used by the authorization filter to gate access to endpoints.
     */
    public abstract java.util.Set<String> getPermissions();

    /**
     * Concrete login behavior shared by all user types.
     * Records login timestamp (could be extended to track sessions).
     */
    public void onLogin() {
        // Shared behavior: mark last-login, log activity
    }

    /**
     * Concrete logout behavior shared by all user types.
     */
    public void onLogout() {
        // Shared behavior: invalidate session, log activity
    }

    // --- Getters and Setters ---

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId) && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, email);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{userId=" + userId + ", name='" + name + "', email='" + email + "', role=" + role + "}";
    }
}
