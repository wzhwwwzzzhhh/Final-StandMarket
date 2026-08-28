# OPS-github-ci-collaboration · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| AC1 | `actionlint 1.7.12` 检查两份 workflow；顶层 `permissions: contents: read` | PASS（本地） |
| AC2 | Maven 6 tests、两个前端 `npm ci && npm run build`、隔离 Python venv 37 tests | PASS（本地） |
| AC3 | Gitleaks 8.30.1 扫描 25 commits / 2.58 MB；两条已删除文档示例以完整 fingerprint 记录后复扫；远程 workflow 区分事件范围与手动全历史基线 | PASS（本地，0 findings） |
| AC4 | PyYAML 解析三份 Issue 配置，并检查必需顶层字段、body 结构和唯一 id；PR 模板人工复核；远程 Private Vulnerability Reporting 回读为 enabled | PASS |
| AC5 | `CODEOWNERS` 包含仓库默认规则及 `.github/`、`backend/`、`frontend/`、`agent-service/` | PASS（本地） |
| AC6 | `AGENTS.md`、`CLAUDE.md`、开发规范、开发交付 skill 和 GitHub 协作文档交叉复核；skill 镜像 SHA-256 相同 | PASS（本地） |
| AC7 | Gitleaks 复扫无发现；限定范围和 `git diff --check` 复核通过 | PASS（本地） |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-08-28 13:28 +08:00 | JDK 8u121 下 `cd backend; mvn --batch-mode --no-transfer-progress test` | exit 0；6 tests，0 failure/error；BUILD SUCCESS | 使用本机实际 Java 8 重新编译 114 个源文件；仅有 deprecation/unchecked、MySQL artifact relocation 和预期异常路径测试日志 |
| 2026-08-28 13:29 +08:00 | 两个前端分别 `npm ci`、`npm run build` | exit 0；client 1728 modules、admin 2290 modules | Vite 报告大 chunk/图片体积警告；当前无 test/lint/typecheck 脚本 |
| 2026-08-28 13:28 +08:00 | 临时 Python 3.11 venv 下 `python -m pytest -q` | exit 0；37 passed in 20.92s | 同一隔离环境此前已从 `requirements-ci.txt` 成功安装依赖；不写入仓库 |
| 2026-08-28 13:18 +08:00 | `gitleaks 8.30.1 git . --redact --no-banner` | 初扫 2 条历史示例；精确 fingerprint 记录后 exit 0、no leaks found | 扫描 25 commits / 2.58 MB；未输出 token 内容 |
| 2026-08-28 13:29 +08:00 | `actionlint 1.7.12`、Issue Form/本地链接检查、skill 镜像 hash、`git diff --check` | exit 0 | workflow 语法、Issue 结构、链接、镜像一致性和空白检查通过 |
| 2026-08-28 13:29 +08:00 | Gitleaks 对当前工作包目录/文件逐项 `dir` 扫描，并再次 `git .` 全历史扫描 | exit 0；no leaks found | 不读取本地忽略的运行时 `.env`；CI 提交范围内文件均已覆盖 |
| 2026-08-28 13:27 +08:00 | `PUT /private-vulnerability-reporting` 后 GET 回读 | `{"enabled":true}` | GitHub 私密漏洞报告入口已启用 |
| 2026-08-28 13:31 +08:00 | 显式暂存工作包文件后 `git diff --cached --check`、staged path 守卫和全部 FSM 镜像 hash | exit 0 | 35 个工作包文件；staged backend files = 0；现有 B1 修改全部留在工作树 |

## Pending remote evidence

- GitHub Actions：必须推送后记录真实 PR、run 和 check 结果；本地等价命令不冒充远程 CI。
- `master` 分支保护：必须在真实 check 名称出现且本次 PR 合并后配置并回读验证。

## Local delivery summary

独立审查 PASS，本地等价验证和 AC1-AC7 证据完整。现有 B1 支付/订单工作树修改未纳入本工作包，也不会被 staged 或提交。下一状态是在限定范围 commit/push 并创建 PR 后进入 `CI 验证中`。
