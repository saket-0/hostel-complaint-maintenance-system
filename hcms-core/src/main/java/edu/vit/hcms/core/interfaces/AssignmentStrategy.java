package edu.vit.hcms.core.interfaces;

import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.MaintenanceStaff;

import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern interface for staff assignment algorithms.
 * Different implementations provide different assignment logic:
 * <ul>
 *   <li>SpecializationMatch - matches staff by complaint category specialization</li>
 *   <li>LeastWorkload - assigns to the staff with the fewest active complaints</li>
 * </ul>
 */
public interface AssignmentStrategy {

    /**
     * Selects the best staff member to handle the given complaint.
     *
     * @param complaint      the complaint to assign
     * @param availableStaff list of currently available staff
     * @return the selected staff member, or empty if no suitable match
     */
    Optional<MaintenanceStaff> selectStaff(Complaint complaint, List<MaintenanceStaff> availableStaff);

    /**
     * Returns the name of this strategy for logging and UI display.
     */
    String getStrategyName();
}
