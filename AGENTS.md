# HCMS: Agent Guide (READ THIS FIRST, DO NOT RE-EXPLORE THE REPO)

## Architecture (One-Way Dependencies: web -> service -> persistence -> core)
- **hcms-core**: Domain models, enums, interfaces, exceptions. NO framework, Javalin, or JDBC imports.
- **hcms-persistence**: JDBC DAOs, HikariCP pool (`DatabaseManager`), Flyway migrations (`V1__create_schema.sql`, `V2__seed_data.sql`).
- **hcms-service**: Business logic (`ComplaintManager`), SLA escalation engine, auto-assignment strategies, notification dispatch.
- **hcms-web**: Javalin controllers, auth filters, request/response DTOs, static SPA frontend.

---

## Where Things Live (Verified Paths - Consult docs/CODEMAP.md for Full Map)
- **Complaint Business Logic**: `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`
- **User Governance Service**: `hcms-service/src/main/java/edu/vit/hcms/service/user/UserManager.java`
- **Status State Machine**: `hcms-core/src/main/java/edu/vit/hcms/core/enums/ComplaintStatus.java`
- **Auto-Assignment Strategies**: `hcms-service/src/main/java/edu/vit/hcms/service/strategy/` (`SpecializationMatchStrategy.java`, `LeastWorkloadStrategy.java`)
- **SLA & Escalation Engine**: `hcms-service/src/main/java/edu/vit/hcms/service/engine/SlaEscalationEngine.java`
- **DAOs & DB Access**: `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/` (`ComplaintDao.java`, `UserDao.java`, `SupportDao.java`)
- **REST Controllers**: `hcms-web/src/main/java/edu/vit/hcms/web/controller/{Admin,Auth,Complaint,Health,Notification,Staff,User}Controller.java`
- **Frontend JavaScript**: `hcms-web/src/main/resources/public/js/{admin,api,app,auth,dates,history,icons,metrics,notifications,staff,student,toast}.js`
- **Frontend Entry & UI**: `hcms-web/src/main/resources/public/index.html` & `hcms-web/src/main/resources/public/css/style.css`
- **DB Migrations**: `hcms-persistence/src/main/resources/db/migration/` (`V1__create_schema.sql`, `V2__seed_data.sql`)

---

## Strict Agent Rules
1. **Never edit applied Flyway migrations**. Add a new sequential `V{n}__<description>.sql` instead.
2. **Do not scan or re-explore the entire repo**. Open only the specific files relevant to the active task.
3. **Never read or index**: `target/`, `logs/`, `node_modules/`, `*.log`, or minified files like `chart.min.js`.
4. **Do not run full build or full test suites** (`mvn verify`, `mvn clean test`, `test.sh`) unless explicitly requested. Full verification spins up Testcontainers and takes minutes.
5. **Use cheap verification**:
   - Compile one module: `./quick.sh service` or `make quick-service`
   - Fast compile all: `./quick.sh check` or `make quick-compile`
   - Test one class: `./quick.sh test ComplaintManagerTest` or `make quick-test test=ComplaintManagerTest`
6. **Make the smallest diff that solves the task**. No unsolicited refactorings, style changes, or formatting passes.
7. **Two-line declaration before editing**: Before modifying files, state in 2 concise lines which files you will touch and why.
8. **Never commit or push changes to git/GitHub** without explicit user permission.

---

## Split Work by Module
Always restrict task scope to the layer where the change belongs:
- **UI Tweaks / Frontend Features**: Touch only `hcms-web/src/main/resources/public/js/*.js` or `index.html`. Do not touch backend code.
- **New REST Endpoint**: Touch Controller -> DTO -> Service method -> (DAO if new query needed). Do not modify core models unless a new enum state is required.
- **Business Logic / Workflow**: Touch `ComplaintManager.java` and its unit test `ComplaintManagerTest.java`. Do not touch controllers or DAOs.
- **State Transition Changes**: Update transitions in `ComplaintStatus.java` and verify with `ComplaintStatusTest.java`.
- **Database Schema**: Add a new `V{n}__*.sql` under `hcms-persistence/.../db/migration/` and update corresponding DAO/Model.

---

## Fast Verification Commands Cheat-Sheet
| Target | Fast Command | Alternative / Make |
| :--- | :--- | :--- |
| **hcms-core** | `./quick.sh core` | `make quick-core` |
| **hcms-persistence** | `./quick.sh persistence` | `make quick-persistence` |
| **hcms-service** | `./quick.sh service` | `make quick-service` |
| **hcms-web** | `./quick.sh web` | `make quick-web` |
| **All modules (compile only)** | `./quick.sh check` | `make quick-compile` |
| **Single test class** | `./quick.sh test <TestClass>` | `make quick-test test=<TestClass>` |
| **Single test in module** | `./quick.sh test <TestClass> <module>` | `mvn -q -pl hcms-<module> -am test -Dtest=<TestClass> -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` |
| **Documentation sync** | `./quick.sh docs` | `make quick-docs` |

---

## Task Prompting Template (For Users & Agents)
When providing instructions or creating subagent prompts, use this scoped structure:

```text
Task: <one-sentence description of the exact change>
Files to touch: <explicit file paths>
Do NOT: scan the repo, refactor unrelated code, reformat, or touch other modules.
Verify with: ./quick.sh test <TestClass> (or ./quick.sh <module>)
```

*If the target file is unknown:*
Ask with a 2-step prompt first:
> "Using ripgrep/grep search only, find which files handle [FEATURE]. Do not open more than 3 files."
Then proceed with the scoped edit prompt above.
