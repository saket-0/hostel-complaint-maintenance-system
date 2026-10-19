package edu.vit.hcms.web.dto;

/**
 * Request payload for reopening a resolved complaint.
 */
public record ReopenRequest(
        String reason
) {}
