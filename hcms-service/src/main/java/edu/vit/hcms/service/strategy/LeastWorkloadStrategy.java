package edu.vit.hcms.service.strategy;

import edu.vit.hcms.core.interfaces.AssignmentStrategy;
import edu.vit.hcms.core.model.Complaint;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.persistence.dao.UserDao;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern implementation: assigns to the staff member with the fewest active complaints.
 * Acts as a load-balancing fallback when specialization match doesn't find a result.
 */
public class LeastWorkloadStrategy implements AssignmentStrategy {

    private final UserDao userDao;

    public LeastWorkloadStrategy(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Optional<MaintenanceStaff> selectStaff(Complaint complaint, List<MaintenanceStaff> availableStaff) {
        return availableStaff.stream()
                .filter(MaintenanceStaff::isAvailable)
                .min(Comparator.comparingInt(s -> userDao.getActiveComplaintCount(s.getUserId())));
    }

    @Override
    public String getStrategyName() {
        return "LeastWorkload";
    }
}
