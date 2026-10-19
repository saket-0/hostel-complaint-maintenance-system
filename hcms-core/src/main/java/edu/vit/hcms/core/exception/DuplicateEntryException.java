package edu.vit.hcms.core.exception;

/**
 * Thrown when a duplicate entry is detected (e.g., duplicate email, duplicate complaint within time window).
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateEntryException extends HcmsException {

    public DuplicateEntryException(String message) {
        super(message, "DUPLICATE_ENTRY");
    }
}
