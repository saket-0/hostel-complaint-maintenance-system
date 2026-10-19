package edu.vit.hcms.service.notification;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.interfaces.ComplaintStatusListener;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.Notification;
import edu.vit.hcms.persistence.dao.SupportDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * Asynchronous notification service using a producer/consumer pattern.
 * Implements {@link ComplaintStatusListener} (Observer pattern) to react to status changes.
 *
 * <p>Thread-safety: uses a BlockingQueue and an ExecutorService fixed thread pool.
 * Notifications are enqueued by the main thread and processed asynchronously by workers.
 *
 * <p>Multithreading features demonstrated:
 * <ul>
 *   <li>ExecutorService fixed pool</li>
 *   <li>BlockingQueue producer/consumer</li>
 *   <li>CompletableFuture for async operations</li>
 *   <li>Graceful shutdown via shutdown hooks</li>
 * </ul>
 */
public class NotificationService implements ComplaintStatusListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SupportDao supportDao;
    private final BlockingQueue<Notification> notificationQueue;
    private final ExecutorService executor;
    private volatile boolean running = true;

    /**
     * Creates the notification service with a fixed-size thread pool.
     * Starts a consumer thread that drains the queue and persists notifications.
     *
     * @param supportDao the DAO for persisting notifications
     * @param poolSize   number of worker threads
     */
    public NotificationService(SupportDao supportDao, int poolSize) {
        this.supportDao = supportDao;
        this.notificationQueue = new LinkedBlockingQueue<>(1000);
        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "notification-worker");
            t.setDaemon(true);
            return t;
        });

        // Start consumer workers
        for (int i = 0; i < poolSize; i++) {
            executor.submit(this::processQueue);
        }
        log.info("NotificationService started with {} workers", poolSize);
    }

    /**
     * Observer callback: creates notifications when complaint status changes.
     */
    @Override
    public void onStatusChange(Complaint complaint, ComplaintStatus oldStatus,
                                ComplaintStatus newStatus, Long changedBy, String note) {
        String message = formatMessage(complaint, oldStatus, newStatus, note);

        // Notify the student
        enqueue(new Notification(complaint.getStudentId(), message));

        // Notify assigned staff if applicable
        if (complaint.getStaffId() != null && !complaint.getStaffId().equals(changedBy)) {
            enqueue(new Notification(complaint.getStaffId(),
                    "Complaint " + complaint.getComplaintId() + " status: " + newStatus.getDisplayName()));
        }
    }

    /**
     * Sends a notification asynchronously by adding it to the queue.
     * Uses CompletableFuture for non-blocking operation.
     *
     * @param notification the notification to send
     * @return a CompletableFuture that completes when the notification is enqueued
     */
    public CompletableFuture<Void> sendAsync(Notification notification) {
        return CompletableFuture.runAsync(() -> enqueue(notification), executor);
    }

    /**
     * Sends a notification to a user (convenience method).
     */
    public void notifyUser(Long userId, String message) {
        enqueue(new Notification(userId, message));
    }

    /**
     * Returns notifications for a user.
     */
    public List<Notification> getNotifications(Long userId, int limit) {
        return supportDao.findNotificationsByUserId(userId, limit);
    }

    /**
     * Returns unread count for notification badge.
     */
    public int getUnreadCount(Long userId) {
        return supportDao.countUnread(userId);
    }

    /**
     * Marks a notification as read.
     */
    public void markAsRead(Long notificationId, Long userId) {
        supportDao.markAsRead(notificationId, userId);
    }

    /**
     * Enqueues a notification for async processing.
     * If the queue is full, logs a warning (non-blocking).
     */
    private void enqueue(Notification notification) {
        boolean added = notificationQueue.offer(notification);
        if (!added) {
            log.warn("Notification queue is full, dropping notification for user {}", notification.getUserId());
        }
    }

    /**
     * Consumer loop: takes notifications from the queue and persists them.
     * Runs until the service is shut down.
     */
    private void processQueue() {
        while (running || !notificationQueue.isEmpty()) {
            try {
                Notification notification = notificationQueue.poll(1, TimeUnit.SECONDS);
                if (notification != null) {
                    try {
                        supportDao.saveNotification(notification);
                        log.debug("Notification saved for user {}", notification.getUserId());
                    } catch (Exception e) {
                        log.error("Failed to save notification: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Notification worker thread exiting");
    }

    /**
     * Returns the current queue depth (for metrics).
     */
    public int getQueueDepth() {
        return notificationQueue.size();
    }

    private String formatMessage(Complaint complaint, ComplaintStatus oldStatus,
                                  ComplaintStatus newStatus, String note) {
        return switch (newStatus) {
            case ASSIGNED -> "Your complaint " + complaint.getComplaintId() + " has been assigned to a staff member.";
            case IN_PROGRESS -> "Work has started on your complaint " + complaint.getComplaintId() + ".";
            case RESOLVED -> "Your complaint " + complaint.getComplaintId() +
                    " has been resolved. Please review and provide feedback.";
            case CLOSED -> "Complaint " + complaint.getComplaintId() + " has been closed.";
            case REOPENED -> "Complaint " + complaint.getComplaintId() + " has been reopened.";
            default -> "Complaint " + complaint.getComplaintId() + " status updated to " + newStatus.getDisplayName();
        };
    }

    /**
     * Graceful shutdown: stops accepting new notifications, drains the queue, shuts down workers.
     */
    @Override
    public void close() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("NotificationService shut down");
    }
}
