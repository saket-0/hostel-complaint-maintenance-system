package edu.vit.hcms.core.enums;

/**
 * Complaint categories mapped to staff specializations.
 * Each category defines which {@link Specialization} is required to handle it,
 * allowing the auto-assignment strategy to match staff appropriately.
 */
public enum Category {
    ELECTRICAL("Electrical", Specialization.ELECTRICAL),
    PLUMBING("Plumbing", Specialization.PLUMBING),
    WIFI("Wi-Fi / Network", Specialization.NETWORK),
    AC("Air Conditioning", Specialization.HVAC),
    CARPENTRY("Carpentry", Specialization.CARPENTRY),
    CLEANING("Cleaning / Sanitation", Specialization.CLEANING),
    OTHER("Other", Specialization.GENERAL);

    private final String displayName;
    private final Specialization requiredSpecialization;

    Category(String displayName, Specialization requiredSpecialization) {
        this.displayName = displayName;
        this.requiredSpecialization = requiredSpecialization;
    }

    /** Human-readable label for the UI. */
    public String getDisplayName() {
        return displayName;
    }

    /** The staff specialization needed to handle complaints of this category. */
    public Specialization getRequiredSpecialization() {
        return requiredSpecialization;
    }
}
