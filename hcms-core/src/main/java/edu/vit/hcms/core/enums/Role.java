package edu.vit.hcms.core.enums;

/**
 * Represents user roles in the HCMS system.
 * Each role maps to a specific set of permissions and dashboard views.
 */
public enum Role {
    STUDENT("Student"),
    ADMIN("Administrator"),
    STAFF("Maintenance Staff");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    /** Returns a human-readable name for UI display. */
    public String getDisplayName() {
        return displayName;
    }
}
