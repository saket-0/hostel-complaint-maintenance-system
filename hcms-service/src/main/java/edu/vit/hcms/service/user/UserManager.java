package edu.vit.hcms.service.user;

import edu.vit.hcms.core.enums.Specialization;
import edu.vit.hcms.core.exception.ResourceNotFoundException;
import edu.vit.hcms.core.exception.ValidationException;
import edu.vit.hcms.core.model.Admin;
import edu.vit.hcms.core.model.MaintenanceStaff;
import edu.vit.hcms.core.model.Student;
import edu.vit.hcms.core.model.User;
import edu.vit.hcms.persistence.dao.UserDao;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Business service managing user profile updates, administrative user governance,
 * and staff availability/specialization modifications.
 */
public class UserManager {

    private static final Logger log = LoggerFactory.getLogger(UserManager.class);

    private final UserDao userDao;
    private final TransactionManager txManager;

    public UserManager(UserDao userDao) {
        this(userDao, null);
    }

    public UserManager(UserDao userDao, TransactionManager txManager) {
        this.userDao = userDao;
        this.txManager = txManager;
    }

    /**
     * Retrieves a user by ID with all role-specific metadata.
     *
     * @param userId unique identifier
     * @return User subclass instance
     * @throws ValidationException if userId is null
     * @throws ResourceNotFoundException if user is not found
     */
    public User getUserById(Long userId) {
        if (userId == null) {
            throw new ValidationException("User ID must not be null");
        }
        return userDao.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Updates self-service profile attributes for the authenticated user.
     * Enforces role boundary constraints (students cannot promote role or change email).
     *
     * @param userId         ID of the authenticated user
     * @param name           optional new display name
     * @param roomNumber     optional new room number (for Students)
     * @param hostelBlock    optional new hostel block (for Students)
     * @param available      optional new availability flag (for Staff)
     * @param specialization optional new specialization (for Staff)
     * @return updated User
     */
    public User updateProfile(Long userId, String name, String roomNumber, String hostelBlock,
                              Boolean available, Specialization specialization) {
        User user = getUserById(userId);

        if (name != null) {
            if (name.trim().isEmpty()) {
                throw new ValidationException("name", "Name must not be blank");
            }
            user.setName(name.trim());
        }

        if (user instanceof Student student) {
            if (roomNumber != null) {
                if (roomNumber.trim().isEmpty()) {
                    throw new ValidationException("roomNumber", "Room number must not be blank");
                }
                if (roomNumber.trim().length() > 10) {
                    throw new ValidationException("roomNumber", "Room number cannot exceed 10 characters");
                }
                student.setRoomNumber(roomNumber.trim());
            }
            if (hostelBlock != null) {
                if (hostelBlock.trim().isEmpty()) {
                    throw new ValidationException("hostelBlock", "Hostel block must not be blank");
                }
                student.setHostelBlock(hostelBlock.trim().toUpperCase());
            }
        } else if (user instanceof MaintenanceStaff staff) {
            if (available != null) {
                staff.setAvailable(available);
            }
            if (specialization != null) {
                staff.setSpecialization(specialization);
            }
        }

        runInTransaction(() -> userDao.update(user));
        log.info("Profile updated for user ID: {} ({})", userId, user.getRole());
        return user;
    }

    /**
     * Finds users matching role and search criteria with pagination for admin console.
     */
    public List<User> listUsers(String roleFilter, String searchQuery, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = (limit <= 0) ? 20 : Math.min(limit, 100);
        return userDao.findUsers(roleFilter, searchQuery, safeOffset, safeLimit);
    }

    /**
     * Returns total matching users for pagination metadata in admin console.
     */
    public long countUsers(String roleFilter, String searchQuery) {
        return userDao.countUsers(roleFilter, searchQuery);
    }

    /**
     * Admin override method allowing full modification of any user's profile and active status.
     *
     * @param userId         user identifier
     * @param name           optional updated name
     * @param active         optional updated active flag
     * @param roomNumber     optional updated room number (for Students)
     * @param hostelBlock    optional updated hostel block (for Students)
     * @param specialization optional updated specialization (for Staff)
     * @param available      optional updated availability (for Staff)
     * @param adminRole      optional updated administrative role title (for Admins)
     * @return updated User
     */
    public User adminUpdateUser(Long userId, String name, Boolean active, String roomNumber,
                                String hostelBlock, Specialization specialization,
                                Boolean available, String adminRole) {
        User user = getUserById(userId);

        if (name != null) {
            if (name.trim().isEmpty()) {
                throw new ValidationException("name", "Name must not be blank");
            }
            user.setName(name.trim());
        }

        if (active != null) {
            user.setActive(active);
        }

        if (user instanceof Student student) {
            if (roomNumber != null) {
                if (roomNumber.trim().isEmpty()) {
                    throw new ValidationException("roomNumber", "Room number must not be blank");
                }
                student.setRoomNumber(roomNumber.trim());
            }
            if (hostelBlock != null) {
                if (hostelBlock.trim().isEmpty()) {
                    throw new ValidationException("hostelBlock", "Hostel block must not be blank");
                }
                student.setHostelBlock(hostelBlock.trim().toUpperCase());
            }
        } else if (user instanceof MaintenanceStaff staff) {
            if (specialization != null) {
                staff.setSpecialization(specialization);
            }
            if (available != null) {
                staff.setAvailable(available);
            }
        } else if (user instanceof Admin admin) {
            if (adminRole != null) {
                if (adminRole.trim().isEmpty()) {
                    throw new ValidationException("adminRole", "Admin role must not be blank");
                }
                admin.setAdminRole(adminRole.trim());
            }
        }

        runInTransaction(() -> userDao.update(user));
        log.info("Admin updated user ID: {} ({})", userId, user.getRole());
        return user;
    }

    /**
     * Dedicated helper for updating staff specialization and availability.
     */
    public MaintenanceStaff updateStaff(Long staffId, Specialization specialization, Boolean available) {
        User user = getUserById(staffId);
        if (!(user instanceof MaintenanceStaff staff)) {
            throw new ValidationException("staffId", "User is not a maintenance staff member");
        }

        if (specialization != null) {
            staff.setSpecialization(specialization);
        }
        if (available != null) {
            staff.setAvailable(available);
        }

        runInTransaction(() -> userDao.update(staff));
        log.info("Staff record updated for ID: {}", staffId);
        return staff;
    }

    /**
     * Soft-deactivates a user account (preserves relational integrity on complaints and logs).
     *
     * @param userId user identifier to deactivate
     */
    public void deactivateUser(Long userId) {
        // Ensure user exists before deactivating
        getUserById(userId);
        runInTransaction(() -> userDao.deactivate(userId));
        log.info("User ID {} deactivated by admin", userId);
    }

    private void runInTransaction(Runnable action) {
        if (txManager != null) {
            txManager.begin();
            try {
                action.run();
                txManager.commit();
            } catch (Exception e) {
                txManager.rollback();
                throw e;
            }
        } else {
            action.run();
        }
    }
}
