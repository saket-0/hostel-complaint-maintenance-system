package edu.vit.hcms.core.model;

import java.time.LocalDateTime;

/**
 * A comment on a complaint, enabling threaded discussion between student, admin, and staff.
 */
public class ComplaintComment {

    private Long id;
    private String complaintId;
    private Long authorId;
    private String message;
    private LocalDateTime createdAt;

    public ComplaintComment() {
        this.createdAt = LocalDateTime.now();
    }

    public ComplaintComment(String complaintId, Long authorId, String message) {
        this();
        this.complaintId = complaintId;
        this.authorId = authorId;
        this.message = message;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
