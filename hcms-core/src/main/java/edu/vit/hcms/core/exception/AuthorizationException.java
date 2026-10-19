package edu.vit.hcms.core.exception;

/**
 * Thrown when a user lacks permission for the requested operation.
 * Maps to HTTP 403 Forbidden.
 */
public class AuthorizationException extends HcmsException {

    public AuthorizationException(String message) {
        super(message, "ACCESS_DENIED");
    }

    public AuthorizationException() {
        super("You do not have permission to perform this action", "ACCESS_DENIED");
    }
}
