# seckill-system · 高并发商品秒杀系统

> 基于 Spring Boot 3 的秒杀系统。围绕「**流量削峰 → 原子扣减 → 可靠落库**」主线，实现四层漏斗式削峰、Redis Lua 消费端原子扣减、Outbox 最终一致性，万线程压测下 0 超卖。

**核心指标：500+ QPS（单机混部）｜ 0 超卖 ｜ 下单接口异步化，RT 与落库完全解耦**

---

## 架构总览

![seckill-architecture](C:\Users\20781\Desktop\seckill\docs\images\seckill-architecture.png)

**请求链路**：请求依次经过四层 Filter（内存售罄标记 → 全局自适应限流 → 用户级/商品级限流 → JWT 认证），无效流量在最外层被拦截；通过校验的请求完成活动时间与库存标记检查后，写入本地缓冲队列（`BlockingQueue`）立即返回「排队中」；异步线程池批量取出（`drainTo`）聚合投递 RabbitMQ；消费端逐条执行 Lua 脚本完成防重校验与原子扣减，再按商品聚合、条件更新库存并三表批量插入落库；事务提交后写缓存，前端轮询接口兜底查单。

## 核心设计

### 1. 四层漏斗式流量削峰

- **为什么**：秒杀瞬时流量可达日常百倍，且大部分请求注定失败。越早拦截，越省下层资源——过滤层次每下移一层，无效请求的处理成本就上升一个数量级。
- **怎么做**：四个 Filter 各司其职——① `PreCheckFilter`：内存售罄标记（`AtomicBoolean` CAS），售罄后请求不触碰 Redis；② `GlobalRateLimitFilter`：动态自适应限流，根据 GC/CPU 反馈调节阈值；③ `RateLimitFilter`：用户级 + 商品级限流（Caffeine 管理限流器实例，防恶意刷单）；④ `JwtAuthenticationFilter`：认证后才进入业务。
- **效果**：压测中绝大部分无效流量在最外两层被消化，落到 Controller 的请求量与真实库存同一量级。

### 2. Redis Lua 原子扣减——在消费端执行

- **为什么**：经典「check-then-act」（先查库存再扣减）在并发下必然超卖；而把扣减放在**请求端**同步执行，Redis 会成为主链路瓶颈，削峰就白做了。放在**消费端**执行，Redis 操作从主链路彻底摘除，削峰收益完整保留。
- **怎么做**：扣减逻辑封装为单个 Lua 脚本，`SISMEMBER` 防重 + `DECRBY` 扣库存在一次脚本执行内原子完成；库存不足时 `INCRBY` 回滚，杜绝半完成状态。
- **效果**：万线程压测，**0 超卖**，防重表 0 重复订单。

### 3. 本地缓冲队列 + 批量投递

- **为什么**：瞬时洪峰如果逐条 RPC 发往 MQ，发送端自身就会被打垮，网络往返也是无谓开销。
- **怎么做**：请求先入本地 `BlockingQueue` 缓冲，`@Async` 线程池（16/32/200/CallerRuns）批量 `drainTo` 取出、聚合后一次投递多条；线程池打满时 `CallerRuns` 反压，请求在 Filter 层被自然限速。
- **效果**：MQ 投递次数从「每请求一次」降为「每批次一次」，发送端吞吐显著提升。

### 4. Outbox 本地消息表——投递可靠性

- **为什么**：MQ 发送可能失败（网络抖动、Broker 不可用），逐条重试会阻塞主流程，丢弃则直接丢单。
- **怎么做**：发送失败的消息落 Outbox 表，`OutboxRetryService` 定时扫描，指数退避重试（10s × 2ⁿ），5 次后置终态转人工介入；Broker 恢复后自动重发。
- **效果**：MQ 不可用期间订单不丢，恢复后自动补投，全链路最终一致。

### 5. 消费端幂等与落库一致性

- **为什么**：MQ 是至少一次投递语义，重复消费不可避免；落库阶段是超卖防线的最后一环。
- **怎么做**：幂等靠 Lua 内 `SISMEMBER` + 数据库防重表唯一约束双保险；落库按**商品聚合**、`UPDATE ... WHERE stock >= n` 条件更新兜底防超卖，订单主表 + 明细表 + 防重表三表批量插入（雪花 ID 主键）；另有定时对账任务对 MySQL 与 Redis 库存做**双向差异修正**。
- **效果**：重复消息、极端并发、Redis/DB 库存漂移三种场景下库存均保持一致。

### 6. 超时关单与补偿回滚

- **为什么**：下单成功不支付会长期占库存；消费失败需要可回滚的补偿路径。
- **怎么做**：订单超时走 TTL + DLX（15 分钟）四级回滚链，释放库存与占位；消费端执行失败触发补偿回滚链——回补库存、移除防重标记、nack 重试。

## 技术栈

| 分类 | 组件 |
|---|---|
| 框架 | Spring Boot 3、Spring Security + JWT |
| 数据 | MySQL 8、MyBatis-Plus、Redis（Lettuce + Lua） |
| 中间件 | RabbitMQ（批量消费 prefetch 50、TTL+DLX）、Caffeine、Guava RateLimiter |
| 可观测 | Micrometer + Prometheus（业务指标 + SLO 监控） |
| 压测 | JMeter |

## 快速启动

**环境依赖**：JDK 17+、MySQL 8、Redis 6+、RabbitMQ 3.x、Maven 3.8+

```bash
# 1. 建库建表（订单主表/明细表/防重表等）
mysql -uroot -p < sql/schema.sql

# 2. 配置连接信息（密码等敏感项通过环境变量注入）
#    编辑 src/main/resources/application.yml

# 3. 启动（启动时自动执行 SeckillInitializer 完成缓存预热）
mvn spring-boot:run
```

配置项均使用 `${ENV_VAR:默认值}` 占位符，启动前请自行设置 MySQL / Redis / RabbitMQ 连接密码。

## 压测

| 指标 | 结果 |
|---|---|
| 系统吞吐 | 500+ QPS |
| 超卖数量 | **0** |
| 防重表重复订单 | 0 |
| 压测方式 | JMeter 万级线程并发 + 库存事后核对 |

> 环境定语：单机混部（应用与中间件同机部署）。压测脚本见 `docs/`。

## 已知边界与后续计划

本项目为单机部署的工程实践，以下边界是**已知的主动取舍**：

- **内存售罄标记**（AtomicBoolean）在多实例部署下实例间不一致，需引入 Redis Pub/Sub 广播或版本号方案；
- **全局限流为单机维度**，集群限流需 Redis + Lua 分布式令牌桶；
- **库存对账为定时修正**，秒级窗口内允许短暂不一致（最终一致的典型取舍）；
- **Outbox 终态消息**依赖人工介入，可扩展为告警联动。

后续计划：库存分桶、Redis Cluster、多实例部署验证。

## 目录结构

```
src/main/java/com/ygq/seckill/
├── controller/          # 秒杀接口：校验、入队、查单轮询
├── filter/              # 四层漏斗 Filter 链
├── service/             # 按商品聚合落库、条件更新
├── mq/                  # MQAsyncSender / OrderReceiver / OutboxRetryService
├── config/              # RabbitMQ / Redis / 线程池 / 安全配置
├── entity/ mapper/      # MyBatis-Plus 实体与数据访问层
├── vo/ result/ exception/  # 出入参、统一响应、全局异常
└── util/                # 雪花 ID、JWT 工具
src/main/resources/
├── application.yml      # 敏感配置均为环境变量占位符
├── seckill.lua          # 消费端原子扣减脚本
└── sql/schema.sql       # 建表脚本
docs/                    # JMeter 压测脚本
```

## License

[MIT](LICENSE)