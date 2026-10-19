package edu.vit.hcms.service.strategy;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.persistence.dao.UserDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Staff Assignment Strategy Tests")
class StrategyTest {

    @Mock
    private UserDao userDao;

    @Test
    @DisplayName("SpecializationMatchStrategy matches staff with required specialization")
    void testSpecializationMatch() {
        SpecializationMatchStrategy strategy = new SpecializationMatchStrategy();
        assertEquals("SpecializationMatch", strategy.getStrategyName());

        Complaint electricalComplaint = Complaint.builder()
                .studentId(101L)
                .category(Category.ELECTRICAL)
                .priority(Priority.HIGH)
                .description("Power failure in room 201")
                .build();

        MaintenanceStaff electrician = new MaintenanceStaff(1L, "Ramesh", "ramesh@vit.ac.in", "hash", Specialization.ELECTRICAL, true);
        MaintenanceStaff plumber = new MaintenanceStaff(2L, "Suresh", "suresh@vit.ac.in", "hash", Specialization.PLUMBING, true);

        Optional<MaintenanceStaff> match = strategy.selectStaff(electricalComplaint, List.of(plumber, electrician));
        assertTrue(match.isPresent());
        assertEquals(1L, match.get().getUserId());

        // When no matching specialization exists
        Complaint wifiComplaint = Complaint.builder()
                .studentId(101L)
                .category(Category.WIFI)
                .priority(Priority.LOW)
                .description("No internet in hostel room")
                .build();

        Optional<MaintenanceStaff> noMatch = strategy.selectStaff(wifiComplaint, List.of(plumber, electrician));
        assertFalse(noMatch.isPresent());
    }

    @Test
    @DisplayName("LeastWorkloadStrategy selects available staff with lowest active workload")
    void testLeastWorkload() {
        when(userDao.getActiveComplaintCount(1L)).thenReturn(5);
        when(userDao.getActiveComplaintCount(2L)).thenReturn(1);
        when(userDao.getActiveComplaintCount(3L)).thenReturn(3);

        LeastWorkloadStrategy strategy = new LeastWorkloadStrategy(userDao);
        assertEquals("LeastWorkload", strategy.getStrategyName());

        MaintenanceStaff s1 = new MaintenanceStaff(1L, "Staff 1", "s1@vit.ac.in", "hash", Specialization.GENERAL, true);
        MaintenanceStaff s2 = new MaintenanceStaff(2L, "Staff 2", "s2@vit.ac.in", "hash", Specialization.GENERAL, true);
        MaintenanceStaff s3 = new MaintenanceStaff(3L, "Staff 3", "s3@vit.ac.in", "hash", Specialization.GENERAL, true);

        Complaint complaint = Complaint.builder()
                .studentId(101L)
                .category(Category.OTHER)
                .description("General maintenance request")
                .build();
        Optional<MaintenanceStaff> selected = strategy.selectStaff(complaint, List.of(s1, s2, s3));

        assertTrue(selected.isPresent());
        assertEquals(2L, selected.get().getUserId(), "Staff with 1 active task should be selected");
    }
}
