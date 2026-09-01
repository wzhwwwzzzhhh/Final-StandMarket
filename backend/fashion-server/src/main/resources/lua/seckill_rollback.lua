-- B5 秒杀取消 Redis 回补。
-- KEYS[1]: 库存 key
-- KEYS[2]: 已购买用户 ZSET key
-- ARGV[1]: 回补数量（B5 仅允许 1）
-- ARGV[2]: 用户 ID（非空十进制整数）

local RETURN_SUCCESS = 1
local RETURN_NOT_APPLIED = 0
local RETURN_STOCK_MISSING = -1
local RETURN_WRONG_TYPE = -2
local RETURN_INVALID_VALUE = -3

local stock_key = KEYS[1]
local users_key = KEYS[2]
local quantity_raw = ARGV[1]
local user_id = ARGV[2]

local function type_name(key)
    local value = redis.call('TYPE', key)
    if type(value) == 'table' then
        return value['ok']
    end
    return value
end

-- Redis 不会回滚脚本运行时错误，因此所有可预见错误必须在首次写入前验证。
if quantity_raw ~= '1' or not user_id or not string.match(user_id, '^%d+$') then
    return RETURN_INVALID_VALUE
end

local stock_type = type_name(stock_key)
if stock_type == 'none' then
    return RETURN_STOCK_MISSING
end
if stock_type ~= 'string' then
    return RETURN_WRONG_TYPE
end

local users_type = type_name(users_key)
if users_type == 'none' then
    return RETURN_NOT_APPLIED
end
if users_type ~= 'zset' then
    return RETURN_WRONG_TYPE
end

local stock_raw = redis.call('GET', stock_key)
if not stock_raw or not string.match(stock_raw, '^%d+$')
        or (stock_raw ~= '0' and string.sub(stock_raw, 1, 1) == '0') then
    return RETURN_INVALID_VALUE
end

local stock = tonumber(stock_raw)
if not stock or stock < 0 or stock > 2147483646 then
    return RETURN_INVALID_VALUE
end

if not redis.call('ZSCORE', users_key, user_id) then
    return RETURN_NOT_APPLIED
end

-- 上述验证后 INCRBY 不会发生类型、整数或上溢错误；ZREM 的 key 类型也已固定。
redis.call('INCRBY', stock_key, 1)
redis.call('ZREM', users_key, user_id)
return RETURN_SUCCESS
