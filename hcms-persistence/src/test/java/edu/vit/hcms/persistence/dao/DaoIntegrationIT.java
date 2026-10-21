package edu.vit.hcms.persistence.dao;

import edu.vit.hcms.core.enums.Category;
import edu.vit.hcms.core.enums.ComplaintStatus;
import edu.vit.hcms.core.enums.Priority;
import edu.vit.hcms.core.exception.ConcurrentModificationException;
import edu.vit.hcms.core.model.*;
import edu.vit.hcms.persistence.db.DatabaseManager;
import edu.vit.hcms.persistence.db.TransactionManager;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JDBC DAOs (UserDao, ComplaintDao, SupportDao) against
 * a real MySQL 8 Testcontainer.
 * Executes during integration-test phase (maven-failsafe-plugin).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("DAO Integration Tests with MySQL Testcontainer")
class DaoIntegrationIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("hcms_test")
            .withUsername("testuser")
            .withPassword("testpass");

    private static DatabaseManager dbManager;
    private static TransactionManager txManager;
    private static UserDao userDao;
    private static ComplaintDao complaintDao;
    private static SupportDao supportDao;

    @BeforeAll
    static void setUpAll() {
        String envDbUrl = System.getenv("DB_URL");
        if (envDbUrl != null && !envDbUrl.isBlank()) {
            String user = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "hcms";
            String pass = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "hcmspass";
            dbManager = new DatabaseManager(envDbUrl, user, pass);
        } else {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Docker is not running or available. Skipping MySQL Testcontainers tests.");
            if (!MYSQL.isRunning()) {
                MYSQL.start();
            }
            dbManager = new DatabaseManager(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }

        txManager = new TransactionManager(dbManager.getDataSource());
        userDao = new UserDao(txManager);
        complaintDao = new ComplaintDao(txManager);
        supportDao = new SupportDao(txManager);
    }

    @AfterAll
    static void tearDownAll() {
        if (dbManager != null) {
            dbManager.close();
        }
        if (MYSQL.isRunning()) {
            MYSQL.stop();
        }
    }

    @Test
    @DisplayName("UserDao finds seeded admin and staff")
    void testFindSeededUsers() {
        Optional<User> adminOpt = userDao.findByEmail("admin@vit.ac.in");
        assertTrue(adminOpt.isPresent(), "Seeded admin must be found");
        assertTrue(adminOpt.get() instanceof Admin);

        List<MaintenanceStaff> staffList = userDao.findAllStaff(true);
        assertFalse(staffList.isEmpty(), "Seeded staff must be present and available");
    }

    @Test
    @DisplayName("ComplaintDao saves, retrieves, and updates complaint")
    void testComplaintLifecycle() {
        String complaintId = complaintDao.generateComplaintId();
        assertNotNull(complaintId);
        assertTrue(complaintId.startsWith("HCM-"));

        Complaint complaint = Complaint.builder()
                .complaintId(complaintId)
                .studentId(6L) // Seeded student Priya Sharma
                .category(Category.ELECTRICAL)
                .description("Ceiling fan making clicking sound in room 301")
                .priority(Priority.HIGH)
                .status(ComplaintStatus.NEW)
                .dueBy(LocalDateTime.now().plusHours(24))
                .build();

        complaintDao.save(complaint);

        Optional<Complaint> found = complaintDao.findById(complaintId);
        assertTrue(found.isPresent());
        assertEquals(ComplaintStatus.NEW, found.get().getStatus());
        assertEquals(Priority.HIGH, found.get().getPriority());
        assertEquals("Priya Sharma", found.get().getStudentName());
        assertEquals("priya.sharma@vitstudent.ac.in", found.get().getStudentEmail());
        assertEquals("101", found.get().getStudentRoomNumber());
        assertEquals("A", found.get().getStudentHostelBlock());

        // Update status to ASSIGNED
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        complaint.setStaffId(2L); // Seeded electrical staff
        complaintDao.update(complaint);

        Optional<Complaint> updated = complaintDao.findById(complaintId);
        assertTrue(updated.isPresent());
        assertEquals(ComplaintStatus.ASSIGNED, updated.get().getStatus());
        assertEquals(2L, updated.get().getStaffId());
        assertEquals(2, updated.get().getVersion(), "Version should increment for optimistic locking");
    }

    @Test
    @DisplayName("Optimistic locking throws ConcurrentModificationException on stale version")
    void testOptimisticLocking() {
        String complaintId = complaintDao.generateComplaintId();
        Complaint c1 = Complaint.builder()
                .complaintId(complaintId)
                .studentId(6L)
                .category(Category.PLUMBING)
                .description("Water leakage under washbasin in 301")
                .priority(Priority.MEDIUM)
                .status(ComplaintStatus.NEW)
                .build();
        complaintDao.save(c1);

        // Fetch two instances simulating concurrent reads by two admins
        Complaint copy1 = complaintDao.findById(complaintId).orElseThrow();
        Complaint copy2 = complaintDao.findById(complaintId).orElseThrow();

        // Admin 1 assigns staff 3
        copy1.setStaffId(3L);
        copy1.setStatus(ComplaintStatus.ASSIGNED);
        complaintDao.update(copy1); // Version becomes 2

        // Admin 2 attempts to assign staff 4 using stale copy2 (version 1)
        copy2.setStaffId(4L);
        copy2.setStatus(ComplaintStatus.ASSIGNED);

        assertThrows(ConcurrentModificationException.class, () -> complaintDao.update(copy2),
                "Concurrent modification with stale version must throw ConcurrentModificationException");
    }

    @Test
    @DisplayName("SupportDao saves and retrieves feedback, comments, and notifications")
    void testSupportDaoOperations() {
        // Test notification
        Notification notification = new Notification();
        notification.setUserId(6L);
        notification.setMessage("Your complaint has been acknowledged.");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        supportDao.saveNotification(notification);

        List<Notification> notifications = supportDao.findNotificationsByUserId(6L, 10);
        assertFalse(notifications.isEmpty());

        Notification n = notifications.get(0);
        assertFalse(n.isRead());

        supportDao.markAsRead(n.getId(), 6L);
        List<Notification> updatedNotifications = supportDao.findNotificationsByUserId(6L, 10);
        assertTrue(updatedNotifications.stream().anyMatch(Notification::isRead));
    }
}
