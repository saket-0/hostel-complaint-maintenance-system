package edu.vit.hcms.web.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import edu.vit.hcms.core.exception.AuthenticationException;
import edu.vit.hcms.core.exception.AuthorizationException;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.auth.AuthService;
import io.javalin.http.Context;

/**
 * Authentication and authorization filter for Javalin routes.
 * Extracts JWT from Authorization header or cookie, validates it,
 * and sets the current user in the request context.
 */
public class AuthFilter {

    private final AuthService authService;
    private final UserDao userDao;

    public AuthFilter(AuthService authService, UserDao userDao) {
        this.authService = authService;
        this.userDao = userDao;
    }

    /**
     * Requires authentication. Extracts and validates the JWT token,
     * sets userId and role attributes on the context.
     */
    public void requireAuth(Context ctx) {
        String token = extractToken(ctx);
        if (token == null) {
            throw new AuthenticationException("Authentication required");
        }

        DecodedJWT decoded = authService.validateToken(token);
        Long userId = Long.parseLong(decoded.getSubject());
        userDao.findById(userId).ifPresent(u -> {
            if (!u.isActive()) {
                throw new AuthenticationException("Account is deactivated");
            }
        });
        String role = decoded.getClaim("role").asString();

        ctx.attribute("userId", userId);
        ctx.attribute("role", role);
        ctx.attribute("email", decoded.getClaim("email").asString());
        ctx.attribute("name", decoded.getClaim("name").asString());
    }

    /**
     * Requires a specific role. Calls requireAuth first,
     * then checks the role matches.
     */
    public void requireRole(Context ctx, String requiredRole) {
        requireAuth(ctx);
        String userRole = ctx.attribute("role");
        if (!requiredRole.equals(userRole)) {
            throw new AuthorizationException(
                    "Access denied. Required role: " + requiredRole);
        }
    }

    /**
     * Extracts JWT token from Authorization header (Bearer scheme)
     * or from an HttpOnly cookie named "token".
     */
    private String extractToken(Context ctx) {
        // Try Authorization header first
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Fall back to cookie
        String cookieToken = ctx.cookie("token");
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }

        return null;
    }
}
