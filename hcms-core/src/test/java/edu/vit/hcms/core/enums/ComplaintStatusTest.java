package edu.vit.hcms.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ComplaintStatus State Machine Tests")
class ComplaintStatusTest {

    @Test
    @DisplayName("NEW can only transition to ASSIGNED")
    void testNewTransitions() {
        assertTrue(ComplaintStatus.NEW.canTransitionTo(ComplaintStatus.ASSIGNED));
        assertFalse(ComplaintStatus.NEW.canTransitionTo(ComplaintStatus.IN_PROGRESS));
        assertFalse(ComplaintStatus.NEW.canTransitionTo(ComplaintStatus.RESOLVED));
        assertFalse(ComplaintStatus.NEW.canTransitionTo(ComplaintStatus.CLOSED));
        assertFalse(ComplaintStatus.NEW.canTransitionTo(ComplaintStatus.REOPENED));
    }

    @Test
    @DisplayName("ASSIGNED can transition to IN_PROGRESS or ASSIGNED (reassign)")
    void testAssignedTransitions() {
        assertTrue(ComplaintStatus.ASSIGNED.canTransitionTo(ComplaintStatus.IN_PROGRESS));
        assertTrue(ComplaintStatus.ASSIGNED.canTransitionTo(ComplaintStatus.ASSIGNED));
        assertFalse(ComplaintStatus.ASSIGNED.canTransitionTo(ComplaintStatus.NEW));
        assertFalse(ComplaintStatus.ASSIGNED.canTransitionTo(ComplaintStatus.RESOLVED));
        assertFalse(ComplaintStatus.ASSIGNED.canTransitionTo(ComplaintStatus.CLOSED));
    }

    @Test
    @DisplayName("IN_PROGRESS can transition to RESOLVED or ASSIGNED (reassign)")
    void testInProgressTransitions() {
        assertTrue(ComplaintStatus.IN_PROGRESS.canTransitionTo(ComplaintStatus.RESOLVED));
        assertTrue(ComplaintStatus.IN_PROGRESS.canTransitionTo(ComplaintStatus.ASSIGNED));
        assertFalse(ComplaintStatus.IN_PROGRESS.canTransitionTo(ComplaintStatus.NEW));
        assertFalse(ComplaintStatus.IN_PROGRESS.canTransitionTo(ComplaintStatus.CLOSED));
    }

    @Test
    @DisplayName("RESOLVED can transition to CLOSED or REOPENED")
    void testResolvedTransitions() {
        assertTrue(ComplaintStatus.RESOLVED.canTransitionTo(ComplaintStatus.CLOSED));
        assertTrue(ComplaintStatus.RESOLVED.canTransitionTo(ComplaintStatus.REOPENED));
        assertFalse(ComplaintStatus.RESOLVED.canTransitionTo(ComplaintStatus.NEW));
        assertFalse(ComplaintStatus.RESOLVED.canTransitionTo(ComplaintStatus.IN_PROGRESS));
    }

    @Test
    @DisplayName("CLOSED cannot transition to any status (terminal state)")
    void testClosedTransitions() {
        for (ComplaintStatus status : ComplaintStatus.values()) {
            assertFalse(ComplaintStatus.CLOSED.canTransitionTo(status),
                    "CLOSED should not be able to transition to " + status);
        }
    }

    @Test
    @DisplayName("REOPENED can transition to ASSIGNED or RESOLVED")
    void testReopenedTransitions() {
        assertTrue(ComplaintStatus.REOPENED.canTransitionTo(ComplaintStatus.ASSIGNED));
        assertTrue(ComplaintStatus.REOPENED.canTransitionTo(ComplaintStatus.RESOLVED));
        assertFalse(ComplaintStatus.REOPENED.canTransitionTo(ComplaintStatus.IN_PROGRESS));
        assertFalse(ComplaintStatus.REOPENED.canTransitionTo(ComplaintStatus.CLOSED));
    }

    @Test
    @DisplayName("Display names and color hex codes are populated")
    void testMetadata() {
        for (ComplaintStatus status : ComplaintStatus.values()) {
            assertNotNull(status.getDisplayName());
            assertNotNull(status.getColorHex());
            assertTrue(status.getColorHex().startsWith("#"));
        }
    }
}
