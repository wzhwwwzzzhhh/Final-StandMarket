-- B6 atomic reservation: stock + user ZSET + order token HASH + active coupon registry.
local stock_key = KEYS[1]
local start_key = KEYS[2]
local end_key = KEYS[3]
local users_key = KEYS[4]
local reservations_key = KEYS[5]
local registry_key = KEYS[6]
local quantity_raw = ARGV[1]
local now_raw = ARGV[2]
local user_id = ARGV[3]
local order_number = ARGV[4]

local function type_name(key)
    local value = redis.call('TYPE', key)
    if type(value) == 'table' then return value['ok'] end
    return value
end

local function nonnegative_integer(value)
    return value and string.match(value, '^%d+$')
end

local function positive_integer(value)
    return value and string.match(value, '^%d+$') and tonumber(value) > 0
end

if #KEYS ~= 6 or #ARGV ~= 4 or not positive_integer(quantity_raw)
        or not positive_integer(now_raw) or not positive_integer(user_id)
        or not order_number or #order_number > 50 or not positive_integer(order_number) then
    return -5
end
if type_name(stock_key) ~= 'string' or type_name(start_key) ~= 'string'
        or type_name(end_key) ~= 'string'
        or (type_name(users_key) ~= 'none' and type_name(users_key) ~= 'zset')
        or (type_name(reservations_key) ~= 'none' and type_name(reservations_key) ~= 'hash')
        or (type_name(registry_key) ~= 'none' and type_name(registry_key) ~= 'set') then
    return -5
end

local stock_raw = redis.call('GET', stock_key)
local start_raw = redis.call('GET', start_key)
local end_raw = redis.call('GET', end_key)
if not nonnegative_integer(stock_raw) or not positive_integer(start_raw) or not positive_integer(end_raw) then
    return -5
end
local quantity = tonumber(quantity_raw)
local now = tonumber(now_raw)
if now < tonumber(start_raw) then return -3 end
if now > tonumber(end_raw) then return -2 end

local score = redis.call('ZSCORE', users_key, user_id)
local token = redis.call('HGET', reservations_key, user_id)
if score ~= false or token ~= false then
    if score == false or token == false then return -6 end
    return -4
end
if tonumber(stock_raw) < quantity then return -1 end

redis.call('DECRBY', stock_key, quantity)
redis.call('ZADD', users_key, now, user_id)
redis.call('HSET', reservations_key, user_id, order_number)
redis.call('SADD', registry_key, string.sub(stock_key, string.len('seckill:coupon:stock:') + 1))
return 0
