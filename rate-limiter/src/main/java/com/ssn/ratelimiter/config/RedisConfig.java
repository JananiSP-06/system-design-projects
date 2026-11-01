package com.ssn.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * Plain String-in/String-out template. We don't need object
     * serialization here - the bucket is just two numeric fields.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * Loads token_bucket.lua once at startup and registers it as a
     * reusable, atomic Redis script (EVALSHA under the hood).
     */
    @Bean
    public RedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        return script;
    }
}
