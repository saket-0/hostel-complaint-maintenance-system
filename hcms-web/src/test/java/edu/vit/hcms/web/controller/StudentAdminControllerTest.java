package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.exception.DuplicateEntryException;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.web.dto.CreateStudentRequest;
import edu.vit.hcms.web.dto.UpdateStudentRequest;
import edu.vit.hcms.web.dto.UpdateStudentStatusRequest;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StudentAdminController Unit Tests")
class StudentAdminControllerTest {

    private UserDao userDao;
    private ComplaintDao complaintDao;
    private StudentAdminController controller;
    private Context ctx;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        complaintDao = mock(ComplaintDao.class);
        controller = new StudentAdminController(userDao, complaintDao);
        ctx = mock(Context.class, RETURNS_DEEP_STUBS);
        when(ctx.status(anyInt())).thenReturn(ctx);
    }

    @Test
    @DisplayName("listStudents returns students with active complaints count and total")
    void testListStudents() {
        Student s = new Student(10L, "Priya Sharma", "priya.sharma@vitstudent.ac.in", "hash", "101", "A", "21BCE0001", "9876543210");
        when(userDao.findAllStudents(isNull(), isNull(), eq(0), eq(20))).thenReturn(List.of(s));
        when(userDao.countStudents(isNull(), isNull())).thenReturn(1L);
        when(complaintDao.studentActiveComplaintCounts()).thenReturn(Map.of(10L, 2L));

        controller.listStudents(ctx);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());

        Map<String, Object> resp = captor.getValue();
        assertEquals(1L, resp.get("total"));
        List<Map<String, Object>> students = (List<Map<String, Object>>) resp.get("students");
        assertEquals(1, students.size());
        assertEquals("21BCE0001", students.get(0).get("registrationNumber"));
        assertEquals(2L, students.get(0).get("activeComplaints"));
        assertEquals("ACTIVE", students.get(0).get("status"));
    }

    @Test
    @DisplayName("createStudent hashes temporary password, forces password change, and records audit history")
    void testCreateStudentSuccess() {
        CreateStudentRequest req = new CreateStudentRequest(
                "Rohan Gupta", "rohan.g@vitstudent.ac.in", "22BCE1005", "B", "204", "9988776655", "tempPass123"
        );
        when(ctx.bodyAsClass(CreateStudentRequest.class)).thenReturn(req);
        when(ctx.attribute("userId")).thenReturn(1L);
        when(userDao.findByEmail("rohan.g@vitstudent.ac.in")).thenReturn(Optional.empty());
        when(userDao.findByRegistrationNumber("22BCE1005")).thenReturn(Optional.empty());

        controller.createStudent(ctx);

        verify(ctx).status(201);
        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(userDao).save(studentCaptor.capture());

        Student saved = studentCaptor.getValue();
        assertEquals("Rohan Gupta", saved.getName());
        assertEquals("rohan.g@vitstudent.ac.in", saved.getEmail());
        assertEquals("22BCE1005", saved.getRegistrationNumber());
        assertEquals("B", saved.getHostelBlock());
        assertEquals("204", saved.getRoomNumber());
        assertTrue(saved.isForcePasswordChange());
        assertTrue(saved.isActive());
        assertNotNull(saved.getPasswordHash());
        assertNotEquals("tempPass123", saved.getPasswordHash());

        verify(userDao).saveUserHistory(isNull(), eq("STUDENT_CREATED"), eq(1L), contains("Admin added student"));
    }

    @Test
    @DisplayName("createStudent rejects duplicate email with 409 Conflict")
    void testCreateStudentDuplicateEmail() {
        CreateStudentRequest req = new CreateStudentRequest(
                "Priya Sharma", "priya.sharma@vitstudent.ac.in", "21BCE9999", "A", "101", null, "password123"
        );
        when(ctx.bodyAsClass(CreateStudentRequest.class)).thenReturn(req);
        Student existing = new Student();
        existing.setUserId(2L);
        when(userDao.findByEmail("priya.sharma@vitstudent.ac.in")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateEntryException.class, () -> controller.createStudent(ctx));
    }

    @Test
    @DisplayName("createStudent rejects duplicate registration number with 409 Conflict")
    void testCreateStudentDuplicateRegistrationNumber() {
        CreateStudentRequest req = new CreateStudentRequest(
                "Another Student", "another@vitstudent.ac.in", "21BCE0001", "A", "102", null, "password123"
        );
        when(ctx.bodyAsClass(CreateStudentRequest.class)).thenReturn(req);
        when(userDao.findByEmail("another@vitstudent.ac.in")).thenReturn(Optional.empty());
        Student existing = new Student();
        existing.setUserId(2L);
        when(userDao.findByRegistrationNumber("21BCE0001")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateEntryException.class, () -> controller.createStudent(ctx));
    }

    @Test
    @DisplayName("updateStudent records ROOM_CHANGED audit note when room or block changes")
    void testUpdateStudentRoomChange() {
        Student student = new Student(10L, "Rahul Verma", "rahul.verma@vitstudent.ac.in", "hash", "205", "A", "21BCE0002", null);
        when(ctx.pathParam("id")).thenReturn("10");
        when(ctx.attribute("userId")).thenReturn(1L);
        when(userDao.findById(10L)).thenReturn(Optional.of(student));

        UpdateStudentRequest req = new UpdateStudentRequest(null, null, null, "B", "310", null);
        when(ctx.bodyAsClass(UpdateStudentRequest.class)).thenReturn(req);

        controller.updateStudent(ctx);

        verify(userDao).update(argThat(s ->
                s.getUserId().equals(10L) &&
                ((Student) s).getHostelBlock().equals("B") &&
                ((Student) s).getRoomNumber().equals("310")
        ));
        verify(userDao).saveUserHistory(eq(10L), eq("ROOM_CHANGED"), eq(1L), contains("Admin reassigned student room from Block A Room 205 to Block B Room 310"));
    }

    @Test
    @DisplayName("updateStatus soft-deactivates student and writes audit history")
    void testDeactivateStudent() {
        Student student = new Student(10L, "Anjali Patel", "anjali@vitstudent.ac.in", "hash", "302", "B", "21BCE0003", null);
        student.setActive(true);

        when(ctx.pathParam("id")).thenReturn("10");
        when(ctx.attribute("userId")).thenReturn(1L);
        when(userDao.findById(10L)).thenReturn(Optional.of(student));

        UpdateStudentStatusRequest req = new UpdateStudentStatusRequest(false, "DEACTIVATED");
        when(ctx.bodyAsClass(UpdateStudentStatusRequest.class)).thenReturn(req);
        when(complaintDao.countActiveComplaintsByStudentId(10L)).thenReturn(3L);

        controller.updateStatus(ctx);

        verify(userDao).update(argThat(u -> !u.isActive()));
        verify(userDao).saveUserHistory(eq(10L), eq("STUDENT_DEACTIVATED"), eq(1L), contains("deactivated (soft-deleted)"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(ctx).json(captor.capture());
        Map<String, Object> resp = captor.getValue();
        assertEquals(false, resp.get("active"));
        assertEquals("DEACTIVATED", resp.get("status"));
    }
}
