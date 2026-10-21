# HCMS Agent Workflows & Commands Guide

A quick-reference guide for developers and pair-programmers working with AI agents in the Hostel Complaint & Maintenance System (HCMS).

---

## 1. Quick Decision Matrix

| What You Want To Do | Use Workflow / Command | What Happens |
| :--- | :--- | :--- |
| **"I have a general request / not sure where to start"** | `/do <request>` | **Auto-triage**: classifies, sizes, prints scope block, then executes with minimal file reads. |
| **"Something is broken / behavior is wrong"** | `/bug <symptom>` | **Hypothesis-driven debug**: checks `docs/LESSONS.md`, max 3 files inspected, writes regression test, fixes, stops. |
| **"Where is feature X implemented? (No edits)"** | `/find <feature>` | **Read-only search**: uses grep/ripgrep, reports file paths and line numbers without touching code. |
| **"I know exactly which file/method needs fixing"** | `/fix <task>` | **Targeted fix**: touches only declared files, smallest diff, runs `./quick.sh`. |
| **"Add a new API endpoint"** | `/endpoint <details>` | **Layered REST endpoint**: Controller $\to$ DTO $\to$ Service $\to$ DAO (if needed) $\to$ unit test. |
| **"Tweak buttons, CSS, modals, student/staff UI"** | `/ui <details>` | **Frontend isolation**: touches only `hcms-web/src/main/resources/public/`. Backend code remains untouched. |
| **"Major feature, redesign, DB schema change (Size L)"** | `/epic <large goal>` | **Architecture plan only**: writes `docs/plans/<slug>/PLAN.md` split into bite-sized phases ($P_1, P_2\dots$). Writes no code. |
| **"Run the next phase of an active epic"** | `/next` | **Phase execution**: runs in a **new chat**, executes only the next unchecked phase, verifies, updates plan, and stops. |
| **"Verify documentation links and sync"** | `./quick.sh docs` | **Doc integrity**: checks backticked file paths, workflow parity, and slash command completeness. |

---

## 2. Detailed Scenario Guide

### Scenario A: General Work — `/do` (Default)
**When to use:** Whenever you have a feature or enhancement that fits in a single session.
```text
/do Add an export button for staff task history in staff.js
```
- **Behavior:** The agent classifies the task (UI, ENDPOINT, LOGIC, etc.), calculates the size (S, M, or L), prints a scope block with exact files, and executes with minimal file reading.
- *Note:* If the task is sized **L** or touches architecture, `/do` will stop and prompt you to run `/epic` instead.

---

### Scenario B: Something Broke — `/bug`
**When to use:** A test failed, an error toast appeared, or business rules are misbehaving.
```text
/bug Reopening a resolved complaint fails if submitted on the 6th day
```
- **Behavior:**
  1. Checks `docs/LESSONS.md` and `docs/CODEMAP.md`.
  2. Forms top 2 hypotheses and checks at most 3 files.
  3. Writes a reproducing unit test first.
  4. Applies the smallest fix and verifies with `./quick.sh test <TestClass>`.
  5. Records non-obvious gotchas to `docs/LESSONS.md`.

---

### Scenario C: Exploration Only — `/find`
**When to use:** You want to know which files handle a feature before making any decision.
```text
/find Where is the SLA escalation background thread started and stopped?
```
- **Behavior:** Searches with grep/ripgrep, opens at most 3 files to inspect exact lines, and reports line numbers without making any code edits. If paths are stale in docs, suggests `/fix`.

---

### Scenario D: Precision Edit — `/fix`
**When to use:** You already know the bug or change and the exact file that owns it.
```text
/fix Change maximum reopen window from 5 days to 7 days in ComplaintActionPolicy.java
```
- **Behavior:** Declares files before touching, reads only the relevant line range, makes minimal diff, verifies with `./quick.sh`, updates affected docs, and stops.

---

### Scenario E: Adding REST APIs — `/endpoint`
**When to use:** Adding new backend functionality for the frontend to consume.
```text
/endpoint POST /api/complaints/{id}/escalate to allow manual escalation by admin
```
- **Layer Flow:**
  `Controller` $\to$ `DTO` $\to$ `ComplaintManager / Service` $\to$ `DAO` (only if new query needed) $\to$ `Unit Test`.
- Verifies with `./quick.sh test <TestClass>` or `./quick.sh web`.
- Outputs a 4-line API contract (method, path, request, response) and updates `docs/API.md`.

---

### Scenario F: Frontend Only — `/ui`
**When to use:** Styling, layouts, form validation, client-side filters, modals and management screens (e.g. Profile Modal, Complaint Drawer, User & Staff Management Screen).
```text
/ui Add status badge color highlights for URGENT complaints in student timeline
```
- **Behavior:** Guaranteed zero edits to Java backend, `pom.xml`, or database. Touches only `hcms-web/src/main/resources/public/`. Handles client-side state synchronization (e.g. top-bar profile avatars, badge triggers, dynamic modals, staff toggles, student directory tables).

---

### Scenario G: Large Multi-Step Initiatives — `/epic` & `/next`
**When to use:** Any large feature, major UI overhaul, database migration, or cross-layer refactor.

#### Step 1: Create the Plan with `/epic`
```text
/epic Redesign complaint submission with multi-step category selection and attachment previews
```
- Explores `docs/CODEMAP.md`, `AGENTS.md`, and relevant files.
- Creates `docs/plans/<kebab-slug>/PLAN.md` with small phases (`P1`, `P2`, `P3`...), verification commands, and rollback instructions.
- Sizing gate: if discovery shows work is S or M, suggests `/do` and stops.

#### Step 2: Execute Each Phase with `/next`
- Open a **brand new chat session** for each phase.
- Simply type:
  ```text
  /next
  ```
- The agent reads only the plan and the phase's files, executes according to phase Type, verifies with `./quick.sh`, updates docs listed in `Docs:`, marks `[x] P<n>`, logs notes, and stops.

---

## 3. Fast Verification Commands Cheat-Sheet

Avoid running slow full builds (`mvn clean verify` or `test.sh`). Use the instant targeted scripts:

| Command | Target / Purpose | Approx Speed |
| :--- | :--- | :--- |
| `./quick.sh check` / `make quick-compile` | Fast-compiles all 4 modules (skips tests/containers) | ~3s |
| `./quick.sh service` / `make quick-service` | Fast-compiles `hcms-service` and dependencies | ~3s |
| `./quick.sh web` / `make quick-web` | Fast-compiles `hcms-web` and dependencies | ~2s |
| `./quick.sh core` / `make quick-core` | Fast-compiles `hcms-core` | ~1s |
| `./quick.sh test <TestClass>` | Runs a single unit test class directly | ~3-4s |
| `make quick-test test=<TestClass>` | Alternative Make target for single test | ~3-4s |
| `./quick.sh docs` / `make quick-docs` | Validates doc links, workflow files, and path integrity | ~1s |
| `./gen-codemap.sh` | Regenerates `docs/CODEMAP.md` after class changes | ~1s |

---

## 4. Key Developer Gotchas & Tips

1. **Javalin Static Assets**: Javalin serves static assets (`hcms-web/src/main/resources/public/css/style.css`, etc.) from the fat JAR classpath at runtime. When running the local fat JAR, run `mvn package -DskipTests` and restart the server to see CSS/JS changes.
2. **Fresh Conversations**: Always start a fresh chat when switching tasks or running `/next`. Long chats accumulate stale context and slow down responses.
3. **Applied Migrations**: Never modify `V1__create_schema.sql` or `V2__seed_data.sql`. Always create `V{n}__<name>.sql` under `hcms-persistence/src/main/resources/db/migration/`.
4. **Offline Vendor Assets**: `chart.min.js` must remain committed for offline charts, but is ignored in `.agentignore` and `.geminiignore` so agents do not waste context tokens reading it.

---

## 5. How Workflows Connect

- **Router**:
  - `general/unknown` $\to$ `/do`
  - `bug/breakage` $\to$ `/bug`
  - `"where is X" / question` $\to$ `/find`
  - `known file/small change` $\to$ `/fix`
  - `new API` $\to$ `/endpoint`
  - `frontend only` $\to$ `/ui`
  - `L-sized / redesign / schema / state machine / core model` $\to$ `/epic`, then `/next`.
- **Delegation**: `/do` dispatches by type (BUG $\to$ `.agents/workflows/bug.md`, ENDPOINT $\to$ `.agents/workflows/endpoint.md`, UI $\to$ `.agents/workflows/ui.md`, QUESTION $\to$ `.agents/workflows/find.md`; else executes itself). `/next` follows phase Type the same way.
- **Escalation Chain**: Escalation only goes up:
  `/fix`, `/bug`, `/endpoint`, `/ui` $\to$ `/do` $\to$ `/epic`.
- **Handoff Outputs**:
  - `/find` outputs exact line numbers and suggests the next command (e.g. `/fix <task> in <path>` or `/do <request>`).
  - `/endpoint` outputs a 4-line API contract (method, path, request body, response body) and suggests `/ui` for frontend integration.
  - `/epic` phases carry `Type` and `Docs` fields that `/next` strictly follows.
- **Doc Sync (Definition of Done)**:
  Updating affected docs is part of every task, never "out of scope":
  | If you changed... | Update |
  | :--- | :--- |
  | Java or JS file added/renamed/deleted | run `./gen-codemap.sh`; fix moved paths in `AGENTS.md` and `.agents/rules/hcms-guide.md` |
  | REST endpoint, DTO, or error code | `docs/API.md` (and README feature text if user-visible) |
  | Architecture, patterns, concurrency, state machine, boundaries | `docs/DESIGN.md`, README sections 2-6 (diagrams, concepts), `AGENTS.md` |
  | DB schema or new migration | README ER diagram, `docs/DESIGN.md`, `AGENTS.md` migration list |
  | `quick.sh`, `Makefile`, `run.sh`, `test.sh` commands | `AGENTS.md` cheat sheet, `docs/WORKFLOWS.md` section 3, `.agents/rules/hcms-guide.md` |
  | Workflow or rule added/changed/removed | `docs/WORKFLOWS.md` (matrix, scenarios, sec 5), routing blocks |
  | Docker, ports, env vars, demo credentials | README sections 7, 8, 11, and `.env.example` |
  | Ignore rules | keep `.gitignore`, `.agentignore`, `.geminiignore` consistent; `chart.min.js` stays tracked |
  | Non-obvious gotcha learned / corrected by user | append one line to `docs/LESSONS.md` |

Verify all doc edits with `./quick.sh docs`.
