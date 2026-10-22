# Lessons (agent gotchas)

Append one line per non-obvious gotcha: `<date> <area>: <lesson>`.
Keep under 40 lines. Merge duplicates. Delete lessons that are no longer true.

2026-10-20 web: Javalin serves static assets from fat JAR classpath; css changes require 'mvn package' and server restart.
2026-10-21 web: Javalin queryParamAsClass uses final Kotlin methods that fail under Mockito; parse queryParam directly with safe defaults.
2026-10-22 web: Priority is serialized as enum name ('HIGH'/'MEDIUM'/'LOW') from controllers; frontend must use getPriorityInfo() to normalize both string and numeric levels.
2026-10-22 build: In multi-module Maven, packaging child module alone fails if siblings are uninstalled; always include '-am' ('mvn package -pl hcms-web -am -DskipTests').
2026-10-22 staff: Staff task list (/staff/tasks) omits student room/block; client fetches complaint detail to enrich card location and reopen notes.
2026-10-22 ui: Playwright driver downloads can fail in sandbox environments; verify mobile UI responsiveness via CSS media query audits and static layout tests.
2026-10-22 a11y: Yellow and light orange badge backgrounds fail WCAG AA with white text; dark amber text (#7A4F01) on pastel amber achieves 7:1+ contrast.

