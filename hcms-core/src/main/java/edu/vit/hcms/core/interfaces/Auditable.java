package edu.vit.hcms.core.interfaces;

/**
 * Interface for entities that should have their changes logged to an audit trail.
 * Implemented by domain entities that require full history tracking.
 */
public interface Auditable {

    /**
     * Records an audit entry for a state change.
     *
     * @param changedBy the user ID who made the change
     * @param oldValue  description of the old state
     * @param newValue  description of the new state
     * @param note      optional note about the change
     */
    void audit(Long changedBy, String oldValue, String newValue, String note);
}
