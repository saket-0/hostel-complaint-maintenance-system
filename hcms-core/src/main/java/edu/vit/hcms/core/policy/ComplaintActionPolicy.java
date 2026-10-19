package edu.vit.hcms.core.policy;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Role;
import edu.vit.hcms.core.model.Complaint;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central business policy determining permitted contextual actions per role and complaint state.
 *
 * <p>Enforces the following rules:
 * <ul>
 *   <li><b>Admin:</b>
 *     <ul>
 *       <li>NEW and unassigned: ASSIGN (primary), AUTO_ASSIGN (secondary), VIEW</li>
 *       <li>ASSIGNED or IN_PROGRESS: REASSIGN, VIEW</li>
 *       <li>REOPENED: ASSIGN (needs attention), VIEW</li>
 *       <li>RESOLVED or CLOSED: VIEW (no assignment actions)</li>
 *     </ul>
 *   </li>
 *   <li><b>Student:</b>
 *     <ul>
 *       <li>RESOLVED: GIVE_FEEDBACK (if feedback not yet given), REOPEN (within 7 days), CLOSE, VIEW</li>
 *       <li>NEW, ASSIGNED, IN_PROGRESS, REOPENED: VIEW only</li>
 *       <li>CLOSED: VIEW only</li>
 *     </ul>
 *   </li>
 *   <li><b>Staff:</b>
 *     <ul>
 *       <li>ASSIGNED: ACCEPT, VIEW</li>
 *       <li>IN_PROGRESS: RESOLVE, VIEW</li>
 *       <li>REOPENED: RESOLVE, VIEW</li>
 *       <li>RESOLVED, CLOSED: VIEW only</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class ComplaintActionPolicy {

    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_AUTO_ASSIGN = "AUTO_ASSIGN";
    public static final String ACTION_REASSIGN = "REASSIGN";
    public static final String ACTION_ACCEPT = "ACCEPT";
    public static final String ACTION_RESOLVE = "RESOLVE";
    public static final String ACTION_GIVE_FEEDBACK = "GIVE_FEEDBACK";
    public static final String ACTION_REOPEN = "REOPEN";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_VIEW = "VIEW";

    public static final int REOPEN_WINDOW_DAYS = 7;

    private ComplaintActionPolicy() {
        // Utility / policy class
    }

    /**
     * Determines the allowed actions for a given user role on a complaint.
     *
     * @param role the authenticated user's role
     * @param complaint the complaint being inspected
     * @return an unmodifiable list of allowed action identifiers
     */
    public static List<String> allowedActions(Role role, Complaint complaint) {
        return allowedActions(role, null, complaint);
    }

    /**
     * Determines the allowed actions for a specific user ID and role on a complaint.
     * Restricts feedback submission and closing/reopening strictly to the owner student.
     *
     * @param role the authenticated user's role
     * @param userId the authenticated user's ID (nullable)
     * @param complaint the complaint being inspected
     * @return an unmodifiable list of allowed action identifiers
     */
    public static List<String> allowedActions(Role role, Long userId, Complaint complaint) {
        if (role == null || complaint == null || complaint.getStatus() == null) {
            return Collections.emptyList();
        }

        List<String> actions = new ArrayList<>();
        ComplaintStatus status = complaint.getStatus();

        switch (role) {
            case ADMIN -> {
                if (status == ComplaintStatus.NEW && complaint.getStaffId() == null) {
                    actions.add(ACTION_ASSIGN);
                    actions.add(ACTION_AUTO_ASSIGN);
                } else if (status == ComplaintStatus.ASSIGNED || status == ComplaintStatus.IN_PROGRESS) {
                    actions.add(ACTION_REASSIGN);
                } else if (status == ComplaintStatus.REOPENED) {
                    actions.add(ACTION_ASSIGN);
                }
                actions.add(ACTION_VIEW);
            }
            case STUDENT -> {
                // When userId is provided, strictly enforce that only the owner student can close/reopen/feedback
                boolean isOwner = (userId == null || userId.equals(complaint.getStudentId()));
                if (status == ComplaintStatus.RESOLVED && isOwner) {
                    if (!complaint.isFeedbackGiven()) {
                        actions.add(ACTION_GIVE_FEEDBACK);
                    }
                    if (isWithinReopenWindow(complaint.getResolvedDate(), REOPEN_WINDOW_DAYS)) {
                        actions.add(ACTION_REOPEN);
                    }
                    actions.add(ACTION_CLOSE);
                }
                actions.add(ACTION_VIEW);
            }
            case STAFF -> {
                boolean isAssigned = (userId == null || userId.equals(complaint.getStaffId()));
                if (isAssigned) {
                    if (status == ComplaintStatus.ASSIGNED) {
                        actions.add(ACTION_ACCEPT);
                    } else if (status == ComplaintStatus.IN_PROGRESS || status == ComplaintStatus.REOPENED) {
                        actions.add(ACTION_RESOLVE);
                    }
                }
                actions.add(ACTION_VIEW);
            }
        }

        return Collections.unmodifiableList(actions);
    }

    /**
     * Checks if a resolution date falls within the allowable reopen window (7 days).
     *
     * @param resolvedDate timestamp when the complaint was marked RESOLVED
     * @param days max allowed days
     * @return true if still eligible for reopening
     */
    public static boolean isWithinReopenWindow(LocalDateTime resolvedDate, int days) {
        if (resolvedDate == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(resolvedDate, LocalDateTime.now()) <= days;
    }
}
