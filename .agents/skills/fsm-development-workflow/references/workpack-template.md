# Workpack templates

## plan.md

```markdown
# <phase>-<slice> · Workpack plan

> Status: 待确认
> Requirement source: <Stage B section or PRD path>
> Design: <confirmed Design path or 无>

## Scope

### In scope

### Out of scope

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|

## Slices

## File-level change surface

## Risks and rollback

## Verification commands
```

## review.md

```markdown
# <phase>-<slice> · Independent review

> Verdict: pending

## Scope and drift

## Findings

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|

## Residual risks
```

Use `PASS`, `FAIL`, or `tooling_blocked` for the final verdict. P0/P1 findings require `FAIL`.

## evidence.md

```markdown
# <phase>-<slice> · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|

## Not run or blocked

## Local delivery summary
```

Never pre-fill PASS results. Add evidence only after the command or inspection actually occurred.
