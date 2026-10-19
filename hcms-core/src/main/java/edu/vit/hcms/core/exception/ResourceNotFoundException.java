package edu.vit.hcms.core.exception;

/**
 * Thrown when a requested resource (complaint, user, etc.) is not found.
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends HcmsException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(resourceType + " not found with identifier: " + identifier, "NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, "NOT_FOUND");
    }
}
