package edu.vit.hcms.web.dto;

/**
 * Request payload for administrator updates to any user account.
 */
public record AdminUpdateUserRequest(
        String name,
        Boolean active,
        String specialization,
        Boolean available,
        String roomNumber,
        String hostelBlock,
        String adminRole
) {}
