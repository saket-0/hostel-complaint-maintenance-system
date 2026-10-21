package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.service.user.UserManager;
import edu.vit.hcms.web.dto.AdminUpdateUserRequest;
import edu.vit.hcms.web.dto.UpdateProfileRequest;
import edu.vit.hcms.web.dto.UpdateStaffRequest;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UserController & AdminController User Management Unit Tests")
class UserControllerTest {

    private UserManager userManager;
    private UserController userController;
    private AdminController adminController;
    private Context ctx;

    @BeforeEach
    void setUp() {
        userManager = mock(UserManager.class);
        userController = new UserController(userManager);
        adminController = new AdminController(mock(edu.vit.hcms.service.complaint.ComplaintManager.class),
                mock(edu.vit.hcms.persistence.dao.ComplaintDao.class),
                mock(edu.vit.hcms.persistence.dao.UserDao.class),
                mock(edu.vit.hcms.service.analytics.AnalyticsService.class),
                mock(edu.vit.hcms.persistence.dao.SupportDao.class),
                userManager);
        ctx = mock(Context.class);
    }

    @Test
    @DisplayName("getProfile returns student profile with hostelBlock and roomNumber")
    void testGetProfile() {
        Student student = new Student();
        student.setUserId(10L);
        student.setName("Priya Sharma");
        student.setEmail("priya@vit.ac.in");
        student.setRole(Role.STUDENT);
        student.setHostelBlock("D");
        student.setRoomNumber("402");
        student.setActive(true);

        when(ctx.attribute("userId")).thenReturn(10L);
        when(userManager.getUserById(10L)).thenReturn(student);

        userController.getProfile(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals("Priya Sharma", res.get("name"));
        assertEquals("D", res.get("hostelBlock"));
        assertEquals("402", res.get("roomNumber"));
    }

    @Test
    @DisplayName("updateProfile delegates to UserManager and returns serialized user")
    void testUpdateProfile() {
        Student student = new Student();
        student.setUserId(10L);
        student.setName("Priya S");
        student.setRole(Role.STUDENT);
        student.setHostelBlock("E");
        student.setRoomNumber("101");
        student.setActive(true);

        when(ctx.attribute("userId")).thenReturn(10L);
        UpdateProfileRequest req = new UpdateProfileRequest("Priya S", "101", "E", null, null);
        when(ctx.bodyAsClass(UpdateProfileRequest.class)).thenReturn(req);
        when(userManager.updateProfile(10L, "Priya S", "101", "E", null, null)).thenReturn(student);

        userController.updateProfile(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals("Profile updated successfully", res.get("message"));
        assertNotNull(res.get("user"));
    }

    @Test
    @DisplayName("admin listUsers delegates to UserManager with pagination")
    void testAdminListUsers() {
        Student student = new Student();
        student.setUserId(10L);
        student.setName("Priya Sharma");
        student.setEmail("priya@vit.ac.in");
        student.setRole(Role.STUDENT);
        student.setActive(true);

        when(ctx.queryParam("role")).thenReturn("STUDENT");
        when(ctx.queryParam("q")).thenReturn("priya");
        when(ctx.queryParam("page")).thenReturn("0");
        when(ctx.queryParam("size")).thenReturn("25");

        when(userManager.listUsers("STUDENT", "priya", 0, 25)).thenReturn(List.of(student));
        when(userManager.countUsers("STUDENT", "priya")).thenReturn(1L);

        adminController.listUsers(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals(1L, res.get("total"));
        assertEquals(0, res.get("page"));
    }

    @Test
    @DisplayName("admin updateUser updates student room and active status")
    void testAdminUpdateUser() {
        Student student = new Student();
        student.setUserId(10L);
        student.setName("Priya Sharma");
        student.setRole(Role.STUDENT);
        student.setActive(true);

        when(ctx.pathParam("id")).thenReturn("10");
        AdminUpdateUserRequest req = new AdminUpdateUserRequest("Priya Sharma", true, null, null, "205", "C", null);
        when(ctx.bodyAsClass(AdminUpdateUserRequest.class)).thenReturn(req);
        when(userManager.adminUpdateUser(10L, "Priya Sharma", true, "205", "C", null, null, null)).thenReturn(student);

        adminController.updateUser(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals("User updated successfully", res.get("message"));
    }

    @Test
    @DisplayName("admin updateStaff updates specialization and availability")
    void testAdminUpdateStaff() {
        MaintenanceStaff staff = new MaintenanceStaff();
        staff.setUserId(20L);
        staff.setName("Ramesh Plumber");
        staff.setRole(Role.STAFF);
        staff.setSpecialization(Specialization.PLUMBING);
        staff.setAvailable(true);
        staff.setActive(true);

        when(ctx.pathParam("id")).thenReturn("20");
        UpdateStaffRequest req = new UpdateStaffRequest("PLUMBING", true);
        when(ctx.bodyAsClass(UpdateStaffRequest.class)).thenReturn(req);
        when(userManager.updateStaff(20L, Specialization.PLUMBING, true)).thenReturn(staff);

        adminController.updateStaff(ctx);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals("Staff updated successfully", res.get("message"));
    }

    @Test
    @DisplayName("admin deleteUser deactivates user")
    void testAdminDeleteUser() {
        when(ctx.pathParam("id")).thenReturn("15");

        adminController.deleteUser(ctx);

        verify(userManager).deactivateUser(15L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> res = captor.getValue();
        assertEquals("User deactivated successfully", res.get("message"));
    }
}
