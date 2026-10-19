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
