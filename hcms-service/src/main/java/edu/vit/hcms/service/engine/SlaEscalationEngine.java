package edu.vit.hcms.service.engine;

import edu.vit.hcms.core.config.AppConfig;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SLA Escalation Engine using a ScheduledExecutorService.
 * Periodically scans for overdue complaints and auto-escalates priority.
 * Also auto-closes resolved complaints past the auto-close window.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>ScheduledExecutorService for periodic tasks</li>
 *   <li>Stream API for filtering and grouping</li>
 *   <li>Batch operations</li>
 * </ul>
 */
public class SlaEscalationEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationEngine.class);

    private final ComplaintDao complaintDao;
    private final NotificationService notificationService;
    private final ScheduledExecutorService scheduler;

    public SlaEscalationEngine(ComplaintDao complaintDao, NotificationService notificationService) {
        this.complaintDao = complaintDao;
        this.notificationService = notificationService;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sla-engine");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the periodic escalation and auto-close checks.
     */
    public void start() {
        int intervalMinutes = AppConfig.getInstance().getInt("ESCALATION_CHECK_MINUTES");

        scheduler.scheduleAtFixedRate(this::checkAndEscalate,
                1, intervalMinutes, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(this::autoCloseResolved,
                2, intervalMinutes, TimeUnit.MINUTES);

        log.info("SLA Escalation Engine started (interval: {} min)", intervalMinutes);
    }

    /**
     * Scans for overdue complaints and escalates their priority.
     * LOW -> MEDIUM, MEDIUM -> HIGH (HIGH stays HIGH but gets flagged).
     */
    void checkAndEscalate() {
        try {
            List<Complaint> overdue = complaintDao.findOverdue();
            if (overdue.isEmpty()) return;

            log.info("Found {} overdue complaints", overdue.size());

            // Group by current priority for batch escalation using Streams
            var groupedByPriority = overdue.stream()
                    .collect(Collectors.groupingBy(Complaint::getPriority));

            // Escalate LOW -> MEDIUM
            List<String> lowIds = groupedByPriority.getOrDefault(Priority.LOW, List.of())
                    .stream()
                    .map(Complaint::getComplaintId)
                    .collect(Collectors.toList());
            if (!lowIds.isEmpty()) {
                complaintDao.batchUpdatePriority(lowIds, Priority.MEDIUM.getLevel());
                log.info("Escalated {} LOW complaints to MEDIUM", lowIds.size());
            }

            // Escalate MEDIUM -> HIGH
            List<String> mediumIds = groupedByPriority.getOrDefault(Priority.MEDIUM, List.of())
                    .stream()
                    .map(Complaint::getComplaintId)
                    .collect(Collectors.toList());
            if (!mediumIds.isEmpty()) {
                complaintDao.batchUpdatePriority(mediumIds, Priority.HIGH.getLevel());
                log.info("Escalated {} MEDIUM complaints to HIGH", mediumIds.size());
            }

            // Notify admins about overdue HIGH-priority complaints
            List<Complaint> highOverdue = groupedByPriority.getOrDefault(Priority.HIGH, List.of());
            if (!highOverdue.isEmpty()) {
                String message = highOverdue.size() + " HIGH-priority complaints are overdue and need attention";
                notificationService.notifyUser(1L, message); // Admin user ID 1
                log.warn("{} HIGH-priority complaints overdue", highOverdue.size());
            }
        } catch (Exception e) {
            log.error("SLA escalation check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Auto-closes resolved complaints past the auto-close window.
     */
    void autoCloseResolved() {
        try {
            int autoCloseDays = AppConfig.getInstance().getInt("AUTO_CLOSE_DAYS");
            List<Complaint> resolved = complaintDao.findResolvedOlderThan(autoCloseDays);

            for (Complaint c : resolved) {
                c.setStatus(ComplaintStatus.CLOSED);
                complaintDao.update(c);
                notificationService.notifyUser(c.getStudentId(),
                        "Your complaint " + c.getComplaintId() + " has been auto-closed after " +
                                autoCloseDays + " days.");
                log.info("Auto-closed complaint: {}", c.getComplaintId());
            }
        } catch (Exception e) {
            log.error("Auto-close check failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SLA Escalation Engine shut down");
    }
}
