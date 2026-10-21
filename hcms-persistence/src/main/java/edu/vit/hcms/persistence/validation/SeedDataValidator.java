package edu.vit.hcms.persistence.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates seed data and complaint lifecycle timestamp invariants.
 *
 * <p>Invariants enforced:
 * <ul>
 *   <li>created_date &lt;= every history row's changed_at &lt;= updated_date</li>
 *   <li>When resolved_date is present: updated_date &lt;= resolved_date</li>
 *   <li>History entries reflect all lifecycle states the complaint has passed through with correct actors</li>
 * </ul>
 */
public final class SeedDataValidator {

    private static final Logger log = LoggerFactory.getLogger(SeedDataValidator.class);

    private SeedDataValidator() {}

    public static record ComplaintRow(
            String complaintId,
            long studentId,
            Long staffId,
            String status,
            LocalDateTime createdDate,
            LocalDateTime updatedDate,
            LocalDateTime resolvedDate
    ) {}

    public static record HistoryRow(
            long id,
            String complaintId,
            String oldStatus,
            String newStatus,
            long changedBy,
            String note,
            LocalDateTime changedAt
    ) {}

    /**
     * Validates all complaints and history records against chronological and state invariants.
     *
     * @param dataSource the DataSource to query
     * @throws IllegalStateException if any invariant is violated
     */
    public static void validate(DataSource dataSource) {
        log.info("Starting seed data validation...");
        try (Connection conn = dataSource.getConnection()) {
            List<ComplaintRow> complaints = loadComplaints(conn);
            if (complaints.isEmpty()) {
                log.info("No complaints found to validate.");
                return;
            }

            Map<String, List<HistoryRow>> historyMap = loadHistory(conn);

            for (ComplaintRow c : complaints) {
                validateComplaintTimestamps(c);
                List<HistoryRow> history = historyMap.getOrDefault(c.complaintId(), List.of());
                validateComplaintHistory(c, history);
            }
            log.info("Seed data validation passed successfully for {} complaints.", complaints.size());
        } catch (SQLException e) {
            throw new IllegalStateException("Database error during seed data validation", e);
        }
    }

    static void validateComplaintTimestamps(ComplaintRow c) {
        if (c.createdDate() == null || c.updatedDate() == null) {
            throw new IllegalStateException("Complaint " + c.complaintId() + " has null created_date or updated_date");
        }
        if (c.createdDate().isAfter(c.updatedDate())) {
            throw new IllegalStateException("Complaint " + c.complaintId() + ": created_date (" + c.createdDate()
                    + ") is after updated_date (" + c.updatedDate() + ")");
        }
        if (c.resolvedDate() != null) {
            if ("RESOLVED".equals(c.status()) && c.updatedDate().isAfter(c.resolvedDate())) {
                throw new IllegalStateException("Complaint " + c.complaintId() + ": updated_date (" + c.updatedDate()
                        + ") is after resolved_date (" + c.resolvedDate() + ")");
            }
        }
    }

    static void validateComplaintHistory(ComplaintRow c, List<HistoryRow> history) {
        if (history.isEmpty()) {
            throw new IllegalStateException("Complaint " + c.complaintId() + " has no history records");
        }

        // Check history timestamps
        for (HistoryRow h : history) {
            if (h.changedAt() == null) {
                throw new IllegalStateException("Complaint " + c.complaintId() + " history row " + h.id() + " has null changed_at");
            }
            if (c.createdDate().isAfter(h.changedAt())) {
                throw new IllegalStateException("Complaint " + c.complaintId() + ": created_date (" + c.createdDate()
                        + ") is after history changed_at (" + h.changedAt() + ") in row " + h.id());
            }
            if (h.changedAt().isAfter(c.updatedDate())) {
                throw new IllegalStateException("Complaint " + c.complaintId() + ": history changed_at (" + h.changedAt()
                        + ") in row " + h.id() + " is after updated_date (" + c.updatedDate() + ")");
            }
            if (c.resolvedDate() != null && h.changedAt().isAfter(c.resolvedDate())) {
                throw new IllegalStateException("Complaint " + c.complaintId() + ": history changed_at (" + h.changedAt()
                        + ") in row " + h.id() + " is after resolved_date (" + c.resolvedDate() + ")");
            }
        }

        // Check status lifecycle progression and actors
        boolean hasNew = false;
        boolean hasAssigned = false;
        boolean hasInProgress = false;
        boolean hasResolved = false;
        boolean hasClosed = false;
        boolean hasReopened = false;

        for (HistoryRow h : history) {
            if ("NEW".equals(h.newStatus())) {
                hasNew = true;
                if (h.changedBy() != c.studentId()) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + ": NEW history must be changed by student");
                }
            } else if ("ASSIGNED".equals(h.newStatus())) {
                hasAssigned = true;
            } else if ("IN_PROGRESS".equals(h.newStatus())) {
                hasInProgress = true;
                if (c.staffId() != null && h.changedBy() != c.staffId()) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + ": IN_PROGRESS history must be changed by assigned staff");
                }
            } else if ("RESOLVED".equals(h.newStatus())) {
                hasResolved = true;
                if (c.staffId() != null && h.changedBy() != c.staffId()) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + ": RESOLVED history must be changed by assigned staff");
                }
            } else if ("CLOSED".equals(h.newStatus())) {
                hasClosed = true;
                if (h.changedBy() != c.studentId()) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + ": CLOSED history must be changed by student");
                }
            } else if ("REOPENED".equals(h.newStatus())) {
                hasReopened = true;
                if (h.changedBy() != c.studentId()) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + ": REOPENED history must be changed by student");
                }
            }
        }

        if (!hasNew) {
            throw new IllegalStateException("Complaint " + c.complaintId() + " missing NEW history");
        }

        switch (c.status()) {
            case "ASSIGNED" -> {
                if (!hasAssigned) throw new IllegalStateException("Complaint " + c.complaintId() + " missing ASSIGNED history");
            }
            case "IN_PROGRESS" -> {
                if (!hasAssigned || !hasInProgress) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + " missing ASSIGNED or IN_PROGRESS history");
                }
            }
            case "RESOLVED" -> {
                if (!hasAssigned || !hasInProgress || !hasResolved) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + " missing ASSIGNED, IN_PROGRESS, or RESOLVED history");
                }
            }
            case "CLOSED" -> {
                if (!hasAssigned || !hasInProgress || !hasResolved || !hasClosed) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + " missing full lifecycle history through CLOSED");
                }
            }
            case "REOPENED" -> {
                if (!hasAssigned || !hasInProgress || !hasResolved || !hasReopened) {
                    throw new IllegalStateException("Complaint " + c.complaintId() + " missing full lifecycle history through REOPENED");
                }
            }
        }
    }

    private static List<ComplaintRow> loadComplaints(Connection conn) throws SQLException {
        List<ComplaintRow> list = new ArrayList<>();
        String sql = "SELECT complaint_id, student_id, staff_id, status, created_date, updated_date, resolved_date FROM complaints";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Timestamp created = rs.getTimestamp("created_date");
                Timestamp updated = rs.getTimestamp("updated_date");
                Timestamp resolved = rs.getTimestamp("resolved_date");
                long staffVal = rs.getLong("staff_id");
                Long staffId = rs.wasNull() ? null : staffVal;

                list.add(new ComplaintRow(
                        rs.getString("complaint_id"),
                        rs.getLong("student_id"),
                        staffId,
                        rs.getString("status"),
                        created != null ? created.toLocalDateTime() : null,
                        updated != null ? updated.toLocalDateTime() : null,
                        resolved != null ? resolved.toLocalDateTime() : null
                ));
            }
        }
        return list;
    }

    private static Map<String, List<HistoryRow>> loadHistory(Connection conn) throws SQLException {
        Map<String, List<HistoryRow>> map = new HashMap<>();
        String sql = "SELECT id, complaint_id, old_status, new_status, changed_by, note, changed_at FROM complaint_history ORDER BY changed_at ASC, id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Timestamp changed = rs.getTimestamp("changed_at");
                HistoryRow row = new HistoryRow(
                        rs.getLong("id"),
                        rs.getString("complaint_id"),
                        rs.getString("old_status"),
                        rs.getString("new_status"),
                        rs.getLong("changed_by"),
                        rs.getString("note"),
                        changed != null ? changed.toLocalDateTime() : null
                );
                map.computeIfAbsent(row.complaintId(), k -> new ArrayList<>()).add(row);
            }
        }
        return map;
    }
}
