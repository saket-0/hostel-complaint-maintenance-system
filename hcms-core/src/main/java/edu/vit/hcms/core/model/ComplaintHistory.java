package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.ComplaintStatus;

import java.time.LocalDateTime;

/**
 * Audit trail entry recording a status transition on a complaint.
 * Immutable once created; stores old status, new status, who changed it, and an optional note.
 */
public class ComplaintHistory {

    private Long id;
    private String complaintId;
    private ComplaintStatus oldStatus;
    private ComplaintStatus newStatus;
    private Long changedBy;
    private String note;
    private LocalDateTime changedAt;

    public ComplaintHistory() {
        this.changedAt = LocalDateTime.now();
    }

    public ComplaintHistory(String complaintId, ComplaintStatus oldStatus, ComplaintStatus newStatus,
                            Long changedBy, String note) {
        this();
        this.complaintId = complaintId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.note = note;
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

    public ComplaintStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(ComplaintStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public ComplaintStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ComplaintStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
