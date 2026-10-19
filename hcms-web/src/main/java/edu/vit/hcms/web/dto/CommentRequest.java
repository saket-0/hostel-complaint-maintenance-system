package edu.vit.hcms.web.dto;

/**
 * Request payload for posting a comment on a complaint.
 */
public record CommentRequest(
        String message
) {}
