package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.complaint.ComplaintManager;
import io.javalin.http.Context;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for maintenance staff operations: view tasks, accept, update status, availability.
 */
public class StaffController {

    private final ComplaintManager complaintManager;
    private final UserDao userDao;

    public StaffController(ComplaintManager complaintManager, UserDao userDao) {
        this.complaintManager = complaintManager;
        this.userDao = userDao;
    }

    /** GET /api/v1/staff/tasks */
    public void getTasks(Context ctx) {
        Long staffId = ctx.attribute("userId");
        List<Complaint> tasks = complaintManager.getStaffTasks(staffId);

        List<Map<String, Object>> result = tasks.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("complaintId", c.getComplaintId());
            m.put("category", c.getCategory().name());
            m.put("categoryDisplay", c.getCategory().getDisplayName());
            m.put("description", c.getDescription());
            m.put("priority", c.getPriority().name());
            m.put("priorityLevel", c.getPriority().getLevel());
            m.put("status", c.getStatus().name());
            m.put("statusDisplay", c.getStatus().getDisplayName());
            m.put("createdDate", c.getCreatedDate().toString());
            m.put("dueBy", c.getDueBy() != null ? c.getDueBy().toString() : null);
            m.put("overdue", c.isOverdue());
            userDao.findById(c.getStudentId()).ifPresent(u -> m.put("studentName", u.getName()));
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of("tasks", result));
    }

    /** GET /api/v1/staff/tasks/history - Full task history for staff. */
    public void getTaskHistory(Context ctx) {
        Long staffId = ctx.attribute("userId");
        List<Complaint> tasks = complaintManager.getStaffHistory(staffId);

        List<Map<String, Object>> result = tasks.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("complaintId", c.getComplaintId());
            m.put("category", c.getCategory().name());
            m.put("categoryDisplay", c.getCategory().getDisplayName());
            m.put("description", c.getDescription());
            m.put("priority", c.getPriority().name());
            m.put("priorityLevel", c.getPriority().getLevel());
            m.put("status", c.getStatus().name());
            m.put("statusDisplay", c.getStatus().getDisplayName());
            m.put("createdDate", c.getCreatedDate().toString());
            m.put("updatedDate", c.getUpdatedDate().toString());
            m.put("resolvedDate", c.getResolvedDate() != null ? c.getResolvedDate().toString() : null);
            userDao.findById(c.getStudentId()).ifPresent(u -> m.put("studentName", u.getName()));
            return m;
        }).collect(Collectors.toList());

        ctx.json(Map.of("tasks", result));
    }

    /** POST /api/v1/staff/tasks/{id}/accept - Accept an assigned task (transitions to IN_PROGRESS). */
    public void acceptTask(Context ctx) {
        String id = ctx.pathParam("id");
        Long staffId = ctx.attribute("userId");

        Complaint complaint = complaintManager.updateComplaintStatus(
                id, ComplaintStatus.IN_PROGRESS, staffId, "Accepted and started work");
        ctx.json(Map.of("message", "Task accepted", "status", complaint.getStatus().name()));
    }

    /** PUT /api/v1/staff/tasks/{id}/status - Update task status (IN_PROGRESS -> RESOLVED). */
    public void updateStatus(Context ctx) {
        String id = ctx.pathParam("id");
        Long staffId = ctx.attribute("userId");
        edu.vit.hcms.web.dto.UpdateStatusRequest req = ctx.bodyAsClass(edu.vit.hcms.web.dto.UpdateStatusRequest.class);

        ComplaintStatus newStatus = ComplaintStatus.valueOf(req.status());
        String note = req.note() != null ? req.note() : "";

        Complaint complaint = complaintManager.updateComplaintStatus(id, newStatus, staffId, note);
        ctx.json(Map.of("message", "Status updated", "status", complaint.getStatus().name()));
    }

    /** PUT /api/v1/staff/availability - Toggle staff availability. */
    public void updateAvailability(Context ctx) {
        Long staffId = ctx.attribute("userId");
        edu.vit.hcms.web.dto.AvailabilityRequest req = ctx.bodyAsClass(edu.vit.hcms.web.dto.AvailabilityRequest.class);
        boolean available = req.available();

        userDao.updateStaffAvailability(staffId, available);
        ctx.json(Map.of("message", "Availability updated", "available", available));
    }
}
