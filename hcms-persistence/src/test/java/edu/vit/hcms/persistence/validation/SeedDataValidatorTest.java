package edu.vit.hcms.persistence.validation;

import edu.vit.hcms.persistence.validation.SeedDataValidator.ComplaintRow;
import edu.vit.hcms.persistence.validation.SeedDataValidator.HistoryRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedDataValidatorTest {

    private final LocalDateTime now = LocalDateTime.now();

    @Test
    @DisplayName("Valid complaint timestamps pass validation")
    void validTimestampsPass() {
        LocalDateTime created = now.minusDays(5);
        LocalDateTime updated = now.minusDays(1);
        LocalDateTime resolved = now.minusDays(1);

        ComplaintRow row = new ComplaintRow("HCM-TEST-001", 100L, 200L, "RESOLVED", created, updated, resolved);
        assertDoesNotThrow(() -> SeedDataValidator.validateComplaintTimestamps(row));
    }

    @Test
    @DisplayName("Fail if created_date is after updated_date")
    void failIfCreatedAfterUpdated() {
        LocalDateTime created = now.minusDays(1);
        LocalDateTime updated = now.minusDays(5);

        ComplaintRow row = new ComplaintRow("HCM-TEST-002", 100L, null, "NEW", created, updated, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SeedDataValidator.validateComplaintTimestamps(row));
        assertTrue(ex.getMessage().contains("created_date"));
        assertTrue(ex.getMessage().contains("is after updated_date"));
    }

    @Test
    @DisplayName("Fail if updated_date is after resolved_date")
    void failIfUpdatedAfterResolved() {
        LocalDateTime created = now.minusDays(5);
        LocalDateTime updated = now.minusDays(1);
        LocalDateTime resolved = now.minusDays(2);

        ComplaintRow row = new ComplaintRow("HCM-TEST-003", 100L, 200L, "RESOLVED", created, updated, resolved);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SeedDataValidator.validateComplaintTimestamps(row));
        assertTrue(ex.getMessage().contains("updated_date"));
        assertTrue(ex.getMessage().contains("is after resolved_date"));
    }

    @Test
    @DisplayName("Fail if history changed_at is before complaint created_date")
    void failIfHistoryBeforeCreated() {
        LocalDateTime created = now.minusDays(2);
        LocalDateTime updated = now.minusDays(1);
        ComplaintRow row = new ComplaintRow("HCM-TEST-004", 100L, null, "NEW", created, updated, null);

        List<HistoryRow> history = List.of(
                new HistoryRow(1L, "HCM-TEST-004", null, "NEW", 100L, "Note", created.minusHours(1))
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SeedDataValidator.validateComplaintHistory(row, history));
        assertTrue(ex.getMessage().contains("created_date"));
        assertTrue(ex.getMessage().contains("is after history changed_at"));
    }

    @Test
    @DisplayName("Fail if history changed_at is after complaint updated_date")
    void failIfHistoryAfterUpdated() {
        LocalDateTime created = now.minusDays(2);
        LocalDateTime updated = now.minusDays(1);
        ComplaintRow row = new ComplaintRow("HCM-TEST-005", 100L, null, "NEW", created, updated, null);

        List<HistoryRow> history = List.of(
                new HistoryRow(1L, "HCM-TEST-005", null, "NEW", 100L, "Note", updated.plusMinutes(5))
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SeedDataValidator.validateComplaintHistory(row, history));
        assertTrue(ex.getMessage().contains("history changed_at"));
        assertTrue(ex.getMessage().contains("is after updated_date"));
    }

    @Test
    @DisplayName("Valid lifecycle progression and actors for RESOLVED complaint passes")
    void validLifecycleProgressionPasses() {
        LocalDateTime created = now.minusDays(3);
        LocalDateTime assigned = created.plusHours(1);
        LocalDateTime inProgress = created.plusHours(2);
        LocalDateTime resolved = created.plusHours(5);
        ComplaintRow row = new ComplaintRow("HCM-TEST-006", 100L, 200L, "RESOLVED", created, resolved, resolved);

        List<HistoryRow> history = List.of(
                new HistoryRow(1L, "HCM-TEST-006", null, "NEW", 100L, "Registered", created),
                new HistoryRow(2L, "HCM-TEST-006", "NEW", "ASSIGNED", 999L, "Assigned to staff", assigned),
                new HistoryRow(3L, "HCM-TEST-006", "ASSIGNED", "IN_PROGRESS", 200L, "Accepted", inProgress),
                new HistoryRow(4L, "HCM-TEST-006", "IN_PROGRESS", "RESOLVED", 200L, "Resolved", resolved)
        );

        assertDoesNotThrow(() -> SeedDataValidator.validateComplaintHistory(row, history));
    }

    @Test
    @DisplayName("Fail if IN_PROGRESS history is not by assigned staff")
    void failIfInProgressNotByStaff() {
        LocalDateTime created = now.minusDays(3);
        LocalDateTime assigned = created.plusHours(1);
        LocalDateTime inProgress = created.plusHours(2);
        ComplaintRow row = new ComplaintRow("HCM-TEST-007", 100L, 200L, "IN_PROGRESS", created, inProgress, null);

        List<HistoryRow> history = List.of(
                new HistoryRow(1L, "HCM-TEST-007", null, "NEW", 100L, "Registered", created),
                new HistoryRow(2L, "HCM-TEST-007", "NEW", "ASSIGNED", 999L, "Assigned to staff", assigned),
                new HistoryRow(3L, "HCM-TEST-007", "ASSIGNED", "IN_PROGRESS", 300L, "Accepted by wrong staff", inProgress)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SeedDataValidator.validateComplaintHistory(row, history));
        assertTrue(ex.getMessage().contains("must be changed by assigned staff"));
    }
}
