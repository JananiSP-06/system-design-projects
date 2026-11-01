package com.ssn.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** Max tokens a bucket can hold - this is the maximum burst size allowed. */
    private int capacity = 5;

    /** Tokens added back per second - this is the sustained (steady-state) rate. */
    private double refillRate = 1.0;

    /** Request header used to identify the client. Falls back to remote IP if absent. */
    private String headerName = "X-Client-Id";

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
