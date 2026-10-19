package edu.vit.hcms.persistence.dao;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.exception.DataAccessException;
import edu.vit.hcms.core.model.ComplaintHistory;
import edu.vit.hcms.core.model.Feedback;
import edu.vit.hcms.core.model.ComplaintComment;
import edu.vit.hcms.core.model.Notification;
import edu.vit.hcms.core.model.Attachment;
import edu.vit.hcms.persistence.db.TransactionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for supporting entities: ComplaintHistory, Feedback, Comments, Notifications, Attachments.
 * All use PreparedStatement and try-with-resources.
 */
public class SupportDao {

    private final TransactionManager txManager;

    public SupportDao(TransactionManager txManager) {
        this.txManager = txManager;
    }

    // ==================== COMPLAINT HISTORY ====================

    /**
     * Saves a complaint history (audit trail) entry.
     */
    public void saveHistory(ComplaintHistory history) {
        String sql = """
                INSERT INTO complaint_history (complaint_id, old_status, new_status, changed_by, note, changed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, history.getComplaintId());
            ps.setString(2, history.getOldStatus() != null ? history.getOldStatus().name() : null);
            ps.setString(3, history.getNewStatus().name());
            ps.setLong(4, history.getChangedBy());
            ps.setString(5, history.getNote());
            ps.setTimestamp(6, Timestamp.valueOf(history.getChangedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save complaint history", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns the full history (timeline) of a complaint, ordered chronologically.
     */
    public List<ComplaintHistory> findHistoryByComplaintId(String complaintId) {
        String sql = "SELECT * FROM complaint_history WHERE complaint_id = ? ORDER BY changed_at ASC";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ComplaintHistory> list = new ArrayList<>();
                while (rs.next()) {
                    ComplaintHistory h = new ComplaintHistory();
                    h.setId(rs.getLong("id"));
                    h.setComplaintId(rs.getString("complaint_id"));
                    String oldStatus = rs.getString("old_status");
                    if (oldStatus != null) {
                        h.setOldStatus(ComplaintStatus.valueOf(oldStatus));
                    }
                    h.setNewStatus(ComplaintStatus.valueOf(rs.getString("new_status")));
                    h.setChangedBy(rs.getLong("changed_by"));
                    h.setNote(rs.getString("note"));
                    h.setChangedAt(rs.getTimestamp("changed_at").toLocalDateTime());
                    list.add(h);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find complaint history", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns all audit history entries across all complaints, ordered chronologically.
     */
    public List<ComplaintHistory> findAllHistory() {
        String sql = "SELECT * FROM complaint_history ORDER BY changed_at DESC, id DESC";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ComplaintHistory> list = new ArrayList<>();
            while (rs.next()) {
                ComplaintHistory h = new ComplaintHistory();
                h.setId(rs.getLong("id"));
                h.setComplaintId(rs.getString("complaint_id"));
                String oldStatus = rs.getString("old_status");
                if (oldStatus != null) {
                    h.setOldStatus(ComplaintStatus.valueOf(oldStatus));
                }
                h.setNewStatus(ComplaintStatus.valueOf(rs.getString("new_status")));
                h.setChangedBy(rs.getLong("changed_by"));
                h.setNote(rs.getString("note"));
                h.setChangedAt(rs.getTimestamp("changed_at").toLocalDateTime());
                list.add(h);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all complaint history", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ==================== FEEDBACK ====================

    /**
     * Saves feedback for a complaint (1:1 relationship).
     */
    public Feedback saveFeedback(Feedback feedback) {
        String sql = "INSERT INTO feedback (complaint_id, rating, comment, created_at) VALUES (?, ?, ?, ?)";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, feedback.getComplaintId());
            ps.setInt(2, feedback.getRating());
            ps.setString(3, feedback.getComment());
            ps.setTimestamp(4, Timestamp.valueOf(feedback.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    feedback.setFeedbackId(keys.getLong(1));
                }
            }
            return feedback;
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                throw new edu.vit.hcms.core.exception.DuplicateEntryException(
                        "Feedback already exists for complaint " + feedback.getComplaintId());
            }
            throw new DataAccessException("Failed to save feedback", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds feedback for a complaint.
     */
    public Optional<Feedback> findFeedbackByComplaintId(String complaintId) {
        String sql = "SELECT * FROM feedback WHERE complaint_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Feedback f = new Feedback();
                    f.setFeedbackId(rs.getLong("feedback_id"));
                    f.setComplaintId(rs.getString("complaint_id"));
                    f.setRating(rs.getInt("rating"));
                    f.setComment(rs.getString("comment"));
                    f.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find feedback", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ==================== COMMENTS ====================

    /**
     * Saves a comment on a complaint.
     */
    public ComplaintComment saveComment(ComplaintComment comment) {
        String sql = "INSERT INTO complaint_comments (complaint_id, author_id, message, created_at) VALUES (?, ?, ?, ?)";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, comment.getComplaintId());
            ps.setLong(2, comment.getAuthorId());
            ps.setString(3, comment.getMessage());
            ps.setTimestamp(4, Timestamp.valueOf(comment.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    comment.setId(keys.getLong(1));
                }
            }
            return comment;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save comment", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns all comments for a complaint, ordered chronologically.
     */
    public List<ComplaintComment> findCommentsByComplaintId(String complaintId) {
        String sql = "SELECT * FROM complaint_comments WHERE complaint_id = ? ORDER BY created_at ASC";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ComplaintComment> list = new ArrayList<>();
                while (rs.next()) {
                    ComplaintComment c = new ComplaintComment();
                    c.setId(rs.getLong("id"));
                    c.setComplaintId(rs.getString("complaint_id"));
                    c.setAuthorId(rs.getLong("author_id"));
                    c.setMessage(rs.getString("message"));
                    c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(c);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find comments", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ==================== NOTIFICATIONS ====================

    /**
     * Saves a notification.
     */
    public Notification saveNotification(Notification notification) {
        String sql = "INSERT INTO notifications (user_id, message, read_flag, created_at) VALUES (?, ?, ?, ?)";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, notification.getUserId());
            ps.setString(2, notification.getMessage());
            ps.setBoolean(3, notification.isRead());
            ps.setTimestamp(4, Timestamp.valueOf(notification.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    notification.setId(keys.getLong(1));
                }
            }
            return notification;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save notification", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns notifications for a user, most recent first.
     */
    public List<Notification> findNotificationsByUserId(Long userId, int limit) {
        String sql = "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Notification> list = new ArrayList<>();
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setId(rs.getLong("id"));
                    n.setUserId(rs.getLong("user_id"));
                    n.setMessage(rs.getString("message"));
                    n.setRead(rs.getBoolean("read_flag"));
                    n.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(n);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find notifications", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Returns count of unread notifications for a user.
     */
    public int countUnread(Long userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_flag = FALSE";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count unread notifications", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Marks a notification as read.
     */
    public void markAsRead(Long notificationId, Long userId) {
        String sql = "UPDATE notifications SET read_flag = TRUE WHERE id = ? AND user_id = ?";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to mark notification as read", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    // ==================== ATTACHMENTS ====================

    /**
     * Saves an attachment record.
     */
    public Attachment saveAttachment(Attachment attachment) {
        String sql = """
                INSERT INTO attachments (complaint_id, file_name, stored_path, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, attachment.getComplaintId());
            ps.setString(2, attachment.getFileName());
            ps.setString(3, attachment.getStoredPath());
            ps.setString(4, attachment.getContentType());
            ps.setLong(5, attachment.getSizeBytes());
            ps.setTimestamp(6, Timestamp.valueOf(attachment.getCreatedAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    attachment.setId(keys.getLong(1));
                }
            }
            return attachment;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save attachment", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }

    /**
     * Finds attachments for a complaint.
     */
    public List<Attachment> findAttachmentsByComplaintId(String complaintId) {
        String sql = "SELECT * FROM attachments WHERE complaint_id = ? ORDER BY created_at ASC";
        Connection conn = txManager.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Attachment> list = new ArrayList<>();
                while (rs.next()) {
                    Attachment a = new Attachment();
                    a.setId(rs.getLong("id"));
                    a.setComplaintId(rs.getString("complaint_id"));
                    a.setFileName(rs.getString("file_name"));
                    a.setStoredPath(rs.getString("stored_path"));
                    a.setContentType(rs.getString("content_type"));
                    a.setSizeBytes(rs.getLong("size_bytes"));
                    a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(a);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find attachments", e);
        } finally {
            txManager.releaseConnection(conn);
        }
    }
}
