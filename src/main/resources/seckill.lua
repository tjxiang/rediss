local voucherId =  ARGV[1]
local userId =  ARGV[2]
local stock_key =  'seckill:stock:' .. voucherId
local order_key =  'seckill:order:' .. voucherId

if(tonumber(redis.call('get',stock_key)) <= 0) then
    return 1;
end

if(redis.call('sismember',order_key,userId) == 1) then
    return 2;
end

redis.call('incrby',stock_key,-1)
redis.call('sadd',order_key,userId)

return 0;


--if(id == ARGV[1]) then
--    return redis.call('del',KEYS[1])
--end
--return 0