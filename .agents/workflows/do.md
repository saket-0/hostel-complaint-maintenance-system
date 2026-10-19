---
description: Auto-triage any request. Classify, decompose, define scope, then execute with minimal file reads.
---

# /do: Triage first, then execute

The user's request follows the command. Never ask the user to pick a category, file, or scope. You decide.

## Phase 1: Triage (no edits; read only docs/CODEMAP.md at first)

1. Restate the request in one line.
2. Classify it. Pick ONE primary type:
   - UI (public/js, index.html, css)
   - ENDPOINT (controller, DTO, service, maybe DAO)
   - LOGIC (ComplaintManager, strategies, SLA engine, notifications)
   - STATE (ComplaintStatus transitions)
   - DB (schema, migration, DAO)
   - BUG (unknown cause; locate first)
   - TEST (add or fix tests only)
   - BUILD/INFRA (pom, Docker, Makefile, scripts, CI)
   - DOCS
   - QUESTION (explain only; make no edits)
   Add secondary layers if the change crosses modules.
3. Decompose into atomic sub-tasks (max 5), each touching one layer.
4. Locate files using CODEMAP, then grep/ripgrep for symbols. Open at most 3 files, only the needed line ranges. Never open a whole file over 300 lines unless editing most of it.
5. Size it:
   - S = 1 file, or 1 layer with at most 2 files
   - M = 2 to 4 files, or 2 layers
   - L = 5+ files, 3+ layers, or any DB / STATE / core-model change

## Phase 2: Scope block (print before touching any file)

REQUEST:      <one line>
TYPE:         <primary> (+ <secondary layers>)
SIZE:         S | M | L
SUB-TASKS:    1. ...  2. ...
TO EDIT:      <paths with method or line range>
READ-ONLY:    <paths for context, max 3>
OUT OF SCOPE: <what I will NOT touch>
ASSUMPTIONS:  <decisions I made instead of asking>
VERIFY:       <exact ./quick.sh command>
RISK:         none | <what could break>

## Phase 3: Gate

- S or M with no real ambiguity: proceed immediately, no approval needed.
- L-sized or architectural/redesign requests: do not execute. Tell the user in one line to run /epic with the same request, and stop.
- Ambiguity: prefer a reasonable assumption and list it under ASSUMPTIONS. Ask only if a wrong guess would be destructive or the intent is truly unclear. Maximum one question.
- QUESTION type: answer from CODEMAP and grep. Make no edits.
- BUG type: state your top hypothesis and the single file/method to check first. If the cause is not found after 3 file opens, stop and report findings instead of widening the search.

## Delegation
After classifying, follow the matching workflow file instead of improvising: BUG -> bug.md, ENDPOINT -> endpoint.md, UI -> ui.md, QUESTION -> find.md. For DB, BUG or concurrency work, read docs/LESSONS.md first. Still print the scope block first. LOGIC, STATE, TEST, BUILD and DOCS are handled by /do itself.

## Phase 4: Execute

- Do sub-tasks in order with the smallest possible diff. No refactors, reformatting, or unrelated cleanup.
- Respect module boundaries (core has no framework/JDBC imports; never edit applied Flyway migrations, add a new V{n}).
- Never re-read a file already read in this conversation.
- If you need a file outside TO EDIT, say so in one line, update the scope block, and continue only if it stays S or M.

## Phase 5: Verify and report

- Run only the VERIFY command. If it passes, stop. Do not re-run it or run broader suites.
- If it fails, fix at most twice, then report instead of looping.
- Apply the Doc sync table from the rules. If docs changed, also run ./quick.sh docs. Final reply ends with the `Docs:` line.
- Do not commit or push.
- Final reply, max 6 lines: files changed, what changed, verify result, follow-ups.
