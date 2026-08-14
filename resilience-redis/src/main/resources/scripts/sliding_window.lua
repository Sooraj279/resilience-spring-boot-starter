-- KEYS[1] window key
-- ARGV[1] limit  ARGV[2] window (ms)  ARGV[3] now (ms)  ARGV[4] unique member id
-- returns {allowed, remaining, retryAfterMillis}

local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])
local member    = ARGV[4]

redis.call("ZREMRANGEBYSCORE", key, 0, now - window_ms)
local count = redis.call("ZCARD", key)

if count < limit then
    redis.call("ZADD", key, now, member)
    redis.call("PEXPIRE", key, window_ms)
    return {1, limit - count - 1, 0}
end

local oldest = redis.call("ZRANGE", key, 0, 0, "WITHSCORES")
local retry_after = 1
if oldest[2] then
    retry_after = math.ceil((tonumber(oldest[2]) + window_ms) - now)
    if retry_after < 1 then retry_after = 1 end
end

redis.call("PEXPIRE", key, window_ms)
return {0, 0, retry_after}