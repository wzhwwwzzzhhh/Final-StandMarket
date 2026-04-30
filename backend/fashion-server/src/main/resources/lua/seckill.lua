-- seckill.lua
-- KEYS[1]: 库存key (seckill:coupon:stock:{couponId})
-- KEYS[2]: 开始时间key (seckill:coupon:startTime:{couponId})
-- KEYS[3]: 结束时间key (seckill:coupon:endTime:{couponId})
-- KEYS[4]: 已购买用户集合 (seckill:coupon:users:{couponId})
-- ARGV[1]: 扣减数量 (通常为1)
-- ARGV[2]: 当前时间戳（秒）
-- ARGV[3]: 用户ID

local stock_key = KEYS[1]
local start_time_key = KEYS[2]
local end_time_key = KEYS[3]
local users_key = KEYS[4]
local decrease_num = tonumber(ARGV[1]) or 1
local current_time = tonumber(ARGV[2])
local user_id = ARGV[3]

-- 获取开始时间
local start_time = tonumber(redis.call('GET', start_time_key))

-- 判断是否未开始
if start_time and current_time < start_time then
    return -3
end

-- 获取结束时间
local end_time = tonumber(redis.call('GET', end_time_key))

-- 判断是否已过期
if end_time and current_time > end_time then
    return -2
end

-- 判断用户是否已购买
if redis.call('ZSCORE', users_key, user_id) then
    return -4
end

-- 获取当前库存
local stock = tonumber(redis.call('GET', stock_key))

-- 库存不足
if not stock or stock <= 0 then
    return -1
end

-- 扣减库存
local new_stock = redis.call('DECRBY', stock_key, decrease_num)

-- 如果扣减后库存小于0，恢复库存并返回失败
if new_stock < 0 then
    redis.call('INCRBY', stock_key, decrease_num)
    return -1
end

-- 记录用户购买信息（score为购买时间戳）
redis.call('ZADD', users_key, current_time, user_id)

-- 返回剩余库存
return new_stock
