package edu.vit.hcms.core.exception;

/**
 * Base exception for all HCMS domain exceptions.
 * Unchecked (extends RuntimeException) for most cases;
 * subclasses can be checked where appropriate.
 */
public class HcmsException extends RuntimeException {

    private final String errorCode;

    public HcmsException(String message) {
        super(message);
        this.errorCode = "HCMS_ERROR";
    }

    public HcmsException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public HcmsException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "HCMS_ERROR";
    }

    public HcmsException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Machine-readable error code for API responses. */
    public String getErrorCode() {
        return errorCode;
    }
}
