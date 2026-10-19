---
description: Small targeted change in known files. Escalates instead of widening.
---

# /fix: Targeted change

The user's request follows the command, including file names if given. If no files were named, locate them with docs/CODEMAP.md plus grep (max 3 file opens).
1. Declare in 2 lines which files you will touch and why.
2. Read only needed line ranges. Never open a whole file over 300 lines unless editing most of it.
3. Guard: if the change needs more than 2 files, a migration, a ComplaintStatus transition, or an hcms-core model change, STOP. Tell the user to run /do (if M) or /epic (if L). If the cause is unknown rather than the change, follow bug.md instead.
4. Smallest diff. No refactors, reformatting, or touching other modules.
5. Verify with `./quick.sh test <TestClass>` or `./quick.sh <module>`. Pass -> stop. Fail -> fix at most twice, then report.
6. Doc sync: apply the Doc sync table from the rules. This includes fixing a stale path in AGENTS.md, CODEMAP.md or the rules when /find reported one. Run ./quick.sh docs if docs changed.
7. Do not commit or push. Reply max 4 lines, ending with the Docs line.
