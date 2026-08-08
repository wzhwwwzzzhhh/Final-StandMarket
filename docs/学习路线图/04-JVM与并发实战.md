# 第四阶段：JVM 与并发实战（6步）

---

## Step 4.1 JVM 内存结构

### 考点覆盖

**JVM 内存区域（JDK 8）：**

| 区域 | 存储内容 | 线程私有？ | 异常 |
|------|---------|-----------|------|
| 堆（Heap） | 对象实例、数组 | 共享 | OOM: Java heap space |
| 方法区（Metaspace） | 类信息、常量、静态变量 | 共享 | OOM: Metaspace |
| 虚拟机栈 | 局部变量表、操作数栈、方法出口 | 私有 | StackOverflowError / OOM |
| 本地方法栈 | native 方法 | 私有 | StackOverflowError |
| 程序计数器 | 当前线程执行字节码地址 | 私有 | 无 |
| 直接内存 | NIO 堆外内存 | 共享 | OOM: Direct buffer memory |

**对象创建全流程：**
1. 类加载检查 → 2. 分配内存（指针碰撞 / 空闲列表）→ 3. 零值初始化 → 4. 设置对象头 → 5. 执行 init

**对象内存布局（HotSpot）：**
- **Mark Word**：8 字节（32位）/ 12 字节（64位压缩）— 锁信息、GC 标记、HashCode
- **Klass Pointer**：4 字节（压缩） / 8 字节 — 指向类元数据
- **实例数据**：字段按类型对齐
- **对齐填充**：补齐到 8 的倍数

### 实操练习

**1. 查看 JVM 默认参数：**

```bash
# 查看默认 GC
java -XX:+PrintCommandLineFlags -version

# 查看 G1 相关参数
java -XX:+PrintFlagsFinal -version | grep -E "G1|HeapSize|Metaspace"
```

**2. 给项目配置 JVM 参数（在启动脚本中添加）：**

```bash
# 开发环境测试参数
java -Xms512m -Xmx512m \
     -XX:+UseG1GC \
     -XX:+PrintGCDetails -XX:+PrintGCDateStamps \
     -Xloggc:logs/gc.log \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=logs/heap.hprof \
     -jar fashion-server-1.0-SNAPSHOT.jar
```

**3. 查看运行时堆信息：**

```bash
# 查看堆内存使用
jhsdb jmap --heap --pid <pid>
# 或
jmap -heap <pid>
```

---

## Step 4.2 OOM 模拟与排查

### 考点覆盖

**四种 OOM 场景：**

| 类型 | 模拟方式 | 现象 |
|------|---------|------|
| 堆溢出 | 不断向 List 添加对象 | `java.lang.OutOfMemoryError: Java heap space` |
| 栈溢出 | 无终止递归 | `java.lang.StackOverflowError` |
| 元空间溢出 | CGLib 不停生成新类 | `java.lang.OutOfMemoryError: Metaspace` |
| 直接内存溢出 | 分配大量 DirectBuffer | `java.lang.OutOfMemoryError: Direct buffer memory` |

### 项目现状中的风险

- `SeckillCouponServiceImpl:99-101` — 异常时返回 `new ArrayList<>()`，调用方无限循环可能 OOM
- 秒杀 ZSET 不断增大用户数据，没有上限控制
- 订单查询没有 limit 限制，百万级数据一次加载到内存

### 模拟与排查

**1. 模拟堆溢出：**

```java
// 在测试接口中
@GetMapping("/test/oom")
public String testOom() {
    List<Object> list = new ArrayList<>();
    while (true) {
        list.add(new byte[1024 * 1024]); // 每次 1MB
    }
}
```

**2. 堆转储分析：**

```bash
# 配置自动转储
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heap.hprof

# 或手动抓取
jmap -dump:format=b,file=heap.hprof <pid>
```

**3. MAT 分析步骤：**
- 打开 heap.hprof
- **Leak Suspects** → 自动报告可疑泄漏点
- **Dominator Tree** → 按保留堆大小排序，找最大对象
- **GC Root Paths** → 跟踪引用链，找到泄漏源头
- **OQL** → 编写类 SQL 查询特定对象

### 面试话术

> **"为了准备 JVM 面试，在项目里手动制造了各种 OOM。堆溢出：往 List 不断加 1MB 数组，配置了 -XX:+HeapDumpOnOutOfMemoryError 自动抓堆转储。用 MAT 分析 Dominator Tree 快速定位了大对象，然后看 GC Root 路径找到引用源头。还模拟了栈溢出（递归）和元空间溢出（CGLib 动态代理）。每种场景都总结了：问题现象 → 排查工具 → 定位方法 → 解决方案。"**

---

## Step 4.3 GC 日志分析与调优

### 考点覆盖

**GC 三大核心指标：**
- **吞吐量**：用户代码时间 / (用户代码时间 + GC时间)
- **停顿时间（Pause Time）**：GC 暂停应用的时间
- **GC 频率**：单位时间内 GC 次数

**G1 收集器详解：**
- **Region**：堆划分为 2048 个 Region，每个 1-32MB
- **RememberSet（RSet）**：记录其他 Region 对当前 Region 的引用
- **SATB（Snapshot At The Beginning）**：并发标记开始时的堆快照
- **Mixed GC**：混合收集新生代 + 部分老年代 Region
- **IHOP（Initiating Heap Occupancy Percent）**：触发并发标记的堆占用阈值

**CMS 收集器（了解）：**
- 初始标记 → 并发标记 → 重新标记 → 并发清理
- 缺点：Concurrent Mode Failure（碎片化 → 退化为 Serial Old）、CPU 敏感

### 实操步骤

**1. 配置 GC 日志：**

```bash
# JDK 8 格式（项目用的是 JDK 8）
-Xms1g -Xmx1g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+PrintGCTimeStamps
-XX:+PrintAdaptiveSizePolicy
-Xloggc:gc.log
```

**2. 压测：**
```bash
# JMeter 命令模式压测秒杀接口
jmeter -n -t seckill.jmx -l result.jtl
```

**3. 分析 GC 日志（关注以下指标）：**

```bash
# GC log 样本
2024-06-24T10:00:00.123+0800: 1.234: [GC pause (G1 Evacuation Pause) (young), 0.0123456 secs]
   [Eden: 512.0M(512.0M)->0.0B(512.0M) Survivors: 10.0M->10.0M Heap: 600.0M(1024.0M)->100.0M(1024.0M)]
   [Times: user=0.05 sys=0.01, real=0.01 secs]
```

- YoungGC 频率：太频繁 → 增大新生代
- MixedGC 耗时：> 200ms → 调整 IHOP
- FullGC：尽量为零

**4. 调优参数参考：**

```bash
# 基础 G1 参数
-Xms2g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200             # 目标停顿时间
-XX:InitiatingHeapOccupancyPercent=45 # 触发并发标记阈值（默认45）
-XX:G1HeapRegionSize=4m               # Region 大小
-XX:MetaspaceSize=256m                # 元空间初始大小（避免扩容 FullGC）
-XX:MaxMetaspaceSize=256m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=logs/heap.hprof
-Xloggc:logs/gc-%t.log
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
```

### 面试话术

> **"用 JMeter 对秒杀接口压测 5 分钟，分析 GC 日志。初始配置下 YoungGC 约 2 秒一次，MixedGC 约 30 秒一次，无 FullGC。优化：增大了 MetaspaceSize 到 256m（避免扩容触发 FullGC），调整了 IHOP 到 45（默认 45 适合大多场景）。压测结果：吞吐量 98%，停顿时间平均 80ms，GC 表现健康。面试官常问的 G1 原理我也重点准备了：Region、RSet、SATB，尤其 SATB 如何解决并发标记阶段对象引用变化的问题。"**

### 常见问题排查

| 现象 | 可能原因 | 解决 |
|------|---------|------|
| YoungGC 频繁（<1s一次） | 新生代太小 | 增大堆或调整新生代比例 |
| FullGC 频繁 | 老年代增长过快 / 内存泄漏 | MAT 分析堆转储 |
| MixedGC 耗时 > 200ms | IHOP 设置过低 | 调整 IHOP 到 50-60 |
| Concurrent Mode Failure | CMS 碎片化 | 用 G1 替代 CMS |

---

## Step 4.4 线程池深度

### 考点覆盖

**ThreadPoolExecutor 七大参数：**

| 参数 | 含义 | 说明 |
|------|------|------|
| corePoolSize | 核心线程数 | 一直存活 |
| maxPoolSize | 最大线程数 | 大于 core 时，队列满后创建新线程 |
| keepAliveTime | 空闲线程存活时间 | 超过 core 的线程空闲多久回收 |
| workQueue | 任务队列 | ArrayBlockingQueue / LinkedBlockingQueue / SynchronousQueue |
| threadFactory | 线程工厂 | 设置线程名、daemon |
| RejectedExecutionHandler | 拒绝策略 | Abort / CallerRuns / Discard / DiscardOldest |

**参数计算公式：**
- **CPU 密集型**：`N + 1`（N 为 CPU 核心数）
- **IO 密集型**：`2 * N`（等待 IO 时可让出 CPU）

**四种拒绝策略：**
- `AbortPolicy`（默认）— 抛 RejectedExecutionException
- `CallerRunsPolicy` — 调用者线程执行（反压）
- `DiscardPolicy` — 直接丢弃
- `DiscardOldestPolicy` — 丢弃最旧任务

### 项目现状

- `application.yml:3-8` — Tomcat 线程池：max=500, min-spare=50
- 没有自定义业务线程池，`@Async` 使用默认 `SimpleAsyncTaskExecutor`（**每次都 new Thread()！**）

### 模拟问题

**复现步骤：**
1. 写一个 @Async 方法，内部模拟耗时操作（sleep 1s）
2. 循环调用 1000 次
3. 观察线程数：`jstack <pid> | grep 'async' | wc -l`
4. 默认 SimpleAsyncTaskExecutor 会创建 1000 个线程！

### 解决方案

**1. 配置自定义业务线程池：**

```java
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("seckillExecutor")
    public ThreadPoolTaskExecutor seckillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("seckill-exec-");
        // 拒绝策略：调用者执行（反压）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成再 shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("report-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
```

**2. 使用自定义线程池：**

```java
@Async("seckillExecutor")
public CompletableFuture<Boolean> asyncProcessOrder(Long orderId) {
    // 异步处理
    return CompletableFuture.completedFuture(true);
}
```

**3. 线程池监控：**

```java
public class ThreadPoolMonitor {
    public void monitor(ThreadPoolExecutor executor) {
        log.info("活跃线程数: {}", executor.getActiveCount());
        log.info("核心线程数: {}", executor.getCorePoolSize());
        log.info("最大线程数: {}", executor.getMaximumPoolSize());
        log.info("队列积压: {}", executor.getQueue().size());
        log.info("已完成任务: {}", executor.getCompletedTaskCount());
    }
}
```

### 面试话术

> **"项目中有异步操作用的 @Async，Spring 默认的 SimpleAsyncTaskExecutor 每次创建新线程，高并发下会创建几千个线程导致系统资源耗尽。配置了自定义线程池：core=10, max=50, queue=200。参数根据 IO 密集型计算（2N+1=17，取整 10-50）。还用 CallerRunsPolicy 做反压——线程池满了让 Tomcat 线程自己执行，Tomcat 线程占满后自然拒绝新请求，形成正向反馈。不同业务用了不同线程池，秒杀和报表完全隔离。"**

---

## Step 4.5 并发编程实战

### 考点覆盖

**八股文知识点：**
- **synchronized vs Lock vs Redisson**：
  - synchronized：JVM 层面，自动释放，不可中断
  - Lock（ReentrantLock）：API 层面，可中断，可超时，公平/非公平
  - Redisson：分布式锁，跨 JVM
- **CAS（Compare And Swap）原理**：
  - 底层：`Unsafe.compareAndSwapInt()` → CPU 的 `cmpxchg` 指令
  - ABA 问题：A→B→A，CAS 检查值没变但中间已被修改
  - 解决：AtomicStampedReference 加版本号
- **AQS（AbstractQueuedSynchronizer）**：
  - CLH 锁队列变体 + state 状态
  - ReentrantLock / Semaphore / CountDownLatch 都基于 AQS
- **volatile**：可见性（内存屏障）+ 禁止指令重排序，不保证原子性

### 项目中的并发问题

**1. 订单号生成：**

```java
// OrderServiceImpl:94 — 有并发问题！
orders.setNumber("ORD" + System.currentTimeMillis());
```

- 高并发下同一毫秒会生成重复订单号
- 项目已经有 UniqueID（雪花算法），但因 OrderServiceImpl 没用

**2. `@Transactional` 并发问题**（已在 MySQL Step 3.4 中讨论）

### 解决方案

**订单号改为雪花算法：**

```java
@Autowired
private UniqueID uniqueID;

public Orders create(OrderCreateDTO orderCreateDTO) {
    // ...
    orders.setNumber("ORD" + String.valueOf(uniqueID.nextId("order")));
    // ...
}
```

### 面试话术

> **"订单号用 System.currentTimeMillis() 生成，1000 并发时验证发现重复订单号。原因是毫秒精度不够并发。项目已有 UniqueID 工具类（雪花算法），改用它生成：41 位时间戳 + 10 位机器 ID + 12 位序列号，64 位 long 保证全局唯一。雪花算法的时钟回拨问题也分析了方案：记录上次时间戳，回拨时等待或使用 Zookeeper 序列号分配。"**

---

## Step 4.6 生产问题定位工具

### 工具实战清单

**1. jstack — 线程状态分析：**

```bash
# 查看所有线程
jstack <pid>

# 定位死锁
jstack <pid> | grep -A 30 "Found one Java-level deadlock"

# 高 CPU 线程排查
top -H -p <pid>                    # 找 CPU 高的线程 ID
printf "%x\n" <tid>                # 转十六进制
jstack <pid> | grep <hex_tid> -A 30  # 看对应线程栈
```

**2. jstat — GC 实时监控：**

```bash
# 每 1 秒打印一次 GC 情况
jstat -gcutil <pid> 1000

# 输出说明：S0/S1 幸存区、E 伊甸区、O 老年代、M 元空间
# YGCT/YGGC YoungGC 总时间/次数、FGCT/FGGC FullGC 总时间/次数
```

**3. jmap — 堆内存分析：**

```bash
# 查看堆概要
jmap -heap <pid>

# 抓堆转储（不触发 FullGC）
jmap -dump:live,format=b,file=heap.hprof <pid>
```

**4. Arthas（强烈推荐，面试加分项）：**

```bash
# 启动
java -jar arthas-boot.jar

# 常用命令
dashboard        # 全局仪表盘：线程/内存/GC
thread           # 查看线程
thread -b        # 找 BLOCKED 的线程（死锁）
trace com.fashion.service.impl.OrderServiceImpl listUserOrders  # 追踪方法调用链
watch com.fashion.service.impl.SeckillCouponServiceImpl seckillCoupon '{params,returnObj}' -x 2
monitor com.fashion.service.impl.OrderServiceImpl listUserOrders  # 监控方法调用统计
ognl '@java.lang.System@getProperty("java.version")'  # 在线执行表达式
```
