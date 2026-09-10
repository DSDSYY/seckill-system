# 秒杀系统 · 微信小程序 Demo

一个可直接导入「微信开发者工具」运行的小程序前端，对接同目录后端 `seckill-backend`（Spring Boot 3）。

## 目录结构

```
seckill-miniprogram/
├── app.js / app.json / app.wxss     # 全局配置
├── project.config.json              # 开发者工具工程配置
├── utils/
│   ├── config.js                    # ★ BASE_URL 等全局配置（改这里）
│   └── request.js                   # wx.request 封装：自动带 token / 统一解析 Result
├── services/
│   └── api.js                       # 后端接口定义（增删接口改这里）
└── pages/index/                     # 抢购 Demo 页（登录 -> 查库存 -> 抢购）
```

## 快速开始（本地联调）

### 前置条件
1. 启动后端依赖：MySQL、Redis、RabbitMQ；
2. 执行 `seckill-backend/sql/seckill_schema.sql` 建库建表，插入一条测试活动：
   ```sql
   INSERT INTO product (product_name, original_price) VALUES ('测试手机', 6999.00);
   INSERT INTO seckill_activity
     (product_id, seckill_price, seckill_stock, total_stock, start_time, end_time, status)
   VALUES (1, 4999.00, 5, 5, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 1);
   ```
3. 启动后端：`cd seckill-backend && mvn spring-boot:run`（JDK 17+）；
4. 后端默认 **mock 登录**（`seckill.wechat.mock-enabled=true`），无需真实微信 appid。

### 运行
1. 打开「微信开发者工具」→ 导入项目 → 选择本目录 `seckill-miniprogram`；
   - AppID 可先用「测试号」，或填你自己的小程序 AppID；
2. 本地 http 调试需勾选：右上角「详情」→「本地设置」→「不校验合法域名...」
   （`project.config.json` 已设 `urlCheck: false`）；
3. 若后端不在本机：修改 `utils/config.js` 的 `BASE_URL` 为后端地址
   （真机预览请填电脑局域网 IP，如 `http://192.168.1.100:8081`）；
4. 编译运行：页面自动登录 → 输入活动ID → 「查询库存」→「立即抢购」。

### 体验验证点
- 连续快速点击抢购 → 返回 `429`（后端令牌桶限流 5 次/秒）；
- 同用户抢购成功后再次抢购 → 返回 `1006`（一人一单 SETNX）；
- 抢到后 15 分钟未支付 → 订单自动取消、库存回补（看后端日志 / DB）。

## 已对接接口

| 小程序方法 | 后端接口 | 说明 |
|---|---|---|
| `login(code)` | `POST /api/auth/login` | 登录换 token（mock 模式） |
| `doSeckill(activityId)` | `POST /api/seckill/do` | 抢购，Header 带 `Bearer token` |
| `getStock(activityId)` | `GET /api/seckill/stock/{id}` | 查 Redis 剩余库存 |

## 错误码对照（页面已自动映射中文提示）

| code | 含义 |
|---|---|
| 401 | 未登录/登录过期（自动提示重新登录） |
| 429 | 操作太频繁（>5 次/秒） |
| 1001/1002/1003 | 活动不存在 / 未开始 / 已结束 |
| 1004 | 已售罄 |
| 1005 | 人数过多，请重试 |
| 1006 | 已参与过本场秒杀 |

## 生产上线 Checklist（重要）

1. **域名与 HTTPS**：小程序强制要求 `https` + 已备案域名；在
   「小程序后台 → 开发管理 → 开发设置 → 服务器域名」把 HTTPS 域名加入 request 合法域名；
2. **真实登录**：把后端 `application.yml` 的 `seckill.wechat.mock-enabled` 改为 `false`，
   实现微信 `code2session`（appid/secret 换 openid）并建用户表；
3. **密钥安全**：后端 `seckill.jwt.secret` 改为随机长密钥，用环境变量注入，不要提交到仓库；
4. **部署**：MySQL/Redis/RabbitMQ 上云，后端多实例时配置雪花 `worker-id` 互不相同；
5. **库存预热**：活动开始前调用 `POST /api/seckill/stock/init/{activityId}`（建议定时任务自动预热）。

## 复制到你已有的小程序工程

只需拷贝 4 个文件并调用：
1. `utils/config.js` + `utils/request.js`（请求封装）
2. `services/api.js`（接口定义，可按需增删）
3. 页面里调用：`api.login(code)` → 存 token → `api.doSeckill(activityId)`

## 后续可扩展（需要后端配合新增接口）

- 首页活动列表：`GET /api/activity/list`
- 订单查询/轮询：`GET /api/order/{orderNo}`
- 支付（模拟/微信支付）：`POST /api/order/pay`
- 秒杀结果页、倒计时、收货地址等业务页面

> 后端代码见 `seckill-backend/`；联调接口集合见 `seckill-backend/docs/postman/seckill.postman_collection.json`。