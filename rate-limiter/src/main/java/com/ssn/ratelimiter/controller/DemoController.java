package com.ssn.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A trivial protected endpoint. All the interesting work happens in
 * RateLimitInterceptor before this method is ever reached.
 */
@RestController
public class DemoController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "pong");
        response.put("servedAt", Instant.now().toString());
        return response;
    }
}
