# B3-refund-state · Independent implementation review

> Verdict: PASS（2026-08-30；P0/P1/P2/P3 均为 0）

## Scope and drift

- Review scope: Stage B B3 / Issue #10 / confirmed Design / product code / tests / SQL.
- Independent reviewer directly inspected all tracked/untracked B3 files and independently ran all seven B3 test classes.
- Independent rerun: 25 tests / 0 failures / 0 errors / 0 skipped, including real MySQL, Spring AOP proxies and production MyBatis XML.
- No B1 dirty-main-worktree changes, B2 files, local datasource configuration or unrelated feature changes are included.

## Findings

- **P1 closed — CHECK marker normalization collision**: the initial migration normalization removed commas and parentheses, so malformed `IN (0,12,3)` / `IN (34)` could collide with valid definitions. The script now preserves list delimiters, and real MySQL tests verify both collisions fail with the expected definition-mismatch signal.
- **P2 closed — stale service contract comment**: `RefundService#approve` still described inventory restoration and order updates. The comment now states the `0 -> 1` waiting boundary and is covered by a source contract test.
- **Test-evidence correction closed**: early wrong-marker tests could fail first on nullable `order_status`. Marker-specific scenarios now first establish the expected NOT NULL column shape and assert the precise failure message, eliminating the false positive.
- Final open findings: none.

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| B3-AC1 | Dedicated approve CAS plus Spring/MySQL before/after snapshots | PASS |
| B3-AC2 | Concurrent approve and approve/reject races have one winner | PASS |
| B3-AC3 | Reject and order restore share a real proxy transaction; zero-row restore rolls back | PASS |
| B3-AC4 | Generic refund update, `refund_time` write and refund inventory dependencies removed | PASS |
| B3-AC5 | Apply/confirm race uses both production CAS paths through real proxies | PASS |
| B3-AC6 | Controller and both Vue clients use the exact state-1 wording and distinguish state 2 | PASS |
| B3-AC7 | First migration, valid rerun, marker faults/collisions, history gates and clean/upgrade equivalence verified on MySQL 8 | PASS |

## Residual risks

- Production history in state `1/2` or with inconsistent order/refund facts intentionally blocks the migration and requires B10/B11 reconciliation evidence.
- Trusted `1 -> 2`, payment-state updates and one-time inventory restoration remain deliberately unimplemented until a real refund completion source exists.
- Explicit MySQL tests are skipped by the default Maven suite and must continue to be run with the documented property/config path.
- The two frontend projects expose build scripts only; no test/lint/typecheck success is claimed.
- B0-AC6 and B11 still block production deployment and Stage B completion.
