package edu.vit.hcms.web.handler;

import edu.vit.hcms.core.exception.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler mapping custom exceptions to consistent JSON error responses.
 * Ensures no stack traces leak in production, while logging full details server-side.
 *
 * <p>Uniform error shape: {timestamp, status, error, message, path, details[]}
 */
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {}

    /** Registers all exception handlers on the Javalin app. */
    public static void register(Javalin app) {
        app.exception(ValidationException.class, (e, ctx) ->
                sendError(ctx, 422, "Unprocessable Entity", e.getMessage()));

        app.exception(ResourceNotFoundException.class, (e, ctx) ->
                sendError(ctx, 404, "Not Found", e.getMessage()));

        app.exception(InvalidStatusTransitionException.class, (e, ctx) ->
                sendError(ctx, 409, "Conflict", e.getMessage()));

        app.exception(DuplicateEntryException.class, (e, ctx) ->
                sendError(ctx, 409, "Conflict", e.getMessage()));

        app.exception(ConcurrentModificationException.class, (e, ctx) ->
                sendError(ctx, 409, "Conflict", e.getMessage()));

        app.exception(AuthenticationException.class, (e, ctx) ->
                sendError(ctx, 401, "Unauthorized", e.getMessage()));

        app.exception(AuthorizationException.class, (e, ctx) ->
                sendError(ctx, 403, "Forbidden", e.getMessage()));

        app.exception(DataAccessException.class, (e, ctx) -> {
            log.error("Data access error: {}", e.getMessage(), e);
            sendError(ctx, 500, "Internal Server Error", "A database error occurred");
        });

        app.exception(HcmsException.class, (e, ctx) -> {
            int code = "RATE_LIMITED".equals(e.getErrorCode()) ? 429 : 400;
            sendError(ctx, code, "Bad Request", e.getMessage());
        });

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception: {}", e.getMessage(), e);
            sendError(ctx, 500, "Internal Server Error",
                    "An unexpected error occurred. Please try again later.");
        });
    }

    /**
     * Sends a uniform JSON error response.
     */
    private static void sendError(Context ctx, int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", sanitize(message));
        body.put("path", ctx.path());
        body.put("details", List.of());

        ctx.status(status).json(body);
    }

    /** Sanitizes error messages to prevent XSS in JSON responses. */
    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
