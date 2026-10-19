package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.dao.ComplaintDao;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.analytics.AnalyticsService;
import edu.vit.hcms.service.complaint.ComplaintManager;
import io.javalin.http.Context;

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
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);

        List<Complaint> complaints = complaintDao.search(
                status, category, priority, hostelBlock, fromDate, toDate,
                query, sortBy, sortDir, page * size, size);

        long total = complaintDao.searchCount(status, category, priority,
                hostelBlock, fromDate, toDate, query);

        List<Map<String, Object>> result = complaints.stream().map(c -> {
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
        supportDao.findFeedbackByComplaintId(c.getComplaintId()).ifPresent(f -> {
            m.put("feedbackRating", f.getRating());
            m.put("feedbackComment", f.getComment());
        });
        return m;
    }
}
