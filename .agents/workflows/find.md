---
description: Locate files and symbols for a feature, or answer a question about the code, without modifying anything.
---

# /find: Locate, do not edit

The user's question follows the command.
1. Read docs/CODEMAP.md first.
2. Use grep/ripgrep. Open at most 3 files, only relevant line ranges.
3. Report paths with line numbers, max 10 lines. Make no edits.
4. If the code contradicts AGENTS.md, CODEMAP.md or the rules (a moved or missing path), say so in one line and suggest `/fix` to correct the doc.
5. End with one suggested next command with exact files filled in, e.g. `/fix <task> in <path>` or `/do <request>`. If the work is large, suggest `/epic`.
