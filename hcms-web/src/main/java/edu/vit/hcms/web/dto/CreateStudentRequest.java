package edu.vit.hcms.web.dto;

/**
 * Request payload for creating a new student account by admin.
 */
public record CreateStudentRequest(
        String name,
        String email,
        String registrationNumber,
        String hostelBlock,
        String roomNumber,
        String phone,
        String password
) {}
