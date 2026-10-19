package edu.vit.hcms.core.enums;

/**
 * Complaint priority levels with associated SLA hours.
 * Higher urgency means a lower numeric value (HIGH=1 sorts first in a min-heap).
 * SLA hours are configurable at runtime via the config holder, but defaults live here.
 */
public enum Priority {
    HIGH(1, 24, "High"),
    MEDIUM(2, 72, "Medium"),
    LOW(3, 168, "Low");

    private final int level;
    private final int defaultSlaHours;
    private final String displayName;

    Priority(int level, int defaultSlaHours, String displayName) {
        this.level = level;
        this.defaultSlaHours = defaultSlaHours;
        this.displayName = displayName;
    }

    /** Numeric urgency level (1 = most urgent). Used for PriorityQueue ordering. */
    public int getLevel() {
        return level;
    }

    /** Default SLA resolution window in hours. */
    public int getDefaultSlaHours() {
        return defaultSlaHours;
    }

    /** Human-readable label. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Converts an integer level to the corresponding Priority enum constant.
     *
     * @param level 1, 2, or 3
     * @return the matching Priority
     * @throws IllegalArgumentException if level is not 1, 2, or 3
     */
    public static Priority fromLevel(int level) {
        for (Priority p : values()) {
            if (p.level == level) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown priority level: " + level);
    }
}
