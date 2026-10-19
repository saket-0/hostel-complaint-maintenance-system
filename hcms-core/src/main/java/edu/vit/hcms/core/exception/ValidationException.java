package edu.vit.hcms.core.exception;

/**
 * Thrown when input validation fails (e.g., missing required fields, invalid email format).
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class ValidationException extends HcmsException {

    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR");
    }

    public ValidationException(String field, String reason) {
        super("Validation failed for '" + field + "': " + reason, "VALIDATION_ERROR");
    }
}
