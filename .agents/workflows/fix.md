---
description: Scoped bug fix or logic modification with pre-declared files and quick verification.
---

# Fix Workflow

Task: {{task_description}}
Files to touch: {{files_to_touch}}

Strict Execution Rules:
1. Two-line declaration before editing: State in 2 concise lines which files you will touch and why.
2. Read only the specified line ranges using grep first. Never read entire files over 300 lines unless editing most of it.
3. Make the smallest diff that solves the task.
4. Do NOT: scan the repo, refactor unrelated code, reformat, or touch other modules.
5. Verify with: `./quick.sh test {{TestClass}}` (or `./quick.sh {{module}}`)
6. After verification passes, stop immediately.
