---
description: Debug a defect with a bounded, hypothesis-driven loop and a regression test.
---

# /bug: Find and fix a defect

The user's bug report follows the command. Do not ask the user where the bug is. You find it.

## Steps

1. **Restate** the symptom and the expected behavior in two lines.
2. **Read context**: docs/LESSONS.md and docs/CODEMAP.md. Nothing else yet.
3. **Hypothesize**: state the top 2 hypotheses. For each, name the single file and method you will check first.
4. **Locate**: grep/ripgrep for the symbol, then read only the relevant line range. Max 3 file opens. Never open a whole file over 300 lines.
5. **Stop rule**: if the cause is not found after 3 file opens, STOP. Report what you checked, what you ruled out, and your next best hypothesis. Do not widen the search on your own.
6. **Reproduce**: write a failing test first when feasible (unit test in the matching module). If a test is not feasible (UI-only or environment bug), say how you confirmed the cause instead.
7. **Fix**: smallest possible diff. No refactors, no unrelated cleanup. Respect module boundaries (core has no framework/JDBC imports; never edit applied Flyway migrations, add a new V{n}).
8. **Verify**: run only the targeted command, for example `./quick.sh test <TestClass>`. If it passes, stop. If it fails, fix at most twice, then report instead of looping.
9. **Learn**: if the root cause was non-obvious, append one line to docs/LESSONS.md in the form `<date> <area>: <lesson>`.
10. Do not commit or push.

## Final reply (max 6 lines)
Cause, fix (file and method), test added, verify result, lesson added (yes/no).
