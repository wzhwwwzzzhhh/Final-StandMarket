# B8 商品缓存版本化与 ES 可恢复同步 · Design

> Status: 已确认（2026-09-04）
> Requirement source: `docs/plans/阶段B-P0P1交易链路修复.md` B8 / GitHub Issue #20（Stage B 总跟踪 #3）
> Baseline: `98454176227bb0b0a936a0b6c58e35f3696e6788`
> Updated: 2026-09-04

## 1. Goal and scope

### In scope

- 把商品列表缓存改成由 MySQL 单调版本驱动的不可变命名空间；旧版本不扫描、不批量删除，只按有界物理 TTL 自然过期。
- 把商品新增、修改、上下架、删除、列表版本、详情代际和 Redis/ES 投递事实纳入同一个 MySQL 本地事务。
- 使 Redis 不可用、发布失败、版本镜像丢失/淘汰/损坏时，读请求安全回源 MySQL，而不是复用可能过期的命名空间。
- 用详情代际隔离并发旧读回写；修复缓存重建锁的 token 所有权和 Lua compare-delete。
- 为 ES ACTIVE 商品 UPSERT、下架/删除商品 DELETE 建立持久、幂等、有序、有限重试、可观察、可由多实例安全抢占的任务。
- 明确 `stock` 与 `sales` 的投影边界，以及从 B8 上线到 B11 发布门禁之间的迁移、兼容和回滚要求。
- 为真实 Spring 事务代理、MyBatis、MySQL 8.0.16+、Redis 7.0.x 和仓库隔离 Elasticsearch 8.17.0/目标环境实测兼容版本设计可重复集成验证。

### Out of scope

- 搜索相关性、IK/Pinyin mapping 重构、前端视觉改造和 B9 Agent/前端契约治理。
- 给订单链路新增“支付后累计销量”等尚未确认的业务能力；B8 只固定已有 `sales` 字段若发生写入时必须遵守的投影契约。
- 秒杀券库存、其他缓存域、通用分布式事务框架和 B10 Flyway/baseline 总体治理。
- 生产 ES 全量重建、生产 Redis/ES 操作、生产迁移或部署；这些仍需 B10/B11 和单独授权。
- 宣称 MySQL、Redis、ES 是原子分布式事务，或在 Redis/ES 失败时回滚已经提交的 MySQL 商品事实。

## 2. Current behavior and constraints

### 2.1 Confirmed repository facts

1. `ProductController` 在 Controller 层执行 MySQL 写、`DEL productPage:*` 和 ES 调用；普通 Redis `DEL` 不解释通配符，且 MySQL、缓存、ES 没有共同恢复事实。
2. 用户列表 key 直接拼接可空参数，未固定用户端 `isSale=true`，默认缓存 24 小时；没有列表版本，也没有参数合法域和无歧义编码。
3. 用户详情使用 `product:{id}` 逻辑过期缓存。`setWithLogicalExpire` 没有物理 TTL；实际值分支忽略调用方 `TimeUnit` 并固定按秒；空值和实际值共用 TTL。
4. `CacheClient` 的锁值固定为 `1`，解锁直接 `DEL`；`queryWithMutex` 即使没有获得锁也会进入 `finally` 解锁。
5. 商品新增、修改、下架和删除不会可靠改变详情代际；异步重建可在更新提交后把旧对象重新写回同一 key。
6. `ProductIndexServiceImpl` 吞掉 UPSERT 异常，并把所有 `IOException` DELETE 异常都当成可忽略；这会错误吞掉连接失败、超时和索引不存在。
7. `ProductSyncTask` 每 5 分钟只遍历 MySQL 当前商品并 UPSERT。它不能证明单次变更是否成功，也无法为 MySQL 已删除而 ES 仍存在的文档生成 DELETE。
8. 当前 Java 低级 REST client 为 7.17.15，项目提供的隔离 ES 镜像和 Python client 为 8.x；实现前必须用真实 8.17.0 实例验证所用 REST 契约，不能只凭客户端编译通过。
9. 普通订单 `OrderServiceImpl.create` 在真实 Spring 事务中调用 `ProductMapper.deductStock`，取消在独立 Spring 事务中调用 `restoreStock`。仓库没有运行时 `sales` 增减语句；管理员 DTO 也不接收 `sales`。
10. 用户列表显示 `sales`，详情显示并依赖 `stock/sales`；Agent 搜索支持 `sales_desc`。因此不能在 B8 静默删除响应字段或搜索排序契约。
11. 现有 MySQL 脚本是 B10 前的人工增量迁移模式；MySQL DDL 会隐式提交，错误/部分定义必须在第一条 DDL 前拦截。

### 2.2 Consistency model

- **唯一业务事实源是 MySQL。** 商品行、全局列表版本、商品详情代际和两类投递任务在一个本地事务中提交或回滚。
- Redis 只保存版本镜像和可丢弃缓存；ES 只保存可重建搜索投影。Redis/ES 回调或重试不得自行 `INCR` 业务版本。
- 对于写成功返回以后开始的列表/详情请求，读路径先读取 MySQL 权威版本/代际。健康 Redis 可命中新命名空间；Redis 异常、镜像落后、缺失或异常超前时直接读 MySQL。这样 DB 已提交而 Redis 发布失败不会导致旧 key 再次可达。
- 与写事务并发、且在写提交前已经取得旧版本的读请求允许返回旧快照；它即使稍后回写，也只能写旧代际 key。写成功返回以后新建的请求不得选择旧代际。
- ES 在依赖健康时的目标收敛窗口为任务轮询周期加一次调用超时，默认不超过 5 秒；依赖持续失败时窗口保持开放并可查询，达到有限重试上限后进入 `FAILED_TERMINAL`，不得宣称已收敛。

## 3. Design decisions

### 3.1 Durable list version and normalized key

新增 MySQL 单例表 `product_catalog_state`：

| Column | Contract |
| --- | --- |
| `id` | 固定为 `1`，主键并有检查约束 |
| `list_version` | `BIGINT NOT NULL`，合法域 `1..9007199254740991`（JavaScript/Lua 精确整数上限）；迁移时以 UTC epoch milliseconds 作为非零 seed，此后只在商品目录事务内 `+1` |
| `updated_at` | `DATETIME(3)`，仅用于诊断 |

商品目录事务通过 `SELECT ... FOR UPDATE` 锁定单例行；一次成功的目录新增、目录字段修改、上下架或删除只执行一次带上限谓词的 `list_version=list_version+1 WHERE list_version < 9007199254740991`，影响行数必须为 1。相同事务失败则商品行、版本、详情代际和任务全部回滚。Redis 端把版本当十进制字符串，只能“发布至少为 V”，重复回调不产生新的版本；MySQL、Java `long`、JSON 和 Lua 均拒绝越界值。

Redis key：

- 权威版本镜像：`cache:product:list:v2:published-version`
- 列表值：`cache:product:list:v2:{listVersion}:{querySha256}`
- 不再执行 `DEL productPage:*`，也不以 `KEYS`/`SCAN` 删除新列表命名空间。

用户查询在访问缓存或 MyBatis 前生成不可变 `NormalizedProductQuery`：

1. `page >= 1`，`1 <= pageSize <= 100`；非法值返回明确参数错误，不通过截断制造 key 冲突。
2. `categoryId` 为 `null` 或正整数；用户端强制 `isSale=true`，忽略外部把它改成 `false/null` 的企图。
3. `sortBy` 只允许 `createTime/default`（统一为 `createTime`）、`price_asc`、`price_desc`、`sales`；未知值返回参数错误。
4. `keyword/tag` 只去除首尾 `Character.isWhitespace` 字符，保留内部空白、大小写和 Unicode code point；不做会改变现有 `utf8mb3_bin` 查询语义的 NFKC/lowercase。
5. canonical bytes 固定为 UTF-8、固定字段顺序和长度前缀编码：`v1|page|pageSize|categoryId-or--|sort|keywordByteLength:keyword|tagByteLength:tag|sale=1`；key 只放其 SHA-256 小写十六进制，不暴露原始搜索词，也没有分隔符歧义。

每次列表读都先从 MySQL 单例行读取 `Vdb`。Redis 镜像的处理是：

- `Vredis == Vdb`：允许读 `Vdb` 命名空间。
- 镜像缺失或 `Vredis < Vdb`：用 Lua max-publish 尝试补到 `Vdb`；成功才允许访问 Redis 值，失败则本次直接查询 MySQL。
- `Vredis > Vdb`：先重新读取一次 `Vdb`。若第二次已追平，按正常并发推进处理；若仍 ahead，本次绕过 Redis并增加 ahead 指标，只有连续超过配置阈值才告警。绝不把 MySQL 版本向上“修复”到 Redis 值，也不向下覆盖未知更高值。
- 镜像非数字、负数或越过安全整数上限：视为损坏，立即绕过并告警。
- 连接、超时或 Redis 命令异常：本次绕过全部 Redis 读写并查询 MySQL。序列化/代码错误不得伪装成普通 cache miss，需进入错误日志和指标。

MySQL singleton 读取超时、连接失败、缺行、多行或非法版本时不得退回 Redis，admin/user 统一映射为稳定错误码 `PRODUCT_CATALOG_SOURCE_UNAVAILABLE`（HTTP 503），不得误报商品不存在或返回旧缓存。缓存可用性不能越过 MySQL 事实源故障。

一个旧读可在 V1 下完成 DB 查询并晚于 V2 提交写回 V1 key，但所有后续请求由 MySQL 选出 V2，V1 只按有界物理 TTL 消亡。

### 3.2 Detail generation and stale-write fencing

新增 `product_catalog_revision`，保留物理删除后的 tombstone：

| Column | Contract |
| --- | --- |
| `product_id` | 主键 |
| `item_version` | 本次目录事务取得的全局 `list_version` |
| `item_state` | `ACTIVE / INACTIVE / DELETED` |
| `es_locked_by`, `es_locked_until` | 同 product ES 外呼的持久 lease；商品变更保留 lease 字段 |
| `updated_at` | 诊断时间 |

迁移为既有商品创建 `item_version=seed`；以后新增/修改/上下架/删除都在商品事务中 upsert。`INACTIVE` 表示行仍存在但用户不可见，`DELETED` 表示行已物理删除；二者在用户详情均形成空值，在 ES 均形成 DELETE。

详情 key 和锁 key：

- 值：`cache:product:detail:v2:{productId}:{itemVersion}`
- 已发布代际镜像：`cache:product:detail:v2:{productId}:published-version`
- 锁：`lock:product:detail:v2:{productId}:{itemVersion}`

list/detail published-version 都没有应用 TTL，但允许 Redis 淘汰；丢失时从 MySQL恢复，不能默认 0。详情镜像缺失/behind 时 max-publish 后才访问缓存；ahead 时重读 revision 一次并按列表相同规则 bypass/计量；损坏或 Redis 故障则直接走 MySQL。

用户详情先读取 MySQL revision，再选择相同代际 key。revision 查询失败统一返回 `PRODUCT_CATALOG_SOURCE_UNAVAILABLE`，直接 fail closed。revision 缺失时再查 MySQL：商品也不存在表示从未存在的合法 not-found，本次不缓存；商品存在表示迁移不变量破坏，返回同一 503 并告警。`ACTIVE` revision 与商品不存在/下架、`INACTIVE` 与商品启用等矛盾同样 fail closed，不能绕过 revision 返回对象。并发旧读只能写旧 key；更新后的请求选择新代际，因此旧对象无法覆盖新事实。下架/删除后的新代际保存独立短 TTL 空值。

重建落值使用 Lua fence：只有“Redis 当前发布的详情代际不高于本次代际，且锁值仍等于 token”时才写入该代际 key；锁过期或代际已经推进时放弃写入。即使 Lua 因 Redis 故障未执行，读路径的 MySQL revision 门禁仍保证旧代际不可达。

管理员详情不使用用户缓存，按主键读取包括下架状态在内的 MySQL 行。

### 3.3 Lock ownership

- `tryLock` 返回随机 UUID token，而不是 boolean；值至少包含 128 bit 随机量，不能复用线程 ID、商品 ID 或固定字符串。
- 获取使用单条 `SET key token NX PX lockTtlMillis`。未取得 token 的调用栈没有解锁能力，也不得进入 `finally` compare-delete。
- 释放使用预加载 Lua：`GET key == ARGV[1]` 才 `DEL key`，比较和删除原子完成。返回 0 表示租约已失效或已换主，不是可再次裸删的理由。
- A 的租约过期、B 获得新 token 后，A 的释放脚本必须返回 0，不能删除 B 的锁。
- 锁只降低击穿，绝不是正确性事实源；正确性来自 MySQL revision 和版本化 key。

### 3.4 TTL, units and jitter

新建 `@ConfigurationProperties`，所有时长使用 Spring `Duration`，内部统一换算毫秒；公共缓存 API 不再混用裸 `Long + TimeUnit`。

| Value | Default | Meaning |
| --- | --- | --- |
| list actual physical TTL | 15m | 列表实际值的 Redis 生存期 |
| detail logical TTL | 10m | 命中后允许同步返回的鲜度窗口 |
| detail actual physical TTL | 30m | 包含逻辑过期 envelope 的硬上限 |
| empty physical TTL | 30s | 有 revision 的 `INACTIVE/DELETED` 空值；从未存在的 ID 不缓存 |
| actual-value jitter | 0..120s | 列表/详情物理 TTL 的均匀非负抖动 |
| empty-value jitter | 0..10s | 空值 TTL 的独立抖动 |
| rebuild lock TTL | 10s | 单次重建租约 |

启动校验要求所有 TTL 为正、jitter 非负，且详情物理 TTL 下界大于逻辑 TTL加锁租期。jitter 通过可注入 `LongSupplier/Random` 产生闭区间 `[0,max]` 值，测试固定 seed 或边界 supplier，精确断言 0 和 max；不以概率测试冒充边界证明。

空值没有逻辑过期 envelope；实际详情有逻辑 TTL 和有界物理 TTL。任何值都不得创建无物理 TTL key。

### 3.5 MySQL transaction and after-commit boundary

Controller 不再拼装跨存储时序。`ProductService` 提供通过 Spring bean 代理调用的事务用例：新增、字段分类更新、上下架、删除。更新先锁定现有行并比较规范值：

- 目录字段为 `name/description/price/image/categoryId/tag/status/sales`；库存字段只有 `stock`。
- stock-only 更新只在该 MySQL 事务中更新 stock，不推进目录版本、不改 revision、不创建 Redis/ES task。
- 目录字段与 stock 混合更新只推进一次目录版本并产生一组任务；完全 no-op 不写 SQL、不推进版本。
- 当前公开 admin DTO 不允许 sales 写入；未来 sales 用例必须走目录事务。通用 `ProductMapper.update` 不得被 Controller、订单或其他服务直接调用来绕过分类。
- 新增用例在 SQL 中持久化 `image`，并显式写入 `sales=0`；事务内按主键回读后必须证明 image 和其他投影字段等于提交事实，再构造 task。为兼容迁移前/回滚后的旧二进制显式插入 NULL，B8 保留 `sales INT NULL DEFAULT 0` 列定义；领域规范把 legacy null 解释为 0，codec、列表排序、cache/ES 和 API 都使用该规范值，负数或越界仍作为脏数据停止。`NOT NULL` 收紧留给 B10 的 expand-contract 顺序。

需要目录投影的事务依次：

1. 校验并写入/锁定目标商品；更新和删除使用包含下架商品的 locking read，删除前保留必要快照。
2. 锁 `product_catalog_state`，推进一次 `list_version` 得到 V。
3. upsert `product_catalog_revision(productId,V,state)`；删除也保留 tombstone。
4. 以提交后的规范商品快照创建 Redis 发布任务和 ES UPSERT/DELETE 任务；ACTIVE 为 UPSERT，INACTIVE/DELETED 为 DELETE。
5. 注册事务同步；事务提交后只通过同一 task claim/token/CAS 处理器尝试 REDIS fast-path，并唤醒异步 worker。事务回滚时 after-commit 不执行，也没有版本、revision 或任务残留。

`afterCommit` wrapper 必须捕获所有异常且永不向 Controller 外抛；fast-path claim、状态落库或 executor wakeup 任一步失败，原始 `PENDING/RETRY_WAIT` task 仍由固定轮询恢复。它不得在 scheduler 之外直接改 task，也不得因 Redis 异常跳过最终 wakeup。Redis fast-path 有严格短超时并使用 `REQUIRES_NEW` 状态事务。MySQL 一旦提交，Redis/ES 失败不能把 API 伪装成“DB 未写入”；接口仍返回商品写成功，同时任务保持可恢复。由于后续读请求每次比较 MySQL 权威版本，Redis 发布失败会降级为 MySQL 读，不会命中旧列表/详情。

不得在同类内部 `this.save/this.update` 绕过代理。真实集成测试必须断言服务对象是 AOP proxy，并通过代理证明 commit/rollback 与 after-commit 的先后关系。

### 3.6 Stock and sales boundary

- **`stock` 从列表/详情长 TTL payload 和 ES 文档中移除。** 普通订单扣减、取消/退款回补及 admin stock-only 更新继续以 MySQL 事务和条件更新为事实，不推进全局目录版本，也不创建 ES 任务，避免每笔订单造成所有商品列表命名空间失效。
- 为保持现有用户 API，列表 cache hit/miss 都在返回前按本页 product IDs 用一次 MyBatis 批量查询补齐 MySQL `stock`；详情同样从 MySQL 读取动态库存。缓存对象与 HTTP 响应对象分离，不能把补齐后的 stock 重新写回长 TTL cache。下单仍以条件扣减结果为最终库存门禁，页面库存不是预留承诺。
- **`sales` 保留在列表/详情慢投影和 ES 文档中，并纳入目录一致性链路。** 当前仓库没有任何运行时 sales 写路径，B8 不新增销量结算语义；管理员现有 DTO 也不能直接写 sales。以后若增加 sales 写入，必须通过一个事务用例推进目录版本并产生恢复任务，否则不得合入。
- `sortBy=sales` 当前依赖 MySQL 的持久 sales 值并可安全缓存；若未来销售量变成高频字段，须另行设计短 TTL/ranking 投影，不能直接让每笔订单触发 B8 全局版本风暴。

### 3.7 Recoverable Redis/ES task

新增 `product_projection_task`，每个目录版本在商品事务中插入两个 target 行：

| Column | Contract |
| --- | --- |
| `id` | 自增主键 |
| `target` | `REDIS / ES` |
| `product_id`, `catalog_version` | 业务对象和全局顺序 |
| `operation` | REDIS 为 `PUBLISH`；ES 为 `UPSERT / DELETE` |
| `payload`, `payload_sha256` | UPSERT 的不可变规范 JSON；DELETE 至少保留 productId、state、version tombstone |
| `status` | `PENDING / PROCESSING / RETRY_WAIT / SUCCEEDED / SUPERSEDED / FAILED_TERMINAL` |
| `attempt_count`, `claim_count`, `repair_count`, `next_retry_at` | 每次获准外呼的 delivery attempt、总 claim、同版本 drift repair 周期与调度 |
| `locked_by`, `locked_until` | 多实例 lease；token 每次 claim 唯一 |
| `last_error_summary` | 最多 500 字符的脱敏类型/HTTP 状态/摘要，不保存凭据或完整响应 |
| timestamps | created/updated/completed |

唯一键为 `(target, product_id, catalog_version)`；检查约束固定合法 target/operation/status 组合、非负 attempt 和 payload 规则。UPSERT payload 必须从事务内回读的规范商品快照产生，避免新增后 Java 对象仍为默认/null；它包含 `id/name/description/categoryId/price/image/tag/status/sales/catalogVersion`，不含 stock。

`CanonicalProductProjectionCodec` 是 task 与 ES 的唯一编码器：字段顺序严格为上述顺序；UTF-8、无 BOM/尾换行；字符串使用同一 RFC 8259 escaping；null 写 JSON `null`；`price` 先按 MySQL `DECIMAL(10,2)` 规范为两位小数；整数只用十进制；不包含 `projectionHash` 自身。`payload_sha256` 与 ES `projectionHash` 都等于这些 exact payload bytes 的 SHA-256 小写十六进制。golden vectors 覆盖 null、中文/emoji/转义字符、`1.00` 与字段顺序，防止不同编码造成永久 drift。

状态机：

```text
PENDING/RETRY_WAIT --claim--> PROCESSING --success--> SUCCEEDED
                                      |--newer revision--> SUPERSEDED
                                      |--retryable failure, attempts < max--> RETRY_WAIT
                                      `--attempts >= max / non-retryable--> FAILED_TERMINAL
PROCESSING --lease expired--> 可再次 claim；下一次获准外呼会消费新的 attempt
FAILED_TERMINAL --显式人工重放并留审计日志--> RETRY_WAIT
SUCCEEDED --reconciliation 证明同版本 drift 且 repair_count 未达上限--> RETRY_WAIT
```

默认 max attempts 为 8；退避为 `min(1s * 2^(attempt-1), 5m) + [0,jitter]`。claim 事务在发放每一次可能的外部调用前原子增加 `attempt_count` 和 `claim_count`；若 attempt 已达上限则不再外呼并转终态。即使进程总在 I/O 或完成 CAS 前死亡，也最多发放 8 次外呼。连接、超时、429、可恢复 5xx 可重试；认证/授权、mapping 4xx、非法 payload 直接终态。配置不得把 max attempts 设为无限。

两条 claim 路径明确分离：

- `target=REDIS` 只使用自己的 task row `locked_by/locked_until`、token、attempt 和完成 CAS；不读取/占用 revision 的 `es_*` lease，因此 Redis fast-path 不阻塞 ES worker。
- `target=ES` 在短 MySQL 事务中先选择候选 task，再 `SELECT product_catalog_revision ... FOR UPDATE` 锁同一 product 行，检查并 CAS 写 `revision.es_locked_by/es_locked_until`，随后复查 current revision/旧 task 状态并写 task 的唯一 lease/attempt。这个 revision 行 lease 是数据库级 per-product ES 互斥；两个 SKIP LOCKED claim 即使选中不同 task，也必须串行竞争同一 revision 行，后提交者看到未过期 lease 后放弃。

ES I/O 在事务外执行，调用前最后核对 task owner 和 current revision，connect+socket/request timeout 必须小于 lease；完成同时以 owner CAS 结束 task并释放 revision lease，旧 owner 晚到只能 CAS 失败。商品变更 upsert revision 时保留现有 lease。服务重启后 lease 到期可继续，多实例不能同时拥有同 product 的 ES 外呼权。REDIS 和 ES target 可以并行收敛，互不占用对方 lease。

Redis `PUBLISH` 分别使用 Lua max-set 发布 list mirror 和对应 product detail mirror；二者不是跨 key 原子事务，task 只有在两者均达到目标值后才成功，部分成功可安全重试。重复、乱序回调只会保持或推进，不会回退。若任务版本小于当前 MySQL revision，可标记 `SUPERSEDED`；这不影响新版本任务。详情重建的 value/lock/mirror Lua 原子边界单独定义，不把 list 与 detail 假装为一个 Redis 原子事务。

### 3.8 ES ordering, idempotency and delete replay

- 逻辑索引名继续为 `products`；B8 不改分词/相关性字段，但增加内部 `catalogVersion: long`、`projectionHash: keyword` 元数据。切换前验证现有 mapping 中这些字段 absent-or-compatible、`status/sales` 类型和 dynamic policy；冲突即停止，不能依赖 dynamic mapping 猜测。
- 写请求携带 `catalogVersion/projectionHash`，并用 ES external/external_gte version 做服务端顺序保护。仓库 Docker 8.17.0 只是本地测试目标；目标兼容实例的实际版本、插件和 API 行为必须现场取证。
- worker 外呼前读取 `product_catalog_revision`。task version 小于 current revision 时直接 `SUPERSEDED`；相等时才调用 ES。这个 DB 门禁加 ES server-side version 共同防止 claim 后网络乱序。
- 重复 UPSERT/DELETE 使用相同 product ID 和 version。相同 UPSERT 可重复成功；旧版本冲突只在已证明 DB 存在更高 revision 时作为 superseded 成功，否则保留失败证据。
- DELETE task 的 tombstone 不依赖 `product` 行，所以物理删除后仍可无限期重放，直到成功、superseded 或终态。
- 只有“目标索引存在，且响应明确是该 document 的 `result=not_found`”才把 DELETE 404 视为幂等成功。`index_not_found_exception`、连接失败、超时、认证失败及其他 4xx/5xx 都不得吞掉。
- 下架与物理删除都发 ES DELETE；重新上架生成更高版本 UPSERT。Agent 不会搜索到下架商品。
- 当前 `/admin/es/sync -> rebuildIndex -> delete/create` 入口必须在 B8 禁用或替换为“创建/唤醒非破坏性 reconciliation run”；产品服务不再暴露删除 live index 的方法。若以后需要全量重建，只能写入新隔离索引、验证后原子切 alias，并受独立运维设计/授权约束，不能清空 external version/tombstone。

既有 ES 文档没有 B8 external version。迁移 seed 使用 epoch-millis 高水位，切换前通过隔离/目标 ES 扫描验证所有既有 `_version < seed`；不满足时停止切换，不能强行覆盖。上线 backfill 按 MySQL status 为 `status=1` 商品幂等创建 baseline UPSERT task，为 `status=0` 商品创建 INACTIVE revision + DELETE task，再扫描 ES 与 MySQL/revision 差异。ES-only ID 在 MySQL 创建 DELETED revision 和 DELETE task。

external delete version tombstone 受 ES `index.gc_deletes` 保留期限制，因此这里承诺的是**有界乱序保护加最终收敛**，不是永久原子屏障：同 product 外呼串行；HTTP 总 timeout 小于 lease；切换前验证 `gc_deletes` 大于 lease、最大外呼 timeout 和时钟/调度裕量之和；完成响应后重读 current revision，若已推进则确保新 task 已存在。周期 reconciliation 比较 `id + catalogVersion + projectionHash`，能修复 tombstone 窗口外极端迟到导致的旧文档复活。故障测试必须覆盖超过 tombstone 窗口后注入 stale doc，再由 reconciliation 收敛。

### 3.9 Durable reconciliation run

新增第四张表 `product_projection_reconcile_run`：

| Column | Contract |
| --- | --- |
| `run_id` | UUID 主键；同一时刻只有一个 ACTIVE run |
| `mode`, `phase`, `status` | `CUTOVER/PERIODIC`；`MYSQL_SCAN/ES_SCAN/VERIFY`；`PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED_TERMINAL` |
| `active_slot` | stored generated column：active status 映射为常量 `1`，终态为 `NULL`；唯一索引保证全库最多一个 active run |
| `mysql_cursor`, `es_search_after` | 已完成稳定边界；ES token 以无凭据 JSON 保存 |
| `locked_by`, `locked_until`, `attempt_count`, `next_retry_at` | 与 task 相同的有限 lease/retry 规则 |
| counts/timestamps/error | scanned、drift、repair、clean-pass 数和脱敏错误摘要 |

scheduler 创建 run 时依赖 `UNIQUE(active_slot)`；并发 duplicate-key 后读取既有 active run 而不是另建。run 逐批比较：ACTIVE MySQL 商品期望 ES 存在且 `catalogVersion/projectionHash` 相等；INACTIVE/DELETED 期望 ES 不存在；ES-only 期望 DELETE。发现 drift 时不直接写 ES，而是插入缺失 current-version task，或以 CAS 将相同 immutable SUCCEEDED task 重新打开为 RETRY_WAIT并增加 `repair_count`/audit；同版本自动 repair 默认最多 3 周期，达到上限或原 task 已 `FAILED_TERMINAL` 时只报告并进入人工终态，不能由每次周期扫描无限复活。唯一键仍禁止副本。

PIT/search_after 上下文过期或进程重启时，同一 run 的 ES phase 从头重扫，依靠 task 唯一键幂等；不能拿失效 cursor 跳过区间。只有完整 MYSQL_SCAN、完整 ES_SCAN 和随后一次零 drift VERIFY clean pass 都完成，run 才能 SUCCEEDED；部分扫描、ahead version、payload hash 冲突或 cursor 丢失不得标完成。ES 版本高于 MySQL current version 视为 split-brain，停止自动覆盖并进入人工终态。

### 3.10 Observability

- 管理端状态查询返回每 target/status 的数量、最老 pending age、max/total attempts、最近 `last_error_summary`、`next_retry_at` 和 terminal 数；不返回 payload、凭据或完整 ES body。
- 日志只记录 `taskId/target/productId/catalogVersion/status/httpStatus/errorClass`；搜索词、商品完整描述、连接 URL 中凭据和响应正文不进入日志。
- 指标至少包括 Redis bypass 次数、version missing/behind/ahead/corrupt、cache rebuild outcome、ES/Redis task success/retry/terminal、lease recovery 和 reconciliation drift。
- API 写成功但投影未收敛可由 task 状态证明；不得只用“已经打印 error 日志”作为补偿事实。
- reconciliation 状态查询还需展示 phase、cursor 是否有效、scanned/drift/repair/clean-pass、lease 和 terminal 原因。

## 4. Contracts and state transitions

### 4.1 Product mutation transaction

```text
Spring proxy enters TX
  -> mutate/lock product
  -> lock catalog singleton; V := V + 1 exactly once
  -> upsert revision(productId,V,state)
  -> insert REDIS task(V) + ES task(V)
  -> register synchronization
commit
  -> afterCommit uses task claim/CAS for bounded Redis fast-path
  -> catch every callback failure; finally signal async worker
  -> controller returns DB success
rollback
  -> product/version/revision/tasks all absent; no afterCommit call
```

### 4.2 List read

```text
normalize and validate query
  -> read MySQL Vdb
  -> MySQL state unavailable/invalid: fail closed; never use Redis
  -> Redis unavailable/corrupt/ahead/publish failure: query MySQL page
  -> Redis mirror == Vdb: GET list(Vdb,hash)
       -> hit: batch hydrate stock from MySQL and return
       -> miss: query MySQL page; SET bounded TTL+jitter; hydrate stock; return
```

### 4.3 Detail read and rebuild

```text
read MySQL revision R
  -> MySQL unavailable/invariant broken: fail closed; never use Redis
  -> Redis unavailable/invalid: query MySQL public product + stock
  -> GET detail(productId,R)
       -> fresh actual/empty: return
       -> logically expired actual: return old static view, holder rebuilds same R
       -> miss: holder queries DB; Lua writes only same R/token
update commits R+1
  -> every new request selects R+1; any late R write is unreachable
```

### 4.4 Projection outcomes

| MySQL outcome | Redis behavior | ES behavior | API meaning |
| --- | --- | --- | --- |
| transaction rollback | version/task absent | task absent | write fails; no projection action |
| commit, dependencies healthy | fast-path publishes new generation | worker converges within target window | success |
| commit, Redis unavailable | readers bypass to MySQL; REDIS task retries | independent | success, cache degraded and observable |
| commit, ES unavailable | cache remains safe | ES task retries then terminal if exhausted | success, search is explicitly not converged |
| newer event overtakes old | Lua cannot regress version | external version/current revision blocks old | old task `SUPERSEDED` |

## 5. File-level change surface

Expected implementation surface after plan confirmation; names may be refined without changing contracts:

- `backend/fashion-common/.../CacheClient.java` and new cache properties/Lua resource.
- `backend/fashion-pojo/.../Product*.java` plus cache projection/revision/task entities or DTOs; HTTP response remains field-compatible.
- `backend/fashion-server/.../controller/admin/ProductController.java`, `controller/user/UserProductController.java`, `service/ProductService.java`, `service/impl/ProductServiceImpl.java`.
- `ProductMapper.java/xml` and new catalog state/revision/projection task mappers/XML.
- `ProductIndexServiceImpl.java`, `ProductSyncTask.java`, `EsSyncController.java`（移除破坏性 live rebuild 入口）、admin ES/task/reconciliation status surface、scheduling/configuration。
- `backend/fashion-server/src/main/resources/application.yml` only for non-secret B8 defaults；B9 同路径修改必须在以后集成时人工协调，不能覆盖。
- `mysql/add_product_cache_consistency.sql`、`mysql/README.md`；不改 B10 Flyway 总体方案。
- 聚焦单元、Spring/MyBatis/MySQL、Redis 7 和 ES 8.17 integration tests。
- 不修改前端与 `agent-service`；B9 worktree 内容不得被复制、覆盖或回退。

本阶段只创建本 Design，不修改以上产品文件，也不修改共享索引文档。

## 6. Failure handling, idempotency, and compensation

1. **DB success / Redis failure**：事务内 REDIS task 是失败前已存在的事实；afterCommit 不外抛；读路径以 MySQL version/revision gate 绕过旧缓存；恢复器重复 max-publish。
2. **DB success / ES failure**：不可变 snapshot/tombstone 保证行删除后仍能重放；有限重试后终态并告警，不吞异常。
3. **Redis key evicted/restarted**：missing 不等于 version 0；从 MySQL读权威值并 max-publish。Redis 整体不可达就不碰缓存。
4. **concurrent stale fill**：旧 reader 只能写旧代际；token/fence 进一步阻止失锁线程落值。正确性不依赖删除旧 key。
5. **duplicate callbacks/tasks**：DB unique key、task completion CAS、Redis max-publish、ES external version 四层幂等。
6. **out-of-order ES**：调用前 current revision 检查，调用时 external version 检查；在配置的 lease/timeout/`gc_deletes` 窗口内阻止旧 DELETE/UPSERT 覆盖新事实，窗口外极端迟到由 version/hash reconciliation 检出并最终修复，不宣称永久屏障。
7. **service restart / worker death**：每次可外呼 claim 先消费一个 attempt；PROCESSING lease 到期后可重新 claim，反复崩溃仍会有限终止；任务 I/O 不持有 DB 行锁。
8. **partial sink success**：REDIS 与 ES 是两行独立任务；一个成功不会掩盖另一个失败，也无需回滚已完成 sink。
9. **manual replay**：只允许将特定 terminal task 重置为 RETRY_WAIT，并写操作日志；不得改 payload/version 或创建无唯一键的副本。
10. **stock change**：不触发目录/ES；响应从 MySQL补齐，最终下单仍以条件扣减为准。
11. **MySQL authority unavailable**：version/revision 读失败或不变量矛盾时 fail closed；不得把 Redis/ES 升格为事实源。
12. **reconciliation drift**：按 current revision/hash 重开同一幂等 task；完整扫描和 clean verify 前不声称收敛。

## 7. Migration, compatibility, and rollback

### 7.1 MySQL migration

`mysql/add_product_cache_consistency.sql` 为 B10 前人工执行的前向迁移：

1. 脚本最前解析并要求 MySQL `>= 8.0.16`，低版本在任何 DDL/DML 前主动 `SIGNAL`，因为 B8 依赖 enforced CHECK；同时要求目标库和 `product` 表存在并检查所依赖列、类型和主键。
2. 在第一条 DDL 前检查四个新表及其完整列、默认值、check、唯一键、索引、engine/collation 签名。允许恢复的 DDL 前缀只有：全无；仅 exact-empty state；exact-empty state+revision；exact-empty state+revision+task。迁移按 state → revision → task → reconcile_run 建表，所有 seed/backfill 都在四表完整后才开始。
3. 任一反向顺序、未知同名表、错误/缺失索引或 check、非空不完整前缀都 `SIGNAL` 停止；不会猜测或自动删除对象。允许的空前缀重跑只创建下一张表，再重新验证全部签名。
4. 四表完整且仍为空后处理 `product.sales`：只接受兼容定义 `INT NULL DEFAULT 0`，先记录/断言非负范围，并在禁商品写窗口执行 `UPDATE ... SET sales=0 WHERE sales IS NULL`；列保持 nullable，使旧 mapper 的显式 NULL insert 在迁移后和应用回滚后仍可运行。中断重跑允许“空四表 + nullable sales（有或无 null）”，重复 UPDATE 幂等；规范化数量写入迁移证据。
5. product 定义和四表完整后插入单例 seed，再按 `product.status` 为现有商品幂等插入 ACTIVE/INACTIVE revision；baseline/codec 对任何回滚后新增的 legacy null sales 仍规范为 0。重复执行验证最终元数据和 seed/revision 关系，不重复推进版本。
6. 脏数据门禁至少覆盖：无效 product id/status、负数/越界 sales、singleton 数量/安全整数越界、ACTIVE/INACTIVE 与 product 状态矛盾、revision 版本大于 singleton、非法 task/run 状态/target/op、payload hash 不匹配和重复业务键。

首次执行、完整重复执行、三个允许 empty prefix、sales 规范化中断、每个反向/非空 prefix、错误列/索引/check、低于 8.0.16 和脏数据都必须在真实 MySQL 8.0.16+ 中验证。脚本不连接 Redis/ES，也不自动执行生产 backfill。

### 7.2 Application and cache cutover

1. 先迁移并验证四表及 nullable sales 的审计归零；旧应用仍可运行，因为新表 additive 且 sales 列定义不收紧。
2. 以禁商品写窗口启动 B8 单实例，完成按 status 分类的 revision/baseline task backfill 和 ES version/hash 差集 dry-run；核对数量、seed 与 existing ES version 门禁。
3. 启用 B8 读写。新代码只读取 `v2` key，因此不会命中旧 `productPage:` 或 `product:`。
4. 新列表/详情 key 都有物理 TTL。旧逻辑详情可能没有 TTL，只能在回滚观察期后用受控 `SCAN`+精确前缀分批清理；禁止 `KEYS`，禁止伪通配符 `DEL`，且该生产操作需单独授权。
5. 多实例启用 worker 前验证 `SKIP LOCKED`、lease 和 task 指标；B11 才能宣称发布门禁完成。

### 7.3 ES compatibility and reconciliation

- 在隔离索引 `products_b8_it_<uuid>` 验证实际 ES 版本、IK/Pinyin 插件、Java REST client、metadata mapping、external version、`gc_deletes`、重复 UPSERT、DELETE not_found、409 顺序保护、索引不存在和超时分类。
- 生产切换不删除/重建 `products`。baseline UPSERT 与 ES-only DELETE 都先形成 MySQL task，再由正常 worker 应用。
- 差异扫描由 durable reconciliation run 使用 point-in-time/search_after；PIT 失效后从头重扫该 phase。若实现阶段确认现有 client 不支持可靠 PIT，可使用 scroll，但必须显式清理 context、失效后从头重扫并验证重启恢复。
- reconciliation 只创建缺失的幂等 task，不直接越过 outbox 写 ES。

### 7.4 Rollback boundary

- 迁移后、B8 写流量开启前可回滚旧应用；新增空表保留，不做反向 DDL。
- B8 已产生版本/task 后，旧应用不知道新命名空间和恢复事实，不能直接回滚。必须停商品写、排空/记录任务、验证 MySQL/ES、受控清理旧应用可能读取的遗留缓存后才可切回。
- 不删除 task/tombstone 来“回滚”，不把 Redis/ES 内容反写覆盖 MySQL，不以全量 ES 重建掩盖未解决 terminal task。
- 新表/字段的最终清理是后续独立迁移；B8、B10、B11 未完成前不执行生产回滚演练以外的操作。

## 8. Verification gates

### 8.1 TDD slices after plan confirmation

1. **版本化读写与缓存原语**：先证明规范化 key、MySQL 版本门禁、missing/behind/ahead/corrupt/unavailable 降级、详情旧读竞态、detail mirror、unique token、Lua compare-delete、TTL 单位和 jitter 边界的失败测试。
2. **事务事实与恢复任务**：先证明目录/mixed/stock-only/no-op 分类、提交恰好推进一次、回滚不推进/不建任务、after-commit 每个失败点不外抛、重复/乱序/崩溃 lease/终态的失败测试。
3. **真实 ES、reconciliation 与迁移**：先证明按 status baseline、DELETE 重放、404 分类、external version、gc_deletes 窗口外 drift repair、持久 cursor/部分扫描、迁移首次/重复/部分/脏数据门禁的失败测试。

Mock 只用于单元故障注入，不能替代真实依赖证据；源码字符串检查只能作为补充架构门禁，不能替代可执行行为测试。

### 8.2 Repeatable dependency conditions

- **MySQL**：loopback MySQL 8.0.16+；测试账号可创建/删除仅匹配 `fsm_b8_it_[0-9a-f]{32}` 的临时 schema；测试启动检查 host、版本、enforced CHECK 和 schema 正则，结束只删除本次 UUID schema。
- **Redis**：loopback、独占临时 Redis 7.0.x 容器或独占空 DB；启动校验版本、DBSIZE=0 和 exclusive flag；测试 key 全部带随机 `fsm:b8:it:{uuid}` 前缀，结束只删此前缀的精确 key。
- **Elasticsearch**：loopback、隔离 ES 8.17.0 实例，安全关闭或专用测试凭据；验证 IK/Pinyin 插件；索引名必须匹配 `products_b8_it_[0-9a-f]{32}`，禁止访问/删除共享 `products`。
- 真实 Spring 测试用 `AnnotationConfigApplicationContext`/实际 transaction manager、真实 MyBatis mapper 和被 AOP 代理的 ProductService；显式断言 `AopUtils.isAopProxy`。
- 三类集成测试都需要显式 system property 开关；环境缺失时报告 blocker，不能显示为“集成通过”。

### 8.3 Required failure injection

- MySQL：在 product 写后、version 后、revision 后、两 target task 之间注入 SQL failure，逐项证明同事务回滚；提交成功时 after-commit 才执行。新增商品还需证明 image 被持久化、B8 写入 sales=0，legacy mapper 显式 NULL 在迁移后仍兼容，事务回读的规范 sales、cache payload/hash 与 ES 文档一致。
- After-commit：在 claim DB、Redis call、task completion、executor wakeup 分别抛错，证明真实 Spring proxy 已提交的 API 不被伪装成失败，固定轮询仍恢复 task。
- Redis：不可达、命令超时、version missing/evicted、behind、ahead、非数字、max-publish 失败；A lease 过期后 B 取锁，再执行 A compare-delete。
- Cache race：旧 reader 在 DB 查询后暂停，写事务提交新 revision，再释放旧 reader；证明新请求不读取旧值且旧 key 有物理 TTL。
- ES：连接拒绝、超时、429、503、mapping 400、认证 401/403、index-not-found、document not-found、重复相同 version、旧 UPSERT 晚到、旧 DELETE 晚到、下架后重上架。
- Worker：REDIS 与 ES target 可并行且 Redis 不占 `es_*` lease；两个实例同时 claim 同 product 不同 ES task 时，revision row lease 只允许一个外呼权。覆盖 I/O 前后进程死亡、lease 超时、每次 re-claim 消费 attempt、旧 owner 重复完成 CAS、max-attempt terminal 和人工重放。
- Reconciliation：两个实例并发创建 run 只能得到一个 active_slot；人工创建 ES-only、MySQL-only、同 ID 旧 version/hash、INACTIVE 残留和 tombstone 窗口外旧文档；中断每个 phase 并重启，证明先创建/重开有界幂等 task，完整 clean pass 前不完成。

### 8.4 Acceptance mapping and completion gate

| Issue #20 acceptance | Required evidence |
| --- | --- |
| versioned normalized list key; no wildcard/KEYS | key unit behavior + Redis integration command observation |
| commit once / rollback none | real Spring/MyBatis/MySQL transaction test |
| post-success request cannot hit old list | concurrent MySQL+Redis integration test |
| Redis unsafe states fail-safe | Redis 7 fault matrix and metrics/task assertions |
| MySQL authority failure fail closed | singleton/revision missing, invalid and connection-failure behavior |
| detail invalidation and stale fill | deterministic latch race test with real MySQL/Redis |
| token compare-delete | Lua unit boundary + real Redis lease takeover |
| TTL/unit/jitter | deterministic boundary tests and Redis PTTL assertions |
| recoverable ES UPSERT/DELETE | real ES task retry and deleted-row replay |
| order/idempotency/finite retry | duplicate/concurrent/out-of-order/terminal behavior tests |
| observability and redaction | task query/metric assertions + sensitive log scan |
| stock/sales boundary | stock transaction tests prove no global bump; response hydration and ES doc assertions |
| migration safety | real MySQL first/repeat/allowed-empty-prefix/reverse/nonempty/wrong/dirty matrix |

最终实现还必须新鲜执行聚焦测试、`backend/mvn test`、真实依赖套件、`git diff --check`、限定范围 diff、敏感信息扫描和独立只读实现 Review；P0/P1/P2 全为 0 且 evidence 完整后才能标记“本地已验证”。

## 9. Confirmed decisions

用户于 2026-09-04 确认以下整组边界；没有遗留的架构二选一事项：

1. 接受 MySQL 权威版本的读前轻量查询，以换取 Redis 发布失败或版本 key 淘汰时不复用旧列表/详情。
2. 接受 stock 移出长 TTL cache/ES、响应时从 MySQL 批量补齐；sales 保留在目录/ES 投影，但 B8 不新增销量累计业务。
3. 接受 DB 提交即返回业务成功，Redis/ES 失败通过持久任务与降级读最终收敛，而不是伪装成跨存储原子回滚。
4. 接受新增四张 B8 表和 forward-only 迁移；生产迁移、cache 清理、ES backfill/cutover 仍需 B10/B11 与单独授权。

本次确认只授权创建 B8 workpack/plan；不授权产品实现、commit、push、PR、merge 或任何生产操作。

## 10. Independent review

- Round 1 verdict: FAIL（P0=0，P1=8，P2=4，P3=1；独立只读 reviewer，文件修改 0）
- Round 1 findings closed in this revision:
  1. baseline 按 status 映射 ACTIVE/INACTIVE，禁止把下架商品 UPSERT 回 ES；
  2. 定义 stock-only、mixed、catalog 和 no-op 更新分类；
  3. afterCommit 复用 task claim/CAS、永不外抛且轮询兜底；
  4. 每次外呼 claim 原子消费 attempt，反复崩溃仍有限终止；
  5. 新增 durable reconciliation run，比较 version/hash 并要求完整 clean pass；
  6. 禁用破坏 external version 的 live index rebuild 入口；
  7. MySQL authority 读取失败/不变量破坏 fail closed；
  8. 将 ES DELETE 保证收窄为 gc_deletes 有界保护 + 最终 reconciliation；
  9. 补齐 detail generation mirror、ES metadata mapping preflight、安全整数域、允许的空迁移前缀和 ahead 并发分类。
- Round 2 verdict: PASS for architecture gate（P0=0，P1=0，P2=5，P3=3；独立只读 reviewer，文件修改 0）
- Round 2 remaining findings closed in this revision:
  1. revision row 持久 lease 提供数据库级同 product ES 串行 claim；
  2. generated `active_slot` 唯一索引限制单个 active reconciliation run；
  3. 固定 canonical projection bytes，令 payload SHA-256 与 ES projectionHash 同源；
  4. MySQL 门禁收紧到 8.0.16+ 并覆盖 enforced CHECK；
  5. 新增商品持久化 image、规范 sales=0；
  6. 统一 never-existed 空值、ES 有界删除语义和 ES 实际版本措辞。
- Round 3 verdict: FAIL（P0=0，P1=1，P2=1，P3=0；独立只读 reviewer，文件修改 0）
- Round 3 findings closed in this revision:
  1. sales 列保持 nullable，旧二进制显式 NULL insert 与应用回滚兼容；B8 领域层统一 null→0，NOT NULL 留给 B10；
  2. revision `es_*` lease 仅用于 ES target，Redis 使用独立 task lease，二者可并行。
- Round 4 verdict: PASS（P0=0，P1=0，P2=0，P3=0；独立只读 reviewer，文件修改 0）
- Round 4 conclusion: nullable sales 的 expand/rollback 兼容与 REDIS/ES lease 分流均已闭合；全量复核未发现新的跨存储事务假设、缓存竞态、ES 恢复、迁移/回滚或范围漂移问题。用户已于 2026-09-04 确认 Design；产品代码继续受 workpack plan 确认门禁约束。
- Final counts: P0=0, P1=0, P2=0, P3=0
- Reviewer scope: 本 Design、Issue #20、Stage B B8 及相关商品写入/库存/缓存/ES/迁移/测试代码；只读，文件修改 0。
