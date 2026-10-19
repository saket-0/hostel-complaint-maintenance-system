package edu.vit.hcms.core.policy;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.model.Complaint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ComplaintActionPolicy Unit Tests")
class ComplaintActionPolicyTest {

    private Complaint createComplaint(ComplaintStatus status, Long staffId, LocalDateTime resolvedDate, boolean feedbackGiven) {
        return Complaint.builder()
                .complaintId("HCM-2026-999999")
                .studentId(101L)
                .staffId(staffId)
                .category(Category.ELECTRICAL)
                .description("Test description for policy unit tests")
                .priority(Priority.HIGH)
                .status(status)
                .createdDate(LocalDateTime.now().minusDays(5))
                .updatedDate(LocalDateTime.now().minusDays(2))
                .resolvedDate(resolvedDate)
                .feedbackGiven(feedbackGiven)
                .build();
    }

    @Test
    @DisplayName("Admin allowed actions for NEW and unassigned complaint")
    void testAdminNewUnassigned() {
        Complaint complaint = createComplaint(ComplaintStatus.NEW, null, null, false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.ADMIN, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_ASSIGN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_AUTO_ASSIGN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_REASSIGN));
    }

    @Test
    @DisplayName("Admin allowed actions for ASSIGNED complaint")
    void testAdminAssigned() {
        Complaint complaint = createComplaint(ComplaintStatus.ASSIGNED, 201L, null, false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.ADMIN, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_REASSIGN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_ASSIGN));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_AUTO_ASSIGN));
    }

    @Test
    @DisplayName("Admin allowed actions for IN_PROGRESS complaint")
    void testAdminInProgress() {
        Complaint complaint = createComplaint(ComplaintStatus.IN_PROGRESS, 201L, null, false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.ADMIN, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_REASSIGN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_ASSIGN));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_AUTO_ASSIGN));
    }

    @Test
    @DisplayName("Admin allowed actions for REOPENED complaint (highlighted assign)")
    void testAdminReopened() {
        Complaint complaint = createComplaint(ComplaintStatus.REOPENED, 201L, null, false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.ADMIN, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_ASSIGN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_AUTO_ASSIGN));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_REASSIGN));
    }

    @Test
    @DisplayName("Admin allowed actions for RESOLVED and CLOSED complaint (no assignment)")
    void testAdminResolvedAndClosed() {
        Complaint resolved = createComplaint(ComplaintStatus.RESOLVED, 201L, LocalDateTime.now(), false);
        List<String> resolvedActions = ComplaintActionPolicy.allowedActions(Role.ADMIN, resolved);
        assertEquals(List.of(ComplaintActionPolicy.ACTION_VIEW), resolvedActions);

        Complaint closed = createComplaint(ComplaintStatus.CLOSED, 201L, LocalDateTime.now().minusDays(1), false);
        List<String> closedActions = ComplaintActionPolicy.allowedActions(Role.ADMIN, closed);
        assertEquals(List.of(ComplaintActionPolicy.ACTION_VIEW), closedActions);
    }

    @Test
    @DisplayName("Student allowed actions for RESOLVED within 7 days, no feedback")
    void testStudentResolvedRecentNoFeedback() {
        Complaint complaint = createComplaint(ComplaintStatus.RESOLVED, 201L, LocalDateTime.now().minusDays(2), false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.STUDENT, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_GIVE_FEEDBACK));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_REOPEN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_CLOSE));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
    }

    @Test
    @DisplayName("Student allowed actions for RESOLVED within 7 days, feedback already given")
    void testStudentResolvedRecentWithFeedback() {
        Complaint complaint = createComplaint(ComplaintStatus.RESOLVED, 201L, LocalDateTime.now().minusDays(2), true);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.STUDENT, complaint);

        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_GIVE_FEEDBACK));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_REOPEN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_CLOSE));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
    }

    @Test
    @DisplayName("Student allowed actions for RESOLVED after 7 days (cannot reopen)")
    void testStudentResolvedExpiredReopenWindow() {
        Complaint complaint = createComplaint(ComplaintStatus.RESOLVED, 201L, LocalDateTime.now().minusDays(10), false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.STUDENT, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_GIVE_FEEDBACK));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_REOPEN));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_CLOSE));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
    }

    @Test
    @DisplayName("Student allowed actions for NEW, ASSIGNED, IN_PROGRESS, CLOSED (view only)")
    void testStudentOtherStatuses() {
        for (ComplaintStatus status : List.of(ComplaintStatus.NEW, ComplaintStatus.ASSIGNED,
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.REOPENED, ComplaintStatus.CLOSED)) {
            Complaint complaint = createComplaint(status, null, null, false);
            List<String> actions = ComplaintActionPolicy.allowedActions(Role.STUDENT, complaint);
            assertEquals(List.of(ComplaintActionPolicy.ACTION_VIEW), actions,
                    "Student should only have VIEW action for status " + status);
        }
    }

    @Test
    @DisplayName("Staff allowed actions for ASSIGNED (accept)")
    void testStaffAssigned() {
        Complaint complaint = createComplaint(ComplaintStatus.ASSIGNED, 201L, null, false);
        List<String> actions = ComplaintActionPolicy.allowedActions(Role.STAFF, complaint);

        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_ACCEPT));
        assertTrue(actions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(actions.contains(ComplaintActionPolicy.ACTION_RESOLVE));
    }

    @Test
    @DisplayName("Staff allowed actions for IN_PROGRESS and REOPENED (mark as resolved)")
    void testStaffInProgressAndReopened() {
        Complaint inProgress = createComplaint(ComplaintStatus.IN_PROGRESS, 201L, null, false);
        List<String> inProgressActions = ComplaintActionPolicy.allowedActions(Role.STAFF, inProgress);
        assertTrue(inProgressActions.contains(ComplaintActionPolicy.ACTION_RESOLVE));
        assertTrue(inProgressActions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(inProgressActions.contains(ComplaintActionPolicy.ACTION_ACCEPT));

        Complaint reopened = createComplaint(ComplaintStatus.REOPENED, 201L, null, false);
        List<String> reopenedActions = ComplaintActionPolicy.allowedActions(Role.STAFF, reopened);
        assertTrue(reopenedActions.contains(ComplaintActionPolicy.ACTION_RESOLVE));
        assertTrue(reopenedActions.contains(ComplaintActionPolicy.ACTION_VIEW));
        assertFalse(reopenedActions.contains(ComplaintActionPolicy.ACTION_ACCEPT));
    }

    @Test
    @DisplayName("Staff allowed actions for RESOLVED and CLOSED (view only)")
    void testStaffCompleted() {
        Complaint resolved = createComplaint(ComplaintStatus.RESOLVED, 201L, LocalDateTime.now(), false);
        List<String> resolvedActions = ComplaintActionPolicy.allowedActions(Role.STAFF, resolved);
        assertEquals(List.of(ComplaintActionPolicy.ACTION_VIEW), resolvedActions);

        Complaint closed = createComplaint(ComplaintStatus.CLOSED, 201L, LocalDateTime.now(), false);
        List<String> closedActions = ComplaintActionPolicy.allowedActions(Role.STAFF, closed);
        assertEquals(List.of(ComplaintActionPolicy.ACTION_VIEW), closedActions);
    }

    @Test
    @DisplayName("Null handling produces empty list")
    void testNullHandling() {
        assertTrue(ComplaintActionPolicy.allowedActions(null, null).isEmpty());
        assertTrue(ComplaintActionPolicy.allowedActions(Role.ADMIN, null).isEmpty());
    }
}
