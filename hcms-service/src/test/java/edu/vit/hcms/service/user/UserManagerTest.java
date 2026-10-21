package edu.vit.hcms.service.user;

import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.exception.ResourceNotFoundException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManager Unit Tests")
class UserManagerTest {

    @Mock
    private UserDao userDao;
    @Mock
    private TransactionManager txManager;

    private UserManager userManager;

    @BeforeEach
    void setUp() {
        userManager = new UserManager(userDao, txManager);
    }

    @Test
    @DisplayName("getUserById returns user when found")
    void testGetUserByIdSuccess() {
        Student student = new Student(1L, "Aarav Patel", "aarav@vitstudent.ac.in", "hash", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        User result = userManager.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("Aarav Patel", result.getName());
    }

    @Test
    @DisplayName("getUserById throws ValidationException when userId is null")
    void testGetUserByIdNull() {
        assertThrows(ValidationException.class, () -> userManager.getUserById(null));
    }

    @Test
    @DisplayName("getUserById throws ResourceNotFoundException when user does not exist")
    void testGetUserByIdNotFound() {
        when(userDao.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userManager.getUserById(999L));
    }

    @Test
    @DisplayName("updateProfile updates student name, room number, and block")
    void testUpdateProfileStudent() {
        Student student = new Student(1L, "Aarav Patel", "aarav@vitstudent.ac.in", "hash", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        User updated = userManager.updateProfile(1L, "Aarav P.", "205", "b", null, null);

        assertTrue(updated instanceof Student);
        Student s = (Student) updated;
        assertEquals("Aarav P.", s.getName());
        assertEquals("205", s.getRoomNumber());
        assertEquals("B", s.getHostelBlock());

        verify(txManager).begin();
        verify(userDao).update(student);
        verify(txManager).commit();
    }

    @Test
    @DisplayName("updateProfile throws ValidationException on blank name")
    void testUpdateProfileBlankName() {
        Student student = new Student(1L, "Aarav Patel", "aarav@vitstudent.ac.in", "hash", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        assertThrows(ValidationException.class, () ->
                userManager.updateProfile(1L, "   ", "205", "B", null, null));
    }

    @Test
    @DisplayName("updateProfile throws ValidationException on room number exceeding 10 characters")
    void testUpdateProfileLongRoomNumber() {
        Student student = new Student(1L, "Aarav Patel", "aarav@vitstudent.ac.in", "hash", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        assertThrows(ValidationException.class, () ->
                userManager.updateProfile(1L, "Aarav", "12345678901", "B", null, null));
    }

    @Test
    @DisplayName("updateProfile updates staff availability and specialization")
    void testUpdateProfileStaff() {
        MaintenanceStaff staff = new MaintenanceStaff(2L, "Suresh Kumar", "suresh@vit.ac.in", "hash",
                Specialization.ELECTRICAL, true);
        when(userDao.findById(2L)).thenReturn(Optional.of(staff));

        User updated = userManager.updateProfile(2L, "Suresh K.", null, null, false, Specialization.CARPENTRY);

        assertTrue(updated instanceof MaintenanceStaff);
        MaintenanceStaff ms = (MaintenanceStaff) updated;
        assertEquals("Suresh K.", ms.getName());
        assertFalse(ms.isAvailable());
        assertEquals(Specialization.CARPENTRY, ms.getSpecialization());

        verify(userDao).update(staff);
    }

    @Test
    @DisplayName("listUsers sanitizes pagination and forwards to userDao")
    void testListUsers() {
        List<User> mockList = List.of(new Student(1L, "Test", "t@vit.ac.in", "h", "1", "A"));
        when(userDao.findUsers("STUDENT", "test", 0, 20)).thenReturn(mockList);

        List<User> result = userManager.listUsers("STUDENT", "test", -5, 0);

        assertEquals(1, result.size());
        verify(userDao).findUsers("STUDENT", "test", 0, 20);
    }

    @Test
    @DisplayName("countUsers forwards query to userDao")
    void testCountUsers() {
        when(userDao.countUsers("STAFF", null)).thenReturn(5L);

        long count = userManager.countUsers("STAFF", null);

        assertEquals(5L, count);
        verify(userDao).countUsers("STAFF", null);
    }

    @Test
    @DisplayName("adminUpdateUser allows full modification of user properties")
    void testAdminUpdateUser() {
        Student student = new Student(1L, "Student Name", "student@vit.ac.in", "h", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        User updated = userManager.adminUpdateUser(1L, "Updated Name", false, "502", "C", null, null, null);

        assertEquals("Updated Name", updated.getName());
        assertFalse(updated.isActive());
        assertEquals("502", ((Student) updated).getRoomNumber());
        assertEquals("C", ((Student) updated).getHostelBlock());

        verify(userDao).update(student);
    }

    @Test
    @DisplayName("adminUpdateUser updates adminRole for Admin users")
    void testAdminUpdateAdminUser() {
        Admin admin = new Admin(3L, "Admin Name", "admin@vit.ac.in", "h", "Warden");
        when(userDao.findById(3L)).thenReturn(Optional.of(admin));

        User updated = userManager.adminUpdateUser(3L, "New Admin Name", true, null, null, null, null, "Chief Warden");

        assertEquals("New Admin Name", updated.getName());
        assertEquals("Chief Warden", ((Admin) updated).getAdminRole());
        verify(userDao).update(admin);
    }

    @Test
    @DisplayName("updateStaff modifies staff availability and specialization")
    void testUpdateStaff() {
        MaintenanceStaff staff = new MaintenanceStaff(2L, "Staff Name", "staff@vit.ac.in", "h",
                Specialization.PLUMBING, false);
        when(userDao.findById(2L)).thenReturn(Optional.of(staff));

        MaintenanceStaff updated = userManager.updateStaff(2L, Specialization.HVAC, true);

        assertEquals(Specialization.HVAC, updated.getSpecialization());
        assertTrue(updated.isAvailable());
        verify(userDao).update(staff);
    }

    @Test
    @DisplayName("updateStaff rejects non-staff user")
    void testUpdateStaffNotStaff() {
        Student student = new Student(1L, "Student", "s@vit.ac.in", "h", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        assertThrows(ValidationException.class, () ->
                userManager.updateStaff(1L, Specialization.ELECTRICAL, true));
    }

    @Test
    @DisplayName("deactivateUser verifies user existence and calls userDao.deactivate")
    void testDeactivateUser() {
        Student student = new Student(1L, "Student", "s@vit.ac.in", "h", "101", "A");
        when(userDao.findById(1L)).thenReturn(Optional.of(student));

        userManager.deactivateUser(1L);

        verify(userDao).deactivate(1L);
    }

    @Test
    @DisplayName("deactivateUser throws ResourceNotFoundException if user does not exist")
    void testDeactivateUserNotFound() {
        when(userDao.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userManager.deactivateUser(99L));
        verify(userDao, never()).deactivate(anyLong());
    }
}
