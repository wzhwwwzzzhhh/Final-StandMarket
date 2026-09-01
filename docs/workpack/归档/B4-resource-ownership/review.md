# B4-resource-ownership · Independent review

> Verdict: PASS（第二轮实现复审；P0/P1/P2/P3 均为 0）

## Review target

- Stage B B4 / GitHub Issue #12.
- Confirmed workpack plan, implementation diff, tests and verification evidence.
- Special focus: exact public route allowlist, IDOR prevention at SQL boundary, transactional default-address changes, order-address ownership and B5/B7 scope separation.

## Findings

- First plan review: NEEDS_REVISION（P0=0, P1=2, P2=1, P3=0）。
  - P1 closed in plan: 支付同步回跳补充 Payment Service/Mapper 用户归属 SQL、文件面及双用户 MySQL 验证。
  - P1 closed in plan: 非空地址引用才校验归属，空 `addressId` 明确保留现有语义。
  - P2 closed in plan: `/upload/**` 明确继续由管理员拦截器保护并加入回归断言。
- Second plan review: PASS（P0/P1/P2/P3 均为 0）。上一轮问题全部关闭，未发现新增问题；无需新增 Formal Design。
- First implementation review: NEEDS_REVISION（P0=0，P1=1，P2=1，P3=1）。
  - P1：秒杀用户 Controller 把底层异常消息拼入公开响应，可能泄露 SQL/驱动信息。
  - P2：公开路由测试只使用探针 Controller，未绑定真实 Controller 映射。
  - P3：上传身份文档措辞与既有 token 兼容行为不一致。
- Revision:
  - 秒杀券、详情和取消异常改为固定公开错误；新增异常脱敏测试，先见 3 个 RED，再 7/7 GREEN（含路由测试）。
  - 路由合约通过反射绑定真实 `UserController`、`UserProductController` 和 `UserCategoryController` 映射。
  - 上传规则统一为 `AdminLoginInterceptor` 拒绝匿名请求，同时维持既有用户/管理员 token 兼容行为。
- Second implementation review: PASS（P0/P1/P2/P3 均为 0）；上一轮 findings 全部关闭，未引入新的范围、事务、MyBatis 或 B5/B7 边界问题。

## Acceptance evidence review

| AC | Result | Notes |
|---|---|---|
| B4-AC1 | PASS | Exact public/private route contract plus real Controller mapping reflection |
| B4-AC2 | PASS | No fallback/default user or client identity trust |
| B4-AC3 | PASS | Address SQL ownership and real Spring/MySQL transactional rollback |
| B4-AC4 | PASS | Order-address ownership and no failed-order side effects |
| B4-AC5 | PASS | Ordinary transaction/payment/refund/AI ownership |
| B4-AC6 | PASS | Review current-user query boundary |
| B4-AC7 | PASS | Seckill order read/write ownership and fixed public error responses |
| B4-AC8 | PASS | Trusted internal regressions, full suite, source audit and independent re-review |

## Residual risks

- B4 不包含 B5 秒杀库存/状态闭环或 B7 完整评价资格，后续阶段仍须按依赖顺序完成。
- 本地没有 `gitleaks` 二进制；远程 push 后仍需以 GitHub Secret Scan check 为交付证据。
- B0-AC6 与 B11 继续阻塞生产发布；B4 通过不等于允许生产部署。
