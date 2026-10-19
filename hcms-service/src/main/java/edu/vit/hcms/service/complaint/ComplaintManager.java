package edu.vit.hcms.service.complaint;

import edu.vit.hcms.core.config.AppConfig;
import edu.vit.hcms.core.enums.*;
import edu.vit.hcms.core.exception.*;
import edu.vit.hcms.core.interfaces.AssignmentStrategy;
import edu.vit.hcms.core.interfaces.ComplaintStatusListener;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central business logic for complaint lifecycle management.
 * Implements the Observer pattern via registered {@link ComplaintStatusListener}s.
 * Supports pluggable assignment strategies via {@link AssignmentStrategy}.
 *
 * <p>Thread-safety: listener list uses CopyOnWriteArrayList for concurrent access.
 */
public class ComplaintManager {

    private static final Logger log = LoggerFactory.getLogger(ComplaintManager.class);

    private final ComplaintDao complaintDao;
    private final SupportDao supportDao;
    private final UserDao userDao;
    private final TransactionManager txManager;

    // Observer pattern: list of status-change listeners (thread-safe)
    private final List<ComplaintStatusListener> listeners = new CopyOnWriteArrayList<>();

    // Strategy pattern: pluggable assignment strategies
    private final Map<String, AssignmentStrategy> strategies = new LinkedHashMap<>();

    // Java Collections Showcase: LinkedHashMap LRU Cache (access-order, capacity 100)
    private final Map<String, Complaint> lruCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Complaint>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Complaint> eldest) {
                    return size() > 100;
                }
            }
    );

    // Java Collections Showcase: Deque for recent system activity (bounded to 20 entries)
    private final Deque<String> recentActivity = new java.util.concurrent.ConcurrentLinkedDeque<>();

    /** High-priority keywords that auto-elevate to HIGH priority. */
    private static final Set<String> HIGH_PRIORITY_KEYWORDS = Set.of(
            "short circuit", "sparking", "flooding", "fire", "smoke", "electrocution",
            "burst", "emergency", "dangerous", "hazard", "gas leak", "sewage"
    );

    public ComplaintManager(ComplaintDao complaintDao, SupportDao supportDao,
                            UserDao userDao, TransactionManager txManager) {
        this.complaintDao = complaintDao;
        this.supportDao = supportDao;
        this.userDao = userDao;
        this.txManager = txManager;
    }

    /**
     * Registers a status-change listener (Observer pattern).
     */
    public void addStatusListener(ComplaintStatusListener listener) {
        listeners.add(listener);
    }

    /**
     * Registers an assignment strategy.
     */
    public void registerStrategy(String name, AssignmentStrategy strategy) {
        strategies.put(name, strategy);
    }

    /**
     * Registers a new complaint from a student.
     * Generates a unique ID, checks for duplicates, determines priority, calculates SLA due date.
     *
     * @param studentId   the filing student's ID
     * @param category    complaint category
     * @param description complaint description text
     * @return the created Complaint with its unique ID
     * @throws DuplicateEntryException if a similar complaint was filed within the window
     * @throws ValidationException     if input is invalid
     */
    public Complaint registerComplaint(Long studentId, Category category, String description) {
        validateComplaint(category, description);

        int dupWindow = AppConfig.getInstance().getInt("DUPLICATE_WINDOW_MINUTES");
        if (complaintDao.isDuplicate(studentId, category, description, dupWindow)) {
            throw new DuplicateEntryException(
                    "A similar complaint was filed within the last " + dupWindow + " minutes");
        }

        // Auto-determine priority based on keywords
        Priority priority = determinePriority(description, category);

        // Calculate SLA due date
        int slaHours = getSlaHours(priority);
        LocalDateTime dueBy = LocalDateTime.now().plusHours(slaHours);

        // Generate unique complaint ID
        String complaintId = complaintDao.generateComplaintId();

        Complaint complaint = Complaint.builder()
                .complaintId(complaintId)
                .studentId(studentId)
                .category(category)
                .description(description)
                .priority(priority)
                .status(ComplaintStatus.NEW)
                .dueBy(dueBy)
                .build();

        txManager.begin();
        try {
            complaintDao.save(complaint);

            // Record initial history entry
            ComplaintHistory history = new ComplaintHistory(
                    complaintId, null, ComplaintStatus.NEW, studentId, "Complaint registered");
            supportDao.saveHistory(history);

            txManager.commit();
            log.info("Complaint registered: {} (category={}, priority={})", complaintId, category, priority);
            return complaint;
        } catch (Exception e) {
            txManager.rollback();
            throw e;
        }
    }

    /**
     * Assigns a complaint to a staff member (admin action).
     * Transitions status from NEW/REOPENED to ASSIGNED.
     *
     * @param complaintId the complaint to assign
     * @param staffId     the staff member to assign to
     * @param adminId     the admin performing the assignment
     * @return the updated complaint
     */
    public Complaint assignComplaint(String complaintId, Long staffId, Long adminId) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        ComplaintStatus oldStatus = complaint.getStatus();
        if (!oldStatus.canTransitionTo(ComplaintStatus.ASSIGNED)) {
            throw new InvalidStatusTransitionException(oldStatus, ComplaintStatus.ASSIGNED);
        }

        complaint.setStaffId(staffId);
        complaint.setStatus(ComplaintStatus.ASSIGNED);

        txManager.begin();
        try {
            complaintDao.update(complaint);
            notifyListeners(complaint, oldStatus, ComplaintStatus.ASSIGNED, adminId, "Assigned to staff " + staffId);
            txManager.commit();
            return complaint;
        } catch (Exception e) {
            txManager.rollback();
            throw e;
        }
    }

    /**
     * Auto-assigns a complaint using the Strategy pattern.
     * Tries SpecializationMatch first, then LeastWorkload.
     */
    public Complaint autoAssignComplaint(String complaintId, Long adminId) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        List<MaintenanceStaff> availableStaff = userDao.findAllStaff(true);
        if (availableStaff.isEmpty()) {
            throw new ValidationException("assignment", "No available staff members");
        }

        // Try strategies in order
        Optional<MaintenanceStaff> selectedStaff = Optional.empty();
        for (AssignmentStrategy strategy : strategies.values()) {
            selectedStaff = strategy.selectStaff(complaint, availableStaff);
            if (selectedStaff.isPresent()) {
                log.info("Auto-assigned via strategy: {}", strategy.getStrategyName());
                break;
            }
        }

        MaintenanceStaff staff = selectedStaff
                .orElseThrow(() -> new ValidationException("assignment", "No suitable staff found for this category"));

        return assignComplaint(complaintId, staff.getUserId(), adminId);
    }

    /**
     * Updates complaint status (staff action: ASSIGNED->IN_PROGRESS, IN_PROGRESS->RESOLVED).
     */
    public Complaint updateComplaintStatus(String complaintId, ComplaintStatus newStatus,
                                            Long changedBy, String note) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        ComplaintStatus oldStatus = complaint.getStatus();
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(oldStatus, newStatus);
        }

        complaint.setStatus(newStatus);
        if (newStatus == ComplaintStatus.RESOLVED) {
            complaint.setResolvedDate(LocalDateTime.now());
        }

        txManager.begin();
        try {
            complaintDao.update(complaint);
            notifyListeners(complaint, oldStatus, newStatus, changedBy, note);
            txManager.commit();
            return complaint;
        } catch (Exception e) {
            txManager.rollback();
            throw e;
        }
    }

    /**
     * Student closes a resolved complaint.
     */
    public Complaint closeComplaint(String complaintId, Long studentId) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        if (!complaint.getStudentId().equals(studentId)) {
            throw new AuthorizationException("You can only close your own complaints");
        }

        return updateComplaintStatus(complaintId, ComplaintStatus.CLOSED, studentId, "Closed by student");
    }

    /**
     * Student reopens a resolved complaint within the reopen window.
     */
    public Complaint reopenComplaint(String complaintId, Long studentId, String reason) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        if (!complaint.getStudentId().equals(studentId)) {
            throw new AuthorizationException("You can only reopen your own complaints");
        }

        if (complaint.getStatus() != ComplaintStatus.RESOLVED) {
            throw new InvalidStatusTransitionException(complaint.getStatus(), ComplaintStatus.REOPENED);
        }

        int reopenDays = AppConfig.getInstance().getInt("REOPEN_WINDOW_DAYS");
        if (complaint.getResolvedDate() != null &&
                ChronoUnit.DAYS.between(complaint.getResolvedDate(), LocalDateTime.now()) > reopenDays) {
            throw new ValidationException("reopen", "Reopen window of " + reopenDays + " days has expired");
        }

        complaint.setResolvedDate(null);
        return updateComplaintStatus(complaintId, ComplaintStatus.REOPENED, studentId,
                reason != null ? reason : "Reopened by student");
    }

    /**
     * Updates complaint priority (admin action).
     */
    public Complaint updatePriority(String complaintId, Priority newPriority, Long adminId) {
        Complaint complaint = complaintDao.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint", complaintId));

        Priority oldPriority = complaint.getPriority();
        complaint.setPriority(newPriority);

        // Recalculate SLA due date
        int slaHours = getSlaHours(newPriority);
        complaint.setDueBy(complaint.getCreatedDate().plusHours(slaHours));

        complaintDao.update(complaint);
        log.info("Priority updated for {}: {} -> {}", complaintId, oldPriority, newPriority);
        return complaint;
    }

    /**
     * Retrieves a complaint by ID, utilizing the LinkedHashMap LRU cache.
     */
    public Complaint getComplaint(String complaintId) {
        return lruCache.computeIfAbsent(complaintId, id ->
                complaintDao.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Complaint", id)));
    }

    /**
     * Retrieves a complaint directly from the LRU cache if present.
     */
    public Optional<Complaint> getCachedComplaint(String complaintId) {
        return Optional.ofNullable(lruCache.get(complaintId));
    }

    /**
     * Builds and returns the admin triage queue using a PriorityQueue.
     * Orders open complaints primarily by urgency (Priority level 1=HIGH first),
     * and secondarily by age (earlier createdDate first) using a Comparator chain.
     *
     * @return a PriorityQueue containing all open complaints
     */
    public PriorityQueue<Complaint> getTriageQueue() {
        Comparator<Complaint> triageComparator = Comparator
                .comparingInt((Complaint c) -> c.getPriority().getLevel())
                .thenComparing(Complaint::getCreatedDate);

        PriorityQueue<Complaint> queue = new PriorityQueue<>(triageComparator);
        List<Complaint> openComplaints = complaintDao.findOpen();
        queue.addAll(openComplaints);
        return queue;
    }

    /**
     * Records an activity event in the bounded Deque.
     */
    public void recordActivity(String message) {
        recentActivity.addFirst(LocalDateTime.now() + " - " + message);
        while (recentActivity.size() > 20) {
            recentActivity.pollLast();
        }
    }

    /**
     * Returns a snapshot of recent activity from the Deque.
     */
    public List<String> getRecentActivity() {
        return new ArrayList<>(recentActivity);
    }

    /**
     * Returns the student's complaints with pagination.
     */
    public List<Complaint> getStudentComplaints(Long studentId, int page, int size) {
        return complaintDao.findByStudentId(studentId, page * size, size);
    }

    /**
     * Returns staff tasks.
     */
    public List<Complaint> getStaffTasks(Long staffId) {
        return complaintDao.findByStaffId(staffId);
    }

    // ---- Private helpers ----

    /**
     * Determines priority based on description keywords and category defaults.
     */
    private Priority determinePriority(String description, Category category) {
        String lowerDesc = description.toLowerCase();
        for (String keyword : HIGH_PRIORITY_KEYWORDS) {
            if (lowerDesc.contains(keyword)) {
                return Priority.HIGH;
            }
        }
        // Default priority based on category
        return switch (category) {
            case ELECTRICAL, AC -> Priority.MEDIUM;
            case PLUMBING -> Priority.MEDIUM;
            case WIFI -> Priority.MEDIUM;
            case CARPENTRY, CLEANING, OTHER -> Priority.LOW;
        };
    }

    private int getSlaHours(Priority priority) {
        AppConfig config = AppConfig.getInstance();
        return switch (priority) {
            case HIGH -> config.getInt("SLA_HIGH_HOURS");
            case MEDIUM -> config.getInt("SLA_MEDIUM_HOURS");
            case LOW -> config.getInt("SLA_LOW_HOURS");
        };
    }

    private void validateComplaint(Category category, String description) {
        if (category == null) {
            throw new ValidationException("category", "Category is required");
        }
        if (description == null || description.isBlank()) {
            throw new ValidationException("description", "Description is required");
        }
        if (description.length() < 10) {
            throw new ValidationException("description", "Description must be at least 10 characters");
        }
        if (description.length() > 2000) {
            throw new ValidationException("description", "Description must be at most 2000 characters");
        }
    }

    /**
     * Notifies all registered listeners of a status change (Observer pattern).
     */
    private void notifyListeners(Complaint complaint, ComplaintStatus oldStatus,
                                  ComplaintStatus newStatus, Long changedBy, String note) {
        for (ComplaintStatusListener listener : listeners) {
            try {
                listener.onStatusChange(complaint, oldStatus, newStatus, changedBy, note);
            } catch (Exception e) {
                log.error("Listener failed for complaint {}: {}", complaint.getComplaintId(), e.getMessage());
            }
        }
    }
}
