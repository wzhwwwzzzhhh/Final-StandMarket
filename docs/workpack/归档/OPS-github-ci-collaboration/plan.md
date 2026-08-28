# OPS-github-ci-collaboration · Workpack plan

> Status: 已归档
> Requirement source: [生产上线与 GitHub CI/CD 主计划](../../../plans/生产上线与GitHub-CICD主计划.md) §4 + 2026-08-28 用户指令
> Design: 无；本工作包不改变业务架构或生产部署架构

> Plan confirmed: 2026-08-28；用户同时授权本工作包后续执行 commit、push、PR、CI 修复、合并和分支保护配置

## Scope

### In scope

- 建立 GitHub Actions CI：Java 后端测试、两个 Vue 前端生产构建、Python Agent 测试。
- 建立 Gitleaks 全历史敏感信息扫描。
- 建立 Bug/新功能 Issue Forms、Issue chooser、PR 模板、CODEOWNERS 和安全报告说明。
- 为 Python CI 增加独立测试依赖文件，不把 pytest 混入运行时依赖。
- 增加 GitHub 协作与 CI 使用文档。
- 把本地开发规范和三个工作流接到 `commit → push → PR → CI → merge` 的可选远程交付阶段，同时继续要求用户显式授权远程操作。

### Out of scope

- 不配置生产/测试环境 CD、Docker 镜像发布、部署密钥或 GitHub Secrets。
- 不启用自动合并、自动部署、自动发版或绕过 required checks 的管理员例外。
- 不引入 Dependabot、CodeQL、制品签名或自动发版。
- 不修改现有 B1 支付代码及其他业务代码。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| AC1 | CI 在针对 `master` 的 PR、推送及手动触发时运行，所有 job 仅有最小只读权限 | YAML/Action 语法检查和配置审查 |
| AC2 | Java 8 Maven 测试、用户端/管理端 Node 22 构建、Python 3.11 pytest 分为可并行 job | 本地运行四类等价命令 |
| AC3 | Secret Scan 使用 Gitleaks v3；PR/push 阻断事件范围内的新泄露，手动运行形成完整历史基线 | Gitleaks 本地全历史等价扫描；检查 `fetch-depth: 0`；合并后手动运行 |
| AC4 | GitHub 可显示结构化 Bug/Feature Issue Forms 和统一 PR 检查清单 | Issue Form/YAML/Markdown 结构检查 |
| AC5 | CODEOWNERS 将仓库和 `.github/` 默认归属到仓库所有者 `@wzhwwwzzzhhh` | 文件规则检查 |
| AC6 | 开发规范明确本地验证与远程 CI 的边界，未经用户授权仍不执行远程操作 | 文档交叉检查和读者测试 |
| AC7 | 配置不包含业务密钥、测试凭据或真实连接信息 | 敏感值扫描和限定范围 diff 复核 |

## Slices

1. **S1 CI 与安全扫描**：Actions、Python CI 依赖、Gitleaks。
2. **S2 GitHub 协作入口**：Issue Forms、PR 模板、CODEOWNERS、SECURITY、协作文档。
3. **S3 工作流接线与验证**：更新本地规范/技能，运行等价检查，独立审查和 evidence。

## File-level change surface

- 新增 `.github/workflows/ci.yml`
- 新增 `.github/workflows/secret-scan.yml`
- 新增 `.github/ISSUE_TEMPLATE/bug_report.yml`
- 新增 `.github/ISSUE_TEMPLATE/feature_request.yml`
- 新增 `.github/ISSUE_TEMPLATE/config.yml`
- 新增 `.github/PULL_REQUEST_TEMPLATE.md`
- 新增 `.github/CODEOWNERS`
- 新增 `.gitleaksignore`
- 新增 `SECURITY.md`
- 新增 `CONTRIBUTING.md`
- 新增 `agent-service/requirements-ci.txt`
- 新增 `docs/GitHub协作与CI.md`
- 修改 `docs/开发规范.md`
- 修改 `AGENTS.md`、本地 `CLAUDE.md`
- 修改 `.agents/skills/fsm-development-workflow/SKILL.md` 及其 `.claude` 镜像
- 修改 `docs/workpack/README.md`、本工作包 `review.md`、`evidence.md`

## Technical choices

- 使用 GitHub 托管 `ubuntu-latest` runner。
- 使用当前官方主版本：`actions/checkout@v7`、`actions/setup-java@v5`、`actions/setup-node@v7`、`actions/setup-python@v7`、`gitleaks/gitleaks-action@v3`。
- Workflow 默认权限为 `contents: read`，不授予部署、包发布或仓库写权限。
- 前端只执行 `npm ci` 和 `npm run build`；项目当前没有 test/lint/typecheck 脚本，不伪造这些检查。
- Python CI 使用 `requirements-ci.txt` 继承运行时依赖并固定 pytest。
- CI 不连接真实 MySQL、Redis、RabbitMQ、Elasticsearch、支付宝、OSS 或 LLM。
- Gitleaks 仅按完整 fingerprint 忽略同一历史提交中、现已删除的两处相同 API 文档示例 token；不按规则、路径或提交整体放行。
- 当前只有一位 GitHub 维护者，分支保护要求 PR 和全部 checks，但 approving review 数先设为 0；本地独立审查仍是交付门禁，新增协作者后再提升为 1。

## Risks and rollback

- 当前工作树包含未提交 B1 修改；所有检查只读取这些改动，不覆盖或纳入本工作包范围。
- 首次推送后 CI 可能暴露环境依赖或历史秘密；失败必须修复或轮换，不能通过 allowlist 隐藏真实问题。
- Gitleaks 全历史扫描可能发现已删除但仍在 Git 历史中的凭据；报告必须保持脱敏。
- 回滚方式是在独立变更中移除新增 GitHub 配置并恢复工作流文档，不影响业务数据库和运行时数据。

## Verification commands

```powershell
# Java
Push-Location backend; mvn --batch-mode --no-transfer-progress test; Pop-Location

# Frontend
Push-Location frontend/fashion-client; npm ci; npm run build; Pop-Location
Push-Location frontend/fashion-admin; npm ci; npm run build; Pop-Location

# Python（使用隔离虚拟环境）
python -m venv <temporary-path>
<temporary-python> -m pip install -r agent-service/requirements-ci.txt
Push-Location agent-service; <temporary-python> -m pytest -q; Pop-Location

# Config and repository checks
actionlint .github/workflows/*.yml
git diff --check
```

若本机没有 `actionlint` 或 Gitleaks，使用临时下载/官方容器做等价验证，并在 `evidence.md` 记录工具版本与命令。临时目录必须位于系统临时区并在验证后安全清理。
