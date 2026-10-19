package edu.vit.hcms.core.exception;

/**
 * Wraps JDBC/SQL exceptions at the persistence layer boundary.
 * Prevents raw SQLExceptions from leaking into service/web layers.
 * Maps to HTTP 500 Internal Server Error.
 */
public class DataAccessException extends HcmsException {

    public DataAccessException(String message, Throwable cause) {
        super(message, "DATA_ACCESS_ERROR", cause);
    }

    public DataAccessException(String message) {
        super(message, "DATA_ACCESS_ERROR");
    }
}
