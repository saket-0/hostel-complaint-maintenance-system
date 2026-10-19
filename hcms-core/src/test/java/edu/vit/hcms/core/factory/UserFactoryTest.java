package edu.vit.hcms.core.factory;

import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserFactory and Polymorphism Tests")
class UserFactoryTest {

    @Test
    @DisplayName("Factory creates correct concrete User subtypes based on role")
    void testCreateUserByRole() {
        User student = UserFactory.createUser(Role.STUDENT);
        assertTrue(student instanceof Student);
        assertEquals(Role.STUDENT, student.getRole());
        assertEquals("student-dashboard", student.getDashboardView());
        assertTrue(student.getPermissions().contains("complaint:create"));

        User admin = UserFactory.createUser(Role.ADMIN);
        assertTrue(admin instanceof Admin);
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals("admin-dashboard", admin.getDashboardView());
        assertTrue(admin.getPermissions().contains("complaint:assign"));

        User staff = UserFactory.createUser(Role.STAFF);
        assertTrue(staff instanceof MaintenanceStaff);
        assertEquals(Role.STAFF, staff.getRole());
        assertEquals("staff-dashboard", staff.getDashboardView());
        assertTrue(staff.getPermissions().contains("task:accept"));
    }

    @Test
    @DisplayName("Factory throws IllegalArgumentException on null role")
    void testNullRole() {
        assertThrows(IllegalArgumentException.class, () -> UserFactory.createUser(null));
    }

    @Test
    @DisplayName("Factory creates user with populated fields")
    void testCreateUserWithFields() {
        User user = UserFactory.createUser("Alice", "alice@vit.ac.in", "hashed_pwd", Role.STUDENT);
        assertEquals("Alice", user.getName());
        assertEquals("alice@vit.ac.in", user.getEmail());
        assertEquals("hashed_pwd", user.getPasswordHash());
        assertEquals(Role.STUDENT, user.getRole());
    }
}
