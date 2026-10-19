package edu.vit.hcms.core.exception;

/**
 * Thrown when authentication fails (bad credentials, expired token).
 * Maps to HTTP 401 Unauthorized.
 */
public class AuthenticationException extends HcmsException {

    public AuthenticationException(String message) {
        super(message, "AUTH_FAILED");
    }
}
