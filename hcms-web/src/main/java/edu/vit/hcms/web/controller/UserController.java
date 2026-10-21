package edu.vit.hcms.web.controller;

import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import edu.vit.hcms.service.user.UserManager;
import edu.vit.hcms.web.dto.UpdateProfileRequest;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller handling user self-service profile inspection and updates.
 */
public class UserController {

    private final UserManager userManager;

    public UserController(UserManager userManager) {
        this.userManager = userManager;
    }

    /**
     * GET /api/v1/users/profile
     * Returns the full profile of the authenticated user.
     */
    public void getProfile(Context ctx) {
        Long userId = ctx.attribute("userId");
        User user = userManager.getUserById(userId);
        ctx.json(toUserMap(user));
    }

    /**
     * PUT /api/v1/users/profile
     * Updates self-service attributes for the authenticated user.
     */
    public void updateProfile(Context ctx) {
        Long userId = ctx.attribute("userId");
        UpdateProfileRequest req = ctx.bodyAsClass(UpdateProfileRequest.class);

        Specialization spec = null;
        if (req.specialization() != null && !req.specialization().isBlank()) {
            try {
                spec = Specialization.valueOf(req.specialization().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("specialization", "Invalid specialization: " + req.specialization());
            }
        }

        User updated = userManager.updateProfile(
                userId,
                req.name(),
                req.roomNumber(),
                req.hostelBlock(),
                req.available(),
                spec
        );

        ctx.json(Map.of(
                "message", "Profile updated successfully",
                "user", toUserMap(updated)
        ));
    }

    /**
     * Serializes any User subclass into a comprehensive JSON map including role-specific attributes.
     */
    public static Map<String, Object> toUserMap(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", user.getUserId());
        m.put("email", user.getEmail());
        m.put("name", user.getName());
        m.put("role", user.getRole().name());
        m.put("active", user.isActive());
        m.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        m.put("dashboard", user.getDashboardView());
        m.put("permissions", user.getPermissions());

        if (user instanceof Student s) {
            m.put("roomNumber", s.getRoomNumber());
            m.put("hostelBlock", s.getHostelBlock());
        } else if (user instanceof MaintenanceStaff ms) {
            m.put("specialization", ms.getSpecialization() != null ? ms.getSpecialization().name() : null);
            m.put("specializationDisplay", ms.getSpecialization() != null ? ms.getSpecialization().getDisplayName() : null);
            m.put("available", ms.isAvailable());
        } else if (user instanceof Admin a) {
            m.put("adminRole", a.getAdminRole());
        }
        return m;
    }
}
