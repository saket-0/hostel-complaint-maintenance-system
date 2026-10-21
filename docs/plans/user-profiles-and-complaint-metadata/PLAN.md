# Epic: User Profiles, Admin User Management & Complaint Student Metadata

branch: `epic/user-profiles-and-complaint-metadata`
auto-commit: `no`

---

## 1. Goal
Provide complete transparency for student metadata in complaint inspection and give all roles full self-service profile management along with comprehensive administrative user governance.

### Measurable Success Criteria
1. Complaint detail drawer displays the student's name, email, hostel block, and room number for Admin, Staff, and Student roles.
2. Every authenticated user (Student, Staff, Admin) can view their profile via a top-bar Profile modal; Students can edit their hostel block and room number directly with instant validation.
3. Admins can view all registered users (students and staff), add new staff, update staff specializations/availability, edit student block/room numbers, and soft-delete/deactivate accounts.
4. All new REST endpoints pass unit/controller tests and `./quick.sh check` and `./quick.sh docs` remain 100% green.

---

## 2. Non-Goals
1. Changing database schema with destructive column removals (all changes use existing tables or backward-compatible queries).
2. Third-party OAuth / SSO integration (authentication remains BCrypt password + JWT tokens).
3. Student self-registration role escalation (students cannot elevate their own role to staff or admin).

---

## 3. Current State
- **Complaint Domain Model**: `hcms-core/src/main/java/edu/vit/hcms/core/model/Complaint.java` only stores `studentId`, `assignedStaffName`, and `assignedStaffSpecialization`. It has no fields for student name, email, block, or room number.
- **Complaint DAO Query**: `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/ComplaintDao.java` (`SELECT_COMPLAINT_BASE`) only joins `users u` on `c.staff_id = u.user_id` and does not join student user or block/room data.
- **Complaint Detail Drawer**: `hcms-web/src/main/resources/public/js/student.js` (`renderComplaintDetail`) only renders category, description, assigned staff, and due date. No student identity or room information is visible to staff or admin.
- **User Management in DAO**: `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/UserDao.java` provides `save(user)`, `findById`, `findByEmail`, `findAllStaff`, `updateStaffAvailability`, and `updatePassword`, but lacks generic user profile update and user deactivation methods.
- **Admin Controller & UI**: `AdminController.java` only has `createStaff` and `listStaff`. The UI `#staffTab` only shows a read-only list with a basic create staff modal. There is no user profile endpoint or page for students or staff.

---

## 4. Target State
- **Complaint Entity & DTO**: `Complaint.java` contains `studentName`, `studentEmail`, `studentHostelBlock`, and `studentRoomNumber`, populated by a single SQL join in `ComplaintDao`.
- **Drawer Presentation**: Complaint detail drawer displays a clean, prominent **Student Information Card** showing student name, block, room, and email.
- **Profile API**: `GET /api/v1/users/profile` and `PUT /api/v1/users/profile` allow students to update their block/room/name, and staff to manage availability.
- **Admin User Management API**: `GET /api/v1/admin/users`, `PUT /api/v1/admin/users/{id}`, `DELETE /api/v1/admin/users/{id}`, and `PUT /api/v1/admin/staff/{id}` enable full admin control over all user accounts.
- **Frontend UI**:
  - Reusable **Profile Modal** in top navigation for all roles.
  - Enhanced **User & Staff Management** view in Admin dashboard with tabbed/filtered table of staff and students, modal for editing student rooms, and staff update/deactivation actions.

---

## 5. Decisions & Assumptions
- **Soft Deletion for Users**: When an admin removes a user, set `users.active = FALSE` rather than SQL `DELETE` to preserve foreign key integrity on complaints, comments, audit logs, and feedback.
- **Join-Based Metadata in Complaint Query**: Join `users stu` and `students st` directly in `SELECT_COMPLAINT_BASE` in `ComplaintDao` rather than making N+1 API calls.
- **Student Profile Restrictions**: Students can update their room number, hostel block, and name, but cannot change their own email (institutional ID) or role.
- **Role Permission Policy**: Student profile editing is limited to self; Admin can edit any user; Staff can toggle their availability.

---

## 6. Phases

### [x] P1: Complaint Domain Model & DAO Student Metadata Enrichment
- **Type**: LOGIC (+ DB read queries)
- **Files to touch**:
  - `hcms-core/src/main/java/edu/vit/hcms/core/model/Complaint.java`
  - `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/ComplaintDao.java`
  - `hcms-web/src/main/java/edu/vit/hcms/web/controller/ComplaintController.java`
  - `hcms-persistence/src/test/java/edu/vit/hcms/persistence/dao/DaoIntegrationIT.java`
- **Do**:
  1. Add `studentName`, `studentEmail`, `studentRoomNumber`, and `studentHostelBlock` fields, getters, setters, and builder methods to `Complaint.java`.
  2. Update `ComplaintDao.SELECT_COMPLAINT_BASE` to `LEFT JOIN users stu ON c.student_id = stu.user_id` and `LEFT JOIN students st ON c.student_id = st.student_id`, selecting student metadata columns.
  3. Map student metadata fields in `ComplaintDao.COMPLAINT_MAPPER`.
  4. Expose student metadata fields in `ComplaintController.toMap()`.
- **Docs**: `docs/CODEMAP.md`, `docs/API.md`
- **Verify**: `./quick.sh check` and `./quick.sh persistence`
- **Rollback**: `git checkout HEAD -- hcms-core/ hcms-persistence/ hcms-web/`

---

### [x] P2: Persistence & Service Layer for User Updates and Governance
- **Type**: LOGIC
- **Files to touch**:
  - `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/UserDao.java`
  - `hcms-service/src/main/java/edu/vit/hcms/service/user/UserManager.java` (new service class or added to existing service structure)
  - `hcms-service/src/test/java/edu/vit/hcms/service/user/UserManagerTest.java` (new test class)
- **Do**:
  1. Add `update(User user)` in `UserDao` to update base fields (`name`, `active`) and role-specific tables (`students`, `staff`, `admins`).
  2. Add `deactivate(Long userId)` in `UserDao` to set `active = FALSE`.
  3. Add `findUsers(String roleFilter, String searchQuery, int offset, int limit)` in `UserDao` with total count support.
  4. Create `UserManager` business service with validation for profile updates and admin overrides.
  5. Add unit tests in `UserManagerTest` verifying student profile updates and admin staff/student modifications.
- **Docs**: `docs/CODEMAP.md`, `docs/DESIGN.md`
- **Verify**: `./quick.sh service` and `./quick.sh test UserManagerTest service`
- **Rollback**: `git checkout HEAD -- hcms-persistence/ hcms-service/`

---

### [x] P3: Profile and Admin User Management REST Endpoints
- **Type**: ENDPOINT
- **Files to touch**:
  - `hcms-web/src/main/java/edu/vit/hcms/web/dto/UpdateProfileRequest.java` (new)
  - `hcms-web/src/main/java/edu/vit/hcms/web/dto/AdminUpdateUserRequest.java` (new)
  - `hcms-web/src/main/java/edu/vit/hcms/web/controller/UserController.java` (new)
  - `hcms-web/src/main/java/edu/vit/hcms/web/controller/AdminController.java`
  - `hcms-web/src/main/java/edu/vit/hcms/web/App.java`
- **Do**:
  1. Create request DTO records: `UpdateProfileRequest` (name, roomNumber, hostelBlock, available) and `AdminUpdateUserRequest` (name, active, specialization, available, roomNumber, hostelBlock, adminRole).
  2. Implement `UserController` handling `GET /api/v1/users/profile` and `PUT /api/v1/users/profile`.
  3. In `AdminController`, add `listUsers(Context ctx)`, `updateUser(Context ctx)`, `deleteUser(Context ctx)`, and `updateStaff(Context ctx)`.
  4. Register routes in `App.java` protected by role access filters (`Role.STUDENT, Role.STAFF, Role.ADMIN` for profile; `Role.ADMIN` for `/api/v1/admin/users/*`).
- **Docs**: `docs/API.md`, `docs/CODEMAP.md`
- **Verify**: `./quick.sh web`
- **Rollback**: `git checkout HEAD -- hcms-web/`

---

### [x] P4: Complaint Drawer & Cards Student Details Display
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/js/student.js`
  - `hcms-web/src/main/resources/public/js/admin.js`
  - `hcms-web/src/main/resources/public/js/staff.js`
  - `hcms-web/src/main/resources/public/css/style.css`
- **Do**:
  1. In `student.js` (`renderComplaintDetail`), add a **Student Information Banner / Card** in the complaint detail drawer displaying Student Name, Room Number, Hostel Block, and Email with distinct icons.
  2. In `admin.js` and `staff.js`, display student name alongside room/block in complaint cards and triage list items.
  3. Add CSS styling in `style.css` for `.student-detail-card`, `.meta-tag`, and student badge elements.
- **Docs**: `docs/WORKFLOWS.md`
- **Verify**: `./quick.sh check` and manual browser check on complaint drawer
- **Rollback**: `git checkout HEAD -- hcms-web/src/main/resources/public/`

---

### [x] P5: Universal User Profile Modal in Navbar
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/index.html`
  - `hcms-web/src/main/resources/public/js/app.js`
  - `hcms-web/src/main/resources/public/js/auth.js`
  - `hcms-web/src/main/resources/public/css/style.css`
- **Do**:
  1. In `index.html`, add modal structure `#profileModal` with fields for Name, Email (read-only), Role (badge), and role-specific fields (Student: Room Number & Hostel Block inputs; Staff: Availability toggle & Specialization; Admin: Admin Role).
  2. In `app.js`, attach click handler to the user badge / profile button in top navigation to open `#profileModal`.
  3. Load user profile data via `GET /api/v1/users/profile` and submit updates via `PUT /api/v1/users/profile`.
  4. Refresh local user state and show toast feedback upon successful save.
- **Docs**: `docs/WORKFLOWS.md`
- **Verify**: `./quick.sh check` and manual browser check on profile modal
- **Rollback**: `git checkout HEAD -- hcms-web/src/main/resources/public/`

---

### [x] P6: Admin User & Staff Management Screen
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/index.html`
  - `hcms-web/src/main/resources/public/js/admin.js`
  - `hcms-web/src/main/resources/public/css/style.css`
- **Do**:
  1. Enhance `#staffTab` in `index.html` to a tabbed "Staff & User Management" interface (Sub-tab 1: Maintenance Staff, Sub-tab 2: Students).
  2. Implement staff action controls: Edit Specialization modal, Toggle Availability, and Deactivate/Activate staff.
  3. Implement student directory table: Search by name/room/block, Edit Student modal (room, block, active status).
  4. Wire API calls in `admin.js` to `/api/v1/admin/users` and `/api/v1/admin/staff/{id}`.
- **Docs**: `docs/WORKFLOWS.md`
- **Verify**: `./quick.sh check` and manual browser check in Admin tab
- **Rollback**: `git checkout HEAD -- hcms-web/src/main/resources/public/`

---

### [x] P7: Documentation Sync, Architecture Audit & CodeMap Regeneration
- **Type**: DOCS
- **Files to touch**:
  - `README.md`
  - `docs/API.md`
  - `docs/DESIGN.md`
  - `docs/CODEMAP.md`
  - `AGENTS.md`
- **Do**:
  1. Run `./gen-codemap.sh` to update `docs/CODEMAP.md` with new controller, service, and DTO entries.
  2. Document all new endpoints (`/api/v1/users/profile`, `/api/v1/admin/users`, etc.) in `docs/API.md`.
  3. Update `README.md` feature matrix and screenshot references.
  4. Update `docs/DESIGN.md` architecture layer section.
  5. Run `./quick.sh docs` to verify complete link and path integrity.
- **Docs**: `README.md`, `docs/API.md`, `docs/DESIGN.md`, `docs/CODEMAP.md`, `AGENTS.md`
- **Verify**: `./quick.sh docs`
- **Rollback**: `git checkout HEAD -- docs/ README.md AGENTS.md`

---

## 7. Risks & Mitigations
1. **Risk**: Joining student tables in `ComplaintDao` could degrade performance on large tables.
   - *Mitigation*: The foreign key `c.student_id -> users(user_id)` is already indexed (`idx_complaints_student`), and `students.student_id` is a primary key, ensuring $O(1)$ indexed joins.
2. **Risk**: Unauthorized profile tampering across accounts.
   - *Mitigation*: `UserController` extracts `userId` directly from verified JWT claims in context attributes (`ctx.attribute("userId")`), completely preventing ID-spoofing by students or staff.
3. **Risk**: Accidental deletion breaking relational constraints on complaints.
   - *Mitigation*: Deletion is strictly soft-delete (`active = FALSE`) so foreign keys and complaint histories remain intact.

---

## 8. Progress Log
<!-- /next appends handoff notes here after completing each phase -->
- **2026-10-21 (P1)**:
  - **Files changed**:
    - `hcms-core/src/main/java/edu/vit/hcms/core/model/Complaint.java`
    - `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/ComplaintDao.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/controller/ComplaintController.java`
    - `hcms-persistence/src/test/java/edu/vit/hcms/persistence/dao/DaoIntegrationIT.java`
  - **Docs updated**: `docs/CODEMAP.md`, `docs/API.md`
  - **Handoff notes for P2**:
    - `Complaint` entity and builder carry `studentName`, `studentEmail`, `studentRoomNumber`, and `studentHostelBlock` populated from `users` and `students` joins in `ComplaintDao`.
    - `ComplaintController.toMap()` and `getById()` expose these fields directly.

- **2026-10-21 (P2)**:
  - **Files changed**:
    - `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/UserDao.java`
    - `hcms-service/src/main/java/edu/vit/hcms/service/user/UserManager.java`
    - `hcms-service/src/test/java/edu/vit/hcms/service/user/UserManagerTest.java`
  - **Docs updated**: `docs/CODEMAP.md`, `docs/DESIGN.md`
  - **Handoff notes for P3**:
    - `UserManager` provides `getUserById`, `updateProfile`, `listUsers`, `countUsers`, `adminUpdateUser`, `updateStaff`, and `deactivateUser`.
    - `UserDao` includes `update(User)`, `deactivate(Long)`, `findUsers(role, query, offset, limit)`, and `countUsers(role, query)`.
    - Next phase P3 (`ENDPOINT`) can wire these service methods directly into `UserController` and `AdminController`.

- **2026-10-21 (P3)**:
  - **Files changed**:
    - `hcms-web/src/main/java/edu/vit/hcms/web/dto/UpdateProfileRequest.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/dto/AdminUpdateUserRequest.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/dto/UpdateStaffRequest.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/controller/UserController.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/controller/AdminController.java`
    - `hcms-web/src/main/java/edu/vit/hcms/web/HcmsApplication.java`
    - `hcms-web/src/test/java/edu/vit/hcms/web/controller/UserControllerTest.java`
  - **Docs updated**: `docs/API.md`, `docs/CODEMAP.md`, `docs/LESSONS.md`
  - **Handoff notes for P4**:
    - REST endpoints are fully functional: `/api/v1/users/profile` (`GET`, `PUT`), `/api/v1/admin/users` (`GET`), `/api/v1/admin/users/{id}` (`PUT`, `DELETE`), and `/api/v1/admin/staff/{id}` (`PUT`).
    - Complaint detail endpoint (`GET /api/v1/complaints/{id}`) returns `studentName`, `studentEmail`, `studentRoomNumber`, and `studentHostelBlock`.
    - P4 is UI-focused: touch `student.js`, `admin.js`, `staff.js`, and `style.css` to render student information card in drawer and complaint cards.

- **2026-10-21 (P4)**:
  - **Files changed**:
    - `hcms-web/src/main/resources/public/css/style.css`
    - `hcms-web/src/main/resources/public/js/student.js`
    - `hcms-web/src/main/resources/public/js/admin.js`
    - `hcms-web/src/main/resources/public/js/staff.js`
  - **Docs updated**: `docs/WORKFLOWS.md`
  - **Handoff notes for P5**:
    - Complaint detail drawer displays a dedicated `.student-detail-card` showing Student Name, Hostel Block & Room (`.meta-tag`), and Email.
    - Complaint cards for students and staff now display room and block tag (`📍 Room: Block - Room`), and staff cards show student name.
    - Admin triage and complaints table now shows `Location & Student` with student name badge and location.
    - Next phase P5 (`UI`): Universal User Profile Modal in Navbar (`#profileModal` in `index.html`, `app.js`, `auth.js`, `style.css`).

- **2026-10-21 (P5)**:
  - **Files changed**:
    - `hcms-web/src/main/resources/public/index.html`
    - `hcms-web/src/main/resources/public/css/style.css`
    - `hcms-web/src/main/resources/public/js/auth.js`
    - `hcms-web/src/main/resources/public/js/app.js`
  - **Docs updated**: `docs/WORKFLOWS.md`
  - **Handoff notes for P6**:
    - Universal `#profileModal` allows Student, Staff, and Admin users to inspect their profile via "My Profile" dropdown item or clicking user badge.
    - Students can edit room number and hostel block with client-side validation; staff can toggle duty availability status; admin can view admin role.
    - Local user state (`currentUser`) and top navbar badges/avatars (`updateProfileMenu`) are updated reactively on profile save without page reload.
- **2026-10-21 (P6)**:
  - **Files changed**:
    - `hcms-web/src/main/resources/public/index.html`
    - `hcms-web/src/main/resources/public/js/admin.js`
    - `hcms-web/src/main/resources/public/css/style.css`
  - **Docs updated**: `docs/WORKFLOWS.md`
  - **Handoff notes for P7**:
    - `#adminStaffTab` upgraded to a tabbed "Staff & User Management" interface with Maintenance Staff and Student Directory subtabs.
    - Added staff controls: Edit Specialization & Status modal, Toggle Availability on card, and Deactivate/Activate toggle.
    - Added student directory table: live search by name/email/room/block, pagination controls, and Edit Student profile modal (block, room, active status).
    - Next phase P7 (`DOCS`): Documentation Sync, Architecture Audit & CodeMap Regeneration (`README.md`, `docs/API.md`, `docs/DESIGN.md`, `docs/CODEMAP.md`, `AGENTS.md`).

- **2026-10-21 (P7)**:
  - **Files changed**:
    - `gen-codemap.sh`
    - `docs/CODEMAP.md`
    - `AGENTS.md`
    - `.agents/rules/hcms-guide.md`
    - `docs/API.md`
    - `docs/DESIGN.md`
    - `README.md`
    - `docs/plans/user-profiles-and-complaint-metadata/PLAN.md`
  - **Docs updated**: `README.md`, `docs/API.md`, `docs/DESIGN.md`, `docs/CODEMAP.md`, `AGENTS.md`, `.agents/rules/hcms-guide.md`
  - **Epic Completion Summary**:
    - All 7 phases (P1 through P7) of `epic/user-profiles-and-complaint-metadata` are 100% complete.
    - `./quick.sh check` and `./quick.sh docs` are green.
    - Full end-to-end user profiles, complaint student metadata enrichment, universal profile modal, and admin staff & user governance are fully operational.
