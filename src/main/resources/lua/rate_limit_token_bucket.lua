-- ============================================================================
-- rate_limit_token_bucket.lua  Redis 令牌桶限流（按用户维度）
--
-- 调用约定：
--   KEYS[1]  seckill:rate:user:{userId}    限流 Key
--   ARGV[1]  桶容量 capacity（突发上限，如 5）
--   ARGV[2]  每秒补充令牌数 refillRate（稳态速率，如 5）
--   ARGV[3]  当前毫秒时间戳 now（由应用传入，避免各节点时钟不一致）
--
-- value 存储格式："剩余令牌数:上次补充时间戳"，例 "3.500:1725600000123"
--
-- 返回值：
--   1  放行（成功取到 1 个令牌）
--   0  限流（桶空，拒绝本次请求）
--
-- 为什么用令牌桶而不是固定窗口计数：
--   1) 允许一定突发（桶容量 5 = 瞬间可连续放行 5 个请求），体验更好；
--   2) 长期速率被 refillRate 平滑限制（5 个/秒），比固定窗口在边界上更公平；
--   3) 整个算法在 Lua 内原子执行（Redis 单线程），并发安全、无竞态。
-- ============================================================================

local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

-- 防御：非法参数直接放行会失控，这里按"拒绝"处理更安全
if not capacity or not rate or not now or capacity <= 0 or rate <= 0 then
    return 0
end

-- 首次访问：桶是满的（允许突发）
local tokens = capacity
local last   = now

local val = redis.call('GET', key)
if val then
    -- 解析 "剩余令牌:上次时间"
    local savedTokens, savedLast = string.match(val, "^(%-?%d+%.?%d*):(%d+)$")
    if savedTokens and savedLast then
        tokens = tonumber(savedTokens)
        last   = tonumber(savedLast)
        -- 按流逝时间补充令牌：tokens = min(capacity, tokens + (now-last)/1000 * rate)
        tokens = tokens + (now - last) / 1000 * rate
        if tokens > capacity then
            tokens = capacity
        end
    end
end

if tokens >= 1 then
    -- 取走 1 个令牌，放行
    tokens = tokens - 1
    redis.call('SET', key, string.format('%.3f', tokens) .. ':' .. now, 'PX', 60000)
    return 1
end

-- 桶空：记录（不消耗），拒绝；PX 60s 让长期不活跃用户的 Key 自动清理
redis.call('SET', key, string.format('%.3f', tokens) .. ':' .. last, 'PX', 60000)
return 0