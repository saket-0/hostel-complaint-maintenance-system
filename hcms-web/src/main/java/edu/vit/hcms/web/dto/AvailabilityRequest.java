package edu.vit.hcms.web.dto;

/**
 * Request payload for toggling maintenance staff availability.
 */
public record AvailabilityRequest(
        boolean available
) {}
