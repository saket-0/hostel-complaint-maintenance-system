package edu.vit.hcms.service.notification;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.interfaces.ComplaintStatusListener;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.ComplaintHistory;
import edu.vit.hcms.persistence.dao.SupportDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous audit log writer implementing the Observer pattern.
 * Listens for complaint status changes and writes them to the complaint_history table.
 *
 * <p>Uses a single-thread executor to avoid blocking the main request thread.
 */
public class AuditListener implements ComplaintStatusListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);
    private final SupportDao supportDao;
    private final ExecutorService executor;

    public AuditListener(SupportDao supportDao) {
        this.supportDao = supportDao;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audit-writer");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onStatusChange(Complaint complaint, ComplaintStatus oldStatus,
                                ComplaintStatus newStatus, Long changedBy, String note) {
        // Write audit asynchronously to avoid blocking the request
        executor.submit(() -> {
            try {
                ComplaintHistory history = new ComplaintHistory(
                        complaint.getComplaintId(), oldStatus, newStatus, changedBy, note);
                supportDao.saveHistory(history);
                log.debug("Audit log written: {} {} -> {}", complaint.getComplaintId(), oldStatus, newStatus);
            } catch (Exception e) {
                log.error("Failed to write audit log for {}: {}", complaint.getComplaintId(), e.getMessage());
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
