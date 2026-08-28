---
name: fsm-pm-workflow
description: Use when deciding what Final-StandMarket should build next, changing priorities or scope, or writing/reviewing a PRD. Reuse the confirmed Stage B plan for its existing items; do not create duplicate PRDs for B0-B11.
---

# Final-StandMarket PM workflow

Keep requirements small, testable, and separate from implementation. This workflow is local-only: do not create remote issues, push branches, or modify remote project state unless the user separately requests it.

## Sources of truth

Read `docs/开发规范.md` first.

- Current corrective work: `docs/plans/阶段B-P0P1交易链路修复.md` is the confirmed requirement source for B0-B11.
- New product capability: one PRD under `docs/prd/<domain>/` is the requirement source.
- Requirement index and status: `docs/prd/README.md`.
- Implementation details and evidence belong in `docs/workpack/`, not in the PRD.
- Architecture decisions belong in `docs/design/`, not in the PRD.

If documents conflict, stop and reconcile the authoritative source before changing code.

## Route the request

1. **Existing B0-B11 item**: cite its B number and acceptance criteria. Do not write another PRD.
2. **Small defect that restores already-confirmed behavior**: record the expected behavior in a workpack; no PRD is required.
3. **New or materially changed user-visible capability**: use the full PM chain below.
4. **Idea with uncertain product fit**: discuss outcome, users, exclusions, dependencies, and risks before drafting a PRD.

## Full PM chain for new capabilities

### 1. Roadmap proposal

- Express candidates as user or business outcomes, not implementation lists.
- State priority, dependency, and the smallest useful cut.
- Let the user choose direction; do not silently promote a proposal to confirmed scope.

### 2. PRD writing

Read `references/prd-template.md` and create `docs/prd/<domain>/<phase>-<feature>.md`.

- PRD answers what success looks like, not how code will be written.
- Every acceptance criterion must be observable and testable.
- Explicitly list what is out of scope.
- Use statuses `草稿 → 已确认 → 进行中 → 完成`.
- Keep the PRD frontmatter and `docs/prd/README.md` synchronized.

### 3. PRD review and confirmation

Review alignment, boundary clarity, testability, security, consistency, and feasibility.

- Output `PASS` or `FAIL`; list blocking findings for `FAIL`.
- Fix until `PASS`, then ask the user for a lightweight direction confirmation.
- Only after confirmation set the PRD to `已确认`.
- Do not advance to development if scope, acceptance criteria, or a required architecture decision remains unresolved.

## State ownership

| Transition | Owner/action |
|---|---|
| `草稿 → 已确认` | PM review PASS plus user confirmation |
| `已确认 → 进行中` | Development plan confirmed |
| `进行中 → 完成` | Local review PASS, evidence complete, workpack archived |

Remote Issue synchronization is intentionally deferred. Do not leave fake issue numbers or claim remote tracking exists.
