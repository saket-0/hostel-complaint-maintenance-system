package edu.vit.hcms.web.dto;

/**
 * Request payload for changing user password.
 */
public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {}
