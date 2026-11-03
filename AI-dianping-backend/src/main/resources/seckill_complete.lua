-- 结果更新与失败补偿一起执行，重投消息不会重复补偿。
local previous = redis.call('HGET', KEYS[3], 'status')
if previous == 'CREATED' or previous == 'FAILED' then return 0 end
local outcome = ARGV[4]
if outcome == 'DUPLICATE' or outcome == 'SOLD_OUT' then
    local stock = redis.call('GET', KEYS[1])
    if stock and tonumber(stock) then redis.call('INCR', KEYS[1]) end
    if outcome == 'SOLD_OUT' then redis.call('SREM', KEYS[2], ARGV[1]) end
end
local status = 'CREATED'
if outcome ~= 'CREATED' then status = 'FAILED' end
redis.call('HSET', KEYS[3], 'userId', ARGV[1], 'voucherId', ARGV[2], 'orderId', ARGV[3],
    'status', status, 'message', ARGV[5], 'existingOrderId', ARGV[6])
redis.call('EXPIRE', KEYS[3], 604800)
return 1

