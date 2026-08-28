# Design template

```markdown
# <phase> <feature> · Design

> Status: 草稿
> Requirement source: <Stage B section or PRD path>
> Updated: <YYYY-MM-DD>

## 1. Goal and scope

### In scope

### Out of scope

## 2. Current behavior and constraints

## 3. Design decisions

## 4. Contracts and state transitions

## 5. File-level change surface

## 6. Failure handling, idempotency, and compensation

## 7. Migration, compatibility, and rollback

## 8. Verification gates

## 9. Decisions requiring user confirmation

## 10. Independent review

- Verdict: pending
- Findings: pending
```

Before confirmation, replace placeholders and make state transitions explicit. For database/Redis/MQ work, distinguish atomic operations from asynchronous compensation.
