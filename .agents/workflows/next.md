---
description: Execute the next unchecked phase of the active epic plan, verify, update the plan file, then stop.
---

# /next: Run one phase

1. Find the plan: the one the user names, otherwise the most recently modified docs/plans/*/PLAN.md. Read ONLY that file, plus the files listed for the phase. Do not re-explore the repo.
2. Check the branch matches the plan header (`git branch --show-current`). If on main, create it: `git checkout -b <branch>`. If the working tree has unrelated uncommitted changes, stop and tell the user.
3. Pick the first unchecked phase. Print a scope block (REQUEST, TO EDIT, READ-ONLY, OUT OF SCOPE, VERIFY, RISK) taken from the plan.
4. Execute with the smallest diff. If the phase needs files outside its list, or turns out bigger than M, STOP, split the phase in PLAN.md, and report. Do not push through.
5. Run only the phase's Verify command. If it fails, fix at most twice, then report.
6. On success: tick the checkbox in PLAN.md, append to the Progress log: date, files changed, anything the next phase must know (renamed things, new helpers, gotchas). If classes were added/renamed, run ./gen-codemap.sh.
7. Commit only if `auto-commit: yes` in the plan header, with message `epic(<slug>): P<n> <title>`. Otherwise print a suggested commit message.
8. STOP. Reply max 6 lines, ending with: "Next: P<n+1> <title>. Start a new chat and run /next."
