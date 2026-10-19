package edu.vit.hcms.web.dto;

/**
 * Request payload for user authentication.
 */
public record LoginRequest(
        String email,
        String password
) {}
