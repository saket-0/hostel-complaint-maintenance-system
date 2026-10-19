package edu.vit.hcms.web.dto;

/**
 * Request payload for student feedback.
 */
public record FeedbackRequest(
        int rating,
        String comment
) {}
