package edu.vit.hcms.web.dto;

/**
 * Request payload for lodging a complaint.
 */
public record CreateComplaintRequest(
        String category,
        String description,
        String suggestedPriority
) {}
