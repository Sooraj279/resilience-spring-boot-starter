-- KEYS[1] bucket key
-- ARGV[1] capacity  ARGV[2] refill tokens  ARGV[3] refill period (s)
-- ARGV[4] now (ms)  ARGV[5] requested
-- returns {allowed, remaining, retryAfterMillis}

local key            = KEYS[1]
local capacity       = tonumber(ARGV[1])
local refill_tokens  = tonumber(ARGV[2])
local refill_period  = tonumber(ARGV[3])
local now            = tonumber(ARGV[4])
local requested      = tonumber(ARGV[5])

local bucket      = redis.call("HMGET", key, "tokens", "last_refill")
local tokens      = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now
end

local rate_per_ms = refill_tokens / (refill_period * 1000)
tokens = math.min(capacity, tokens + ((now - last_refill) * rate_per_ms))
last_refill = now

local allowed = 0
local retry_after = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    retry_after = math.ceil((requested - tokens) / rate_per_ms)
    if retry_after < 1 then retry_after = 1 end
end

redis.call("HMSET", key, "tokens", tokens, "last_refill", last_refill)
redis.call("EXPIRE", key, refill_period * 2)

return {allowed, math.floor(tokens), retry_after}