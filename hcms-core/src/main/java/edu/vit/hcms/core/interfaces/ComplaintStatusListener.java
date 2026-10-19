package edu.vit.hcms.core.interfaces;

import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.model.Complaint;

/**
 * Observer pattern: listener for complaint status changes.
 * Implementations are registered with the ComplaintManager and notified
 * whenever a complaint transitions to a new status.
 *
 * <p>Used by:
 * <ul>
 *   <li>NotificationListener - sends in-app notifications</li>
 *   <li>AuditListener - writes to the complaint_history table</li>
 * </ul>
 */
@FunctionalInterface
public interface ComplaintStatusListener {

    /**
     * Called when a complaint's status changes.
     *
     * @param complaint the complaint that changed
     * @param oldStatus the previous status
     * @param newStatus the new status
     * @param changedBy the user ID who triggered the change
     * @param note      optional note about the change
     */
    void onStatusChange(Complaint complaint, ComplaintStatus oldStatus,
                         ComplaintStatus newStatus, Long changedBy, String note);
}
