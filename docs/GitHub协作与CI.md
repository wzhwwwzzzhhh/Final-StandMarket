# GitHub 协作与 CI

> 状态：基础配置已完成，并由 PR #2 的 5 个真实 checks 验证通过
>
> 默认分支：`master`

## 1. 工作流

### CI

`.github/workflows/ci.yml` 在以下事件运行：

- 针对 `master` 的 Pull Request；
- 推送到 `master`；
- 手动 `workflow_dispatch`。

并行检查：

| Check | 环境 | 命令 |
|---|---|---|
| `Java backend` | Temurin JDK 8 | `cd backend && mvn test` |
| `Frontend build (fashion-client)` | Node.js 22 | `npm ci && npm run build` |
| `Frontend build (fashion-admin)` | Node.js 22 | `npm ci && npm run build` |
| `Python agent` | Python 3.11 | `pip install -r requirements-ci.txt && pytest -q` |

两个前端当前没有 test/lint/typecheck 脚本，因此 CI 只验证可重复安装和生产构建。

### Secret Scan

`.github/workflows/secret-scan.yml` 使用 Gitleaks v3 并通过 `fetch-depth: 0` 取回完整 Git 历史。PR 和 `master` push 检查对应事件提交范围，手动 `workflow_dispatch` 执行全历史基线扫描。发现泄露必须轮换和修复；不得用 allowlist 掩盖真实凭据。

Secret Scan 禁用 PR 自动评论和 SARIF artifact 上传，因此不需要 `pull-requests: write` 或额外写权限；失败详情只保留在受 GitHub 权限控制的 job 日志中。

仓库的 `.gitleaksignore` 只精确忽略提交 `41efb44` 中两处内容相同、随后已随 `md/` 目录删除的 API 文档示例 token。例外绑定完整 fingerprint，不会放行相同路径、规则或提交中的其他新发现。

## 2. Issue 与 PR

- Bug 使用结构化 Bug Issue Form，并要求复现步骤、预期/实际结果和脱敏证据。
- 新功能使用 Feature Issue Form；确认后仓库内 PRD 才是需求事实来源。
- 普通空白 Issue 已禁用。
- GitHub Private Vulnerability Reporting 已启用，安全问题使用 chooser 中的私密入口，不进入公开 Issue。
- PR 必须关联需求源、workpack、独立 Review 和 AC 证据。
- CODEOWNERS 当前指向仓库维护者 `@wzhwwwzzzhhh`。

## 3. 远程交付顺序

```text
本地测试与独立 Review PASS
  → scoped commit
  → push feature branch
  → Pull Request
  → CI + Secret Scan 全绿
  → 用户确认合并
  → merge 到 master
  → 同步本地 master
```

不得直推 `master`，不得 force push，不得绕过失败检查。

## 4. 首次启用步骤

1. 将本配置通过功能分支推送并创建 PR。
2. 观察 CI 与 Secret Scan 的真实 job 名称和结果。
3. 修复到全部检查通过并合并到 `master`。
4. 手动触发一次 Secret Scan，记录默认分支全历史基线结果。
5. 再配置 `master` 分支保护，建议要求：
   - 必须通过 Pull Request；
   - 单维护者阶段不强制 GitHub approving review，避免 PR 作者无法自审造成合并死锁；仍必须附本地独立 `review.md`；
   - 增加第二位维护者后，将 required approving reviews 调整为至少 1 次；
   - required checks 包含 Java、两个前端、Python、Gitleaks；
   - 合并前分支必须保持最新；
   - 禁止 force push 和删除受保护分支。

分支保护属于远程仓库设置，必须在 CI check 名称首次真实出现后配置，避免填入不存在的 required check。

## 5. 权限和秘密

- CI 和 Secret Scan 默认只有 `contents: read`。
- 当前 CI 不需要业务 GitHub Secrets，也不连接真实 MySQL、Redis、RabbitMQ、Elasticsearch、支付宝、OSS 或 LLM。
- 后续镜像发布或部署应使用独立最小权限凭据，不复用个人 Token 或生产管理员密码。
- 安全漏洞按根目录 [SECURITY.md](../SECURITY.md) 处理，不在公开 Issue 中披露细节。

## 6. 当前不包含

- Docker 镜像构建与推送；
- 测试/生产 CD；
- Flyway 自动迁移；
- Dependabot、CodeQL、制品签名和自动发版。

这些能力应在基础 CI 稳定后按独立 workpack 增加。
