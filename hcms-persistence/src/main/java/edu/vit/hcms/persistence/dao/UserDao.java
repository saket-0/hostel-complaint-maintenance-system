package edu.vit.hcms.persistence.dao;

import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.exception.DataAccessException;
import edu.vit.hcms.core.exception.DuplicateEntryException;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.db.RowMapper;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO for User entity and its subclasses (Student, Admin, MaintenanceStaff).
 * Uses PreparedStatement exclusively (no string concatenation).
 * Uses RowMapper for ResultSet-to-domain mapping.
 * Uses try-with-resources for all JDBC resources.
 */
public class UserDao {

    private static final Logger log = LoggerFactory.getLogger(UserDao.class);
    private final TransactionManager txManager;

    /** RowMapper for the base User (returns appropriate subclass via factory logic). */
    private static final RowMapper<User> USER_MAPPER = rs -> {
        Role role = Role.valueOf(rs.getString("role"));
        User user;
        switch (role) {
            case STUDENT -> {
                Student s = new Student();
                s.setRoomNumber(rs.getString("room_number"));
                s.setHostelBlock(rs.getString("hostel_block"));
                user = s;
            }
            case ADMIN -> {
                Admin a = new Admin();
                a.setAdminRole(rs.getString("admin_role"));
                user = a;
            }
            case STAFF -> {
                MaintenanceStaff ms = new MaintenanceStaff();
                String spec = rs.getString("specialization");
                if (spec != null) {
                    ms.setSpecialization(Specialization.valueOf(spec));
                }
                ms.setAvailable(rs.getBoolean("staff_available"));
                user = ms;
            }
            default -> throw new DataAccessException("Unknown role: " + role);
        }
        user.setUserId(rs.getLong("user_id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(role);
        user.setActive(rs.getBoolean("active"));
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return user;
    };

    public UserDao(TransactionManager txManager) {
        this.txManager = txManager;
    }

    private static final String SELECT_USER_WITH_JOINS = """
            SELECT u.user_id, u.name, u.email, u.password_hash, u.role, u.active, u.created_at,
                   s.room_number, s.hostel_block,
                   a.admin_role,
                   st.specialization, st.available AS staff_available
            FROM users u
            LEFT JOIN students s ON u.user_id = s.student_id
            LEFT JOIN admins a ON u.user_id = a.admin_id
            LEFT JOIN staff st ON u.user_id = st.staff_id
            """;

    /**
     * Saves a new user and its role-specific record (student/admin/staff tables).
     * Uses explicit transaction management for the multi-table insert.
     *
     * @param user the user to save (must be a Student, Admin, or MaintenanceStaff)
     * @return the saved user with generated userId
     * @throws DuplicateEntryException if the email already exists
     */
    public User save(User user) {
        String insertUser = "INSERT INTO users (name, email, password_hash, role, active) VALUES (?, ?, ?, ?, ?)";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setBoolean(5, user.isActive());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getLong(1));
                }
            }

            // Insert role-specific record
            insertRoleSpecific(conn, user);
            log.info("Saved user: {} ({})", user.getEmail(), user.getRole());
            return user;
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                throw new DuplicateEntryException("A user with email '" + user.getEmail() + "' already exists");
            }
            throw new DataAccessException("Failed to save user", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    private void insertRoleSpecific(Connection conn, User user) throws SQLException {
        if (user instanceof Student s) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students (student_id, room_number, hostel_block) VALUES (?, ?, ?)")) {
                ps.setLong(1, user.getUserId());
                ps.setString(2, s.getRoomNumber());
                ps.setString(3, s.getHostelBlock());
                ps.executeUpdate();
            }
        } else if (user instanceof Admin a) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO admins (admin_id, admin_role) VALUES (?, ?)")) {
                ps.setLong(1, user.getUserId());
                ps.setString(2, a.getAdminRole() != null ? a.getAdminRole() : "Warden");
                ps.executeUpdate();
            }
        } else if (user instanceof MaintenanceStaff ms) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO staff (staff_id, specialization, available) VALUES (?, ?, ?)")) {
                ps.setLong(1, user.getUserId());
                ps.setString(2, ms.getSpecialization() != null ? ms.getSpecialization().name() : "GENERAL");
                ps.setBoolean(3, ms.isAvailable());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Finds a user by ID with role-specific fields via JOIN.
     *
     * @param userId the user ID
     * @return Optional containing the user if found
     */
    public Optional<User> findById(Long userId) {
        String sql = SELECT_USER_WITH_JOINS + " WHERE u.user_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(USER_MAPPER.mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by ID: " + userId, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds a user by email (for login).
     *
     * @param email the email address
     * @return Optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        String sql = SELECT_USER_WITH_JOINS + " WHERE u.email = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(USER_MAPPER.mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user by email: " + email, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns all staff members, optionally filtered by availability.
     */
    public List<MaintenanceStaff> findAllStaff(Boolean availableOnly) {
        String sql = SELECT_USER_WITH_JOINS + " WHERE u.role = 'STAFF'";
        if (Boolean.TRUE.equals(availableOnly)) {
            sql += " AND st.available = TRUE";
        }
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<MaintenanceStaff> staff = new ArrayList<>();
            while (rs.next()) {
                staff.add((MaintenanceStaff) USER_MAPPER.mapRow(rs));
            }
            return staff;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find staff", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns the count of active (non-closed) complaints assigned to a staff member.
     * Used by the LeastWorkload assignment strategy.
     */
    public int getActiveComplaintCount(Long staffId) {
        String sql = "SELECT COUNT(*) FROM complaints WHERE staff_id = ? AND status NOT IN ('CLOSED', 'RESOLVED')";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get complaint count for staff: " + staffId, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Updates staff availability.
     */
    public void updateStaffAvailability(Long staffId, boolean available) {
        String sql = "UPDATE staff SET available = ? WHERE staff_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, available);
            ps.setLong(2, staffId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update staff availability", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Updates user password hash.
     */
    public void updatePassword(Long userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns all users.
     */
    public List<User> findAll() {
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_USER_WITH_JOINS);
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(USER_MAPPER.mapRow(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all users", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Updates an existing user's base information (name, active) and role-specific fields.
     *
     * @param user the user with updated fields
     */
    public void update(User user) {
        String updateUser = "UPDATE users SET name = ?, active = ? WHERE user_id = ?";
        Connection conn = txManager.getConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(updateUser)) {
                ps.setString(1, user.getName());
                ps.setBoolean(2, user.isActive());
                ps.setLong(3, user.getUserId());
                ps.executeUpdate();
            }
            updateRoleSpecific(conn, user);
            log.info("Updated user: {} ({})", user.getEmail(), user.getRole());
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update user: " + user.getUserId(), e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    private void updateRoleSpecific(Connection conn, User user) throws SQLException {
        if (user instanceof Student s) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE students SET room_number = ?, hostel_block = ? WHERE student_id = ?")) {
                ps.setString(1, s.getRoomNumber());
                ps.setString(2, s.getHostelBlock());
                ps.setLong(3, user.getUserId());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO students (student_id, room_number, hostel_block) VALUES (?, ?, ?)")) {
                        ins.setLong(1, user.getUserId());
                        ins.setString(2, s.getRoomNumber());
                        ins.setString(3, s.getHostelBlock());
                        ins.executeUpdate();
                    }
                }
            }
        } else if (user instanceof MaintenanceStaff ms) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE staff SET specialization = ?, available = ? WHERE staff_id = ?")) {
                ps.setString(1, ms.getSpecialization() != null ? ms.getSpecialization().name() : "GENERAL");
                ps.setBoolean(2, ms.isAvailable());
                ps.setLong(3, user.getUserId());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO staff (staff_id, specialization, available) VALUES (?, ?, ?)")) {
                        ins.setLong(1, user.getUserId());
                        ins.setString(2, ms.getSpecialization() != null ? ms.getSpecialization().name() : "GENERAL");
                        ins.setBoolean(3, ms.isAvailable());
                        ins.executeUpdate();
                    }
                }
            }
        } else if (user instanceof Admin a) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE admins SET admin_role = ? WHERE admin_id = ?")) {
                ps.setString(1, a.getAdminRole() != null ? a.getAdminRole() : "Warden");
                ps.setLong(2, user.getUserId());
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO admins (admin_id, admin_role) VALUES (?, ?)")) {
                        ins.setLong(1, user.getUserId());
                        ins.setString(2, a.getAdminRole() != null ? a.getAdminRole() : "Warden");
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Soft-deactivates a user account by setting active = FALSE.
     *
     * @param userId the user ID to deactivate
     */
    public void deactivate(Long userId) {
        String sql = "UPDATE users SET active = FALSE WHERE user_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            log.info("Deactivated user ID: {}", userId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to deactivate user: " + userId, e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds users matching role and search criteria with pagination.
     *
     * @param roleFilter  optional role filter ("STUDENT", "STAFF", "ADMIN")
     * @param searchQuery optional search query against name, email, room, or hostel block
     * @param offset      pagination offset (>= 0)
     * @param limit       pagination limit (> 0)
     * @return list of matching users
     */
    public List<User> findUsers(String roleFilter, String searchQuery, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_USER_WITH_JOINS);
        sql.append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (roleFilter != null && !roleFilter.isBlank()) {
            sql.append(" AND u.role = ?");
            params.add(roleFilter.trim().toUpperCase());
        }

        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append(" AND (u.name LIKE ? OR u.email LIKE ? OR s.room_number LIKE ? OR s.hostel_block LIKE ?)");
            String like = "%" + searchQuery.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append(" ORDER BY u.created_at DESC, u.user_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer intVal) {
                    ps.setInt(i + 1, intVal);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(USER_MAPPER.mapRow(rs));
                }
                return users;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find users", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Counts users matching role and search criteria.
     *
     * @param roleFilter  optional role filter ("STUDENT", "STAFF", "ADMIN")
     * @param searchQuery optional search query against name, email, room, or hostel block
     * @return total count of matching users
     */
    public long countUsers(String roleFilter, String searchQuery) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) FROM users u
                LEFT JOIN students s ON u.user_id = s.student_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (roleFilter != null && !roleFilter.isBlank()) {
            sql.append(" AND u.role = ?");
            params.add(roleFilter.trim().toUpperCase());
        }

        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append(" AND (u.name LIKE ? OR u.email LIKE ? OR s.room_number LIKE ? OR s.hostel_block LIKE ?)");
            String like = "%" + searchQuery.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i).toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count users", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }
}
