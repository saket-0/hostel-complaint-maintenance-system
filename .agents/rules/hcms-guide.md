# HCMS Workspace Guide & Agent Rules

## Architecture (web -> service -> persistence -> core)
- **core**: Models, enums, interfaces, exceptions. No framework/JDBC imports.
- **persistence**: JDBC DAOs, HikariCP (`DatabaseManager`), Flyway migrations.
- **service**: Business logic (`ComplaintManager`), SLA engine, strategies, notifications.
- **web**: Javalin controllers, filters, DTOs, static SPA.

## Where Things Live (Verified Paths)
- **Logic**: `hcms-service/src/main/java/edu/vit/hcms/service/complaint/ComplaintManager.java`, `hcms-service/src/main/java/edu/vit/hcms/service/user/UserManager.java`
- **State Machine**: `hcms-core/src/main/java/edu/vit/hcms/core/enums/ComplaintStatus.java`
- **Strategies**: `hcms-service/src/main/java/edu/vit/hcms/service/strategy/` (`SpecializationMatchStrategy.java`, `LeastWorkloadStrategy.java`)
- **SLA Engine**: `hcms-service/src/main/java/edu/vit/hcms/service/engine/SlaEscalationEngine.java`
- **DAOs**: `hcms-persistence/src/main/java/edu/vit/hcms/persistence/dao/` (`ComplaintDao.java`, `UserDao.java`, `SupportDao.java`)
- **Controllers**: `hcms-web/src/main/java/edu/vit/hcms/web/controller/{Admin,Auth,Complaint,Health,Notification,Staff,User}Controller.java`
- **Frontend JS**: `hcms-web/src/main/resources/public/js/{admin,api,app,auth,dates,history,icons,metrics,notifications,staff,student,toast}.js`
- **Frontend UI**: `hcms-web/src/main/resources/public/index.html` & `hcms-web/src/main/resources/public/css/style.css`
- **Migrations**: `hcms-persistence/src/main/resources/db/migration/` (`V1__create_schema.sql`, `V2__seed_data.sql`)

## Strict Reading & Execution Rules
1. **Start with `docs/CODEMAP.md`**; no broad scanning.
2. **Grep symbols first**; read needed lines only (<300 lines).
3. **Never re-read** files in same session.
4. **Never edit applied migrations (`V1`, `V2`)**; add sequential `V{n}__*.sql`.
5. **Never read**: `target/`, `logs/`, `node_modules/`, `chart.min.js`, `*.log`.
6. **Cheap verification**: `./quick.sh <module|test>`.
7. **Pass -> stop** immediately.
8. **Two-line declaration before editing**: state files and why.
9. **Never commit or push** without explicit permission.
10. **Final reply**: max 6 lines ending with `Docs:` line.

## Default behavior & Lessons
For non-questions, follow `.agents/workflows/do.md`: triage, print scope block, execute. Skip scope block for trivial one-line edits in a named file. L-sized / architectural requests: tell user to run `/epic` and stop. Read `docs/LESSONS.md` before bug, DB, or concurrency tasks. Append learned gotchas to `docs/LESSONS.md`.

## Workflow routing
- Slash commands override `/do`. Router: general -> `/do`; bug -> `/bug`; locate/question -> `/find`; known file -> `/fix`; API -> `/endpoint`; UI -> `/ui`; L-sized/architecture -> `/epic`, then `/next`.
- Delegation: `/do` dispatches by type (BUG -> `bug.md`, ENDPOINT -> `endpoint.md`, UI -> `ui.md`, QUESTION -> `find.md`; else self-executes). `/next` follows phase Type.
- Workflow files don't count toward file limits. Fit: S/M -> follow in same chat; L -> stop and route to `/epic`. Escalation: `/fix`, `/bug`, `/endpoint`, `/ui` -> `/do` -> `/epic`.
- Tools: browser tool only for visual check at end. Skills only on exact match.

## Doc sync (Definition of Done)
Doc updates never count toward file-open limits or the 2-file /fix guard. Only touch a doc if a row in the table below is actually triggered by this change. If none is triggered, write `Docs: none needed` and do nothing else. Editing wording, fixing a typo, changing CSS, or changing logic inside a method without changing its signature triggers NO doc update.
| If you changed... | Update |
| :--- | :--- |
| Java or JS file added/renamed/deleted | run `./gen-codemap.sh`; fix moved paths in `AGENTS.md` and this rule |
| REST endpoint, DTO, or error code | `docs/API.md` (and README feature text if user-visible) |
| Architecture, patterns, concurrency, state machine, boundaries | `docs/DESIGN.md`, README sections 2-6 (diagrams, concepts), `AGENTS.md` |
| DB schema or new migration | README ER diagram, `docs/DESIGN.md`, `AGENTS.md` migration list |
| Verification or build scripts | `AGENTS.md` cheat sheet, `docs/WORKFLOWS.md` section 3, this rule |
| Workflow or rule added/changed/removed | `docs/WORKFLOWS.md` (matrix, sec 5), routing block above |
| Docker, ports, env vars, demo credentials | README sections 7, 8, 11, and `.env.example` |
| Ignore rules | keep `.gitignore`, `.agentignore`, `.geminiignore` consistent; `chart.min.js` stays tracked |
| Non-obvious gotcha learned / corrected by user | append one line to `docs/LESSONS.md` |

Run `./quick.sh docs` only when a doc file was actually edited in this task, never otherwise. If it reports problems unrelated to this task, do not fix them; mention in one line in the final reply. Every final reply ends with `Docs: <files updated> | none needed`.
