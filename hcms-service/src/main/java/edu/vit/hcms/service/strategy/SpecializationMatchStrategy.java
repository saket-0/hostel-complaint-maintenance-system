package edu.vit.hcms.service.strategy;

import edu.vit.hcms.core.interfaces.AssignmentStrategy;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.MaintenanceStaff;

import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern implementation: assigns staff based on specialization match.
 * Matches the complaint's category to a staff member's specialization.
 */
public class SpecializationMatchStrategy implements AssignmentStrategy {

    @Override
    public Optional<MaintenanceStaff> selectStaff(Complaint complaint, List<MaintenanceStaff> availableStaff) {
        return availableStaff.stream()
                .filter(staff -> staff.getSpecialization() == complaint.getCategory().getRequiredSpecialization())
                .filter(MaintenanceStaff::isAvailable)
                .findFirst();
    }

    @Override
    public String getStrategyName() {
        return "SpecializationMatch";
    }
}
