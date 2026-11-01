-- token_bucket.lua
-- Atomically checks and updates a single client's token bucket in Redis.
--
-- KEYS[1] = bucket key, e.g. "ratelimit:client123"
-- ARGV[1] = capacity        (max tokens the bucket can hold)
-- ARGV[2] = refill_rate     (tokens added per second)
-- ARGV[3] = now             (current time in seconds, as a float)
-- ARGV[4] = requested       (tokens this request needs, normally 1)
--
-- Returns: { allowed (1 or 0), tokens_remaining }
--
-- Runs as a single atomic operation inside Redis, so concurrent requests
-- from the same client can never read-modify-write the same bucket at
-- the same time (no race condition, no separate locking needed).

local bucket_key   = KEYS[1]
local capacity     = tonumber(ARGV[1])
local refill_rate  = tonumber(ARGV[2])
local now          = tonumber(ARGV[3])
local requested    = tonumber(ARGV[4])

local bucket = redis.call("HMGET", bucket_key, "tokens", "last_refill")
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    -- First request ever seen for this client: start with a full bucket.
    tokens = capacity
    last_refill = now
end

-- Refill: work out how many tokens should have accumulated since the
-- last request, capped at the bucket's capacity.
local elapsed = math.max(0, now - last_refill)
local refill_amount = elapsed * refill_rate
tokens = math.min(capacity, tokens + refill_amount)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call("HMSET", bucket_key, "tokens", tokens, "last_refill", now)
-- Let unused buckets expire so idle clients don't linger in Redis forever.
redis.call("EXPIRE", bucket_key, 3600)

return { allowed, tostring(tokens) }
