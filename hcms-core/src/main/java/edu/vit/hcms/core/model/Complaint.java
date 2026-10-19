package edu.vit.hcms.core.model;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Core domain entity representing a hostel complaint.
 * Uses the Builder pattern for flexible construction with many optional fields.
 * Includes a version field for optimistic locking (prevents double-assignment by two admins).
 *
 * <p>Complaint ID format: HCM-YYYY-NNNNNN (e.g., HCM-2026-000042).
 */
public class Complaint {

    private String complaintId;
    private Long studentId;
    private Long staffId; // nullable until assigned
    private Category category;
    private String description;
    private Priority priority;
    private ComplaintStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private LocalDateTime resolvedDate;
    private LocalDateTime dueBy;
    private int version; // optimistic locking

    /** Private constructor enforces use of the Builder. */
    private Complaint() {
    }

    /**
     * Returns a new Builder for constructing Complaint instances.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // --- Getters ---

    public String getComplaintId() {
        return complaintId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public LocalDateTime getResolvedDate() {
        return resolvedDate;
    }

    public LocalDateTime getDueBy() {
        return dueBy;
    }

    public int getVersion() {
        return version;
    }

    // --- Setters for mutable fields ---

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
        this.updatedDate = LocalDateTime.now();
    }

    public void setResolvedDate(LocalDateTime resolvedDate) {
        this.resolvedDate = resolvedDate;
    }

    public void setDueBy(LocalDateTime dueBy) {
        this.dueBy = dueBy;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    /**
     * Checks if this complaint has breached its SLA deadline.
     *
     * @return true if due_by is past and the complaint is not resolved/closed
     */
    public boolean isOverdue() {
        if (dueBy == null) return false;
        if (status == ComplaintStatus.RESOLVED || status == ComplaintStatus.CLOSED) return false;
        return LocalDateTime.now().isAfter(dueBy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Complaint that = (Complaint) o;
        return Objects.equals(complaintId, that.complaintId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(complaintId);
    }

    @Override
    public String toString() {
        return "Complaint{id='" + complaintId + "', category=" + category +
                ", priority=" + priority + ", status=" + status + "}";
    }

    /**
     * Builder for {@link Complaint}. Demonstrates the Builder design pattern.
     * Required fields: studentId, category, description.
     * Optional fields have sensible defaults (NEW status, MEDIUM priority, now timestamps).
     */
    public static class Builder {
        private final Complaint complaint = new Complaint();

        private Builder() {
            // Defaults
            complaint.status = ComplaintStatus.NEW;
            complaint.priority = Priority.MEDIUM;
            complaint.createdDate = LocalDateTime.now();
            complaint.updatedDate = LocalDateTime.now();
            complaint.version = 1;
        }

        public Builder complaintId(String complaintId) {
            complaint.complaintId = complaintId;
            return this;
        }

        public Builder studentId(Long studentId) {
            complaint.studentId = studentId;
            return this;
        }

        public Builder staffId(Long staffId) {
            complaint.staffId = staffId;
            return this;
        }

        public Builder category(Category category) {
            complaint.category = category;
            return this;
        }

        public Builder description(String description) {
            complaint.description = description;
            return this;
        }

        public Builder priority(Priority priority) {
            complaint.priority = priority;
            return this;
        }

        public Builder status(ComplaintStatus status) {
            complaint.status = status;
            return this;
        }

        public Builder createdDate(LocalDateTime createdDate) {
            complaint.createdDate = createdDate;
            return this;
        }

        public Builder updatedDate(LocalDateTime updatedDate) {
            complaint.updatedDate = updatedDate;
            return this;
        }

        public Builder resolvedDate(LocalDateTime resolvedDate) {
            complaint.resolvedDate = resolvedDate;
            return this;
        }

        public Builder dueBy(LocalDateTime dueBy) {
            complaint.dueBy = dueBy;
            return this;
        }

        public Builder version(int version) {
            complaint.version = version;
            return this;
        }

        /**
         * Builds and returns the Complaint instance.
         *
         * @return the constructed Complaint
         * @throws IllegalStateException if required fields are missing
         */
        public Complaint build() {
            Objects.requireNonNull(complaint.studentId, "studentId is required");
            Objects.requireNonNull(complaint.category, "category is required");
            Objects.requireNonNull(complaint.description, "description is required");
            return complaint;
        }
    }
}
