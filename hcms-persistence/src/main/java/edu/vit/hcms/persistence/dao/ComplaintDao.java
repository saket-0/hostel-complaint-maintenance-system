package edu.vit.hcms.persistence.dao;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.exception.ConcurrentModificationException;
import edu.vit.hcms.core.exception.DataAccessException;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.persistence.db.RowMapper;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JDBC DAO for Complaint entity.
 * Features:
 * <ul>
 *   <li>Thread-safe complaint ID generation using AtomicLong + DB sequence</li>
 *   <li>Optimistic locking via version column</li>
 *   <li>Pagination with LIMIT/OFFSET</li>
 *   <li>Search/filter/sort support</li>
 *   <li>PreparedStatement exclusively (no string concat for values)</li>
 * </ul>
 */
public class ComplaintDao {

    private static final Logger log = LoggerFactory.getLogger(ComplaintDao.class);
    private final TransactionManager txManager;

    // Thread-safe ID generator with AtomicLong as local cache
    private final AtomicLong localSequence = new AtomicLong(0);

    private static final String BASE_SELECT = """
            SELECT c.*, u.name AS assigned_staff_name, s.specialization AS assigned_staff_specialization,
                   stu.name AS student_name, stu.email AS student_email,
                   st.room_number AS student_room_number, st.hostel_block AS student_hostel_block,
                   (fb.feedback_id IS NOT NULL) AS feedback_given
            FROM complaints c
            LEFT JOIN users u ON c.staff_id = u.user_id
            LEFT JOIN staff s ON c.staff_id = s.staff_id
            LEFT JOIN users stu ON c.student_id = stu.user_id
            LEFT JOIN students st ON c.student_id = st.student_id
            LEFT JOIN feedback fb ON c.complaint_id = fb.complaint_id
            """;

    private static final RowMapper<Complaint> COMPLAINT_MAPPER = rs -> {
        String staffName = null;
        String staffSpec = null;
        String studentName = null;
        String studentEmail = null;
        String studentRoomNumber = null;
        String studentHostelBlock = null;
        boolean feedbackGiven = false;
        try {
            staffName = rs.getString("assigned_staff_name");
            staffSpec = rs.getString("assigned_staff_specialization");
        } catch (SQLException ignored) {
            // Optional columns if not present in custom projection
        }
        try {
            studentName = rs.getString("student_name");
            studentEmail = rs.getString("student_email");
            studentRoomNumber = rs.getString("student_room_number");
            studentHostelBlock = rs.getString("student_hostel_block");
        } catch (SQLException ignored) {
            // Optional columns if not present in custom projection
        }
        try {
            feedbackGiven = rs.getBoolean("feedback_given");
        } catch (SQLException ignored) {
            // Optional column if not present in custom projection
        }
        return Complaint.builder()
                .complaintId(rs.getString("complaint_id"))
                .studentId(rs.getLong("student_id"))
                .staffId(rs.getObject("staff_id") != null ? rs.getLong("staff_id") : null)
                .category(Category.valueOf(rs.getString("category")))
                .description(rs.getString("description"))
                .priority(Priority.fromLevel(rs.getInt("priority")))
                .status(ComplaintStatus.valueOf(rs.getString("status")))
                .createdDate(rs.getTimestamp("created_date").toLocalDateTime())
                .updatedDate(rs.getTimestamp("updated_date").toLocalDateTime())
                .resolvedDate(rs.getTimestamp("resolved_date") != null ?
                        rs.getTimestamp("resolved_date").toLocalDateTime() : null)
                .dueBy(rs.getTimestamp("due_by") != null ?
                        rs.getTimestamp("due_by").toLocalDateTime() : null)
                .version(rs.getInt("version"))
                .assignedStaffName(staffName)
                .assignedStaffSpecialization(staffSpec)
                .studentName(studentName)
                .studentEmail(studentEmail)
                .studentRoomNumber(studentRoomNumber)
                .studentHostelBlock(studentHostelBlock)
                .feedbackGiven(feedbackGiven)
                .build();
    };

    public ComplaintDao(TransactionManager txManager) {
        this.txManager = txManager;
    }

    /**
     * Generates a unique complaint ID in the format HCM-YYYY-NNNNNN.
     * Uses the DB complaint_sequence table with SELECT ... FOR UPDATE to ensure
     * uniqueness across multiple JVM instances, plus an AtomicLong local guard.
     *
     * <p>Thread-safety: synchronized on the AtomicLong plus DB row lock.
     *
     * @return a unique complaint ID string
     */
    public String generateComplaintId() {
        int year = LocalDateTime.now().getYear();
        String selectSql = "SELECT next_val FROM complaint_sequence WHERE year_val = ? FOR UPDATE";
        String updateSql = "UPDATE complaint_sequence SET next_val = next_val + 1 WHERE year_val = ?";
        String insertSql = "INSERT INTO complaint_sequence (year_val, next_val) VALUES (?, 2)";

        Connection conn = txManager.getConnection();
        try {
            boolean autoCommit = conn.getAutoCommit();
            if (autoCommit) {
                conn.setAutoCommit(false);
            }
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setInt(1, year);
                try (ResultSet rs = selectPs.executeQuery()) {
                    long nextVal;
                    if (rs.next()) {
                        nextVal = rs.getLong("next_val");
                        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                            updatePs.setInt(1, year);
                            updatePs.executeUpdate();
                        }
                    } else {
                        nextVal = 1;
                        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                            insertPs.setInt(1, year);
                            insertPs.executeUpdate();
                        }
                    }
                    if (autoCommit) {
                        conn.commit();
                        conn.setAutoCommit(true);
                    }
                    localSequence.set(nextVal);
                    return String.format("HCM-%d-%06d", year, nextVal);
                }
            }
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException re) {
                log.error("Rollback failed during ID generation", re);
            }
            throw new DataAccessException("Failed to generate complaint ID", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Saves a new complaint.
     *
     * @param complaint the complaint to save (complaintId must be set)
     * @return the saved complaint
     */
    public Complaint save(Complaint complaint) {
        String sql = """
                INSERT INTO complaints (complaint_id, student_id, staff_id, category, description,
                    priority, status, created_date, updated_date, resolved_date, due_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Connection conn = txManager.getConnection();
        LocalDateTime created = complaint.getCreatedDate() != null
                ? complaint.getCreatedDate()
                : LocalDateTime.now();
        LocalDateTime updated = complaint.getUpdatedDate() != null
                ? complaint.getUpdatedDate()
                : created;
        int version = complaint.getVersion() <= 0 ? 1 : complaint.getVersion();

        complaint.setCreatedDate(created);
        complaint.setUpdatedDate(updated);
        complaint.setVersion(version);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaint.getComplaintId());
            ps.setLong(2, complaint.getStudentId());
            if (complaint.getStaffId() != null) {
                ps.setLong(3, complaint.getStaffId());
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, complaint.getCategory().name());
            ps.setString(5, complaint.getDescription());
            ps.setInt(6, complaint.getPriority().getLevel());
            ps.setString(7, complaint.getStatus().name());
            ps.setTimestamp(8, Timestamp.valueOf(created));
            ps.setTimestamp(9, Timestamp.valueOf(updated));
            if (complaint.getResolvedDate() != null) {
                ps.setTimestamp(10, Timestamp.valueOf(complaint.getResolvedDate()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }
            if (complaint.getDueBy() != null) {
                ps.setTimestamp(11, Timestamp.valueOf(complaint.getDueBy()));
            } else {
                ps.setNull(11, Types.TIMESTAMP);
            }
            ps.setInt(12, version);
            ps.executeUpdate();
            log.info("Saved complaint: {}", complaint.getComplaintId());
            return complaint;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save complaint", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds a complaint by ID.
     */
    public Optional<Complaint> findById(String complaintId) {
        String sql = BASE_SELECT + " WHERE c.complaint_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(COMPLAINT_MAPPER.mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find complaint: " + complaintId, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Updates a complaint with optimistic locking.
     * The version column is checked and incremented to prevent concurrent modifications.
     *
     * @param complaint the complaint with updated fields
     * @return the updated complaint
     * @throws ConcurrentModificationException if the version doesn't match (another admin modified it)
     */
    public Complaint update(Complaint complaint) {
        String sql = """
                UPDATE complaints SET staff_id = ?, priority = ?, status = ?,
                    updated_date = ?, resolved_date = ?, due_by = ?, version = version + 1
                WHERE complaint_id = ? AND version = ?
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (complaint.getStaffId() != null) {
                ps.setLong(1, complaint.getStaffId());
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setInt(2, complaint.getPriority().getLevel());
            ps.setString(3, complaint.getStatus().name());
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            if (complaint.getResolvedDate() != null) {
                ps.setTimestamp(5, Timestamp.valueOf(complaint.getResolvedDate()));
            } else {
                ps.setNull(5, Types.TIMESTAMP);
            }
            if (complaint.getDueBy() != null) {
                ps.setTimestamp(6, Timestamp.valueOf(complaint.getDueBy()));
            } else {
                ps.setNull(6, Types.TIMESTAMP);
            }
            ps.setString(7, complaint.getComplaintId());
            ps.setInt(8, complaint.getVersion());

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new ConcurrentModificationException(complaint.getComplaintId());
            }
            complaint.setVersion(complaint.getVersion() + 1);
            log.info("Updated complaint: {}", complaint.getComplaintId());
            return complaint;
        } catch (ConcurrentModificationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update complaint", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds complaints by student ID with pagination.
     */
    public List<Complaint> findByStudentId(Long studentId, int offset, int limit) {
        String sql = BASE_SELECT + " WHERE c.student_id = ? ORDER BY c.created_date DESC LIMIT ? OFFSET ?";
        return queryList(sql, ps -> {
            ps.setLong(1, studentId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
        });
    }

    /**
     * Finds active tasks assigned to a staff member (ASSIGNED, IN_PROGRESS, REOPENED).
     */
    public List<Complaint> findByStaffId(Long staffId) {
        String sql = BASE_SELECT + " WHERE c.staff_id = ? AND c.status IN ('ASSIGNED', 'IN_PROGRESS', 'REOPENED') ORDER BY c.priority ASC, c.created_date ASC";
        return queryList(sql, ps -> ps.setLong(1, staffId));
    }

    /**
     * Finds completed tasks assigned to a staff member (RESOLVED, CLOSED, most recent first).
     */
    public List<Complaint> findHistoryByStaffId(Long staffId) {
        String sql = BASE_SELECT + " WHERE c.staff_id = ? AND c.status IN ('RESOLVED', 'CLOSED') ORDER BY c.updated_date DESC";
        return queryList(sql, ps -> ps.setLong(1, staffId));
    }

    /**
     * Advanced search with filters, sorting, and pagination.
     * Builds WHERE clause dynamically with parameter binding (no SQL injection).
     */
    public List<Complaint> search(String status, String category, String priority,
                                   String hostelBlock, String fromDate, String toDate,
                                   String query, String sortBy, String sortDir,
                                   int offset, int limit) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();

        if (hostelBlock != null && !hostelBlock.isBlank()) {
            sql.append(" JOIN students stu ON c.student_id = stu.student_id");
        }
        sql.append(" WHERE 1=1");

        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            params.add(status);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND c.category = ?");
            params.add(category);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND c.priority = ?");
            params.add(Integer.parseInt(priority));
        }
        if (hostelBlock != null && !hostelBlock.isBlank()) {
            sql.append(" AND stu.hostel_block = ?");
            params.add(hostelBlock);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append(" AND c.created_date >= ?");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append(" AND c.created_date <= ?");
            params.add(toDate + " 23:59:59");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (c.description LIKE ? OR c.complaint_id LIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
        }

        // Safe sort column whitelist to prevent SQL injection
        String safeSort = switch (sortBy != null ? sortBy : "created_date") {
            case "priority" -> "c.priority";
            case "status" -> "c.status";
            case "category" -> "c.category";
            case "updated_date" -> "c.updated_date";
            default -> "c.created_date";
        };
        String safeDir = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(safeSort).append(" ").append(safeDir);
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer intVal) {
                    ps.setInt(i + 1, intVal);
                } else {
                    ps.setString(i + 1, param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Complaint> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(COMPLAINT_MAPPER.mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search complaints", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns count matching the same search filters (for pagination metadata).
     */
    public long searchCount(String status, String category, String priority,
                             String hostelBlock, String fromDate, String toDate, String query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM complaints c");
        List<Object> params = new ArrayList<>();

        if (hostelBlock != null && !hostelBlock.isBlank()) {
            sql.append(" JOIN students stu ON c.student_id = stu.student_id");
        }
        sql.append(" WHERE 1=1");

        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            params.add(status);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND c.category = ?");
            params.add(category);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND c.priority = ?");
            params.add(Integer.parseInt(priority));
        }
        if (hostelBlock != null && !hostelBlock.isBlank()) {
            sql.append(" AND stu.hostel_block = ?");
            params.add(hostelBlock);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append(" AND c.created_date >= ?");
            params.add(fromDate);
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append(" AND c.created_date <= ?");
            params.add(toDate + " 23:59:59");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (c.description LIKE ? OR c.complaint_id LIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
        }

        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof Integer intVal) {
                    ps.setInt(i + 1, intVal);
                } else {
                    ps.setString(i + 1, param.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count complaints", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Checks for duplicate complaint (same student, category, description) within the configured window.
     */
    public boolean isDuplicate(Long studentId, Category category, String description, int windowMinutes) {
        String sql = """
                SELECT COUNT(*) FROM complaints
                WHERE student_id = ? AND category = ? AND description = ?
                AND created_date > NOW() - INTERVAL ? MINUTE
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setString(2, category.name());
            ps.setString(3, description);
            ps.setInt(4, windowMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check duplicate", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds all overdue complaints (due_by < NOW and status not RESOLVED/CLOSED).
     * Used by the SLA escalation engine.
     */
    public List<Complaint> findOverdue() {
        String sql = """
                SELECT * FROM complaints
                WHERE due_by < NOW() AND status NOT IN ('RESOLVED', 'CLOSED')
                ORDER BY priority ASC, due_by ASC
                """;
        return queryList(sql, ps -> {});
    }

    /**
     * Finds RESOLVED complaints older than the given days for auto-close.
     */
    public List<Complaint> findResolvedOlderThan(int days) {
        String sql = BASE_SELECT + """
                WHERE c.status = 'RESOLVED' AND c.resolved_date < NOW() - INTERVAL ? DAY
                """;
        return queryList(sql, ps -> ps.setInt(1, days));
    }

    /**
     * Returns all open complaints (NEW, ASSIGNED, IN_PROGRESS, REOPENED) for triage.
     */
    public List<Complaint> findOpen() {
        return queryList(
                BASE_SELECT + " WHERE c.status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED') ORDER BY c.priority ASC, c.created_date ASC",
                ps -> {});
    }

    /**
     * Returns all complaints (for analytics).
     */
    public List<Complaint> findAll() {
        return queryList(BASE_SELECT + " ORDER BY c.created_date DESC", ps -> {});
    }

    /**
     * Batch update for escalation (used by SLA engine).
     */
    public void batchUpdatePriority(List<String> complaintIds, int newPriority) {
        String sql = "UPDATE complaints SET priority = ?, updated_date = NOW() WHERE complaint_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String id : complaintIds) {
                ps.setInt(1, newPriority);
                ps.setString(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("Batch updated priority for {} complaints", complaintIds.size());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to batch update priorities", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ---- Analytics queries ----

    /** Counts complaints grouped by status. */
    public Map<String, Long> countByStatus() {
        return countGroupBy("status");
    }

    /** Counts complaints grouped by category. */
    public Map<String, Long> countByCategory() {
        return countGroupBy("category");
    }

    /** Counts complaints grouped by priority. */
    public Map<String, Long> countByPriority() {
        return countGroupBy("priority");
    }

    /** Returns average resolution time in hours. */
    public double averageResolutionHours() {
        String sql = """
                SELECT AVG(TIMESTAMPDIFF(HOUR, created_date, resolved_date))
                FROM complaints WHERE resolved_date IS NOT NULL
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get avg resolution", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /** Returns SLA breach rate (percentage of resolved complaints that breached due_by). */
    public double slaBreachRate() {
        String sql = """
                SELECT
                    COUNT(CASE WHEN resolved_date > due_by THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0)
                FROM complaints WHERE resolved_date IS NOT NULL AND due_by IS NOT NULL
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get SLA breach rate", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /** Complaints per hostel block. */
    public Map<String, Long> countByHostelBlock() {
        String sql = """
                SELECT s.hostel_block, COUNT(*) as cnt
                FROM complaints c JOIN students s ON c.student_id = s.student_id
                GROUP BY s.hostel_block ORDER BY s.hostel_block
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<String, Long> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("hostel_block"), rs.getLong("cnt"));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count by hostel block", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /** Daily complaint count for the last 30 days. */
    public Map<String, Long> dailyTrend(int days) {
        String sql = """
                SELECT DATE(created_date) as day, COUNT(*) as cnt
                FROM complaints WHERE created_date >= NOW() - INTERVAL ? DAY
                GROUP BY DATE(created_date) ORDER BY day
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> result = new LinkedHashMap<>();
                while (rs.next()) {
                    result.put(rs.getString("day"), rs.getLong("cnt"));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get daily trend", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /** Staff workload: complaint counts per staff member. */
    public Map<Long, Long> staffWorkload() {
        String sql = """
                SELECT staff_id, COUNT(*) as cnt FROM complaints
                WHERE staff_id IS NOT NULL AND status NOT IN ('CLOSED')
                GROUP BY staff_id
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<Long, Long> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("staff_id"), rs.getLong("cnt"));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get staff workload", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /** Average feedback rating per staff member. */
    public Map<Long, Double> staffAverageRating() {
        String sql = """
                SELECT c.staff_id, AVG(f.rating) as avg_rating
                FROM feedback f JOIN complaints c ON f.complaint_id = c.complaint_id
                WHERE c.staff_id IS NOT NULL
                GROUP BY c.staff_id
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<Long, Double> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("staff_id"), rs.getDouble("avg_rating"));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get staff ratings", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ---- Private helpers ----

    private Map<String, Long> countGroupBy(String column) {
        String sql = "SELECT " + column + ", COUNT(*) as cnt FROM complaints GROUP BY " + column;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<String, Long> result = new LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString(1), rs.getLong("cnt"));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count by " + column, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    private List<Complaint> queryList(String sql, PreparedStatementSetter setter) {
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Complaint> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(COMPLAINT_MAPPER.mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute complaint query", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns a map of studentId to active complaint counts (NEW, ASSIGNED, IN_PROGRESS, REOPENED).
     */
    public Map<Long, Long> studentActiveComplaintCounts() {
        String sql = """
                SELECT student_id, COUNT(*) as cnt
                FROM complaints
                WHERE status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
                GROUP BY student_id
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<Long, Long> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getLong("student_id"), rs.getLong("cnt"));
            }
            return map;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count student active complaints", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Counts active complaints for a specific student.
     */
    public long countActiveComplaintsByStudentId(Long studentId) {
        if (studentId == null) return 0;
        String sql = """
                SELECT COUNT(*) FROM complaints
                WHERE student_id = ? AND status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count active complaints for student: " + studentId, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    @FunctionalInterface
    private interface PreparedStatementSetter {
        void set(PreparedStatement ps) throws SQLException;
    }
}
