package edu.vit.hcms.core.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Complaint lifecycle status with a built-in state machine.
 * {@link #canTransitionTo(ComplaintStatus)} enforces legal status transitions,
 * preventing invalid state changes such as moving from CLOSED to IN_PROGRESS.
 *
 * <p>State machine diagram:
 * <pre>
 * NEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED
 *                                   RESOLVED -> REOPENED -> ASSIGNED
 * </pre>
 */
public enum ComplaintStatus {
    NEW("New", "#6B7280") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.of(ASSIGNED);
        }
    },
    ASSIGNED("Assigned", "#0B1F4A") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.of(IN_PROGRESS);
        }
    },
    IN_PROGRESS("In Progress", "#E8960C") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.of(RESOLVED);
        }
    },
    RESOLVED("Resolved", "#1B7A6B") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.of(CLOSED, REOPENED);
        }
    },
    CLOSED("Closed", "#334155") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.noneOf(ComplaintStatus.class);
        }
    },
    REOPENED("Reopened", "#DC2626") {
        @Override
        protected Set<ComplaintStatus> allowedTransitions() {
            return EnumSet.of(ASSIGNED);
        }
    };

    private final String displayName;
    private final String colorHex;

    ComplaintStatus(String displayName, String colorHex) {
        this.displayName = displayName;
        this.colorHex = colorHex;
    }

    /**
     * Returns the set of statuses this status can legally transition to.
     * Subclasses (enum constants) override this to define the state machine.
     */
    protected abstract Set<ComplaintStatus> allowedTransitions();

    /**
     * Checks whether transitioning from this status to the target is legal.
     *
     * @param target the desired next status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(ComplaintStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Human-readable status label for the UI. */
    public String getDisplayName() {
        return displayName;
    }

    /** Hex color code for status badges. */
    public String getColorHex() {
        return colorHex;
    }
}
