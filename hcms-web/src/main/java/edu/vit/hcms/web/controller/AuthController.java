package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.service.auth.AuthService;
import edu.vit.hcms.web.dto.ChangePasswordRequest;
import edu.vit.hcms.web.dto.LoginRequest;
import edu.vit.hcms.web.dto.RegisterRequest;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for authentication endpoints: register, login, logout, me, change-password.
 * No business logic here - delegates to AuthService.
 */
public class AuthController {

    private final AuthService authService;
    private final UserDao userDao;

    public AuthController(AuthService authService, UserDao userDao) {
        this.authService = authService;
        this.userDao = userDao;
    }

    /** POST /api/v1/auth/register - Student registration. */
    public void register(Context ctx) {
        RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);
        Student student = authService.register(
                req.name(),
                req.email(),
                req.password(),
                req.roomNumber(),
                req.hostelBlock());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Registration successful");
        response.put("userId", student.getUserId());
        response.put("email", student.getEmail());
        ctx.status(201).json(response);
    }

    /** POST /api/v1/auth/login - Returns JWT token and user info. */
    public void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
        String token = authService.login(req.email(), req.password());
        User user = userDao.findByEmail(req.email().trim().toLowerCase()).orElseThrow();

        // Set HttpOnly cookie
        ctx.cookie("token", token, 86400);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("user", buildUserResponse(user));
        ctx.json(response);
    }

    /** POST /api/v1/auth/logout - Clears the token cookie. */
    public void logout(Context ctx) {
        ctx.removeCookie("token");
        ctx.json(Map.of("message", "Logged out successfully"));
    }

    /** GET /api/v1/auth/me - Returns current user info. */
    public void me(Context ctx) {
        Long userId = ctx.attribute("userId");
        User user = userDao.findById(userId).orElseThrow();
        ctx.json(buildUserResponse(user));
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.getUserId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        response.put("dashboard", user.getDashboardView());
        response.put("permissions", user.getPermissions());
        response.put("forcePasswordChange", user.isForcePasswordChange());

        // Role-specific fields via polymorphism
        if (user instanceof edu.vit.hcms.core.model.Student s) {
            response.put("roomNumber", s.getRoomNumber());
            response.put("hostelBlock", s.getHostelBlock());
            response.put("registrationNumber", s.getRegistrationNumber());
            response.put("phone", s.getPhone());
        } else if (user instanceof edu.vit.hcms.core.model.Admin a) {
            response.put("adminRole", a.getAdminRole());
        } else if (user instanceof edu.vit.hcms.core.model.MaintenanceStaff ms) {
            response.put("specialization", ms.getSpecialization().name());
            response.put("available", ms.isAvailable());
        }
        return response;
    }

    /** POST /api/v1/auth/change-password */
    public void changePassword(Context ctx) {
        Long userId = ctx.attribute("userId");
        ChangePasswordRequest req = ctx.bodyAsClass(ChangePasswordRequest.class);
        authService.changePassword(userId, req.currentPassword(), req.newPassword());
        ctx.json(Map.of("message", "Password changed successfully"));
    }
}
