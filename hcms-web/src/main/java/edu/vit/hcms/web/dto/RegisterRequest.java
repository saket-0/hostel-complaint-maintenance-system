package edu.vit.hcms.web.dto;

/**
 * Request payload for student registration.
 */
public record RegisterRequest(
        String name,
        String email,
        String password,
        String roomNumber,
        String hostelBlock
) {}
