package edu.vit.hcms.core.factory;

import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;

/**
 * Factory pattern for creating User subclass instances based on role.
 * Centralizes user creation logic and ensures the correct subtype is instantiated.
 *
 * <p>Demonstrates the Factory design pattern and polymorphism:
 * callers work with the {@link User} abstraction while the factory
 * produces the concrete subclass matching the requested role.
 */
public final class UserFactory {

    /** Private constructor prevents instantiation of this utility class. */
    private UserFactory() {
        throw new UnsupportedOperationException("UserFactory is a static factory class");
    }

    /**
     * Creates a User subclass instance based on the given role.
     *
     * @param role the desired user role
     * @return a new User instance of the appropriate concrete type
     * @throws IllegalArgumentException if the role is null or unrecognized
     */
    public static User createUser(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        return switch (role) {
            case STUDENT -> new Student();
            case ADMIN -> new Admin();
            case STAFF -> new MaintenanceStaff();
        };
    }

    /**
     * Creates a User with basic fields populated.
     *
     * @param name         the user's full name
     * @param email        the user's email
     * @param passwordHash BCrypt hash of the password
     * @param role         the user's role
     * @return a new User instance with fields set
     */
    public static User createUser(String name, String email, String passwordHash, Role role) {
        User user = createUser(role);
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
