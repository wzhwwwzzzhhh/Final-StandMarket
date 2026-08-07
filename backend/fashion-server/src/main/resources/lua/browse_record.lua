-- browse_record.lua
-- KEYS[1]: 浏览历史key (user:browse:{userId})
-- ARGV[1]: 商品ID
-- ARGV[2]: 最大保留条数（默认50）
-- ARGV[3]: 过期时间（秒，>0 时设置/续期 TTL）

local key = KEYS[1]
local product_id = ARGV[1]
local max_size = tonumber(ARGV[2]) or 50
local ttl = tonumber(ARGV[3])

-- 去重：移除该商品的历史记录
redis.call('LREM', key, 0, product_id)
-- 新浏览放头部
redis.call('LPUSH', key, product_id)
-- 截断最多 max_size 条
redis.call('LTRIM', key, 0, max_size - 1)
-- 续期过期时间，避免 key 无限累积
if ttl and ttl > 0 then
    redis.call('EXPIRE', key, ttl)
end

return 1
