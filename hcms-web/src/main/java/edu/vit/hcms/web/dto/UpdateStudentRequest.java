package edu.vit.hcms.web.dto;

/**
 * Request payload for updating student profile details by admin.
 */
public record UpdateStudentRequest(
        String name,
        String email,
        String registrationNumber,
        String hostelBlock,
        String roomNumber,
        String phone
) {}
