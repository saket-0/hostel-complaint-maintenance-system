# HCMS Workspace Guide & Agent Rules

## Architecture (One-Way Dependencies)
`hcms-web` -> `hcms-service` -> `hcms-persistence` -> `hcms-core`
- **hcms-core**: Domain models, enums, interfaces, exceptions. No framework or JDBC imports.
- **hcms-persistence**: JDBC DAOs, HikariCP (`DatabaseManager`), Flyway migrations.
- **hcms-service**: Business logic (`ComplaintManager`), SLA engine, assignment strategies, notifications.
- **hcms-web**: Javalin controllers, filters, DTOs, static SPA frontend.

## Where Things Live (Verified Paths)
- **Complaint Business Logic**: `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`
- **Status State Machine**: `hcms-core/src/main/java/edu/vit/hcms/core/enums/ComplaintStatus.java`
- **Auto-Assignment Strategies**: `hcms-service/src/main/java/edu/vit/hcms/service/strategy/` (`SpecializationMatchStrategy.java`, `LeastWorkloadStrategy.java`)
- **SLA & Escalation Engine**: `hcms-service/src/main/java/edu/vit/hcms/service/engine/SlaEscalationEngine.java`
- **DAOs & DB Access**: `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/` (`ComplaintDao.java`, `UserDao.java`, `SupportDao.java`)
- **REST Controllers**: `hcms-web/src/main/java/edu/vit/hcms/web/controller/{Admin,Auth,Complaint,Health,Notification,Staff}Controller.java`
- **Frontend JavaScript**: `hcms-web/src/main/resources/public/js/{admin,api,app,auth,dates,history,icons,metrics,notifications,staff,student,toast}.js`
- **Frontend Entry & UI**: `hcms-web/src/main/resources/public/index.html` & `css/style.css`
- **DB Migrations**: `hcms-persistence/src/main/resources/db/migration/` (`V1__create_schema.sql`, `V2__seed_data.sql`)

## Strict Reading & Execution Rules
1. **Start by reading `docs/CODEMAP.md`**, not by listing directories or scanning the repository.
2. **Find symbols with grep/ripgrep first**, then read only the needed line range. Never open a whole file over 300 lines unless editing most of it.
3. **Never re-read a file already read in this conversation.**
4. **Never edit applied Flyway migrations (`V1`, `V2`).** Add a new sequential `V{n}__*.sql`.
5. **Never read or index**: `target/`, `logs/`, `node_modules/`, `chart.min.js`, `*.log`.
6. **Cheap verification only**: Use `./quick.sh <module>` or `./quick.sh test <TestClass>`. Never run `mvn verify` or Testcontainers unless requested.
7. **After a verification command passes, stop.** Do not re-run it or run broader suites.
8. **Two-line declaration before editing**: State in 2 concise lines which files you will touch and why.
9. **Never commit or push to git/GitHub** without explicit user permission.
10. **Final reply**: Maximum 6 lines (files changed, what, verify result).

## Default behavior
For any request that is not a pure question, follow .agents/workflows/do.md: triage, print the scope block, then execute. Skip the scope block only for trivial one-line edits in a file the user named. L-sized or architectural/redesign requests: do not execute. Tell the user in one line to run /epic with the same request, and stop.

## Lessons
Read docs/LESSONS.md before any bug, DB, or concurrency task. After fixing a non-obvious problem, or after being corrected by the user, append one line to docs/LESSONS.md.

