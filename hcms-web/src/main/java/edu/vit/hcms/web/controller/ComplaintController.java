package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.complaint.ComplaintManager;
import edu.vit.hcms.web.dto.CommentRequest;
import edu.vit.hcms.web.dto.CreateComplaintRequest;
import edu.vit.hcms.web.dto.FeedbackRequest;
import edu.vit.hcms.web.dto.ReopenRequest;
import io.javalin.http.Context;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for student complaint operations: create, view, reopen, close, feedback, comments.
 */
public class ComplaintController {

    private final ComplaintManager complaintManager;
    private final SupportDao supportDao;
    private final UserDao userDao;

    public ComplaintController(ComplaintManager complaintManager, SupportDao supportDao, UserDao userDao) {
        this.complaintManager = complaintManager;
        this.supportDao = supportDao;
        this.userDao = userDao;
    }

    /** POST /api/v1/complaints - Submit a new complaint. */
    public void create(Context ctx) {
        Long studentId = ctx.attribute("userId");
        CreateComplaintRequest req = ctx.bodyAsClass(CreateComplaintRequest.class);

        Category category = Category.valueOf(req.category());
        String description = sanitize(req.description());

        Complaint complaint = complaintManager.registerComplaint(studentId, category, description);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Complaint registered successfully");
        response.put("complaintId", complaint.getComplaintId());
        response.put("category", complaint.getCategory().getDisplayName());
        response.put("priority", complaint.getPriority().getDisplayName());
        response.put("status", complaint.getStatus().name());
        response.put("dueBy", complaint.getDueBy() != null ? complaint.getDueBy().toString() : null);
        ctx.status(201).json(response);
    }

    /** GET /api/v1/complaints/mine - Get student's complaints with pagination. */
    public void getMyComplaints(Context ctx) {
        Long studentId = ctx.attribute("userId");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(20);

        List<Complaint> complaints = complaintManager.getStudentComplaints(studentId, page, size);

        List<Map<String, Object>> result = complaints.stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        ctx.json(Map.of("complaints", result, "page", page, "size", size));
    }

    /** GET /api/v1/complaints/{id} - Get a specific complaint with details. */
    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        Complaint complaint = complaintManager.getComplaint(id);

        Map<String, Object> response = toMap(complaint);

        // Include history timeline
        List<ComplaintHistory> history = supportDao.findHistoryByComplaintId(id);
        response.put("history", history.stream().map(h -> {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("oldStatus", h.getOldStatus() != null ? h.getOldStatus().name() : null);
            hm.put("newStatus", h.getNewStatus().name());
            hm.put("changedBy", h.getChangedBy());
            hm.put("note", h.getNote());
            hm.put("changedAt", h.getChangedAt().toString());
            // Resolve name for the person who changed it
            userDao.findById(h.getChangedBy()).ifPresent(u -> hm.put("changedByName", u.getName()));
            return hm;
        }).collect(Collectors.toList()));

        // Include feedback if any
        supportDao.findFeedbackByComplaintId(id).ifPresent(f -> {
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("rating", f.getRating());
            fb.put("comment", f.getComment());
            fb.put("createdAt", f.getCreatedAt().toString());
            response.put("feedback", fb);
        });

        // Include comments
        List<ComplaintComment> comments = supportDao.findCommentsByComplaintId(id);
        response.put("comments", comments.stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("authorId", c.getAuthorId());
            cm.put("message", c.getMessage());
            cm.put("createdAt", c.getCreatedAt().toString());
            userDao.findById(c.getAuthorId()).ifPresent(u -> cm.put("authorName", u.getName()));
            return cm;
        }).collect(Collectors.toList()));

        // Resolve student and staff names
        userDao.findById(complaint.getStudentId()).ifPresent(u -> response.put("studentName", u.getName()));
        if (complaint.getStaffId() != null) {
            userDao.findById(complaint.getStaffId()).ifPresent(u -> response.put("staffName", u.getName()));
        }

        ctx.json(response);
    }

    /** POST /api/v1/complaints/{id}/reopen */
    public void reopen(Context ctx) {
        String id = ctx.pathParam("id");
        Long studentId = ctx.attribute("userId");
        ReopenRequest req = ctx.bodyAsClass(ReopenRequest.class);
        String reason = req != null ? req.reason() : null;

        Complaint complaint = complaintManager.reopenComplaint(id, studentId, reason);
        ctx.json(Map.of("message", "Complaint reopened", "status", complaint.getStatus().name()));
    }

    /** POST /api/v1/complaints/{id}/close */
    public void close(Context ctx) {
        String id = ctx.pathParam("id");
        Long studentId = ctx.attribute("userId");

        Complaint complaint = complaintManager.closeComplaint(id, studentId);
        ctx.json(Map.of("message", "Complaint closed", "status", complaint.getStatus().name()));
    }

    /** POST /api/v1/complaints/{id}/feedback */
    public void addFeedback(Context ctx) {
        String id = ctx.pathParam("id");
        FeedbackRequest req = ctx.bodyAsClass(FeedbackRequest.class);

        Feedback feedback = new Feedback();
        feedback.setComplaintId(id);
        feedback.setRating(req.rating());
        feedback.setComment(req.comment());

        supportDao.saveFeedback(feedback);
        ctx.status(201).json(Map.of("message", "Feedback submitted", "feedbackId", feedback.getFeedbackId()));
    }

    /** POST /api/v1/complaints/{id}/comments */
    public void addComment(Context ctx) {
        String id = ctx.pathParam("id");
        Long authorId = ctx.attribute("userId");
        CommentRequest req = ctx.bodyAsClass(CommentRequest.class);

        ComplaintComment comment = new ComplaintComment(id, authorId, sanitize(req.message()));
        supportDao.saveComment(comment);
        ctx.status(201).json(Map.of("message", "Comment added", "commentId", comment.getId()));
    }

    /** GET /api/v1/complaints/{id}/comments */
    public void getComments(Context ctx) {
        String id = ctx.pathParam("id");
        List<ComplaintComment> comments = supportDao.findCommentsByComplaintId(id);
        List<Map<String, Object>> result = comments.stream().map(c -> {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("authorId", c.getAuthorId());
            cm.put("message", c.getMessage());
            cm.put("createdAt", c.getCreatedAt().toString());
            userDao.findById(c.getAuthorId()).ifPresent(u -> cm.put("authorName", u.getName()));
            return cm;
        }).collect(Collectors.toList());
        ctx.json(Map.of("comments", result));
    }

    /** GET /api/v1/complaints/{id}/history */
    public void getHistory(Context ctx) {
        String id = ctx.pathParam("id");
        List<ComplaintHistory> history = supportDao.findHistoryByComplaintId(id);
        ctx.json(Map.of("history", history.stream().map(h -> {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("oldStatus", h.getOldStatus() != null ? h.getOldStatus().name() : null);
            hm.put("newStatus", h.getNewStatus().name());
            hm.put("changedBy", h.getChangedBy());
            hm.put("note", h.getNote());
            hm.put("changedAt", h.getChangedAt().toString());
            return hm;
        }).collect(Collectors.toList())));
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
        return m;
    }

    /** Basic XSS sanitization for user input. */
    private String sanitize(String input) {
        if (input == null) return null;
        return input.replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
