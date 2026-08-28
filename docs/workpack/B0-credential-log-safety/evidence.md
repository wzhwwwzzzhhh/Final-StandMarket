# B0-credential-log-safety · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| B0-AC1 | `UserSafeVO` / `EmployeeSafeVO` 收口用户、管理员和员工响应；实体密码字段设为 write-only；管理端分页通过查询哈希准确派生 `hasPassword` | PASS（本地） |
| B0-AC2 | 用户 Redis Hash 精确断言 `id/name/phone/avatar`，员工 Hash 精确断言 `id/name/username/phone`；6 个 Slice 1 测试通过 | PASS（本地） |
| B0-AC3 | 密码专用 JSON DTO/Mapper、BCrypt-only 校验、注册/管理端新增/首次设置/正常修改/旧密码错误、资料更新白名单和真实 MyBatis 动态 SQL 契约测试通过；初始化 SQL 明文计数 0、BCrypt 计数 112 | PASS（本地）；既有运行库明文账号迁移不在本包内 |
| B0-AC4 | 递归结构化脱敏测试、验证码实值捕获、登录 Token/哈希日志测试、支付回调完整 Map 日志模式检查通过；限定敏感模式扫描无匹配 | PASS（本地） |
| B0-AC5 | Controller JSON 请求契约测试通过；设置页仅使用 `hasPassword`，两种路径均调用专用 JSON 密码 API；用户端构建通过 | PASS（本地） |
| B0-AC6 | 固定 JWT 默认值已改为环境变量占位符；但本机无 `gitleaks`/Docker，未获授权运行远程 Secret Scan，且外部凭据无平台轮换证据 | BLOCKED；不得标记 B0 完成 |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-08-28 | Slice 1 聚焦 RED：`UserServiceImplTest,EmployeeServiceImplTest` | exit 1；6 tests / 6 failures / 0 errors | 有效 RED：响应序列化含密码、Redis Hash 字段过宽 |
| 2026-08-28 | Slice 1 聚焦 GREEN：同组测试 | exit 0；6/6 PASS | 安全 VO、write-only 防御、显式最小 Redis Hash |
| 2026-08-28 | Slice 2 聚焦 RED | exit 1；17 tests / 11 failures / 0 errors | 有效 RED：明文兼容登录、资料可写密码、专用密码契约和 SQL 边界缺失 |
| 2026-08-28 | Slice 2 聚焦 GREEN | exit 0；17/17 PASS | BCrypt-only、专用 JSON DTO/Mapper、资料白名单、员工/种子 SQL 修正 |
| 2026-08-28 | Slice 2 补充 RED / GREEN | RED 15 tests / 2 failures；GREEN 15/15 PASS | 覆盖员工新增默认状态/时间和密码更新 0 行不得误报成功 |
| 2026-08-28 | Slice 3 聚焦 RED | exit 1；6 tests / 6 failures / 0 errors | 有效 RED：嵌套敏感值、失败回退、业务/支付日志和固定 JWT 默认值 |
| 2026-08-28 | Slice 3 聚焦 GREEN | exit 0；6/6 PASS | 递归脱敏、安全失败回退、敏感业务日志收敛、JWT 外部化 |
| 2026-08-28 16:27 +08:00 | `backend: mvn test` | exit 0；BUILD SUCCESS；31 tests / 0 failures / 0 errors / 0 skipped | 新鲜后端全量测试 |
| 2026-08-28 | 首轮独立审查 | FAIL；1 P1、2 P2 | P1 员工动态更新 SQL 引用不存在属性；P2 为分页 `hasPassword` 不准确及自动化覆盖不足 |
| 2026-08-28 16:33 +08:00 | 审查修复有效 RED：`MapperSecurityContractTest` | exit 1；4 tests / 2 failures / 0 errors | 动态 SQL 抛缺少 `email` getter；用户分页 SQL 未查询 password |
| 2026-08-28 16:34 +08:00 | 审查修复 GREEN：`MapperSecurityContractTest` | exit 0；4/4 PASS | 员工更新只使用实体白名单字段；用户分页可准确派生 `hasPassword` |
| 2026-08-28 16:35 +08:00 | 覆盖补强聚焦测试 | exit 0；21/21 PASS | 注册/管理端新增/正常改密/旧密码错误、验证码实值日志和支付 Map 日志模式 |
| 2026-08-28 16:35 +08:00 | `backend: mvn test` | exit 0；BUILD SUCCESS；37 tests / 0 failures / 0 errors / 0 skipped | 审查修复后的新鲜后端全量测试；取代 31-test 结果作为最终证据 |
| 2026-08-28 | 同一独立审查者复审 | PASS；P0-P3 均无未关闭发现；独立全量 37/37 | AC6 正式秘密扫描和外部轮换保留为 residual blocker |
| 2026-08-28 | 两个前端首次 `npm run build` | exit 1 | 独立 worktree 未安装依赖，`vite` 不可用；未当作成功证据 |
| 2026-08-28 | 两个前端 `npm ci --no-audit --no-fund` | exit 0 | 分别按 `package-lock.json` 安装 80 / 83 个包 |
| 2026-08-28 16:36 +08:00 | `frontend/fashion-client: npm run build` | exit 0；Vite 6.4.2，1728 modules | 审查修复后重跑成功；保留既有 >500 kB chunk 警告 |
| 2026-08-28 16:36 +08:00 | `frontend/fashion-admin: npm run build` | exit 0；Vite 6.4.2，2290 modules | 审查修复后重跑成功；保留既有 >500 kB chunk 警告 |
| 2026-08-28 | `git diff --check` + 对 14 个未跟踪文件逐个 `git diff --no-index --check` | exit 0 | 无空白错误；Git 仅提示工作树 LF 将转 CRLF |
| 2026-08-28 | 限定业务源码敏感日志模式 `rg` | 无匹配（`rg` exit 1） | 排除 target/node_modules/dist/test；配合日志测试使用 |
| 2026-08-28 | `mysql/final07.sql` 口令计数 | plaintext 0；BCrypt 112 | 只证明初始化快照，不证明既有数据库已迁移 |
| 2026-08-28 | 原 B1 工作区 `git status --short` / `git diff --stat` | 10 files；103 insertions / 59 deletions | 与计划确认时基线一致；B0 未混入原工作区 |
| 2026-08-28 16:41 +08:00 | 远程交付前 `backend: mvn test` | exit 0；BUILD SUCCESS；37 tests / 0 failures / 0 errors / 0 skipped | 用户授权 commit/push/PR/merge 后的新鲜验证 |
| 2026-08-28 16:41 +08:00 | 远程交付前两个前端 `npm run build` | 两者 exit 0；client 1728 / admin 2290 modules | 仅生产构建；均保留既有 >500 kB chunk 警告 |
| 2026-08-28 16:42 +08:00 | 远程交付前 `git diff --check` 与限定敏感模式扫描 | diff exit 0；敏感模式无匹配 | 远程已确认且 GitHub CLI 已认证；首次 fetch 因 github.com:443 连接超时失败，尚无远程 CI 证据 |
| 2026-08-28 | `git commit` | `f2e1e61 fix(security): 收紧 B0 凭据与日志边界` | 34 个 B0 文件；除支付回调单行日志收敛外未包含 B1 文件 |
| 2026-08-28 | SSH 非强制 push | exit 0；`codex/b0-credential-log-safety` | Git HTTPS 连接超时后使用已认证的 GitHub SSH 官方通道；未 force push |
| 2026-08-28 | 创建 Draft [PR #5](https://github.com/wzhwwwzzzhhh/Final-StandMarket/pull/5) | OPEN / DRAFT | `master` ← `codex/b0-credential-log-safety`；使用 `Refs #4`，AC6 阻塞期间不自动关闭 Issue |

## Not run or blocked

- `gitleaks git . --redact --no-banner` 未运行：本机没有 `gitleaks`，同时没有 Docker 可使用固定版本容器；不能用普通 `rg` 冒充秘密扫描。
- GitHub Secret Scan / checks 正在 PR #5 上运行；只有 GitHub 对最新目标提交的真实结果才计入证据，未全绿前不得合并。
- JWT、OSS、支付宝、微信、AI Provider、MySQL、Redis、RabbitMQ 的真实凭据轮换/旧凭据失效验证未运行：当前没有目标环境或平台权限，所需证据与负责人见 `plan.md` 登记表。
- 既有共享/生产数据库中的明文密码迁移未运行；本包只保证新密码写入和初始化 SQL 使用 BCrypt。

## Local delivery summary

B0 的本地产品改动、TDD 测试、后端全量测试、两端构建、diff 与限定敏感模式扫描已完成。首轮独立审查的 P1/P2 已按有效 RED/GREEN 修复并补强覆盖；同一审查者复审结论为 PASS，P0-P3 均无未关闭发现，并独立重跑后端 37/37。代码提交 `f2e1e61` 已推送并建立 Draft PR #5，等待 GitHub CI/安全检查。AC1-AC5 有本地 PASS 证据；AC6 因正式秘密扫描和真实外部凭据轮换证据缺失保持 BLOCKED，因此 workpack 不能归档，也不能声称 Issue #4 已完成。
