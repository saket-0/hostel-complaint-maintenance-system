package edu.vit.hcms.web.filter;

import edu.vit.hcms.core.exception.HcmsException;
import io.javalin.http.Context;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter using a sliding window.
 * Tracks request timestamps per client IP + action, rejects with 429 if limit exceeded.
 *
 * <p>Demonstrates ConcurrentHashMap and Deque (recent activity tracking).
 */
public class RateLimitFilter {

    // ConcurrentHashMap for thread-safe per-client tracking
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000; // 1 minute window

    /**
     * Checks if the current request exceeds the rate limit.
     *
     * @param ctx    the Javalin context
     * @param action the action being rate-limited (e.g., "login", "complaint")
     * @param maxRequests maximum requests per minute
     * @throws HcmsException if rate limit is exceeded
     */
    public void checkLimit(Context ctx, String action, int maxRequests) {
        String clientKey = ctx.ip() + ":" + action;
        long now = System.currentTimeMillis();

        // Deque tracks recent request timestamps (LinkedList implements Deque)
        Deque<Long> timestamps = requestLog.computeIfAbsent(clientKey, k -> new LinkedList<>());

        synchronized (timestamps) {
            // Remove timestamps outside the window
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                throw new HcmsException("Rate limit exceeded. Try again later.", "RATE_LIMITED");
            }

            timestamps.addLast(now);
        }
    }
}
