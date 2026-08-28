# B0-credential-log-safety · Independent review

> Verdict: PASS（代码审查；AC6 外部证据继续 BLOCKED）
> Reviewer mode: 独立只读审查，可执行聚焦与全量验证，不是 `tooling_blocked`

## Scope and drift

- 阶段 B B0、Issue #4、已确认 plan 与实际 diff 一致，未发现未确认的需求扩张。
- B0 在 `codex/b0-credential-log-safety` 独立 worktree 中实现；原 B1 工作区保持 10 个文件、103 insertions / 59 deletions。
- B0 对 `PayNotifyController.java` 相对干净 HEAD 仅删除一处完整回调参数日志，未吸收 B1 支付状态、幂等或订单迁移逻辑。
- 其余产品改动均位于已确认 B0 change surface 内。

## Findings

### Final review

- P0：无
- P1：无
- P2：无
- P3：无

### Closed findings from first review

- 首轮 P1：员工更新动态 SQL 引用实体不存在的 `email/department/position`。已收口为 `name/phone/sex/idNumber/status/updateTime/updateUser`，并由真实 MyBatis `MappedStatement#getBoundSql` 测试覆盖。
- 首轮 P2：用户分页未查询 password，导致 `hasPassword` 恒 false。现仅在 Mapper/Service 内读取哈希以派生布尔值，安全 VO 仍不序列化 password。
- 首轮 P2：密码生命周期与日志测试覆盖不足。已补注册、管理端新增、正常改密、错误旧密码、分页 `hasPassword`、验证码实值和支付完整参数 Map 模式测试。
- 修复具备有效 RED（4 tests / 2 failures）与 GREEN（4/4 PASS）记录。

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| B0-AC1 | 安全 VO、write-only 实体防御、准确 `hasPassword`，响应不序列化 password | PASS |
| B0-AC2 | 用户 Redis Hash 精确为 `id/name/phone/avatar`，管理员精确为 `id/name/username/phone` | PASS |
| B0-AC3 | BCrypt-only、专用密码 DTO/Mapper、资料白名单、员工动态 SQL与初始化密码均有测试证据 | PASS |
| B0-AC4 | 验证码、Token、密码哈希、嵌套敏感字段和支付完整参数日志均已收敛并补测 | PASS |
| B0-AC5 | 设置页使用 `hasPassword`，设置/修改均调用 JSON 专用密码接口，两端构建成功 | PASS |
| B0-AC6 | JWT 已外部化；gitleaks、远程 Secret Scan 和外部平台凭据轮换无真实证据 | RESIDUAL BLOCKED |

## Independent verification

- 后端全量：37 tests / 0 failures / 0 errors，`BUILD SUCCESS`。
- `git diff --check`：无空白错误。
- 限定敏感源码模式扫描：无匹配。
- 初始化 SQL：明文演示密码计数 0，BCrypt 哈希计数 112。

## Residual risks and blockers

- 本机没有 gitleaks 或 Docker，正式秘密扫描未执行；普通 `rg` 不作为其替代品。
- OSS、支付宝、微信、AI Provider、JWT 与基础设施凭据没有平台轮换或旧凭据失效证据。
- 既有运行数据库中的明文密码未迁移；BCrypt-only 上线后这类账号无法密码登录。
- 旧 Redis 登录会话可能保留历史过量字段，部署时需精确失效对应登录 Key，不能广泛清空 Redis。
- JWT 环境变量未注入时服务无法启动，部署前必须完成密钥注入。
- 前端只有生产构建证据；仓库当前没有 test/lint/typecheck 脚本，不得声称这些检查通过。

上述项目不改变本次代码审查 PASS，但阻止将 workpack 归档为“本地已验证”，也阻止宣称 B0、Issue #4 或凭据轮换整体完成。
