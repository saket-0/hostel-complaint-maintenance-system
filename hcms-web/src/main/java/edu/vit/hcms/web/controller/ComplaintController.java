package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.exception.AuthorizationException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.core.policy.ComplaintActionPolicy;
import edu.vit.hcms.persistence.dao.SupportDao;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.complaint.ComplaintManager;
import edu.vit.hcms.web.dto.CommentRequest;
import edu.vit.hcms.web.dto.CreateComplaintRequest;
import edu.vit.hcms.web.dto.FeedbackRequest;
import edu.vit.hcms.web.dto.ReopenRequest;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.time.LocalDateTime;
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
        response.put("priorityLevel", complaint.getPriority().name());
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
                .peek(c -> c.setAllowedActions(ComplaintActionPolicy.allowedActions(Role.STUDENT, studentId, c)))
                .map(this::toMap)
                .peek(m -> {
                    String cid = (String) m.get("complaintId");
                    if (Boolean.TRUE.equals(m.get("feedbackGiven"))) {
                        supportDao.findFeedbackByComplaintId(cid).ifPresent(f -> {
                            m.put("feedbackRating", f.getRating());
                            m.put("feedbackComment", f.getComment());
                        });
                    }
                })
                .collect(Collectors.toList());

        ctx.json(Map.of("complaints", result, "page", page, "size", size));
    }

    /** GET /api/v1/complaints/{id} - Get a specific complaint with details. */
    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        Complaint complaint = complaintManager.getComplaint(id);

        String roleStr = ctx.attribute("role");
        Long currentUserId = ctx.attribute("userId");
        Role role = roleStr != null ? Role.valueOf(roleStr) : Role.STUDENT;
        boolean hasFeedback = supportDao.findFeedbackByComplaintId(id).isPresent();
        complaint.setFeedbackGiven(hasFeedback);
        complaint.setAllowedActions(ComplaintActionPolicy.allowedActions(role, currentUserId, complaint));

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
            response.put("feedbackRating", f.getRating());
            response.put("feedbackComment", f.getComment());
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
        if (complaint.getStudentName() != null) {
            response.put("studentName", complaint.getStudentName());
            response.put("studentEmail", complaint.getStudentEmail());
            response.put("studentRoomNumber", complaint.getStudentRoomNumber());
            response.put("studentHostelBlock", complaint.getStudentHostelBlock());
        } else {
            userDao.findById(complaint.getStudentId()).ifPresent(u -> {
                response.put("studentName", u.getName());
                response.put("studentEmail", u.getEmail());
                if (u instanceof Student s) {
                    response.put("studentRoomNumber", s.getRoomNumber());
                    response.put("studentHostelBlock", s.getHostelBlock());
                }
            });
        }
        if (complaint.getStaffId() != null) {
            userDao.findById(complaint.getStaffId()).ifPresent(u -> {
                response.put("staffName", u.getName());
                response.put("assignedStaffName", u.getName());
                if (u instanceof MaintenanceStaff ms) {
                    response.put("assignedStaffSpecialization", ms.getSpecialization().getDisplayName());
                }
            });
        }

        List<Attachment> attachments = supportDao.findAttachmentsByComplaintId(id);
        response.put("attachments", attachments.stream().map(a -> {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", a.getId());
            am.put("fileName", a.getFileName());
            am.put("contentType", a.getContentType());
            am.put("sizeBytes", a.getSizeBytes());
            am.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            return am;
        }).collect(Collectors.toList()));

        ctx.json(response);
    }

    /**
     * Upload an optional photo attachment for a complaint.
     * Validates file size (max 5MB) and type.
     *
     * @param ctx Javalin context with uploaded file 'photo' or 'file'
     */
    public void uploadAttachment(Context ctx) {
        String complaintId = ctx.pathParam("id");
        Long currentUserId = ctx.attribute("userId");
        Complaint complaint = complaintManager.getComplaint(complaintId);
        if (!complaint.getStudentId().equals(currentUserId)) {
            throw new ValidationException("Only the complaint creator can attach files");
        }

        UploadedFile file = ctx.uploadedFile("photo");
        if (file == null) {
            file = ctx.uploadedFile("file");
        }
        if (file == null) {
            throw new ValidationException("No photo file uploaded");
        }

        if (file.size() > 5 * 1024 * 1024) {
            throw new ValidationException("Photo size exceeds 5MB limit");
        }

        String ct = file.contentType();
        String name = file.filename();
        Attachment attachment = new Attachment();
        attachment.setComplaintId(complaintId);
        attachment.setFileName(name != null ? name : "photo.jpg");
        attachment.setContentType(ct != null ? ct : "image/jpeg");
        attachment.setSizeBytes(file.size());
        attachment.setStoredPath("/uploads/" + complaintId + "_" + (name != null ? name : "photo.jpg"));
        attachment.setCreatedAt(LocalDateTime.now());

        supportDao.saveAttachment(attachment);

        ctx.status(201).json(Map.of(
            "message", "Attachment saved successfully",
            "attachmentId", attachment.getId(),
            "fileName", attachment.getFileName()
        ));
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
        Long studentId = ctx.attribute("userId");
        Complaint complaint = complaintManager.getComplaint(id);
        if (!complaint.getStudentId().equals(studentId)) {
            throw new AuthorizationException("You can only provide feedback on your own complaints");
        }
        if (complaint.getStatus() != ComplaintStatus.RESOLVED && complaint.getStatus() != ComplaintStatus.CLOSED) {
            throw new ValidationException("feedback", "Feedback can only be provided for resolved or closed complaints");
        }
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
            userDao.findById(h.getChangedBy()).ifPresent(u -> hm.put("changedByName", u.getName()));
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
        m.put("assignedStaffName", c.getAssignedStaffName());
        m.put("assignedStaffSpecialization", c.getAssignedStaffSpecialization());
        m.put("staffName", c.getAssignedStaffName());
        m.put("studentName", c.getStudentName());
        m.put("studentEmail", c.getStudentEmail());
        m.put("studentRoomNumber", c.getStudentRoomNumber());
        m.put("studentHostelBlock", c.getStudentHostelBlock());
        m.put("feedbackGiven", c.isFeedbackGiven());
        m.put("allowedActions", c.getAllowedActions());
        return m;
    }

    /** Basic XSS sanitization for user input. */
    private String sanitize(String input) {
        if (input == null) return null;
        return input.replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
