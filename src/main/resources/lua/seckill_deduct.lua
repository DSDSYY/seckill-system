-- ============================================================================
-- seckill_deduct.lua  Redis 原子扣减秒杀库存
--
-- 调用约定：
--   KEYS[1]  seckill:stock:{activityId}   库存 Key（由"预热接口"写入）
--   ARGV[1]  本次扣减数量（秒杀限购 1 件，默认 1）
--
-- 返回值：
--   1  扣减成功
--   0  扣减失败（Key 不存在=未预热/活动不存在，或库存不足）
--
-- 为什么扣库存必须用 Lua（详见 README/讲解）：
--   1) "查库存 -> 判断库存是否足够 -> 扣减" 是三步操作；
--   2) Redis 单条命令（如 DECRBY）原子但"无条件"，库存为 0 时会继续减成负数；
--   3) Lua 脚本在 Redis 内以单线程事件循环"整体、原子"执行，
--      执行期间不会有任何其他客户端命令插入，等价于把三步合成一步；
--   4) 相比"DECR 后判断负数再 INCR 回补"，Lua 不会出现瞬时负库存，
--      且全程只有一次网络往返。
-- ============================================================================

local stockKey = KEYS[1]
local delta    = tonumber(ARGV[1])

-- 防御：未传数量或数量非法时按 1 处理（秒杀限购 1 件）
if delta == nil or delta < 1 then
    delta = 1
end

-- Key 不存在：活动未预热或已下架/删除，直接返回失败
local stock = redis.call('GET', stockKey)
if not stock then
    return 0
end

stock = tonumber(stock)

-- 库存不足（含已扣到 0）：返回失败，不做任何扣减
if stock < delta then
    return 0
end

-- 原子扣减：脚本整体执行，天然串行，不会超卖
redis.call('DECRBY', stockKey, delta)
return 1