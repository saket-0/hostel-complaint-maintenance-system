package edu.vit.hcms.web.dto;

/**
 * Request payload for administrator updates to maintenance staff specialization and availability.
 */
public record UpdateStaffRequest(
        String specialization,
        Boolean available
) {}
