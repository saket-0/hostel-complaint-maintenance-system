package edu.vit.hcms.web.dto;

/**
 * Request payload for staff updating complaint status.
 */
public record UpdateStatusRequest(
        String status,
        String note
) {}
