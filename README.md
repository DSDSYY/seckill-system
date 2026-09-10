# 高并发商品秒杀系统（后端 + 微信小程序）

> 校招简历项目 · Spring Boot 3.3.5 / MySQL 8 / Redis / RabbitMQ / MyBatis-Plus / JWT
> 覆盖"登录 → 活动 → 抢购 → 异步下单 → 超时取消"完整链路，已与微信小程序联调跑通。

## 一、项目简介

秒杀是典型的"瞬时高流量 + 强数据一致性"场景，本项目的核心目标是解决四类问题：

| 问题 | 解法 |
|---|---|
| 超卖（库存扣成负数） | 库存预热 Redis + **Lua 脚本原子预扣**（查-判-扣原子完成） |
| 一人多单 | Redis `SETNX` 抢占资格 + DB `UNIQUE(user_id, activity_id)` 唯一索引兜底 |
| 同步接口慢 / 打垮数据库 | 抢购成功投递 **RabbitMQ 异步落库**，接口只做"快速受理" |
| 占库存不支付 | **TTL + 死信队列**实现 15 分钟延时取消，CAS 只取消"待支付"订单并回滚库存 |

## 二、技术栈

- Spring Boot 3.3.5 / Java 17
- MySQL 8（MyBatis-Plus、逻辑删除、唯一索引）
- Redis（Lua 原子扣减、令牌桶限流、SETNX 一人一单、库存预热）
- RabbitMQ（异步下单、TTL+死信延时取消、处理失败进死信队列）
- JWT（登录鉴权，支持 mock 微信登录便于本地联调）
- 微信小程序（`miniprogram/`，对接后端接口）

## 三、目录结构

```
seckill-backend（仓库根）
├── src/main/java/com/example/seckill/
│   ├── controller/         # 登录、抢购、库存接口
│   ├── service/            # 业务逻辑（抢购核心链路）
│   ├── mq/                 # RabbitMQ 配置、生产者、消费者、延时取消
│   ├── mapper/ entity/     # MyBatis-Plus 数据层
│   └── config/             # JWT / 限流拦截器、Web 配置
├── src/main/resources/
│   ├── application.yml
│   └── lua/                # 秒杀扣减、令牌桶限流脚本
├── sql/                    # 建表脚本 + 演示数据
├── docs/                   # Postman 集合、小程序接口示例
└── miniprogram/            # 微信小程序端
```

## 四、快速开始

前置：JDK 17、Maven 3.9+、MySQL 8、Redis、RabbitMQ。

```powershell
# 1) 初始化数据库（需 MySQL root 权限；创建 seckill 库 + seckill/seckill123 演示账号 + 演示活动）
mysql -uroot -p -e "SOURCE ./sql/seckill_schema.sql"
mysql -uroot -p -e "SOURCE ./sql/init_demo.sql"

# 2) 启动中间件：MySQL、Redis(6379)、RabbitMQ(5672, guest/guest)

# 3) 启动后端（默认 8081）
mvn spring-boot:run
# 或双击 start-backend.cmd

# 4) 验证
curl -X POST http://127.0.0.1:8081/api/auth/login -H "Content-Type: application/json" -d "{\"code\":\"test\"}"
```

> 生产环境请通过环境变量注入 `DB_PASSWORD`、`JWT_SECRET`，不要使用演示默认值。

## 五、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录，返回 userId + JWT token（mock 微信 code） |
| POST | /api/seckill/stock/init/{activityId} | 库存预热（管理端，需登录态） |
| GET | /api/seckill/stock/{activityId} | 查询 Redis 剩余库存 |
| POST | /api/seckill/do | 抢购下单（Header: `Authorization: Bearer {token}`） |

业务错误码：1001 活动不存在 / 1002 未开始 / 1003 已结束 / 1004 售罄 / 1005 系统繁忙 / 1006 重复下单 / 429 限流 / 401 未登录。

## 六、核心设计要点

1. **为什么用 Lua 扣库存**：`查库存 → 判断 → 扣减` 三步若拆开会有竞态；Lua 在 Redis 单线程内原子执行，一次网络往返，且不会出现瞬时负库存。
2. **一人一单**：`SETNX user:activity:{userId}:{activityId}` 前置拦截，TTL=活动剩余时长；DB 唯一索引作为最后防线。
3. **MQ 投递失败必须回补**：Redis 已扣库存但消息未进队列时，若不回补会导致"库存蒸发、少卖"；本项目在 `send` 返回 false 时回补库存并释放用户资格。
4. **延时取消**：下单同时发送到 TTL 队列，15 分钟后死信转入取消队列；DB 用 CAS 只取消"待支付"订单，避免与"已支付"并发时误取消。
5. **限流**：单用户令牌桶（Lua），允许短时突发、长期限速；拦截器层前置，减少无效请求打到业务逻辑。

## 七、本地实测数据（300 并发抢 100 库存）

| 指标 | 结果 |
|---|---|
| 接口吞吐 | ≈ 2,260 req/s（300 并发，0.13s 完成，本地开发机） |
| 成功/拒绝 | 恰好 100 单成功、200 单售罄拒绝 |
| 一致性 | DB 订单 100、DB 库存 0、Redis 库存 0 → **0 超卖、0 漏卖** |

> 说明：以上为本地开发环境并发冒烟测试结果，非生产压测；如需正式指标建议用 JMeter 在更高配置机器上复测。

## 八、已知边界（可继续演进）

- 登录使用 mock 微信 code，生产需接入 `code2session` + 用户表；
- MQ 投递为"尽力投递"，生产建议开启 publisher-confirm + 本地消息表对账；
- 活动元数据仍在抢购热路径查库，可预热到 Redis/本地缓存进一步提性能；
- 未做分库分表，数据量大后可按活动维度归档。