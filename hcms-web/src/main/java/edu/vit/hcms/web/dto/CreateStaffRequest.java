package edu.vit.hcms.web.dto;

/**
 * Request payload for creating new maintenance staff.
 */
public record CreateStaffRequest(
        String name,
        String email,
        String password,
        String specialization
) {}
