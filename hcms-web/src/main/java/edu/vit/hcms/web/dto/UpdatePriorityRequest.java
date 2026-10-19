package edu.vit.hcms.web.dto;

/**
 * Request payload for updating complaint priority.
 */
public record UpdatePriorityRequest(
        int priority
) {}
