# B11-register-login-flow · Evidence

## Acceptance criteria

| AC | Evidence | Result |
|---|---|---|
| 注册入口不再是「开发中」占位 | `Login.vue` `goRegister()` 由 `$message.info('注册功能开发中')` 改为打开注册弹窗（手机号+验证码+密码+确认密码），调 `userApi.sendSmsCode`/`userApi.register` | ✅ |
| 注册能发验证码并创建用户 | 功能测试：`POST /user/sms-code`(13800008888) → Redis 取码 903788 → `POST /user/register` {phone,password,code} → `{"code":1,"data":"注册成功"}`；新用户可登录 | ✅ |
| 错误/过期验证码被拒 | `POST /user/register` {code:"000000"} → `{"code":0,"msg":"验证码错误或已过期"}`；单测 `registrationRejectsWrongCode`（verify userMapper.insert never） | ✅ |
| 重复注册被拒 | register 校验手机号已存在（`selectByPhone`）→ `手机号已注册`（既有逻辑）；验证码用后即删（Redis GET 空）防重放 | ✅ |
| 两端不回归 | `npm run build`(fashion-client) exit=0；`mvn test` 573/0/0（+1 负向测试） | ✅ |

## Verification runs

| Time | Command | Exit/result | Notes |
|---|---|---|---|
| 2026-09-06 | `mvn -pl fashion-server -am test -Dtest=UserServiceImplTest` | 0；14 tests / 0 failures | 含新 `registrationRejectsWrongCode` |
| 2026-09-06 | `npm run build`(fashion-client) | 0 | Login.vue 注册弹窗编译通过 |
| 2026-09-06 | `curl POST /user/sms-code` (text/plain) | `{"code":1,"data":"验证码发送成功"}` | 新构建后端 :8080 |
| 2026-09-06 | `redis-cli GET user:login:code:13800008888` | 903788 | 从隧道 Redis 取码 |
| 2026-09-06 | `curl POST /user/register` {正确码} | `{"code":1,"data":"注册成功"}` | 验证码校验通过并删除 |
| 2026-09-06 | `curl POST /user/register` {错码} | `{"code":0,"msg":"验证码错误或已过期"}` | 伪造手机号被拒 |
| 2026-09-06 | `curl POST /user/login` (新账号) | `{"code":1,"data":{token,...}}` | 新用户可登录 |
| 2026-09-06 | `cd backend && mvn test` | 0；573/0/0/147 | 全量回归 |

## 实现说明

- **后端**：`User` 加瞬态 `code` 字段（`@JsonProperty(WRITE_ONLY)`，Mapper insert 显式列清单故不持久化）；`UserServiceImpl.register` 复用短信登录已验证的 `RedisKey.USER_LOGIN_CODE_KEY` 模式：先校验验证码（Redis 比对，通过后删除防重放），再查重手机号、BCrypt 加密、insert。
- **前端**：`Login.vue` 新增注册弹窗，`goRegister()` 打开弹窗；手机号校验 → 获取验证码（60s 倒计时）→ 填码+密码+确认密码 → `userApi.register` → 成功后关闭弹窗、回填登录手机号并切到密码登录。

## Not run or blocked

- 前端 test/lint 脚本仍不存在（开发规范已知，仅 build 证据）。
- RabbitMQ `delay.queue` TTL 不一致（900000 vs 1800000，预存在，非本 workpack 引入）——另行处理。

## Local delivery summary

注册功能从「开发中」占位补齐为可用流程，且后端补上了验证码校验（防伪造手机号注册）。登录/注册/短信验证码全链路功能测试通过，573 测试无回归，前端构建通过。待独立审查与远程交付授权。
