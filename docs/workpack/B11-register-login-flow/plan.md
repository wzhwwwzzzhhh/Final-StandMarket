# B11-register-login-flow · Workpack plan

> Status: 进行中（用户直接要求补齐，范围明确）
> Requirement source: 阶段 B B11「关键流程可运行」— 注册/登录为关键流程；前端 `Login.vue` 注册入口仍是「开发中」占位，后端 `/user/register`、`/user/sms-code` 已实现但未接。

## Scope

### In scope

1. **前端注册流程**：`Login.vue` 实现注册弹窗（手机号 + 验证码 + 密码 + 确认密码），`goRegister()` 从占位改为真实流程，调 `userApi.sendSmsCode` + `userApi.register`。
2. **后端注册验证码校验**：`register` 复用短信登录已验证的 Redis 验证码模式（`RedisKey.USER_LOGIN_CODE_KEY`），注册前校验验证码，通过后删除防止复用；修正「验证码已发但注册不校验」的伪造手机号注册缺口。
3. `User` 实体加瞬态 `code` 字段（Mapper insert 为显式列清单，不持久化）。

### Out of scope

- 短信登录流程（已实现且正常，不动）。
- 微信/QQ/微博第三方登录（前端占位，另行处理）。
- 忘记密码流程（前端占位，另行处理）。

## Acceptance mapping

| AC | Planned behavior | Verification |
|---|---|---|
| 注册入口不再是「开发中」占位 | `goRegister()` 打开注册弹窗 | 前端源码 + 构建 |
| 注册能发验证码并创建用户 | 手机号 → 获取验证码 → 填码+密码 → register 成功 | curl 测 sendSmsCode + register（含验证码校验） |
| 错误/过期验证码被拒 | register 校验 Redis 码，不匹配返回「验证码错误或已过期」 | curl 传错码验证 |
| 重复注册被拒 | 手机号已存在返回「手机号已注册」 | curl 重复注册 |
| 两端不回归 | 用户端构建通过 | `npm run build` |

## Slices

单切片：`register-login-flow`（前端注册 UI + 后端验证码校验，内聚可验收）。

## File-level change surface

| 文件 | 变更 |
|---|---|
| `frontend/fashion-client/src/views/Login.vue` | + 注册弹窗（模板 + 方法），`goRegister()` 实现 |
| `backend/fashion-pojo/.../entity/User.java` | + `code` 瞬态字段 |
| `backend/fashion-server/.../UserServiceImpl.java` | `register` + 验证码校验 |

## Risks and rollback

- `User.code` 字段不持久化（insert 显式列清单），无迁移风险。
- register 加验证码校验改变匿名注册行为：未发码/错码不能注册；与短信登录模式一致，无新歧义（引用已确认的 B4 匿名注册 + 短信验证码模式）。
- 回滚：撤销 2 处后端改动 + 前端弹窗即可，无数据迁移。

## Verification commands

```bash
# 后端
curl -X POST /user/sms-code -d "<新手机号>"          # text/plain
curl -X POST /user/register -d '{"phone":"<新手机号>","password":"x","code":"<Redis 里的码>"}'
# 前端
cd frontend/fashion-client && npm run build
```
