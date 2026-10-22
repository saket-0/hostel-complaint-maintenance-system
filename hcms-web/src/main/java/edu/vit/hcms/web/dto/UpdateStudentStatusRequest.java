package edu.vit.hcms.web.dto;

/**
 * Request payload for toggling student active status (soft-delete / reactivation).
 */
public record UpdateStudentStatusRequest(
        Boolean active,
        String status
) {}
