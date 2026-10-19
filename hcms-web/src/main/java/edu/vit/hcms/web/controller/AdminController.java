package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.core.policy.ComplaintActionPolicy;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.analytics.AnalyticsService;
import edu.vit.hcms.service.complaint.ComplaintManager;
import io.javalin.http.Context;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for admin operations: complaint management, staff, analytics, CSV export.
 */
public class AdminController {

    private final ComplaintManager complaintManager;
    private final ComplaintDao complaintDao;
    private final UserDao userDao;
    private final AnalyticsService analyticsService;
    private final SupportDao supportDao;

    public AdminController(ComplaintManager complaintManager, ComplaintDao complaintDao,
                           UserDao userDao, AnalyticsService analyticsService, SupportDao supportDao) {
        this.complaintManager = complaintManager;
        this.complaintDao = complaintDao;
        this.userDao = userDao;
        this.analyticsService = analyticsService;
        this.supportDao = supportDao;
    }

    /**
     * GET /api/v1/admin/triage
     * Admin triage queue sorted by:
     * unassigned/reopened first -> overdue next -> priority (HIGH first) -> oldest.
     * Computes live counters across open complaints:
     * unassigned, overdue, reopened, highPriority.
     * Supports filtering by counter, status, category, priority, hostelBlock, date range, q.
     * Default page size: 25.
     */
    public void triage(Context ctx) {
        String counter = ctx.queryParam("counter");
        String status = ctx.queryParam("status");
        String category = ctx.queryParam("category");
        String priority = ctx.queryParam("priority");
        String hostelBlock = ctx.queryParam("hostelBlock");
        String fromDate = ctx.queryParam("fromDate");
        String toDate = ctx.queryParam("toDate");
        String query = ctx.queryParam("q");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(25);

        PriorityQueue<Complaint> queue = complaintManager.getTriageQueue();
        List<Complaint> orderedComplaints = new ArrayList<>();
        while (!queue.isEmpty()) {
            orderedComplaints.add(queue.poll());
        }

        // Live counts for the 4 counter cards across ALL open complaints in triage queue
        long unassignedCount = orderedComplaints.stream().filter(c -> c.getStaffId() == null).count();
        long overdueCount = orderedComplaints.stream().filter(Complaint::isOverdue).count();
        long reopenedCount = orderedComplaints.stream().filter(c -> c.getStatus() == ComplaintStatus.REOPENED).count();
        long highPriorityCount = orderedComplaints.stream().filter(c -> c.getPriority() == Priority.HIGH).count();

        // Local cache for users
        Map<Long, Optional<User>> userCache = new HashMap<>();

        // Apply filters
        List<Complaint> filtered = orderedComplaints.stream().filter(c -> {
            if (counter != null && !counter.isBlank()) {
                switch (counter.toLowerCase()) {
                    case "unassigned":
                        if (c.getStaffId() != null) return false;
                        break;
                    case "overdue":
                        if (!c.isOverdue()) return false;
                        break;
                    case "reopened":
                        if (c.getStatus() != ComplaintStatus.REOPENED) return false;
                        break;
                    case "high_priority":
                    case "high":
                        if (c.getPriority() != Priority.HIGH) return false;
                        break;
                    default:
                        break;
                }
            }

            if (status != null && !status.isBlank()) {
                if (!c.getStatus().name().equalsIgnoreCase(status)) return false;
            }

            if (category != null && !category.isBlank()) {
                if (!c.getCategory().name().equalsIgnoreCase(category)) return false;
            }

            if (priority != null && !priority.isBlank()) {
                boolean matches = c.getPriority().name().equalsIgnoreCase(priority)
                        || String.valueOf(c.getPriority().getLevel()).equals(priority);
                if (!matches) return false;
            }

            if (hostelBlock != null && !hostelBlock.isBlank()) {
                User u = userCache.computeIfAbsent(c.getStudentId(), userDao::findById).orElse(null);
                if (!(u instanceof Student s) || !hostelBlock.equalsIgnoreCase(s.getHostelBlock())) {
                    return false;
                }
            }

            if (fromDate != null && !fromDate.isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(fromDate);
                    if (c.getCreatedDate().toLocalDate().isBefore(d)) return false;
                } catch (Exception ignored) {}
            }

            if (toDate != null && !toDate.isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(toDate);
                    if (c.getCreatedDate().toLocalDate().isAfter(d)) return false;
                } catch (Exception ignored) {}
            }

            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase();
                boolean matchId = c.getComplaintId().toLowerCase().contains(q);
                boolean matchDesc = c.getDescription() != null && c.getDescription().toLowerCase().contains(q);
                boolean matchStaff = c.getAssignedStaffName() != null && c.getAssignedStaffName().toLowerCase().contains(q);
                User u = userCache.computeIfAbsent(c.getStudentId(), userDao::findById).orElse(null);
                boolean matchStudent = u != null && u.getName().toLowerCase().contains(q);
                boolean matchRoom = (u instanceof Student s) && (
                        (s.getRoomNumber() != null && s.getRoomNumber().toLowerCase().contains(q)) ||
                        (s.getHostelBlock() != null && s.getHostelBlock().toLowerCase().contains(q))
                );
                if (!matchId && !matchDesc && !matchStaff && !matchStudent && !matchRoom) {
                    return false;
                }
            }

            return true;
        }).collect(Collectors.toList());

        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Complaint> paged = filtered.subList(fromIndex, toIndex);

        List<Map<String, Object>> result = paged.stream().map(c -> {
            c.setAllowedActions(ComplaintActionPolicy.allowedActions(Role.ADMIN, c));
            Map<String, Object> m = toMap(c);
            userCache.computeIfAbsent(c.getStudentId(), userDao::findById).ifPresent(u -> {
                m.put("studentName", u.getName());
                if (u instanceof Student s) {
                    m.put("hostelBlock", s.getHostelBlock());
                    m.put("roomNumber", s.getRoomNumber());
                }
            });
            if (c.getStaffId() != null && !m.containsKey("staffName")) {
                userCache.computeIfAbsent(c.getStaffId(), userDao::findById).ifPresent(u -> m.put("staffName", u.getName()));
            }
            if (c.isFeedbackGiven()) {
                supportDao.findFeedbackByComplaintId(c.getComplaintId()).ifPresent(f -> {
                    m.put("feedbackRating", f.getRating());
                    m.put("feedbackComment", f.getComment());
                });
            }
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of(
                "counters", Map.of(
                        "unassigned", unassignedCount,
                        "overdue", overdueCount,
                        "reopened", reopenedCount,
                        "highPriority", highPriorityCount
                ),
                "complaints", result,
                "total", total,
                "page", page,
                "size", size
        ));
    }

    /** GET /api/v1/admin/complaints - Search, filter, sort, paginate complaints. */
    public void listComplaints(Context ctx) {
        String status = ctx.queryParam("status");
        String category = ctx.queryParam("category");
        String priority = ctx.queryParam("priority");
        String hostelBlock = ctx.queryParam("hostelBlock");
        String fromDate = ctx.queryParam("fromDate");
        String toDate = ctx.queryParam("toDate");
        String query = ctx.queryParam("q");
        String sortBy = ctx.queryParam("sortBy");
        String sortDir = ctx.queryParam("sortDir");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(25);

        List<Complaint> complaints = complaintDao.search(
                status, category, priority, hostelBlock, fromDate, toDate,
                query, sortBy, sortDir, page * size, size);

        long total = complaintDao.searchCount(status, category, priority,
                hostelBlock, fromDate, toDate, query);

        List<Map<String, Object>> result = complaints.stream().map(c -> {
            c.setAllowedActions(ComplaintActionPolicy.allowedActions(Role.ADMIN, c));
            Map<String, Object> m = toMap(c);
            userDao.findById(c.getStudentId()).ifPresent(u -> {
                m.put("studentName", u.getName());
                if (u instanceof Student s) {
                    m.put("hostelBlock", s.getHostelBlock());
                    m.put("roomNumber", s.getRoomNumber());
                }
            });
            if (c.getStaffId() != null) {
                userDao.findById(c.getStaffId()).ifPresent(u -> m.put("staffName", u.getName()));
            }
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of("complaints", result, "total", total, "page", page, "size", size));
    }

    /** PUT /api/v1/admin/complaints/{id}/priority */
    public void updatePriority(Context ctx) {
        String id = ctx.pathParam("id");
        Long adminId = ctx.attribute("userId");
        edu.vit.hcms.web.dto.UpdatePriorityRequest req = ctx.bodyAsClass(edu.vit.hcms.web.dto.UpdatePriorityRequest.class);
        int level = req.priority();

        Complaint complaint = complaintManager.updatePriority(id, Priority.fromLevel(level), adminId);
        ctx.json(Map.of("message", "Priority updated", "priority", complaint.getPriority().name()));
    }

    /** POST /api/v1/admin/complaints/{id}/assign */
    public void assign(Context ctx) {
        String id = ctx.pathParam("id");
        Long adminId = ctx.attribute("userId");
        edu.vit.hcms.web.dto.AssignStaffRequest req = ctx.bodyAsClass(edu.vit.hcms.web.dto.AssignStaffRequest.class);
        Long staffId = req.staffId();

        Complaint complaint = complaintManager.assignComplaint(id, staffId, adminId);
        ctx.json(Map.of("message", "Complaint assigned", "staffId", staffId, "status", complaint.getStatus().name()));
    }

    /** POST /api/v1/admin/complaints/{id}/auto-assign */
    public void autoAssign(Context ctx) {
        String id = ctx.pathParam("id");
        Long adminId = ctx.attribute("userId");

        Complaint complaint = complaintManager.autoAssignComplaint(id, adminId);
        ctx.json(Map.of("message", "Complaint auto-assigned", "staffId", complaint.getStaffId(),
                "status", complaint.getStatus().name()));
    }

    /** GET /api/v1/admin/staff */
    public void listStaff(Context ctx) {
        List<MaintenanceStaff> staff = userDao.findAllStaff(null);
        Map<Long, Long> workload = complaintDao.staffWorkload();
        Map<Long, Double> ratings = complaintDao.staffAverageRating();

        List<Map<String, Object>> result = staff.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", s.getUserId());
            m.put("name", s.getName());
            m.put("email", s.getEmail());
            m.put("specialization", s.getSpecialization().name());
            m.put("specializationDisplay", s.getSpecialization().getDisplayName());
            m.put("available", s.isAvailable());
            m.put("activeComplaints", workload.getOrDefault(s.getUserId(), 0L));
            m.put("avgRating", Math.round(ratings.getOrDefault(s.getUserId(), 0.0) * 10.0) / 10.0);
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of("staff", result));
    }

    /** POST /api/v1/admin/staff - Create a new staff member. */
    public void createStaff(Context ctx) {
        edu.vit.hcms.web.dto.CreateStaffRequest req = ctx.bodyAsClass(edu.vit.hcms.web.dto.CreateStaffRequest.class);
        MaintenanceStaff staff = new MaintenanceStaff();
        staff.setName(req.name());
        staff.setEmail(req.email());
        String pwd = req.password() != null && !req.password().isBlank() ? req.password() : "password123";
        staff.setPasswordHash(at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                .hashToString(12, pwd.toCharArray()));
        staff.setSpecialization(Specialization.valueOf(req.specialization()));
        staff.setAvailable(true);

        userDao.save(staff);
        ctx.status(201).json(Map.of("message", "Staff created", "userId", staff.getUserId()));
    }

    /** GET /api/v1/admin/analytics */
    public void analytics(Context ctx) {
        ctx.json(analyticsService.getDashboardAnalytics());
    }

    /** GET /api/v1/admin/export.csv */
    public void exportCsv(Context ctx) {
        List<String[]> rows = analyticsService.exportComplaintsAsCsv();
        StringBuilder csv = new StringBuilder();
        for (String[] row : rows) {
            csv.append(String.join(",", row)).append("\n");
        }
        ctx.header("Content-Type", "text/csv");
        ctx.header("Content-Disposition", "attachment; filename=complaints_export.csv");
        ctx.result(csv.toString());
    }

    /** GET /api/v1/admin/export-audit.csv */
    public void exportAuditCsv(Context ctx) {
        List<edu.vit.hcms.core.model.ComplaintHistory> historyList = supportDao.findAllHistory();
        StringBuilder csv = new StringBuilder();
        csv.append("History ID,Complaint ID,Old Status,New Status,Changed By,Changed At,Note\n");
        for (edu.vit.hcms.core.model.ComplaintHistory h : historyList) {
            String noteEscaped = h.getNote() != null ? h.getNote().replace("\"", "\"\"") : "";
            csv.append(h.getId()).append(",")
               .append(h.getComplaintId()).append(",")
               .append(h.getOldStatus() != null ? h.getOldStatus().name() : "").append(",")
               .append(h.getNewStatus().name()).append(",")
               .append(h.getChangedBy()).append(",")
               .append(h.getChangedAt()).append(",")
               .append("\"").append(noteEscaped).append("\"\n");
        }
        ctx.header("Content-Type", "text/csv");
        ctx.header("Content-Disposition", "attachment; filename=audit_history_export.csv");
        ctx.result(csv.toString());
    }

    private Map<String, Object> toMap(Complaint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("complaintId", c.getComplaintId());
        m.put("studentId", c.getStudentId());
        m.put("staffId", c.getStaffId());
        m.put("category", c.getCategory().name());
        m.put("categoryDisplay", c.getCategory().getDisplayName());
        m.put("description", c.getDescription());
        m.put("priority", c.getPriority().name());
        m.put("priorityLevel", c.getPriority().getLevel());
        m.put("status", c.getStatus().name());
        m.put("statusDisplay", c.getStatus().getDisplayName());
        m.put("statusColor", c.getStatus().getColorHex());
        m.put("createdDate", c.getCreatedDate().toString());
        m.put("updatedDate", c.getUpdatedDate().toString());
        m.put("resolvedDate", c.getResolvedDate() != null ? c.getResolvedDate().toString() : null);
        m.put("dueBy", c.getDueBy() != null ? c.getDueBy().toString() : null);
        m.put("overdue", c.isOverdue());
        m.put("version", c.getVersion());
        m.put("assignedStaffName", c.getAssignedStaffName());
        m.put("assignedStaffSpecialization", c.getAssignedStaffSpecialization());
        if (c.getAssignedStaffName() != null) {
            m.put("staffName", c.getAssignedStaffName());
        }
        supportDao.findFeedbackByComplaintId(c.getComplaintId()).ifPresent(f -> {
            m.put("feedbackRating", f.getRating());
            m.put("feedbackComment", f.getComment());
        });
        m.put("feedbackGiven", c.isFeedbackGiven() || m.containsKey("feedbackRating"));
        m.put("allowedActions", c.getAllowedActions());
        return m;
    }
}
