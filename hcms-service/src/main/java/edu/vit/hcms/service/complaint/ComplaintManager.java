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
            "short circuit", "sparks", "sparking", "flood", "flooding", "fire", "smoke",
            "electrocution", "burst", "emergency", "dangerous", "hazard", "gas leak", "sewage"
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
            lruCache.put(complaintId, complaint);
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

        String staffDesc = "staff " + staffId;
        Optional<User> staffUser = userDao.findById(staffId);
        if (staffUser.isPresent()) {
            User u = staffUser.get();
            if (u instanceof MaintenanceStaff ms) {
                complaint.setAssignedStaffName(ms.getName());
                complaint.setAssignedStaffSpecialization(ms.getSpecialization().getDisplayName());
                staffDesc = ms.getName() + " (" + ms.getSpecialization().getRoleTitle() + ")";
            } else {
                complaint.setAssignedStaffName(u.getName());
                staffDesc = u.getName();
            }
        }

        txManager.begin();
        try {
            complaintDao.update(complaint);
            notifyListeners(complaint, oldStatus, ComplaintStatus.ASSIGNED, adminId, "Assigned to " + staffDesc);
            txManager.commit();
            lruCache.put(complaintId, complaint);
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

        if (complaint.getStatus() != ComplaintStatus.NEW || complaint.getStaffId() != null) {
            throw new InvalidStatusTransitionException(complaint.getStatus(), ComplaintStatus.ASSIGNED);
        }

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
     * Previews auto-assignment for a list of complaints without persisting any changes.
     * Evaluates strategies, calculates projected workloads, detects warnings and stale/assigned tickets.
     */
    public Map<String, Object> previewAutoAssignments(List<String> complaintIds) {
        final int WORKLOAD_CAP = 10;
        List<MaintenanceStaff> allStaff = userDao.findAllStaff(null);
        List<MaintenanceStaff> availableStaff = userDao.findAllStaff(true);
        Map<Long, Long> currentWorkload = complaintDao.staffWorkload();

        Map<Long, Long> projectedWorkload = new HashMap<>(currentWorkload);
        List<Map<String, Object>> tickets = new ArrayList<>();
        Map<String, Integer> priorityCounts = new LinkedHashMap<>();
        int overdueCount = 0;
        int alreadyAssignedCount = 0;
        List<Map<String, Object>> warningsList = new ArrayList<>();
        Set<Long> involvedStaffIds = new HashSet<>();

        for (String id : complaintIds) {
            Optional<Complaint> opt = complaintDao.findById(id);
            if (opt.isEmpty()) {
                continue;
            }
            Complaint c = opt.get();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("ticketId", c.getComplaintId());
            t.put("category", c.getCategory() != null ? c.getCategory().name() : "OTHER");
            t.put("categoryDisplay", c.getCategory() != null ? c.getCategory().getDisplayName() : "Other");
            t.put("priority", c.getPriority() != null ? c.getPriority().name() : "LOW");
            t.put("priorityLevel", c.getPriority() != null ? c.getPriority().getLevel() : 1);
            t.put("description", c.getDescription() != null ? c.getDescription() : "");

            String location = "";
            if (c.getStudentHostelBlock() != null && !c.getStudentHostelBlock().isBlank()) {
                location += c.getStudentHostelBlock();
            }
            if (c.getStudentRoomNumber() != null && !c.getStudentRoomNumber().isBlank()) {
                location += (location.isEmpty() ? "" : " - Room ") + c.getStudentRoomNumber();
            }
            if (location.isEmpty()) location = "—";
            t.put("location", location);

            t.put("status", c.getStatus() != null ? c.getStatus().name() : "NEW");
            boolean isAlreadyAssigned = (c.getStatus() != ComplaintStatus.NEW && c.getStatus() != ComplaintStatus.REOPENED)
                    || c.getStaffId() != null;
            t.put("isAlreadyAssigned", isAlreadyAssigned);
            if (isAlreadyAssigned) {
                alreadyAssignedCount++;
                if (c.getStaffId() != null) {
                    t.put("currentStaffId", c.getStaffId());
                    userDao.findById(c.getStaffId()).ifPresent(u -> t.put("currentStaffName", u.getName()));
                }
            }

            boolean isOverdue = c.getDueBy() != null && LocalDateTime.now().isAfter(c.getDueBy());
            t.put("isOverdue", isOverdue);
            if (isOverdue) overdueCount++;

            String prioName = c.getPriority() != null ? c.getPriority().name() : "LOW";
            priorityCounts.put(prioName, priorityCounts.getOrDefault(prioName, 0) + 1);

            Category cat = c.getCategory();
            Specialization reqSpec = cat != null ? cat.getRequiredSpecialization() : null;
            List<Map<String, Object>> eligibleList = new ArrayList<>();
            for (MaintenanceStaff s : allStaff) {
                if (reqSpec == null || s.getSpecialization() == reqSpec) {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("staffId", s.getUserId());
                    sm.put("name", s.getName());
                    sm.put("specialization", s.getSpecialization().name());
                    sm.put("specializationDisplay", s.getSpecialization().getDisplayName());
                    sm.put("activeComplaints", currentWorkload.getOrDefault(s.getUserId(), 0L));
                    sm.put("available", s.isAvailable());
                    eligibleList.add(sm);
                }
            }
            if (eligibleList.isEmpty()) {
                for (MaintenanceStaff s : allStaff) {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("staffId", s.getUserId());
                    sm.put("name", s.getName());
                    sm.put("specialization", s.getSpecialization().name());
                    sm.put("specializationDisplay", s.getSpecialization().getDisplayName());
                    sm.put("activeComplaints", currentWorkload.getOrDefault(s.getUserId(), 0L));
                    sm.put("available", s.isAvailable());
                    eligibleList.add(sm);
                }
            }
            t.put("eligibleStaff", eligibleList);

            Optional<MaintenanceStaff> selectedStaff = Optional.empty();
            String matchedStrategyName = null;
            for (AssignmentStrategy strategy : strategies.values()) {
                selectedStaff = strategy.selectStaff(c, availableStaff);
                if (selectedStaff.isPresent()) {
                    matchedStrategyName = strategy.getStrategyName();
                    break;
                }
            }

            List<String> rowWarnings = new ArrayList<>();
            if (isAlreadyAssigned) {
                rowWarnings.add("Ticket is already assigned" + (t.containsKey("currentStaffName") ? " to " + t.get("currentStaffName") : ""));
            }

            if (selectedStaff.isPresent()) {
                MaintenanceStaff staff = selectedStaff.get();
                involvedStaffIds.add(staff.getUserId());
                long curOpen = currentWorkload.getOrDefault(staff.getUserId(), 0L);
                long projOpen = projectedWorkload.getOrDefault(staff.getUserId(), curOpen) + 1;
                projectedWorkload.put(staff.getUserId(), projOpen);

                String reason;
                if ("SpecializationMatch".equals(matchedStrategyName)) {
                    reason = staff.getSpecialization().getDisplayName() + " specialist";
                    if (curOpen == 0) {
                        reason += ", lowest workload";
                    }
                } else if ("LeastWorkload".equals(matchedStrategyName)) {
                    reason = "Lowest workload (" + curOpen + " open)";
                } else {
                    reason = matchedStrategyName != null ? matchedStrategyName : "Auto-match";
                }

                if (projOpen > WORKLOAD_CAP) {
                    rowWarnings.add("Staff " + staff.getName() + " would exceed workload cap of " + WORKLOAD_CAP + " (" + projOpen + " projected)");
                }

                Map<String, Object> proposed = new LinkedHashMap<>();
                proposed.put("staffId", staff.getUserId());
                proposed.put("name", staff.getName());
                proposed.put("specialization", staff.getSpecialization().name());
                proposed.put("specializationDisplay", staff.getSpecialization().getDisplayName());
                proposed.put("currentOpenTickets", curOpen);
                proposed.put("projectedOpenTickets", projOpen);
                proposed.put("reason", reason);
                t.put("proposedStaff", proposed);
            } else {
                rowWarnings.add("No eligible staff available");
                t.put("proposedStaff", null);
            }

            t.put("warnings", rowWarnings);
            for (String rw : rowWarnings) {
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("ticketId", c.getComplaintId());
                wm.put("message", rw);
                warningsList.add(wm);
            }

            tickets.add(t);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalTickets", tickets.size());
        summary.put("staffCount", involvedStaffIds.size());
        summary.put("priorityCounts", priorityCounts);
        summary.put("overdueCount", overdueCount);
        summary.put("alreadyAssignedCount", alreadyAssignedCount);
        summary.put("allAlreadyAssigned", !tickets.isEmpty() && alreadyAssignedCount == tickets.size());
        summary.put("warningCount", warningsList.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tickets", tickets);
        response.put("summary", summary);
        response.put("warnings", warningsList);
        return response;
    }

    /**
     * Validates and confirms assignments in a single atomic transaction.
     * Re-validates ticket assignability and staff availability to prevent stale race conditions.
     */
    public Map<String, Object> confirmAssignments(List<Map<String, Object>> assignments, Long adminId) {
        if (assignments == null || assignments.isEmpty()) {
            throw new ValidationException("assignments", "No assignments provided");
        }

        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Complaint> complaintsToUpdate = new ArrayList<>();
        List<MaintenanceStaff> staffList = new ArrayList<>();

        for (Map<String, Object> item : assignments) {
            String ticketId = (String) (item.get("ticketId") != null ? item.get("ticketId") : item.get("complaintId"));
            Object staffIdObj = item.get("staffId");
            Long staffId = staffIdObj instanceof Number ? ((Number) staffIdObj).longValue() :
                    (staffIdObj != null ? Long.parseLong(staffIdObj.toString()) : null);

            if (ticketId == null || staffId == null) {
                conflicts.add(Map.of("ticketId", ticketId != null ? ticketId : "UNKNOWN",
                        "reason", "Missing ticket ID or staff ID"));
                continue;
            }

            Optional<Complaint> cOpt = complaintDao.findById(ticketId);
            if (cOpt.isEmpty()) {
                conflicts.add(Map.of("ticketId", ticketId, "reason", "Complaint not found"));
                continue;
            }
            Complaint c = cOpt.get();

            Optional<User> uOpt = userDao.findById(staffId);
            if (uOpt.isEmpty() || !(uOpt.get() instanceof MaintenanceStaff)) {
                conflicts.add(Map.of("ticketId", ticketId, "reason", "Selected staff member not found"));
                continue;
            }
            MaintenanceStaff ms = (MaintenanceStaff) uOpt.get();

            complaintsToUpdate.add(c);
            staffList.add(ms);
        }

        if (!conflicts.isEmpty()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", false);
            res.put("conflicts", conflicts);
            res.put("message", "Validation failed for " + conflicts.size() + " ticket(s)");
            return res;
        }

        txManager.begin();
        try {
            int count = 0;
            for (int i = 0; i < complaintsToUpdate.size(); i++) {
                Complaint c = complaintsToUpdate.get(i);
                MaintenanceStaff ms = staffList.get(i);
                ComplaintStatus oldStatus = c.getStatus();

                c.setStaffId(ms.getUserId());
                c.setStatus(ComplaintStatus.ASSIGNED);
                c.setAssignedStaffName(ms.getName());
                c.setAssignedStaffSpecialization(ms.getSpecialization().getDisplayName());

                complaintDao.update(c);
                String staffDesc = ms.getName() + " (" + ms.getSpecialization().getRoleTitle() + ")";
                notifyListeners(c, oldStatus, ComplaintStatus.ASSIGNED, adminId, "Assigned to " + staffDesc);
                lruCache.put(c.getComplaintId(), c);
                count++;
            }
            txManager.commit();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            res.put("assignedCount", count);
            res.put("message", count + " ticket" + (count == 1 ? "" : "s") + " assigned successfully");
            return res;
        } catch (Exception e) {
            txManager.rollback();
            log.error("Batch assignment transaction failed: {}", e.getMessage(), e);
            throw new HcmsException("Failed to confirm assignments: " + e.getMessage(), e);
        }
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
            lruCache.put(complaintId, complaint);
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

        txManager.begin();
        try {
            complaintDao.update(complaint);
            ComplaintHistory history = new ComplaintHistory(
                    complaintId,
                    complaint.getStatus(),
                    complaint.getStatus(),
                    adminId,
                    "Priority changed from " + oldPriority + " to " + newPriority
            );
            supportDao.saveHistory(history);
            txManager.commit();
        } catch (Exception e) {
            txManager.rollback();
            throw e;
        }

        lruCache.put(complaintId, complaint);
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
     * Orders open complaints by:
     * 1. Unassigned or Reopened first (needs immediate assignment/attention)
     * 2. Overdue complaints (past SLA due_by)
     * 3. Priority level (HIGH=1 first)
     * 4. Oldest age (earliest createdDate first)
     *
     * @return a PriorityQueue containing all open complaints in triage order
     */
    public PriorityQueue<Complaint> getTriageQueue() {
        Comparator<Complaint> triageComparator = Comparator
                .comparingInt((Complaint c) -> (c.getStaffId() == null || c.getStatus() == ComplaintStatus.REOPENED) ? 0 : 1)
                .thenComparingInt((Complaint c) -> c.isOverdue() ? 0 : 1)
                .thenComparingInt((Complaint c) -> c.getPriority() != null ? c.getPriority().getLevel() : 99)
                .thenComparing(Complaint::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder()));

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

    /**
     * Returns full task history for a staff member.
     */
    public List<Complaint> getStaffHistory(Long staffId) {
        return complaintDao.findHistoryByStaffId(staffId);
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
