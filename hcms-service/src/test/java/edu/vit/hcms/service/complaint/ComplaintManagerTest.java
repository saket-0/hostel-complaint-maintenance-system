package edu.vit.hcms.service.complaint;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.exception.DuplicateEntryException;
import edu.vit.hcms.core.exception.InvalidStatusTransitionException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.interfaces.ComplaintStatusListener;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComplaintManager Business Logic & Collections Tests")
class ComplaintManagerTest {

    @Mock
    private ComplaintDao complaintDao;
    @Mock
    private SupportDao supportDao;
    @Mock
    private UserDao userDao;
    @Mock
    private TransactionManager txManager;
    @Mock
    private ComplaintStatusListener statusListener;

    private ComplaintManager complaintManager;

    @BeforeEach
    void setUp() {
        complaintManager = new ComplaintManager(complaintDao, supportDao, userDao, txManager);
        complaintManager.addStatusListener(statusListener);
    }

    @Test
    @DisplayName("Keyword detection automatically elevates priority to HIGH")
    void testKeywordAutoPriority() {
        when(complaintDao.isDuplicate(anyLong(), any(), anyString(), anyInt())).thenReturn(false);
        when(complaintDao.generateComplaintId()).thenReturn("HCM-2026-000001");

        Complaint c = complaintManager.registerComplaint(101L, Category.ELECTRICAL,
                "Urgent! There is a short circuit and sparking in switchboard");

        assertEquals(Priority.HIGH, c.getPriority(), "Keyword 'short circuit' must elevate to HIGH");
        assertEquals(ComplaintStatus.NEW, c.getStatus());
        verify(complaintDao).save(any(Complaint.class));
    }

    @Test
    @DisplayName("Duplicate complaint within window throws DuplicateEntryException")
    void testDuplicateCheck() {
        when(complaintDao.isDuplicate(eq(101L), eq(Category.PLUMBING), anyString(), anyInt())).thenReturn(true);

        assertThrows(DuplicateEntryException.class, () ->
                complaintManager.registerComplaint(101L, Category.PLUMBING, "Tap is leaking water continuously"));
    }

    @Test
    @DisplayName("Validation fails when description is too short")
    void testValidationShortDescription() {
        assertThrows(ValidationException.class, () ->
                complaintManager.registerComplaint(101L, Category.WIFI, "broken"));
    }

    @Test
    @DisplayName("Triage Queue correctly orders by Priority (HIGH first) then CreatedDate (FIFO)")
    void testTriageQueuePriorityQueueOrdering() {
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 19, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 19, 11, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 10, 19, 9, 0);

        Complaint lowOlder = Complaint.builder()
                .complaintId("C-LOW")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Low priority complaint")
                .priority(Priority.LOW)
                .createdDate(t1)
                .status(ComplaintStatus.NEW)
                .build();

        Complaint highNewer = Complaint.builder()
                .complaintId("C-HIGH-2")
                .studentId(101L)
                .category(Category.OTHER)
                .description("High priority complaint newer")
                .priority(Priority.HIGH)
                .createdDate(t2)
                .status(ComplaintStatus.NEW)
                .build();

        Complaint highOlder = Complaint.builder()
                .complaintId("C-HIGH-1")
                .studentId(101L)
                .category(Category.OTHER)
                .description("High priority complaint older")
                .priority(Priority.HIGH)
                .createdDate(t3)
                .status(ComplaintStatus.NEW)
                .build();

        Complaint med = Complaint.builder()
                .complaintId("C-MED")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Medium priority complaint")
                .priority(Priority.MEDIUM)
                .createdDate(t1)
                .status(ComplaintStatus.NEW)
                .build();

        when(complaintDao.findOpen()).thenReturn(List.of(lowOlder, highNewer, highOlder, med));

        PriorityQueue<Complaint> queue = complaintManager.getTriageQueue();

        // High priority must come out first, ordered by age
        assertEquals("C-HIGH-1", queue.poll().getComplaintId());
        assertEquals("C-HIGH-2", queue.poll().getComplaintId());
        assertEquals("C-MED", queue.poll().getComplaintId());
        assertEquals("C-LOW", queue.poll().getComplaintId());
    }

    @Test
    @DisplayName("Triage Queue orders: unassigned/reopened first -> overdue -> priority -> oldest")
    void testTriageQueueFullComparatorHierarchy() {
        LocalDateTime now = LocalDateTime.now();
        // 1. Assigned, not overdue, HIGH priority
        Complaint assignedHigh = Complaint.builder()
                .complaintId("C-ASSIGNED-HIGH")
                .studentId(101L)
                .category(Category.ELECTRICAL)
                .description("Test assigned high")
                .staffId(5L)
                .status(ComplaintStatus.IN_PROGRESS)
                .priority(Priority.HIGH)
                .dueBy(now.plusDays(2))
                .createdDate(now.minusDays(5))
                .build();

        // 2. Assigned, OVERDUE, LOW priority
        Complaint assignedOverdueLow = Complaint.builder()
                .complaintId("C-ASSIGNED-OVERDUE")
                .studentId(101L)
                .category(Category.ELECTRICAL)
                .description("Test assigned overdue low")
                .staffId(5L)
                .status(ComplaintStatus.IN_PROGRESS)
                .priority(Priority.LOW)
                .dueBy(now.minusHours(1)) // Overdue!
                .createdDate(now.minusDays(2))
                .build();

        // 3. Unassigned, LOW priority, not overdue
        Complaint unassignedLow = Complaint.builder()
                .complaintId("C-UNASSIGNED-LOW")
                .studentId(101L)
                .category(Category.ELECTRICAL)
                .description("Test unassigned low")
                .staffId(null)
                .status(ComplaintStatus.NEW)
                .priority(Priority.LOW)
                .dueBy(now.plusDays(5))
                .createdDate(now.minusHours(3))
                .build();

        // 4. Reopened, HIGH priority, not overdue
        Complaint reopenedHigh = Complaint.builder()
                .complaintId("C-REOPENED-HIGH")
                .studentId(101L)
                .category(Category.ELECTRICAL)
                .description("Test reopened high")
                .staffId(6L)
                .status(ComplaintStatus.REOPENED)
                .priority(Priority.HIGH)
                .dueBy(now.plusDays(1))
                .createdDate(now.minusHours(4))
                .build();

        when(complaintDao.findOpen()).thenReturn(List.of(assignedHigh, assignedOverdueLow, unassignedLow, reopenedHigh));

        PriorityQueue<Complaint> queue = complaintManager.getTriageQueue();

        // Level 1: Unassigned/Reopened come first!
        // Between reopenedHigh and unassignedLow: both are unassigned/reopened.
        // Neither is overdue.
        // reopenedHigh has priority HIGH (1) vs unassignedLow LOW (3), so reopenedHigh comes first!
        assertEquals("C-REOPENED-HIGH", queue.poll().getComplaintId());
        assertEquals("C-UNASSIGNED-LOW", queue.poll().getComplaintId());

        // Level 2: Remaining are assignedHigh and assignedOverdueLow.
        // assignedOverdueLow is overdue, while assignedHigh is not -> assignedOverdueLow comes next!
        assertEquals("C-ASSIGNED-OVERDUE", queue.poll().getComplaintId());

        // Remaining is assignedHigh
        assertEquals("C-ASSIGNED-HIGH", queue.poll().getComplaintId());
    }

    @Test
    @DisplayName("LinkedHashMap LRU cache stores and retrieves complaint")
    void testLruCache() {
        Complaint c = Complaint.builder()
                .complaintId("HCM-CACHE-1")
                .studentId(101L)
                .category(Category.AC)
                .description("AC cooling issue in room")
                .priority(Priority.MEDIUM)
                .status(ComplaintStatus.NEW)
                .build();

        when(complaintDao.findById("HCM-CACHE-1")).thenReturn(Optional.of(c));

        // First access loads from DAO
        Complaint fetched1 = complaintManager.getComplaint("HCM-CACHE-1");
        assertNotNull(fetched1);
        verify(complaintDao, times(1)).findById("HCM-CACHE-1");

        // Second access hits LRU cache
        Complaint fetched2 = complaintManager.getComplaint("HCM-CACHE-1");
        assertSame(fetched1, fetched2);
        verify(complaintDao, times(1)).findById("HCM-CACHE-1"); // No second DB call
    }

    @Test
    @DisplayName("Deque records recent activity up to bounded capacity")
    void testRecentActivityDeque() {
        for (int i = 1; i <= 25; i++) {
            complaintManager.recordActivity("Event #" + i);
        }

        List<String> activities = complaintManager.getRecentActivity();
        assertEquals(20, activities.size(), "Deque must be bounded to 20 elements");
        assertTrue(activities.get(0).contains("Event #25"), "Most recent event should be first");
    }

    @Test
    @DisplayName("Invalid status transition throws InvalidStatusTransitionException")
    void testInvalidTransition() {
        Complaint c = Complaint.builder()
                .complaintId("HCM-2026-000099")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Sample issue")
                .status(ComplaintStatus.CLOSED)
                .build();

        when(complaintDao.findById("HCM-2026-000099")).thenReturn(Optional.of(c));

        assertThrows(InvalidStatusTransitionException.class, () ->
                complaintManager.updateComplaintStatus("HCM-2026-000099", ComplaintStatus.IN_PROGRESS, 1L, "Invalid"));
    }

    @Test
    @DisplayName("Observer pattern notifies listeners on status transition")
    void testObserverNotification() {
        Complaint c = Complaint.builder()
                .complaintId("HCM-2026-000077")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Sample issue")
                .status(ComplaintStatus.ASSIGNED)
                .staffId(201L)
                .build();

        when(complaintDao.findById("HCM-2026-000077")).thenReturn(Optional.of(c));

        complaintManager.updateComplaintStatus("HCM-2026-000077", ComplaintStatus.IN_PROGRESS, 201L, "Started work");

        verify(statusListener).onStatusChange(eq(c), eq(ComplaintStatus.ASSIGNED),
                eq(ComplaintStatus.IN_PROGRESS), eq(201L), eq("Started work"));
    }
}
