package edu.vit.hcms.core.model;

import java.time.LocalDateTime;

/**
 * Student feedback on a resolved complaint (1:1 relationship).
 * Rating is 1-5, validated at the service layer.
 */
public class Feedback {

    private Long feedbackId;
    private String complaintId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    public Feedback() {
        this.createdAt = LocalDateTime.now();
    }

    public Feedback(Long feedbackId, String complaintId, int rating, String comment) {
        this();
        this.feedbackId = feedbackId;
        this.complaintId = complaintId;
        this.rating = rating;
        this.comment = comment;
    }

    // --- Getters and Setters ---

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public String getComplaintId() {
        return complaintId;
    }

    public void setComplaintId(String complaintId) {
        this.complaintId = complaintId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
