---
description: Plan a major architectural or UI/UX change into small verified phases stored in a plan file. Planning only, no code edits.
---

# /epic: Plan a large change

Use for: architecture changes, new modules, DB redesigns, cross-layer features, redesigns of the UI/UX, anything sized L. This workflow produces a plan file and STOPS. It writes no app code.

## Step 1: Bounded discovery (this is the one time wide reading is allowed)
- Read docs/CODEMAP.md, AGENTS.md, README.md section 2 (architecture).
- Read docs/LESSONS.md.
- Read docs/WORKFLOWS.md section 5 so phases follow the shared contract.
- Use grep/ripgrep for symbols tied to the request. Budget: max 12 file opens, line ranges only.
- Record what you learned in the plan so no later session re-explores.

## Step 1b: Sizing gate
If discovery shows the work is S or M (at most 4 files, no DB/state/core change), do NOT write a plan. Tell the user in one line to run /do with the same request, and stop.

## Step 2: Write docs/plans/<kebab-slug>/PLAN.md with these sections
1. **Goal**: 2-3 lines, plus measurable success criteria.
2. **Non-goals**: what this epic will NOT do.
3. **Current state**: verified facts only (files, classes, flows), with paths.
4. **Target state**: what it looks like after.
5. **Decisions & assumptions**: choices made, alternatives rejected, one line each.
6. **Phases**: ordered checklist. Every phase must be size S or M, leave the build green, and have:
   - `[ ] P<n> <title>`
   - `Type: UI | ENDPOINT | LOGIC | DB | STATE | TEST | INFRA | DOCS`
   - Files to touch (paths)
   - Do: bullet list, max 5
   - `Docs: <exact doc files this phase must update, per the Doc sync table>`
   - Verify: exact ./quick.sh command (plus any manual check)
   - Rollback: how to undo (usually `git revert` of the phase commit)
7. **Risks**: top 3 and mitigation.
8. **Progress log**: empty; /next appends handoff notes here.
9. Header fields: `branch: epic/<slug>`, `auto-commit: no`.

## Phase design rules
Docs are updated in the same phase as the change that requires them, not deferred. The final phase is a doc audit: run ./quick.sh docs and ./gen-codemap.sh, and reconcile README, DESIGN.md, API.md, AGENTS.md and WORKFLOWS.md against the finished state.

ARCHITECTURE / BACKEND changes:
- P1 is always a safety net: characterization tests for the behavior about to change.
- Use expand/contract: add the new thing alongside the old, migrate callers, then remove the old in a final phase. Never big-bang.
- DB: additive migrations first (new V{n}), data backfill second, destructive cleanup last and flagged high-risk.
- Move one module or one class family per phase. Respect dependency direction web -> service -> persistence -> core.
- Final phase: update docs (README, docs/DESIGN.md, AGENTS.md), run ./gen-codemap.sh.

UI/UX changes:
- P1: design tokens only. CSS variables (colors, spacing, type scale, borders, shadows) in style.css. No visual change yet.
- P2: shared components/classes (buttons, cards, forms, tables, modals, toasts, badges).
- Then ONE screen or ONE role view per phase (login, student dashboard, complaint form, staff view, admin queue, analytics...).
- Last phases: responsive pass, accessibility pass (contrast, focus, labels), cleanup of dead CSS/JS.
- Never restyle the whole app in one phase. Do not change backend or API contracts in UI phases.

## Step 3: Stop
Print a summary of max 10 lines: slug, number of phases, the 3 riskiest phases, and any decision that needs the user. Ask the user to approve or edit PLAN.md, then run /next in a NEW chat. Do not start P1.
