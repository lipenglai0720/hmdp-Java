-- 只初始化缺失库存，保留尚未消费消息对应的限购标记。
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
for i = 2, #ARGV do redis.call('SADD', KEYS[2], ARGV[i]) end
redis.call('SET', KEYS[1], ARGV[1])
return 1

