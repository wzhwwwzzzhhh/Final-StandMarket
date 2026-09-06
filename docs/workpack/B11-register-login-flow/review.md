# B11-register-login-flow · Independent review

> Verdict: **PASS**（2026-09-06，独立只读上下文；无 P0/P1）

## Scope and drift

审查 PR #25 全量 diff（3 提交：fix 回退 / feat register / docs B11），含 bean 回退的 pre-B10 祖先、user 表结构、Mapper insert、controller 绑定、前端 API/组件。无范围内漂移。

## Findings

| 级别 | 位置 | 问题 | 处置 |
|---|---|---|---|
| P2 | `sendSmsCode` | 验证码未真正投递到手机（无 SMS 网关，仅缓存 Redis + 日志）；与既有短信登录共用，非本次引入，但 register 扩展了覆盖面 | 不阻塞合并；生产前需接真实 SMS 网关 |
| P2 | `UserController.register` | `@RequestBody User` 绑定面过宽，匿名可设 name/avatar/idNumber 等（controller 既有，register 首次成为一线功能） | 不阻塞合并；后续用专用 RegisterDTO 收窄 |
| P3 | register | verify→delete→insert 非原子（并发两请求可都过校验；DB `idx_user_phone` 兜底，败者返回"注册失败"而非"已注册"） | 记录，不阻塞 |
| P3 | register | 验证码在查重前即删（已注册用户会烧掉一个码） | 记录，不阻塞 |
| P3 | Login.vue | 注册弹窗关闭后倒计时 timer 未清理（后台继续走） | 记录，不阻塞 |
| P3 | register | 后端不强制密码非空（仅前端 ≥6 校验；直接调 API 可建无密码账号，仍可短信登录） | 记录，不阻塞 |

## Acceptance evidence review

| AC | Code/test evidence | Result |
|---|---|---|
| 注册入口不再是「开发中」占位 | Login.vue `goRegister()` → 注册弹窗，接 sendSmsCode/register | PASS |
| 注册能发验证码并创建用户 | evidence：sms-code → Redis 取码 → register 成功 → 新账号可登录 | PASS |
| 错误/过期验证码被拒 | `storedCode==null \|\| !storedCode.equals(code)` null-safe；evidence curl 错码 → 「验证码错误或已过期」；单测 registrationRejectsWrongCode | PASS |
| 重复注册被拒 | `selectByPhone` 查重 + DB `idx_user_phone` 唯一索引兜底 | PASS |
| 两端不回归 | evidence：mvn test 573/0/0、npm run build 通过；CI 5 checks 全绿 | PASS |

## Residual risks

- P2/P3 六项见上（非阻塞）。F1（SMS 投递）与 F2（绑定面收窄）建议生产前处理。
- 阶段 B 剩余阻塞：B0-AC6 外部密钥轮换、RabbitMQ delay.queue TTL、真实 SMS 网关。
