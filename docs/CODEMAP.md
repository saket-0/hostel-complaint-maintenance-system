# HCMS CodeMap: Architecture & Class Directory

> Auto-generated reference map. Consult this file before reading source files.
> Generated with `./gen-codemap.sh` — strictly kept under 250 lines.

## 1. Top 5 Largest Files (Line Counts & Major Methods)

- **`hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/ComplaintDao.java`** (713 lines)
  - Key methods: save() ~L151, findById() ~L196, update() ~L222, search() ~L299, isDuplicate() ~L447, staffWorkload() ~L628
- **`hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`** (450 lines)
  - Key methods: registerComplaint() ~L95, assignComplaint() ~L152, autoAssignComplaint() ~L195, updateComplaintStatus() ~L227, reopenComplaint() ~L272
- **`hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/SupportDao.java`** (384 lines)
  - Key methods: saveHistory() ~L34, findHistoryByComplaintId() ~L58, saveFeedback() ~L124, saveComment() ~L182, saveNotification() ~L237
- **`hcms-web/src/main/java/edu/vit/hcms/web/controller/AdminController.java`** (366 lines)
  - Key methods: triage() ~L49, listComplaints() ~L188, assign() ~L239, autoAssign() ~L250, listStaff() ~L260, analytics() ~L298
- **`hcms-web/src/main/java/edu/vit/hcms/web/controller/ComplaintController.java`** (313 lines)
  - Key methods: create() ~L40, getMyComplaints() ~L61, getById() ~L77, uploadAttachment() ~L156, reopen() ~L196, close() ~L207

---

## 2. Module Class Directory (One Line Per Class)

### hcms-core
- `hcms-core/src/main/java/edu/vit/hcms/core/config/AppConfig.java`: Central configuration manager reading application properties; key methods: getInstance(), get(), get(), getInt()
- `hcms-core/src/main/java/edu/vit/hcms/core/enums/Category.java`: Enum of complaint categories with SLA and auto-specialization mappings; key methods: getDisplayName(), getRequiredSpecialization()
- `hcms-core/src/main/java/edu/vit/hcms/core/enums/ComplaintStatus.java`: State machine enum governing complaint lifecycle and valid transitions; key methods: canTransitionTo(), getDisplayName(), getColorHex()
- `hcms-core/src/main/java/edu/vit/hcms/core/enums/Priority.java`: Enum of severity levels (LOW, MEDIUM, HIGH, URGENT) with default SLA hours; key methods: getLevel(), getDefaultSlaHours(), getDisplayName(), fromLevel()
- `hcms-core/src/main/java/edu/vit/hcms/core/enums/Role.java`: User roles (STUDENT, MAINTENANCE_STAFF, ADMIN) and authority checks; key methods: getDisplayName()
- `hcms-core/src/main/java/edu/vit/hcms/core/enums/Specialization.java`: Staff maintenance trade domains matching complaint categories; key methods: getDisplayName(), getRoleTitle()
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/AuthenticationException.java`: Exception thrown on bad credentials or expired tokens
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/AuthorizationException.java`: Exception thrown on role permission violations
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/ConcurrentModificationException.java`: Exception for optimistic locking / state conflicts
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/DataAccessException.java`: Wrapper exception for underlying JDBC/SQL errors
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/DuplicateEntryException.java`: Exception when unique constraints or duplicate checks fail
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/HcmsException.java`: Base unchecked runtime exception for HCMS domain errors; key methods: getErrorCode()
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/InvalidStatusTransitionException.java`: Exception when attempting an illegal complaint state transition
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/ResourceNotFoundException.java`: Exception when requested entity ID is not found
- `hcms-core/src/main/java/edu/vit/hcms/core/exception/ValidationException.java`: Exception when input DTO or payload fails validation
- `hcms-core/src/main/java/edu/vit/hcms/core/factory/UserFactory.java`: Factory pattern utility for creating Student, Staff, and Admin users; key methods: createUser(), createUser()
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/AssignmentStrategy.java`: Strategy interface for assigning complaints to maintenance staff
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/Auditable.java`: Interface for entities tracking creation and update timestamps
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/ComplaintStatusListener.java`: Observer interface notified upon complaint status transitions
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/Notifiable.java`: Interface for entities capable of receiving notifications
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/Repository.java`: Generic repository interface defining CRUD operations
- `hcms-core/src/main/java/edu/vit/hcms/core/interfaces/Searchable.java`: Interface for repository criteria searches and pagination
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Admin.java`: Administrator user entity with elevated privileges; key methods: getDashboardView(), getPermissions(), getAdminRole(), setAdminRole()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Attachment.java`: File attachment entity metadata (mime, path, size); key methods: getId(), setId(), getComplaintId(), setComplaintId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Complaint.java`: Primary domain entity tracking complaint lifecycle, student, staff, SLA; key methods: builder(), getComplaintId(), getStudentId(), getStaffId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/ComplaintComment.java`: Threaded comment entity on a complaint; key methods: getId(), setId(), getComplaintId(), setComplaintId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/ComplaintHistory.java`: Immutable audit record of complaint state/field changes; key methods: getId(), setId(), getComplaintId(), setComplaintId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Feedback.java`: Student rating and review entity submitted upon resolution; key methods: getFeedbackId(), setFeedbackId(), getComplaintId(), setComplaintId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/MaintenanceStaff.java`: Staff user entity with specialization, availability, and active tasks; key methods: getDashboardView(), getPermissions(), getSpecialization(), setSpecialization()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Notification.java`: User alert entity with read/unread status; key methods: getId(), setId(), getUserId(), setUserId()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/Student.java`: Student user entity with hostel block and room number; key methods: getDashboardView(), getPermissions(), getRoomNumber(), setRoomNumber()
- `hcms-core/src/main/java/edu/vit/hcms/core/model/User.java`: Abstract base class for all system users; key methods: getDashboardView(), onLogin(), onLogout(), getUserId()
- `hcms-core/src/main/java/edu/vit/hcms/core/policy/ComplaintActionPolicy.java`: Policy evaluator for role-based contextual UI actions; key methods: allowedActions(), isWithinReopenWindow()
- `hcms-core/src/main/java/edu/vit/hcms/core/util/ComplaintIdGenerator.java`: Thread-safe sequential complaint identifier generator (HCM-YYYY-NNNNNN); key methods: nextId(), getCurrentSequence()

### hcms-persistence
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/ComplaintDao.java`: JDBC DAO for complaint persistence, searches, duplicate checks, and metrics; key methods: generateComplaintId(), save(), findById(), update()
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/SupportDao.java`: JDBC DAO for complaint history, comments, attachments, feedback, notifications; key methods: saveHistory(), findHistoryByComplaintId(), findAllHistory(), saveFeedback()
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/UserDao.java`: JDBC DAO for students, staff, admins, credentials, and availability; key methods: save(), findById(), findByEmail(), findAllStaff()
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/db/DatabaseManager.java`: HikariCP connection pool provider and lifecycle manager; key methods: getDataSource(), getPoolStats(), isHealthy(), close()
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/db/RowMapper.java`: Functional interface for mapping JDBC ResultSet rows to domain models
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/db/TransactionManager.java`: Executor for atomic JDBC transactions and read-only queries; key methods: begin(), commit(), rollback(), getConnection()
- `hcms-persistence/src/main/java/edu/vit/hcms/persistence/validation/SeedDataValidator.java`: Validates database schema and seed integrity at startup; key methods: validate()

### hcms-service
- `hcms-service/src/main/java/edu/vit/hcms/service/analytics/AnalyticsService.java`: Aggregates complaint metrics, SLA adherence, and staff efficiency; key methods: getDashboardAnalytics(), exportComplaintsAsCsv()
- `hcms-service/src/main/java/edu/vit/hcms/service/auth/AuthService.java`: BCrypt password hashing and JWT token issuance and validation; key methods: login(), validateToken(), changePassword()
- `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`: Core business logic coordinator: registration, triage, auto-assign, transitions; key methods: addStatusListener(), registerStrategy(), registerComplaint(), assignComplaint()
- `hcms-service/src/main/java/edu/vit/hcms/service/engine/SlaEscalationEngine.java`: Scheduled background service checking SLA breaches and escalating; key methods: start(), close()
- `hcms-service/src/main/java/edu/vit/hcms/service/notification/AuditListener.java`: Status listener recording state changes to ComplaintHistory; key methods: close()
- `hcms-service/src/main/java/edu/vit/hcms/service/notification/NotificationService.java`: Dispatches in-app notifications and admin broadcasts; key methods: sendAsync(), notifyUser(), getNotifications(), getUnreadCount()
- `hcms-service/src/main/java/edu/vit/hcms/service/strategy/LeastWorkloadStrategy.java`: Assignment strategy picking available staff with fewest open complaints; key methods: selectStaff(), getStrategyName()
- `hcms-service/src/main/java/edu/vit/hcms/service/strategy/SpecializationMatchStrategy.java`: Assignment strategy matching category to staff specialization; key methods: selectStaff(), getStrategyName()

### hcms-web
- `hcms-web/src/main/java/edu/vit/hcms/web/HcmsApplication.java`: Javalin web server bootstrapper, routing table, and filter registrations; key methods: getStartTime(), main(), createServer()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/AdminController.java`: Admin dashboard, triage queue, staff provisioning, and analytics endpoints; key methods: triage(), listComplaints(), updatePriority(), assign()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/AuthController.java`: User registration, authentication, token refresh, and profile endpoints; key methods: register(), login(), logout(), me()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/ComplaintController.java`: Complaint creation, details, comments, attachments, close/reopen, ratings; key methods: create(), getMyComplaints(), getById(), uploadAttachment()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/HealthController.java`: System healthcheck, database ping, and uptime endpoints; key methods: health(), metrics()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/NotificationController.java`: User notification queries and mark-as-read endpoints; key methods: getNotifications(), markAsRead()
- `hcms-web/src/main/java/edu/vit/hcms/web/controller/StaffController.java`: Staff task list, availability toggling, and task status updates; key methods: getTasks(), getTaskHistory(), acceptTask(), updateStatus()
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/AssignStaffRequest.java`: Handles AssignStaffRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/AvailabilityRequest.java`: Handles AvailabilityRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/ChangePasswordRequest.java`: Handles ChangePasswordRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/CommentRequest.java`: Handles CommentRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/CreateComplaintRequest.java`: Handles CreateComplaintRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/CreateStaffRequest.java`: Handles CreateStaffRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/FeedbackRequest.java`: Handles FeedbackRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/LoginRequest.java`: Handles LoginRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/RegisterRequest.java`: Handles RegisterRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/ReopenRequest.java`: Handles ReopenRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/UpdatePriorityRequest.java`: Handles UpdatePriorityRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/dto/UpdateStatusRequest.java`: Handles UpdateStatusRequest operations
- `hcms-web/src/main/java/edu/vit/hcms/web/filter/AuthFilter.java`: Javalin before-filter enforcing JWT authentication and role authorization; key methods: requireAuth(), requireRole()
- `hcms-web/src/main/java/edu/vit/hcms/web/filter/CorsFilter.java`: Javalin filter adding CORS headers for local/cross-origin frontend requests; key methods: register()
- `hcms-web/src/main/java/edu/vit/hcms/web/filter/RateLimitFilter.java`: Sliding token bucket IP rate limiter protecting against abuse; key methods: checkLimit()
- `hcms-web/src/main/java/edu/vit/hcms/web/handler/GlobalExceptionHandler.java`: Maps domain exceptions to HTTP status codes and JSON envelopes; key methods: register()

---

## 3. Test Suites & Target Classes

- `hcms-core/.../ComplaintStatusTest.java` $\rightarrow$ Tests **ComplaintStatus (valid & invalid state machine transitions)**
- `hcms-core/.../PriorityCategoryConfigTest.java` $\rightarrow$ Tests **Priority, Category, AppConfig (SLA hours & properties)**
- `hcms-core/.../UserFactoryTest.java` $\rightarrow$ Tests **UserFactory (Student, MaintenanceStaff, Admin instantiation)**
- `hcms-core/.../ComplaintModelTest.java` $\rightarrow$ Tests **Complaint model (invariants, timestamps, audit fields)**
- `hcms-core/.../ComplaintActionPolicyTest.java` $\rightarrow$ Tests **ComplaintActionPolicy (role-based permitted actions)**
- `hcms-core/.../ComplaintIdGeneratorTest.java` $\rightarrow$ Tests **ComplaintIdGenerator (sequential format & concurrency)**
- `hcms-persistence/.../DaoIntegrationIT.java` $\rightarrow$ Tests **ComplaintDao, UserDao, SupportDao (PostgreSQL integration)**
- `hcms-persistence/.../SeedDataValidatorTest.java` $\rightarrow$ Tests **SeedDataValidator (schema & baseline rows verification)**
- `hcms-service/.../AuthServiceTest.java` $\rightarrow$ Tests **AuthService (password hashing, JWT token lifecycle)**
- `hcms-service/.../ComplaintManagerTest.java` $\rightarrow$ Tests **ComplaintManager (registration, auto-assignment, lifecycle)**
- `hcms-service/.../SlaEscalationEngineTest.java` $\rightarrow$ Tests **SlaEscalationEngine (SLA calculation, periodic checks)**
- `hcms-service/.../StrategyTest.java` $\rightarrow$ Tests **SpecializationMatchStrategy, LeastWorkloadStrategy**
- `hcms-web/.../AdminTriageIT.java` $\rightarrow$ Tests **AdminController (/api/admin/triage endpoints)**
- `hcms-web/.../ComplaintStatusConsistencyIT.java` $\rightarrow$ Tests **ComplaintController & ComplaintStatus transitions**
- `hcms-web/.../ContextualActionsIT.java` $\rightarrow$ Tests **ComplaintController action authorization**
- `hcms-web/.../AuthFilterTest.java` $\rightarrow$ Tests **AuthFilter (Bearer token validation & 401 handling)**
- `hcms-web/.../RateLimitFilterTest.java` $\rightarrow$ Tests **RateLimitFilter (token bucket request throttling)**
- `hcms-web/.../GlobalExceptionHandlerTest.java` $\rightarrow$ Tests **GlobalExceptionHandler (exception-to-HTTP mapping)**
