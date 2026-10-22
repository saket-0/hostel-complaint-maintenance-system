# Hostel Complaint & Maintenance System (HCMS) - Architecture & Design Document

**Institution**: VIT Vellore  
**Course Project**: Advanced Object-Oriented Programming in Java  
**System**: Production-Ready University Hostel Complaint & Maintenance System  

---

## 1. Architectural Philosophy & Module Boundaries

HCMS is built as a strict **Maven multi-module system** following clean architecture and one-way dependency rules:

```
hcms-parent
  ├── hcms-core         (Zero dependencies on frameworks/JDBC: domain entities, enums, interfaces, exceptions)
  ├── hcms-persistence  (Depends only on hcms-core: raw JDBC DAOs, HikariCP pool, Flyway migrations)
  ├── hcms-service      (Depends on core & persistence: business logic, state machine, SLA engine, strategies)
  └── hcms-web          (Depends on service: Javalin HTTP routes, controllers, filters, embedded static UI)
```

### Module Separation Principles:
1. **No Framework Leaks in Core**: `hcms-core` has no dependencies on Javalin, Jackson, or JDBC. Domain logic, enums, and models are 100% pure Java.
2. **Encapsulated Data Access**: SQL queries exist strictly in `hcms-persistence` DAOs. Neither controllers nor services execute SQL.
3. **Thin Controllers**: `hcms-web` controllers handle HTTP parsing, invoke services, and map results to HTTP codes. No business validation or entity lifecycle logic resides in web handlers.

---

## 2. Object-Oriented Programming (OOP) Design

### 2.1 Polymorphic User Hierarchy
```
                 +----------------------+
                 |  abstract class User |
                 +----------------------+
                 | - userId: Long       |
                 | - name: String       |
                 | - email: String      |
                 | - passwordHash: Str  |
                 | - role: Role         |
                 +----------------------+
                 | + getDashboardView() | [abstract]
                 | + getPermissions()   | [abstract]
                 +----------------------+
                            ▲
        +-------------------+-------------------+
        |                   |                   |
+---------------+   +---------------+   +-------------------+
|    Student    |   |     Admin     |   |  MaintenanceStaff |
+---------------+   +---------------+   +-------------------+
| - roomNumber  |   | - adminRole   |   | - specialization  |
| - hostelBlock |   +---------------+   | - available: bool |
+---------------+                       +-------------------+
```
* **Inheritance**: `Student`, `Admin`, and `MaintenanceStaff` directly inherit from `User`. There is no subclass chaining.
* **Polymorphism**:
  - `getDashboardView()`: Each role supplies its unique portal view name.
  - `getPermissions()`: Dynamic immutable `Set<String>` describing capabilities (e.g., `CREATE_COMPLAINT` for students vs. `ASSIGN_STAFF`, `AUTO_ASSIGN` for admins).
  - Web controllers and services interact with `User` polymorphically when querying users, validating passwords, and logging activity.

### 2.2 Domain Entities & Immutability
* **Encapsulation**: Private fields accessed via type-checked getters and setters.
* **Builder Pattern**: `Complaint.Builder` provides fluent, validated construction with mandatory field verification (`studentId`, `category`, `description`).
* **Java Records for DTOs**: Immutable carrier records (`RegisterRequest`, `LoginRequest`, `CreateComplaintRequest`, etc.) guarantee payload immutability and eliminate unchecked cast warnings in controllers.

### 2.3 State Machine with Behavioral Enums
`ComplaintStatus` is implemented as an enum with an embedded state transition matrix:

```
  [NEW] ──(assign)──► [ASSIGNED] ──(accept)──► [IN_PROGRESS] ──(resolve)──► [RESOLVED]
    ▲                                                                           │
    │                                                                  (close)  │ (auto-close 7d)
    │                                                                           ▼
    └──────────────(reopen within 7d)────────────── [CLOSED] ◄──────────────────┘
```

The method `ComplaintStatus.canTransitionTo(ComplaintStatus target)` validates transitions. Any unauthorized or invalid transition throws `InvalidStatusTransitionException`.

### 2.4 User Governance & Profile Management
- `UserManager`: Central business service managing user profile modifications, staff availability/specializations, and administrative user governance.
- `UserController`: Thin REST controller exposing `/api/v1/users/profile` (`GET`, `PUT`) with authenticated JWT identity extraction (`ctx.attribute("userId")`) preventing user ID spoofing.
- `AdminController`: Administrative REST controller exposing `/api/v1/admin/users` (`GET`), `/api/v1/admin/users/{id}` (`PUT`, `DELETE`), and `/api/v1/admin/staff/{id}` (`PUT`) protected by `Role.ADMIN` authorization checks.
- **Role Boundary Enforcement**: Students can update their name, room number, and hostel block, but cannot escalate roles or modify email; Staff can update their name and toggle assignment availability; Administrators possess full override authority across accounts.
- **Soft Deletion Policy**: Administrative deactivation sets `users.active = FALSE` rather than SQL `DELETE`, preserving foreign key references and historical integrity across complaints, comments, audit logs, and feedback.

### 2.5 Automated Priority Escalation & Audit Trail Architecture
- **Automated Emergency Detection**: `ComplaintManager.registerComplaint()` scans complaint descriptions and category against emergency keywords (`sparks`, `sparking`, `short circuit`, `fire`, `flood`, `flooding`, `burst`, `smoke`, `gas leak`). If detected or if the student flags the complaint as a safety hazard, initial priority is automatically elevated to `Priority.HIGH`.
- **Administrative Override Audit Logging**: Whenever priority is updated manually via `ComplaintManager.updatePriority()`, an immutable audit record is saved to `complaint_history` via `SupportDao.saveHistory()`, recording the previous and new priority levels, modifier user ID, and timestamp.
- **SLA Escalation Engine**: The scheduled daemon `SlaEscalationEngine` runs every 15 minutes to evaluate overdue tickets, escalating unassigned or unresolved complaints according to defined SLA thresholds.

---

## 3. Design Patterns Applied

| Pattern | Implementation Class | Purpose |
| :--- | :--- | :--- |
| **DAO** | `UserDao`, `ComplaintDao`, `SupportDao` | Decouples business logic from MySQL JDBC operations. |
| **Factory** | `UserFactory` | Creates concrete `Student`, `Admin`, or `MaintenanceStaff` instances from database result sets or registration payloads based on role. |
| **Strategy** | `SpecializationMatchStrategy`, `LeastWorkloadStrategy` | Encapsulates staff auto-assignment algorithms into interchangeable strategies. |
| **Observer** | `ComplaintStatusListener`, `NotificationListener`, `AuditListener` | Decouples status transitions from side-effects (creating notifications, writing audit trail entries). |
| **Singleton** | `AppConfig` | Centralized, thread-safe configuration holder using double-checked locking for environment variables. |
| **Builder** | `Complaint.Builder` | Enforces validated, readable creation of `Complaint` objects. |

---

## 4. Multithreading & Concurrency Architecture

### 4.1 Thread-Safe Unique ID Generation
Complaint IDs follow the format `HCM-YYYY-NNNNNN`.
- `ComplaintDao` uses an `AtomicLong` local sequence counter for fast in-memory increments.
- To survive application restarts, on startup it queries `SELECT MAX(complaint_id)` to initialize the `AtomicLong`.
- A multi-threaded concurrency unit test (`ComplaintIdGeneratorTest`) generates hundreds of IDs concurrently across multiple threads to prove that zero duplicates occur.

### 4.2 Optimistic Locking (`version` column)
To prevent two administrators from simultaneously assigning different staff members to the same complaint:
- The `complaints` table contains a `version INT NOT NULL DEFAULT 1` column.
- Update queries check `WHERE complaint_id = ? AND version = ?`.
- If another thread updated the row first, 0 rows are affected, causing `ComplaintDao` to immediately throw `ConcurrentModificationException`.

### 4.3 Asynchronous Notification Engine (Producer-Consumer)
- `NotificationService` maintains a thread-safe `BlockingQueue<NotificationTask>` (capacity 5,000).
- Notifications triggered by status changes or assignments are placed into the queue in under 1 millisecond.
- A fixed `ExecutorService` thread pool (2 worker threads) consumes from the queue, persisting notifications to MySQL and executing optional external SMTP delivery without blocking the HTTP request thread.

### 4.4 Scheduled SLA Escalation Engine
- Uses `ScheduledExecutorService` running every 15 minutes.
- `checkAndEscalate()`: Scans open complaints whose `dueBy` has passed.
  - Automatically escalates priority: `LOW` (168h) -> `MEDIUM` (72h), and `MEDIUM` -> `HIGH` (24h).
  - Notifies admin when `HIGH` priority complaints become overdue.
- `autoCloseResolved()`: Automatically closes `RESOLVED` complaints after 7 days if the student has neither closed nor reopened them.

### 4.5 Asynchronous Audit Trail Logger
- Every complaint modification is observed by `AuditListener`, which records old status, new status, modifier ID, and note into `complaint_history` via a dedicated single-thread executor to avoid delaying user responses.

### 4.6 Graceful Shutdown Hooks
- `HcmsApplication` registers JVM shutdown hooks via `Runtime.getRuntime().addShutdownHook(...)`.
- On shutdown (`SIGTERM` / `SIGINT`), the application gracefully drains the notification queue, stops the SLA scheduler, terminates thread pools with timeout, and closes the HikariCP connection pool.

---

## 5. Core Java Collections Showcase

| Collection Class | Location in HCMS | Real Business Purpose |
| :--- | :--- | :--- |
| **`PriorityQueue<Complaint>`** | `ComplaintManager.getTriageQueue()` | Orders admin open complaints first by `Priority` (HIGH > MEDIUM > LOW) and second by `createdDate` (FIFO) for triage. |
| **`LinkedHashMap` (LRU Cache)** | `ComplaintManager` (LRU complaint cache) | Bounded in-memory complaint cache (max 200 items) overriding `removeEldestEntry` in access-order mode to eliminate redundant DB reads. |
| **`Deque` (ArrayDeque / LinkedList)**| `ComplaintManager.recentActivity` | Bounded ring-buffer tracking the last 20 hostel maintenance events for real-time dashboard display. |
| **`ConcurrentHashMap`** | `RateLimitFilter.requestLog` | Thread-safe per-client request timestamp history for sliding-window rate limiting. |
| **`EnumMap`** | `AnalyticsService.getStatusDistribution()` | High-performance, array-backed map storing complaint counts indexed by `ComplaintStatus` and `Category`. |
| **`TreeMap`** | `AnalyticsService.get30DayTrend()` | Sorted red-black tree map maintaining chronological day-by-day complaint volume over the past 30 days. |
| **Streams & Collectors** | `AnalyticsService`, `AdminController`, `StaffController` | Used for filtering, mapping to DTOs, computing averages, grouping complaints by hostel block, and CSV export. |

---

## 6. JDBC & Database Architecture

- **No ORM**: Raw JDBC ensures maximum transparency, zero Hibernate reflection overhead, and absolute query control.
- **`PreparedStatement` Exclusively**: Every single SQL statement uses parameter placeholders (`?`). String concatenation is strictly prohibited, providing 100% protection against SQL injection.
- **HikariCP Connection Pool**: High-performance connection pooling with tuned parameters (`cachePrepStmts=true`, `prepStmtCacheSize=250`, `prepStmtCacheSqlLimit=2048`, `useServerPrepStmts=true`).
- **`TransactionManager`**: Programmatic transaction demarcation using thread-local connection binding with explicit `commit()` and `rollback()` handling.
- **`RowMapper<T>`**: Functional interface enabling declarative result set mapping into strongly-typed domain models.

---

## 7. Single-Level Navigation Architecture

HCMS enforces a unified, single-level top navigation structure per user role. Duplicate tab rows and disjoint history pages have been eliminated in favor of contextual detail drawers and dedicated primary views.

### 7.1 Role Navigation Matrix

| Role | Navigation Links | Default Route | Allowed Hash Routes |
| :--- | :--- | :--- | :--- |
| **Student** | `My Complaints` (Active & Closed/Ratings) \| `+ New Complaint` \| `Notifications` \| `Profile Menu` | `#/mine` | `#/mine`, `#/new` |
| **Staff** | `My Tasks` \| `History` (Task History & Completed) \| `Notifications` \| `Profile Menu` | `#/tasks` | `#/tasks`, `#/completed` |
| **Admin** | `Triage` \| `All Complaints` \| `Analytics` \| `Staff & Users` \| `Notifications` \| `Profile Menu` | `#/triage` | `#/triage`, `#/complaints`, `#/analytics`, `#/staff` |

### 7.2 Route Guard & Deep Linking
- **Single Source of Truth**: The client-side router (`app.js`) validates incoming hash fragments against `ROLE_ROUTES[user.role]`.
- **Role Redirection**: Unauthorized or outdated hash routes (such as deprecated `#/history`) automatically redirect to the role's default landing page (`DEFAULT_ROUTE[role]`).
- **Deep Linking**: Deep links and browser back/forward navigation (`popstate` / `hashchange`) preserve current subviews, filter query params, and open modals seamlessly across page refreshes.

---

## 8. Contextual Action Engine (`allowedActions`)

Action buttons in the UI are strictly contextual and driven directly by server-side policy (`ComplaintActionPolicy`), preventing invalid actions and dead clicks.

### 8.1 State & Role Action Matrix

| Complaint Status | Assigned? | Student Actions | Maintenance Staff Actions | Admin Actions |
| :--- | :--- | :--- | :--- | :--- |
| **`NEW`** | No | `VIEW` | `VIEW` | `ASSIGN`, `AUTO_ASSIGN`, `VIEW` |
| **`NEW`** | Yes | `VIEW` | `VIEW` | `REASSIGN`, `VIEW` |
| **`ASSIGNED`** | Yes | `VIEW` | `ACCEPT`, `VIEW` | `REASSIGN`, `VIEW` |
| **`IN_PROGRESS`** | Yes | `VIEW` | `RESOLVE`, `VIEW` | `REASSIGN`, `VIEW` |
| **`RESOLVED`** | Yes | `GIVE_FEEDBACK` (if none), `CLOSE`, `REOPEN` (≤7d), `VIEW` | `VIEW` | `VIEW` |
| **`CLOSED`** | - | `VIEW` | `VIEW` | `VIEW` |
| **`REOPENED`** | - | `VIEW` | `RESOLVE` (if assigned), `VIEW` | `ASSIGN` (highlighted), `VIEW` |

### 8.2 Server-Side State Machine Enforcement
- Any attempt to invoke an action contrary to the state machine or role policy returns HTTP **409 Conflict** (for invalid status transitions) or HTTP **422 Unprocessable Entity** (for semantic or timing violations, e.g. reopening past 7 days).
- The `ComplaintResponse` DTO exposes `List<String> allowedActions` generated dynamically by `ComplaintActionPolicy.computeAllowedActions(complaint, user)`, ensuring the frontend never duplicates business rules.

---

## 9. Seed Data & Temporal Integrity Rules

To ensure realistic development and deterministic testing, seed data satisfies strict temporal monotonicity validated on every application startup:

1. **Temporal Monotonicity**: For every seeded complaint:
   $$\text{createdDate} \le \text{history.changedAt} \le \text{updatedDate} \le \text{resolvedDate}$$
2. **Complete State Traversal**: A corresponding `complaint_history` entry exists for every status the complaint has passed through (`NEW` $\to$ `ASSIGNED` $\to$ `IN_PROGRESS` $\to$ `RESOLVED`).
3. **Readable Actor Audit Trail**: Audit history logs human-readable descriptions server-side (e.g., `"Assigned to Arun (Electrical)"` rather than `"Assigned to staff 2"`).
4. **Automated Startup Validation**: `SeedDataValidator` scans all seeded complaints and history rows on startup and test runs, failing fast with descriptive errors if any invariant is violated.

---

## 10. Design System, WCAG AA Accessibility & Frontend Architecture

The user interface follows a modern, zero-dependency vanilla ES module architecture coupled with refined CSS tokens:

1. **Refined Neo-Brutalist Design Tokens**: Uses softened borders (`--border-width: 1.5px`) and crisp offset drop shadows (`--shadow-hard: 3px 3px var(--border-color)`), reserving heavy borders strictly for focused or actively selected items.
2. **Typography & Sentence Case**: Clean Plus Jakarta Sans font with sentence-case headings and action buttons, keeping uppercase exclusively for data table headers and status tags.
3. **WCAG AA Certified Contrast**: All priority badges (`LOW`, `MEDIUM`, `HIGH`) and status badges (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REOPENED`) satisfy WCAG AA 4.5:1 minimum contrast (achieving 7:1+ ratio).
4. **Normalized Priority Resolution**: `app.js:getPriorityInfo(priority)` provides dual-format parsing capable of handling backend enum string names (`"HIGH"`, `"MEDIUM"`, `"LOW"`) and database level integers (`1`, `2`, `3`), preventing display discrepancies across all views.
5. **Unified SVG Icon System**: Clean inline SVG line icons via `icons.js` replace legacy emojis, ensuring platform-agnostic visual consistency.
6. **Mobile Responsiveness (360px Viewports)**: Data tables feature accessible horizontal scrolling and touch-friendly targets, and modals/drawers seamlessly collapse on viewports down to 360px width.

