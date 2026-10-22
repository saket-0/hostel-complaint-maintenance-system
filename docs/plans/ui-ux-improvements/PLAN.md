# HCMS UI/UX System Overhaul & Defect Remediation

```yaml
slug: ui-ux-improvements
branch: epic/ui-ux-improvements
auto-commit: no
created: 2026-10-21
status: draft
```

---

## 1. Goal

Eliminate priority data discrepancies and functional UI bugs across HCMS, streamline administrative triage into a 3-second actionable view, declutter student complaint filing and status tracking, consolidate staff task and history management, and establish a cohesive, WCAG AA compliant visual design system across mobile (360px) and desktop viewports.

### Measurable Success Criteria
- **100% Priority Agreement**: Admin table, admin drawer, student card, staff task card, and task history display identical priority labels and colors for every complaint record.
- **Automated Severity Elevation**: Emergency keywords (`sparks`, `short circuit`, `fire`, `flood`, `burst`, `smoke`) and the student safety risk toggle auto-escalate complaints to `HIGH` priority, with admin overrides recorded in the audit history.
- **Admin Triage Under 3 Seconds**: Overdue, unassigned, and high-priority tickets are instantly identifiable via visual indicators and a 4-state summary strip filter.
- **Responsive & Accessible**: Zero clipping at 360px mobile width; all status badges and buttons satisfy WCAG AA 4.5:1 contrast standards.

---

## 2. Non-Goals

- Modifying existing database schema or adding new database tables (priority is already stored as 1/2/3 level integers in PostgreSQL/MySQL).
- Redesigning core authentication mechanisms, JWT handling, or BCrypt hashing.
- Creating new backend microservices or changing Javalin into another framework.
- Removing or altering the core state machine transitions defined in `ComplaintStatus.java`.

---

## 3. Current State (Verified Facts & Paths)

- **Priority Display Bug**: `hcms-web/src/main/resources/public/js/admin.js:L511` and `hcms-web/src/main/resources/public/js/staff.js:L80` evaluate `c.priority === 1` and `task.priority === 1`. However, the backend REST controllers (`AdminController.java:L440`, `StaffController.java:L45`, `ComplaintController.java:L314`) map `priority` as the enum string (`"HIGH"`, `"MEDIUM"`, `"LOW"`), causing every item to evaluate to `false` and default to `'Low'`. In contrast, `student.js:L335` uses `priority-${c.priority}` and detail drawers read `c.priority === 'HIGH'`, creating cross-view discrepancies.
- **Priority Escalation Logic**: `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java:L57` defines `HIGH_PRIORITY_KEYWORDS`, but lacks variants like `"flood"`, `"sparks"`, and `"burst"`. Furthermore, `ComplaintManager.updatePriority():L298` changes priority in the database but does not write a `ComplaintHistory` audit record via `supportDao.saveHistory()`.
- **Badge Clipping & Positioning**: `hcms-web/src/main/resources/public/css/style.css:L439` defines `.badge` with `position: absolute; top: -8px; right: -8px; background-color: var(--red);`. When used inside `.profile-meta-badges` (`index.html:L710`) and on staff cards (`staff.js:L79`), it becomes an absolute red badge clipped at the top edge of the modal/card and collides with `.modal-close`.
- **Duty Availability UI**: `index.html:L751-L756` in `#profileModal` renders a raw checkbox input without the styled toggle slider present on `#staffDashboard` (`index.html:L513-L518`).
- **Developer Footer**: `app.js:L414` sets `footer.style.display = 'block'` for all authenticated users, displaying JVM uptime, thread count, and queue stats to students and staff.
- **Toast Collision**: `style.css:L1378-L1389` sets `#toastContainer` to `bottom: 30px; left: 50%; transform: translateX(-50%)`, directly obscuring bottom cards and action buttons.
- **Raw Enum Audit Timeline**: `student.js:L580` renders `${h.oldStatus ? `${h.oldStatus} ➔ ${h.newStatus}` : h.newStatus}` directly as raw uppercase strings (e.g., `IN_PROGRESS`).

---

## 4. Target State

- **Consistent Priority & Real Logic**: A unified priority resolution helper in `app.js` handles both string and integer level values seamlessly. `ComplaintManager.java` auto-assigns `HIGH` priority for emergency keywords (`sparks`, `flood`, `burst`, etc.), and manual priority overrides log audit entries visible in the complaint timeline.
- **Streamlined Admin Triage**: Summary counters strip (`Overdue`, `Unassigned`, `In Progress`, `Resolved Today`) with one-click filtering; overdue rows styled with prominent red accent border; compact single-line location and student metadata (`"A-101 · Priya Sharma"`); category icons; duplicate warning tags; and bulk assignment/priority actions.
- **Clear Student Portal**: Lightweight complaint cards with inline progress bars replacing bulky steppers; consolidated "Closed" tab removing redundant history duplication; enhanced complaint submission with safety toggle, common-area location selector, drag-drop image preview, and duplicate warning; and actionable "Fixed? Confirm or Reopen" banners.
- **Focused Staff Console**: Room and block displayed prominently as the primary visual element (`"Block B · Room 410"`); tasks grouped into clear operational sections (`"Needs Action"`, `"In Progress"`, `"Reopened"`); student reopen reasons displayed directly on task cards; unified history with standardized relative/absolute dates; distinct hover/focus/selected card states.
- **Harmonious Global Visual System**: Refined design tokens reducing harsh borders and shadows; sentence case typography; unified SVG icon system replacing random emojis; contrast-certified status badges meeting WCAG AA standards; and responsive layout down to 360px mobile viewports.

---

## 5. Decisions & Assumptions

- **Triage vs All Complaints**: Rather than merging Triage into All Complaints as a mere filter, Triage is maintained as an operational "Action Required" queue showing only Unassigned, Overdue, and Reopened complaints needing attention, while All Complaints serves as the complete searchable system archive.
- **Student History Consolidation**: Rather than maintaining two near-identical navigation tabs ("My Complaints" and "History"), History stat metrics and completed items are integrated into a dedicated "Closed & Ratings" tab inside the student dashboard.
- **Date Formatting Standard**: Dates under 7 days render relative timestamps (e.g., `"2 hours ago"`, `"3 days ago"`) and dates older than 7 days render formatted absolute dates (`"14 Oct 2026, 10:30 AM"`).
- **Backend Stability**: REST API contracts remain stable. Fixes to priority serialization and history auditing are handled within existing response envelopes and standard models.

---

## 6. Phases

### [x] P1 Fix Priority Inconsistencies & Core UI Bugs
- **Type**: BUG / LOGIC / UI
- **Files to touch**:
  - `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`
  - `hcms-service/src/test/java/edu/vit/hcms/service/complaint/ComplaintManagerTest.java`
  - `hcms-web/src/main/resources/public/js/admin.js`
  - `hcms-web/src/main/resources/public/js/staff.js`
  - `hcms-web/src/main/resources/public/js/student.js`
  - `hcms-web/src/main/resources/public/js/history.js`
  - `hcms-web/src/main/resources/public/js/app.js`
  - `hcms-web/src/main/resources/public/js/toast.js`
  - `hcms-web/src/main/resources/public/css/style.css`
  - `hcms-web/src/main/resources/public/index.html`
- **Do**:
  1. Fix priority display resolution across `admin.js`, `staff.js`, `student.js`, and `history.js` to normalize enum names (`"HIGH"`, `"MEDIUM"`, `"LOW"`) and numerical levels (`1`, `2`, `3`) into identical labels and CSS classes.
  2. Extend `HIGH_PRIORITY_KEYWORDS` in `ComplaintManager.java` to include `"flood"`, `"flooding"`, `"sparks"`, `"sparking"`, `"short circuit"`, `"fire"`, `"burst"`; add audit logging in `ComplaintManager.updatePriority()` using `supportDao.saveHistory()`.
  3. Remove or re-style the mispositioned `.badge` styling from `#profileModalStatusBadge` so it sits neatly within the header meta without clipping or close-button overlap; unify overdue tag styling on staff cards.
  4. Fix staff availability control in `#profileModal` to provide a functional switch slider matching the dashboard control.
  5. Update `app.js` to restrict the developer metrics footer to `ADMIN` users only; relocate `#toastContainer` to top/bottom-right with auto-dismissal; convert raw timeline enum strings to human-readable text.
- **Docs**: `docs/CODEMAP.md`, `docs/LESSONS.md`
- **Verify**: `./quick.sh service && ./quick.sh test ComplaintManagerTest && ./quick.sh web` + manual browser check at `http://localhost:8080`.
- **Rollback**: `git checkout -- hcms-service/ hcms-web/`

---

### [x] P2 Admin UI & Triage Workflow Overhaul
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/js/admin.js`
  - `hcms-web/src/main/resources/public/css/style.css`
  - `hcms-web/src/main/resources/public/index.html`
- **Do**:
  1. Implement the top KPI summary strip above the search bar (Overdue, Unassigned, In Progress, Resolved Today) with interactive 1-click filter toggling and active filter chips.
  2. Implement default sorting (Overdue first, then soonest due date, then priority) with an interactive sort dropdown control.
  3. Style overdue table rows with an accent red border and subtle tint; compact location and student into a single line (`"A-101 · Priya Sharma"`).
  4. Replace repetitive `NEW` badges with count filter chips; add category icons, allow 2-line descriptions, rename "AUTO" to "Auto-assign" with explanatory tooltip, and show assigned staff name.
  5. Add row checkboxes for bulk actions (bulk assign, bulk priority update); collapse exports into an "Export" dropdown; flag possible duplicates; optimize the detail drawer with top-placed staff assignment, SLA countdown timer, and interactive lifecycle stepper.
- **Docs**: `docs/CODEMAP.md`
- **Verify**: `./quick.sh web` + verify Admin Triage and All Complaints tables in browser.
- **Rollback**: `git checkout -- hcms-web/src/main/resources/public/js/admin.js hcms-web/src/main/resources/public/css/style.css`

---

### [x] P3 Student Experience & Complaint Submission Redesign
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/js/student.js`
  - `hcms-web/src/main/resources/public/css/style.css`
  - `hcms-web/src/main/resources/public/index.html`
- **Do**:
  1. Streamline student complaint cards by replacing the vertical stepper with a compact status indicator and thin progress bar; remove redundant student name line; deduplicate filed/updated timestamps.
  2. Overhaul detail drawer: replace self-student info with assigned staff details, expected resolution target, and "What happens next" milestone guide; keep full stepper in drawer.
  3. Consolidate "History" into a streamlined "Closed & Ratings" tab in My Complaints; update KPI counter to "Open Complaints"; format rating display as `"5.0 (from N ratings)"` or hide when < 3.
  4. Enhance New Complaint form: add "Is this a safety risk?" priority elevation checkbox, confirmed room text with edit link, location selector (My room vs. Common area with description), drag-drop image upload with live thumbnail preview, and client-side duplicate check warning.
  5. Add actionable "Fixed? Confirm or Reopen" banner on resolved complaints with rating submission; convert hostel block in profile modal into a validated dropdown.
- **Docs**: `docs/CODEMAP.md`
- **Verify**: `./quick.sh web` + test student complaint submission, drawer, and rating in browser.
- **Rollback**: `git checkout -- hcms-web/src/main/resources/public/js/student.js hcms-web/src/main/resources/public/css/style.css`

---

### [ ] P4 Staff Task Management & History Unification
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/js/staff.js`
  - `hcms-web/src/main/resources/public/css/style.css`
  - `hcms-web/src/main/resources/public/index.html`
- **Do**:
  1. Restructure staff task cards to prioritize location (`"Block B · Room 410"`) with clear reporter labeling (`"Reported by Karthik Reddy"`).
  2. Implement task sorting (overdue first, high priority, soonest due) and group cards visually into 3 clear queues: "Needs Action Now" (Assigned), "In Progress", and "Reopened".
  3. Render student reopen reasons directly inside an alert box on reopened task cards.
  4. Merge "Completed" and "Task History" into a unified "History" view with status filter; standardize timestamps to relative (< 7 days) and absolute formatted dates.
  5. Define distinct CSS styles for card hover, focus, and selected states; add informative tooltip to the Availability toggle ("Off pauses automatic assignment to you").
- **Docs**: `docs/CODEMAP.md`
- **Verify**: `./quick.sh web` + test staff task cards, transitions, and history in browser.
- **Rollback**: `git checkout -- hcms-web/src/main/resources/public/js/staff.js hcms-web/src/main/resources/public/css/style.css`

---

### [ ] P5 Global Design System, Typography, Contrast & Responsive Layout
- **Type**: UI
- **Files to touch**:
  - `hcms-web/src/main/resources/public/css/style.css`
  - `hcms-web/src/main/resources/public/js/icons.js`
  - `hcms-web/src/main/resources/public/index.html`
- **Do**:
  1. Refine design tokens in `style.css` to reduce visual noise: soften neo-brutalist borders/shadows, reserve thick borders for active selections, replace all-caps with clean sentence case (keeping uppercase for table headers and badges only), and use regular fonts for dates.
  2. Replace scattered emoji icons with a consistent line-icon set via `icons.js`.
  3. Perform contrast audits on all badge states (LOW, MEDIUM, RESOLVED) to ensure compliance with WCAG AA (minimum 4.5:1 text-to-background contrast).
  4. Verify and optimize layouts at phone width (360px), ensuring table horizontal scrolling / card collapsing and mobile drawer accessibility.
  5. Add unified loading skeletons and empty state placeholders across all complaint and task lists.
- **Docs**: `docs/CODEMAP.md`
- **Verify**: `./quick.sh web` + mobile browser simulation at 360px width + contrast checks.
- **Rollback**: `git checkout -- hcms-web/src/main/resources/public/css/style.css hcms-web/src/main/resources/public/js/icons.js`

---

### [ ] P6 Documentation Audit & Codemap Sync
- **Type**: DOCS
- **Files to touch**:
  - `docs/CODEMAP.md`
  - `docs/DESIGN.md`
  - `docs/WORKFLOWS.md`
  - `AGENTS.md`
  - `README.md`
- **Do**:
  1. Run `./gen-codemap.sh` to refresh class and module indexes.
  2. Run `./quick.sh docs` to validate all markdown hyperlinks and workflow integrity.
  3. Reconcile `README.md`, `docs/DESIGN.md`, and `AGENTS.md` with the updated UI architecture and priority escalation logic.
  4. Update `docs/LESSONS.md` with any gotchas encountered during implementation.
- **Docs**: `docs/CODEMAP.md`, `docs/DESIGN.md`, `docs/WORKFLOWS.md`, `AGENTS.md`, `README.md`
- **Verify**: `./quick.sh docs`
- **Rollback**: `git checkout -- docs/ AGENTS.md README.md`

---

## 7. Risks & Mitigations

1. **Static Asset Caching in Fat JAR**: Javalin serves static assets (`style.css`, `.js`) from fat JAR classpath; running live changes requires `mvn package -DskipTests` or browser cache bypass (`Ctrl+Shift+R`).
   *Mitigation*: Document verification steps using incognito/hard-refresh and rebuild commands in phase instructions.
2. **Priority Serialization Mismatch**: Inconsistent serialization (`Priority.name()` vs `Priority.getLevel()`) across disparate controllers could reintroduce display bugs.
   *Mitigation*: Implement a robust defensive normalizer in `app.js` (`getPriorityInfo()`) that gracefully parses both string and integer formats.
3. **Admin Table Width on Mobile**: Extensive admin columns (Status, Priority, Location, Student, Actions) overflow on 360px screens.
   *Mitigation*: Wrap table in an accessible horizontally scrollable container with sticky action column, or convert to responsive cards below 640px breakpoint.

---

## 8. Progress Log

| Date | Phase | Status | Summary of Changes | Files Touched |
| :--- | :--- | :--- | :--- | :--- |
| 2026-10-22 | P1 | Complete | Unified priority resolution via `getPriorityInfo()`, added emergency auto-escalation keywords (`flood`, `sparks`) with history audit trail, fixed `.badge` positioning and overdue styles, improved profile availability slider, restricted dev metrics footer to admin, formatted timeline statuses. Next phase (P2): use `getPriorityInfo` and `formatStatus` helpers. Docs updated: `docs/CODEMAP.md`, `docs/LESSONS.md`. | `ComplaintManager.java`, `ComplaintManagerTest.java`, `admin.js`, `staff.js`, `student.js`, `history.js`, `app.js`, `toast.js`, `style.css`, `index.html` |
| 2026-10-22 | P2 | Complete | Implemented top KPI summary strip with 1-click filter toggling and sort dropdowns, overdue row accent borders, compact location/student format, category icons, 2-line clamped descriptions, sticky bulk action bar for assignment/priority, collapsible export dropdown, duplicate detection, and top-placed staff assignment drawer with SLA countdown timer and interactive lifecycle stepper. Docs updated: `docs/CODEMAP.md`, `docs/LESSONS.md`. | `admin.js`, `style.css`, `index.html` |
| 2026-10-22 | P3 | Complete | Overhauled student complaint cards with compact progress indicator & progress bar, removed redundant student name line, deduplicated timestamps; revamped student detail drawer with assigned staff card, SLA countdown/target, and milestone guide; added 4-card KPI summary strip with formatted rating ('★ 5.0 (from N ratings)' or hidden when < 3); consolidated history into 'Closed & Ratings'; upgraded submission modal with safety hazard elevation, confirmed room with edit link, location selector (My Room vs Common Area), drag-and-drop photo dropzone with live preview, and real-time duplicate check; converted hostel block in profile modal to a validated dropdown. Next phase (P4): restructure staff task cards and merge task history. Docs updated: `docs/CODEMAP.md`. | `student.js`, `style.css`, `index.html` |


