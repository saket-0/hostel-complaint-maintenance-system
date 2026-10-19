package edu.vit.hcms.core.exception;

/**
 * Thrown when optimistic locking detects a concurrent modification.
 * Maps to HTTP 409 Conflict.
 */
public class ConcurrentModificationException extends HcmsException {

    public ConcurrentModificationException(String complaintId) {
        super("Complaint " + complaintId + " was modified by another user. Please refresh and try again.",
                "CONCURRENT_MODIFICATION");
    }
}
