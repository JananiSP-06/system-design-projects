package com.ssn.ratelimiter.service;

import com.ssn.ratelimiter.config.RateLimiterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Token Bucket rate limiter backed by Redis.
 *
 * Each client gets one Redis hash key: ratelimit:{clientId} with fields
 *   tokens        - current token count (float, as a string)
 *   last_refill   - epoch seconds of the last time tokens were computed
 *
 * The actual check-and-decrement is delegated entirely to a Lua script
 * (token_bucket.lua) executed with EVALSHA, so the read-refill-compare-
 * write sequence happens as ONE atomic Redis operation. This is what
 * makes the limiter safe under concurrent requests and across multiple
 * application instances hitting the same Redis.
 */
@Service
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<List> tokenBucketScript;

    @Autowired
    private RateLimiterProperties properties;

    /** Checks (and, if allowed, consumes) one token for the given client. */
    public RateLimitResult tryConsume(String clientId) {
        return tryConsume(clientId, 1);
    }

    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String clientId, int requestedTokens) {
        String key = KEY_PREFIX + clientId;
        double nowSeconds = System.currentTimeMillis() / 1000.0;

        List<Object> result = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(properties.getCapacity()),
                String.valueOf(properties.getRefillRate()),
                String.valueOf(nowSeconds),
                String.valueOf(requestedTokens)
        );

        long allowedFlag = ((Number) result.get(0)).longValue();
        double tokensRemaining = Double.parseDouble(String.valueOf(result.get(1)));

        boolean allowed = allowedFlag == 1;
        log.debug("client={} allowed={} tokensRemaining={}", clientId, allowed, tokensRemaining);

        return new RateLimitResult(allowed, tokensRemaining);
    }
}
