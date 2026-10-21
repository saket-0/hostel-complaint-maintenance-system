# Lessons (agent gotchas)

Append one line per non-obvious gotcha: `<date> <area>: <lesson>`.
Keep under 40 lines. Merge duplicates. Delete lessons that are no longer true.

2026-10-20 web: Javalin serves static assets from fat JAR classpath; css changes require 'mvn package' and server restart.
2026-10-21 web: Javalin queryParamAsClass uses final Kotlin methods that fail under Mockito; parse queryParam directly with safe defaults.
