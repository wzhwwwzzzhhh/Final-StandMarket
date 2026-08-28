---
name: fsm-development-workflow
description: Use when planning, implementing, reviewing, verifying, or delivering a Final-StandMarket feature or bugfix. Creates a small workpack with plan, review, and evidence; remote push, PR, merge, and repository-setting changes require explicit user authorization.
---

# Final-StandMarket development workflow

Deliver one small, reviewable unit with evidence. Work locally by default. Enter remote delivery only when the user explicitly authorizes commit/push/PR/merge or the relevant repository-setting change.

## Read first

- `docs/开发规范.md`
- the confirmed Stage B section or PRD
- any required confirmed Design
- `docs/workpack/README.md`
- relevant code and tests

Preserve unrelated user changes. The current checkout may already contain B1 payment edits; do not discard, reset, or silently absorb them into another workpack.

## Choose a route

### Fast path

Use only when all are true:

- at most one file and roughly ten net lines;
- behavior is already confirmed;
- no authentication, authorization, payment, refund, inventory, state machine, MQ, cache-concurrency, database migration, public API, or cross-service contract impact;
- no investigation is needed to determine scope.

Implement directly, run the relevant check, and report the evidence. Documentation-only corrections may also use this path.

### Standard workpack

All other product code changes use a workpack with 1-3 tightly related slices.

## Phase 1: plan

1. Locate the single requirement source. If the request is outside it, stop and return to the PM workflow.
2. Check whether the architecture gate applies. If yes and no confirmed Design exists, stop and use `fsm-architecture-workflow`.
3. Read `references/workpack-template.md`.
4. Create `docs/workpack/<phase>-<slice>/plan.md`, `review.md`, and `evidence.md`.
5. Register it in `docs/workpack/README.md` as `待确认`.
6. Present scope, exclusions, slices, change surface, and verification to the user. Do not change product code until the plan is confirmed.

A dedicated branch is recommended. A separate worktree is required only for parallel agents or overlapping concurrent work; do not force a worktree for the existing dirty B1 checkout.

## Phase 2: execute

- Set the workpack and requirement status to `进行中` after plan confirmation.
- For features, bug fixes, refactors, and behavior changes, follow the repository `test-driven-development` skill: failing test first, minimal implementation, refactor while green.
- Configuration, generated code, or documentation exceptions require the user's agreement where the TDD skill requires it.
- Each new behavior must map to a requirement or acceptance criterion. Stop on scope drift.
- Work slice by slice; do not mix unrelated cleanup.
- Record commands and results in `evidence.md` as they are produced.

## Phase 3: independent review

Review the diff against requirement, plan, confirmed Design, security boundaries, state transitions, exceptions, and test evidence.

- Prefer an independent read-only reviewer when tooling and authorization allow it.
- Write findings and AC mapping to `review.md`.
- P0/P1 findings mean `FAIL`; fix and review again.
- If independent review is unavailable, write `tooling_blocked`; do not claim PASS.

## Phase 4: local delivery

Before declaring completion:

1. Run fresh relevant tests/builds and `git diff --check`.
2. Ensure `review.md` is PASS and `evidence.md` maps every AC.
3. Mark the workpack `本地已验证` and record fresh commands in `evidence.md`.
4. For local-only delivery, synchronize the requirement state, archive the workpack, and update its index.
5. If remote delivery is requested, keep the workpack active and continue to Phase 5; do not claim PR or CI yet.

Local completion must say `本地已验证`; it does not imply PR, CI, deployment, or production readiness.

## Phase 5: remote delivery

Proceed only with explicit user authorization.

1. Verify the remote, base branch, authentication, and dedicated feature branch. Never push directly to `master`.
2. Stage only workpack-owned files; inspect the staged diff and use the repository `conventional-commit` skill. Preserve unrelated dirty files.
3. Fetch the remote base and merge/rebase safely before push. If unrelated local changes prevent safe synchronization, stop and report instead of stashing or resetting them without permission.
4. Push without force, create a PR against `master`, and include requirement, Design, workpack, review, and evidence links.
5. Wait for GitHub CI and security checks. A failing or missing required check returns the work to Phase 2/4; never bypass it.
6. Before merge, synchronize final requirement status and archive the workpack in the feature branch, then push and wait for checks again.
7. Merge only with explicit authorization and all required checks green. Do not self-approve or bypass branch protection.
8. Close linked issues when applicable, record the PR/merge evidence, then update local `master` with a fast-forward pull.

Remote CI results are evidence only after GitHub reports them for the pushed commit. A workflow file that merely exists locally is not active CI.

## Verification matrix

Run the checks relevant to changed modules:

| Area | Minimum local evidence |
|---|---|
| Java backend | focused test during RED/GREEN, then `cd backend; mvn test` |
| User frontend | `cd frontend/fashion-client; npm run build` |
| Admin frontend | `cd frontend/fashion-admin; npm run build` |
| Python Agent | `cd agent-service; python -m pytest -q` |
| Any change | `git diff --check` and scoped diff review |

The frontends currently have no test script. Do not claim frontend unit-test coverage; record build-only evidence until tests are introduced. If Python dependencies block collection, record the exact blocker instead of marking the suite passed.
