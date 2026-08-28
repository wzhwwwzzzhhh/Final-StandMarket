---
name: fsm-architecture-workflow
description: Use when Final-StandMarket work changes payment/refund contracts, transaction or inventory state machines, Redis/MySQL consistency, RabbitMQ reliability, database migrations, authentication/authorization, public APIs, or cross-service contracts. Skip for ordinary low-risk fixes that follow an already-confirmed design.
---

# Final-StandMarket architecture workflow

Use architecture review only where a wrong decision would create security, data, compatibility, or rollback risk. The purpose is to prevent expensive rework, not to add a document to every change.

## Entry gate

Read `docs/开发规范.md`, the confirmed requirement source, relevant existing designs, and current implementation anchors.

Formal Design is required for a new or materially changed:

- payment or refund external contract;
- order, inventory, refund, or seckill state machine;
- Redis/MySQL cross-store consistency strategy;
- RabbitMQ delivery, retry, dead-letter, or reconciliation strategy;
- schema migration or destructive data change;
- authentication, authorization, credential, or ownership boundary;
- public API or Java/Python/frontend cross-service contract.

If a Stage B section already fixes the decision and implementation only follows it, cite that decision instead of writing a duplicate Design. Escalate only new ambiguity or a scope-changing decision.

## Two modes

### Direction triage

Use when there is no confirmed requirement.

1. Restate the intended outcome in one sentence.
2. Check product fit, existing capability reuse, safety, feasibility, and likely scope.
3. Return one conclusion: `合理`, `不合理`, or `需收敛`, with concise reasons.
4. Let the user decide whether the idea proceeds to PM/PRD.

Do not create a formal Design from an unconfirmed idea.

### Formal Design

Use when a confirmed Stage B item or PRD needs an unresolved high-risk decision.

1. Read `references/design-template.md`.
2. Create `docs/design/<domain>/<phase>-<feature>-design.md` with status `草稿`.
3. Map every decision to the confirmed requirement and list explicit exclusions.
4. Cover contracts, transaction boundaries, idempotency, failure handling, migration compatibility, rollback, and verification where applicable.
5. List only genuine user decisions in the confirmation section.

## Independent review gate

The design author and reviewer should use independent contexts when tooling and authorization allow it.

- Grade findings P0-P3.
- Any P0/P1 means `FAIL`; revise and review again.
- If independent review is unavailable, record `tooling_blocked` and tell the user. Never impersonate an independent PASS.
- After review PASS, ask the user to confirm the listed design decisions, then set status to `已确认`.

## Boundary discipline

- Design explains how to build safely; it does not expand product scope.
- If Design changes user-visible behavior or scope, return to the PM workflow and update the requirement source first.
- Do not claim cross-store atomicity where only compensation and convergence exist.
- A draft Design does not satisfy the architecture gate. Implementation may proceed only after independent Review PASS, user confirmation, and Design status `已确认`.
