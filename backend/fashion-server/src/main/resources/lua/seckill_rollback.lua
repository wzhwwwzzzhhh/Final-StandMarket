-- B6 token-aware reservation release.
local RETURN_APPLIED = 1
local RETURN_APPLIED_LEDGER_INCONSISTENT = 2
local RETURN_ALREADY_APPLIED = 0
local RETURN_TOKEN_MISMATCH = -1
local RETURN_LEDGER_CORRUPT = -2
local RETURN_INVALID_VALUE = -3

local stock_key = KEYS[1]
local users_key = KEYS[2]
local reservations_key = KEYS[3]
local registry_key = KEYS[4]
local coupon_id = ARGV[1]
local quantity_raw = ARGV[2]
local user_id = ARGV[3]
local expected_order_number = ARGV[4]

local function type_name(key)
    local value = redis.call('TYPE', key)
    if type(value) == 'table' then return value['ok'] end
    return value
end

local function positive_integer(value)
    return value and string.match(value, '^%d+$') and tonumber(value) > 0
end

if #KEYS ~= 4 or #ARGV ~= 4 or not positive_integer(coupon_id)
        or quantity_raw ~= '1' or not positive_integer(user_id)
        or not expected_order_number or #expected_order_number > 50
        or not positive_integer(expected_order_number) then
    return RETURN_INVALID_VALUE
end
if type_name(stock_key) ~= 'string'
        or (type_name(users_key) ~= 'none' and type_name(users_key) ~= 'zset')
        or (type_name(reservations_key) ~= 'none' and type_name(reservations_key) ~= 'hash')
        or (type_name(registry_key) ~= 'none' and type_name(registry_key) ~= 'set') then
    return RETURN_INVALID_VALUE
end

local stock_raw = redis.call('GET', stock_key)
if not stock_raw or not string.match(stock_raw, '^%d+$') then return RETURN_INVALID_VALUE end
local stock = tonumber(stock_raw)
if not stock or stock < 0 or stock > 2147483646 then return RETURN_INVALID_VALUE end

local score = redis.call('ZSCORE', users_key, user_id)
local token = redis.call('HGET', reservations_key, user_id)
if score == false and token == false then return RETURN_ALREADY_APPLIED end
if score == false or token == false then return RETURN_LEDGER_CORRUPT end
if token ~= expected_order_number then return RETURN_TOKEN_MISMATCH end

redis.call('INCRBY', stock_key, 1)
redis.call('ZREM', users_key, user_id)
redis.call('HDEL', reservations_key, user_id)
local reservations = redis.call('HLEN', reservations_key)
local users = redis.call('ZCARD', users_key)
if reservations == 0 and users == 0 then redis.call('SREM', registry_key, coupon_id) end
if reservations ~= users then return RETURN_APPLIED_LEDGER_INCONSISTENT end
return RETURN_APPLIED
