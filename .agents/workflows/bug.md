---
description: Debug a defect with a bounded, hypothesis-driven loop and a regression test.
---

# /bug: Find and fix a defect

The user's bug report follows the command. Do not ask where the bug is. You find it.

1. Restate the symptom and expected behavior in two lines.
2. Read docs/LESSONS.md and docs/CODEMAP.md. Nothing else yet.
3. State the top 2 hypotheses, each with the single file and method to check first.
4. grep for the symbol, read only the relevant line range. Max 3 file opens. Never open a whole file over 300 lines.
5. Stop rule: if the cause is not found after 3 file opens, STOP. Report what you checked, what you ruled out, and the next best hypothesis, and suggest `/find <that hypothesis>`.
6. Write a failing test first when feasible. If not feasible, say how you confirmed the cause.
7. Smallest possible fix. No refactors. Respect module boundaries (core has no framework/JDBC imports; never edit applied Flyway migrations, add a new V{n}).
8. Verify with the targeted command, e.g. `./quick.sh test <TestClass>`. Pass -> stop. Fail -> fix at most twice, then report.
9. Learn: if the root cause was non-obvious, append one line to docs/LESSONS.md: `<date> <area>: <lesson>`.
10. Doc sync: apply the Doc sync table from the rules (for example API.md if behavior of an endpoint changed). Run ./quick.sh docs if docs changed.
11. Do not commit or push.

## Escalation
- Fix needs more than 2 files, a migration, a ComplaintStatus transition or an hcms-core model change: stop after finding the cause and tell the user to run /epic with the cause included.
- The "bug" is a missing feature: say so in one line and follow do.md.
- UI-only bug: follow ui.md rules.

## Final reply (max 6 lines)
Cause, fix (file and method), test added, verify result, lesson added (yes/no), Docs line.
