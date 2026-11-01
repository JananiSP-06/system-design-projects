package com.ssn.ratelimiter.config;

import com.ssn.ratelimiter.service.RateLimitResult;
import com.ssn.ratelimiter.service.TokenBucketRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Runs before every controller method. Identifies the caller, asks the
 * TokenBucketRateLimiter if they have a token available, and either lets
 * the request through or short-circuits it with HTTP 429.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    @Autowired
    private RateLimiterProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String clientId = resolveClientId(request);

        RateLimitResult result = rateLimiter.tryConsume(clientId);

        // Expose bucket state to the caller regardless of outcome - useful for
        // debugging and for well-behaved clients that back off automatically.
        response.setHeader("X-RateLimit-Capacity", String.valueOf(properties.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf((long) result.getTokensRemaining()));

        if (!result.isAllowed()) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded for client '" + clientId + "'. Try again shortly.\"}"
            );
            return false; // stop here - controller method is never invoked
        }

        return true; // proceed to the controller
    }

    private String resolveClientId(HttpServletRequest request) {
        String header = request.getHeader(properties.getHeaderName());
        if (header != null && !header.isBlank()) {
            return header;
        }
        return request.getRemoteAddr(); // fall back to caller's IP
    }
}
