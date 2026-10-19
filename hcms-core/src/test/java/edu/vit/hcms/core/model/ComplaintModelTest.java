package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Complaint Model & Builder Tests")
class ComplaintModelTest {

    @Test
    @DisplayName("Builder creates complaint with valid defaults and fields")
    void testComplaintBuilder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now.plusHours(24);

        Complaint complaint = Complaint.builder()
                .complaintId("HCM-2026-000001")
                .studentId(101L)
                .staffId(201L)
                .category(Category.ELECTRICAL)
                .description("Ceiling fan not working in room 201")
                .priority(Priority.HIGH)
                .status(ComplaintStatus.ASSIGNED)
                .createdDate(now)
                .updatedDate(now)
                .dueBy(due)
                .version(1)
                .build();

        assertEquals("HCM-2026-000001", complaint.getComplaintId());
        assertEquals(101L, complaint.getStudentId());
        assertEquals(201L, complaint.getStaffId());
        assertEquals(Category.ELECTRICAL, complaint.getCategory());
        assertEquals(Priority.HIGH, complaint.getPriority());
        assertEquals(ComplaintStatus.ASSIGNED, complaint.getStatus());
        assertEquals(1, complaint.getVersion());
        assertFalse(complaint.isOverdue());
    }

    @Test
    @DisplayName("Overdue check returns true when open and past due_by")
    void testOverdue() {
        LocalDateTime past = LocalDateTime.now().minusHours(2);

        Complaint overdueComplaint = Complaint.builder()
                .complaintId("HCM-2026-000002")
                .studentId(101L)
                .category(Category.PLUMBING)
                .description("Water leakage")
                .priority(Priority.MEDIUM)
                .status(ComplaintStatus.IN_PROGRESS)
                .dueBy(past)
                .build();

        assertTrue(overdueComplaint.isOverdue());

        // But if RESOLVED, it is not overdue
        overdueComplaint.setStatus(ComplaintStatus.RESOLVED);
        assertFalse(overdueComplaint.isOverdue());

        // If CLOSED, it is not overdue
        overdueComplaint.setStatus(ComplaintStatus.CLOSED);
        assertFalse(overdueComplaint.isOverdue());
    }
}
