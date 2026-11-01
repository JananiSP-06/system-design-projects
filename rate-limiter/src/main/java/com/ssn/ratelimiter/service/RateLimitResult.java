package com.ssn.ratelimiter.service;

/** Outcome of a single rate-limit check. */
public class RateLimitResult {

    private final boolean allowed;
    private final double tokensRemaining;

    public RateLimitResult(boolean allowed, double tokensRemaining) {
        this.allowed = allowed;
        this.tokensRemaining = tokensRemaining;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public double getTokensRemaining() {
        return tokensRemaining;
    }
}
