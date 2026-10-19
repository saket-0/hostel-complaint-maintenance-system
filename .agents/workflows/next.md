---
description: Execute the next unchecked phase of the active epic plan, verify, update the plan file, then stop.
---

# /next: Run one phase

1. Find the plan: the one the user names, otherwise the most recently modified docs/plans/*/PLAN.md. Read ONLY that file, plus the files listed for the phase. Do not re-explore the repo.
2. Check the branch matches the plan header (`git branch --show-current`). If on main, create it: `git checkout -b <branch>`. If the working tree has unrelated uncommitted changes, stop and tell the user.
3. Pick the first unchecked phase. Print a scope block (REQUEST, TO EDIT, READ-ONLY, OUT OF SCOPE, VERIFY, RISK) taken from the plan.
4. Execute with the smallest diff, following the rules that match the phase Type (UI -> ui.md, ENDPOINT -> endpoint.md, others -> do.md Phase 4). For DB or concurrency phases, read docs/LESSONS.md first. If a bug appears mid-phase, follow bug.md steps within the 3-file limit; if unresolved, stop and log it in the Progress log. If the phase needs files outside its list or is bigger than M, STOP, split the phase in PLAN.md, and report.
5. Run only the phase's Verify command. If it fails, fix at most twice, then report. Also update every doc listed in the phase's Docs field, then run ./quick.sh docs only if a doc was edited.
6. On success: tick the checkbox in PLAN.md, append to the Progress log: date, files changed, anything the next phase must know (renamed things, new helpers, gotchas). If you learned a non-obvious gotcha, append one line to docs/LESSONS.md. Record in the Progress log which docs were updated. If classes were added/renamed, run ./gen-codemap.sh.
7. Commit only if `auto-commit: yes` in the plan header, with message `epic(<slug>): P<n> <title>`. Otherwise print a suggested commit message.
8. STOP. Reply max 6 lines, ending with: "Next: P<n+1> <title>. Start a new chat and run /next."
