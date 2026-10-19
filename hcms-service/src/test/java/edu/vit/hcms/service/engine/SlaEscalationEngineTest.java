package edu.vit.hcms.service.engine;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.service.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaEscalationEngine Tests")
class SlaEscalationEngineTest {

    @Mock
    private ComplaintDao complaintDao;
    @Mock
    private NotificationService notificationService;

    @Test
    @DisplayName("SLA Engine escalates LOW to MEDIUM and MEDIUM to HIGH for overdue complaints")
    void testCheckAndEscalate() {
        SlaEscalationEngine engine = new SlaEscalationEngine(complaintDao, notificationService);

        LocalDateTime overdueTime = LocalDateTime.now().minusDays(5);

        Complaint lowOverdue = Complaint.builder()
                .complaintId("C-LOW")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Overdue low issue")
                .priority(Priority.LOW)
                .dueBy(overdueTime)
                .status(ComplaintStatus.IN_PROGRESS)
                .build();

        Complaint medOverdue = Complaint.builder()
                .complaintId("C-MED")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Overdue med issue")
                .priority(Priority.MEDIUM)
                .dueBy(overdueTime)
                .status(ComplaintStatus.IN_PROGRESS)
                .build();

        Complaint highOverdue = Complaint.builder()
                .complaintId("C-HIGH")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Overdue high issue")
                .priority(Priority.HIGH)
                .dueBy(overdueTime)
                .status(ComplaintStatus.IN_PROGRESS)
                .build();

        when(complaintDao.findOverdue()).thenReturn(List.of(lowOverdue, medOverdue, highOverdue));

        engine.checkAndEscalate();

        // Verify LOW -> MEDIUM batch update
        verify(complaintDao).batchUpdatePriority(List.of("C-LOW"), Priority.MEDIUM.getLevel());

        // Verify MEDIUM -> HIGH batch update
        verify(complaintDao).batchUpdatePriority(List.of("C-MED"), Priority.HIGH.getLevel());

        // Verify HIGH overdue triggers notification to admin
        verify(notificationService).notifyUser(eq(1L), contains("HIGH-priority complaints are overdue"));

        engine.close();
    }

    @Test
    @DisplayName("SLA Engine auto-closes resolved complaints past the window")
    void testAutoCloseResolved() {
        SlaEscalationEngine engine = new SlaEscalationEngine(complaintDao, notificationService);

        Complaint resolvedComplaint = Complaint.builder()
                .complaintId("C-RESOLVED")
                .studentId(101L)
                .category(Category.OTHER)
                .description("Resolved complaint pending close")
                .status(ComplaintStatus.RESOLVED)
                .resolvedDate(LocalDateTime.now().minusDays(10))
                .build();

        when(complaintDao.findResolvedOlderThan(anyInt())).thenReturn(List.of(resolvedComplaint));

        engine.autoCloseResolved();

        verify(complaintDao).update(argThat(c -> c.getStatus() == ComplaintStatus.CLOSED));
        verify(notificationService).notifyUser(eq(101L), contains("auto-closed"));

        engine.close();
    }
}
