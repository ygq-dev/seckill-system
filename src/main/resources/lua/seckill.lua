-- KEYS[1]: 库存key (stock:商品ID)
-- KEYS[2]: 用户已购集合key (seckill:users:商品ID)
-- ARGV[1]: 用户ID
-- ARGV[2]: 扣减数量（默认1）

local stock_key = KEYS[1]
local user_set_key = KEYS[2]
local user_id = ARGV[1]
local quantity = tonumber(ARGV[2]) or 1

-- 1. 优先检查用户是否已抢过（利用 Set 的 O(1) 复杂度尽早拦截）
local is_member = redis.call('SISMEMBER', user_set_key, user_id)
if is_member == 1 then
    return {0, "重复秒杀"}
end

-- 2. 直接扣减库存（利用 Redis 单线程特性保证此步的原子性）
local new_stock = redis.call('DECRBY', stock_key, quantity)

-- 3. 判断扣减后的库存是否不足
if new_stock < 0 then
    -- 库存不足，发生超卖，需要回滚（把刚扣减的库存加回去）
    redis.call('INCRBY', stock_key, quantity)
    return {0, "库存不足"}
end

-- 4. 记录用户购买成功
redis.call('SADD', user_set_key, user_id)

return {1, "成功", new_stock}
