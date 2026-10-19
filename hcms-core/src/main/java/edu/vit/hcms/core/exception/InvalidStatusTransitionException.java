package edu.vit.hcms.core.exception;

import edu.vit.hcms.core.enums.ComplaintStatus;

/**
 * Thrown when an illegal status transition is attempted on a complaint.
 * Maps to HTTP 409 Conflict.
 */
public class InvalidStatusTransitionException extends HcmsException {

    public InvalidStatusTransitionException(ComplaintStatus from, ComplaintStatus to) {
        super("Cannot transition complaint from " + from.getDisplayName() +
                " to " + to.getDisplayName(), "INVALID_TRANSITION");
    }

    public InvalidStatusTransitionException(String message) {
        super(message, "INVALID_TRANSITION");
    }
}
