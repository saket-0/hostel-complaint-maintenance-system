package edu.vit.hcms.web.dto;

/**
 * Request payload for self-service user profile updates.
 */
public record UpdateProfileRequest(
        String name,
        String roomNumber,
        String hostelBlock,
        Boolean available,
        String specialization
) {
    public UpdateProfileRequest(String name, String roomNumber, String hostelBlock, Boolean available) {
        this(name, roomNumber, hostelBlock, available, null);
    }
}
