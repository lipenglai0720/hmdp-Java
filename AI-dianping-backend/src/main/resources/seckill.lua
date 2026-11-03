-- Redis 内原子完成限购、时间校验、排队及库存预扣。KEYS 显式传入。
local stockKey, buyersKey, queueKey, resultKey = KEYS[1], KEYS[2], KEYS[3], KEYS[4]
local userId, voucherId, orderId = ARGV[1], ARGV[2], ARGV[3]
local function kind(key) return redis.call('TYPE', key).ok end
if kind(stockKey) ~= 'string' or (kind(buyersKey) ~= 'none' and kind(buyersKey) ~= 'set')
    or (kind(queueKey) ~= 'none' and kind(queueKey) ~= 'stream')
    or kind(resultKey) ~= 'none' then return 3 end
if redis.call('SISMEMBER', buyersKey, userId) == 1 then return 2 end
local stock = tonumber(redis.call('GET', stockKey))
if not stock or stock <= 0 then return 1 end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now < tonumber(ARGV[4]) then return 4 end
if now >= tonumber(ARGV[5]) then return 5 end
-- 先保证消息成功入队；脚本执行期间其他客户端无法读取中间状态。
local queued = redis.pcall('XADD', queueKey, '*', 'userId', userId, 'voucherId', voucherId, 'orderId', orderId)
if type(queued) == 'table' and queued.err then return 3 end
redis.call('DECR', stockKey)
redis.call('SADD', buyersKey, userId)
redis.call('HSET', resultKey, 'userId', userId, 'voucherId', voucherId, 'orderId', orderId, 'status', 'PENDING')
redis.call('EXPIRE', resultKey, 604800)
return 0

