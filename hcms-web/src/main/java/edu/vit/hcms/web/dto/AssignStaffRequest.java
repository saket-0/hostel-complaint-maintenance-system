package edu.vit.hcms.web.dto;

/**
 * Request payload for assigning staff to a complaint.
 */
public record AssignStaffRequest(
        Long staffId
) {}
