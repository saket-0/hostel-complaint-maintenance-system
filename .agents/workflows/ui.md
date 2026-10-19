---
description: Frontend-only change (JS, HTML, CSS) with cheap checks. Never touches the backend.
---

# /ui: Frontend only

The user's request follows the command.
Scope: hcms-web/src/main/resources/public/js/*.js, index.html, css/style.css. Keep chart.min.js untouched.
1. Use docs/CODEMAP.md for file names, grep for the element/function, read only line ranges. Never open a whole file over 300 lines.
2. Touch only the frontend files above. No Java, pom, or migrations.
3. If the UI needs data or an endpoint that does not exist, STOP and tell the user to run /endpoint first, naming the contract you need.
4. If the request restyles several screens or changes the design system, tell the user to run /epic and stop.
5. Verify cheaply: `node --check <changed.js>` if node is available. Use the browser tool only for visual changes, once at the end, at 375px and 1280px. Javalin serves assets from the jar, so rebuild (`mvn package -DskipTests`) and restart to see changes in a running server.
6. Doc sync: if a JS file was added, renamed or removed, update the frontend list in AGENTS.md and in the rules, and run ./gen-codemap.sh. If user-visible behavior changed, update the README UI section. Run ./quick.sh docs if docs changed.
7. Do not commit. Reply max 4 lines, ending with the Docs line.
