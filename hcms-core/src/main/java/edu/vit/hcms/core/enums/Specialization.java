package edu.vit.hcms.core.enums;

/**
 * Maintenance staff specializations.
 * Maps to {@link Category} to enable specialization-based staff assignment.
 */
public enum Specialization {
    ELECTRICAL("Electrical"),
    PLUMBING("Plumbing"),
    NETWORK("Network / IT"),
    HVAC("HVAC / AC"),
    CARPENTRY("Carpentry"),
    CLEANING("Cleaning"),
    GENERAL("General Maintenance");

    private final String displayName;

    Specialization(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label. */
    public String getDisplayName() {
        return displayName;
    }

    /** Role title for staff display (e.g. Electrician, Plumber). */
    public String getRoleTitle() {
        return switch (this) {
            case ELECTRICAL -> "Electrician";
            case PLUMBING -> "Plumber";
            case NETWORK -> "Network Specialist";
            case HVAC -> "HVAC Technician";
            case CARPENTRY -> "Carpenter";
            case CLEANING -> "Cleaner";
            case GENERAL -> "Maintenance Staff";
        };
    }
}
