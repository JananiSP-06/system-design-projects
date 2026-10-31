package com.example.urlshortener.service;

import com.example.urlshortener.model.Url;
import com.example.urlshortener.repository.UrlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UrlService {

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UrlRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${app.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong databaseHits = new AtomicLong();

    public String shortenUrl(String longUrl) {
        Optional<Url> existing = repository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return existing.get().getShortCode();
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String code = randomCode();
            Url url = new Url();
            url.setLongUrl(longUrl);
            url.setShortCode(code);
            try {
                repository.save(url);
               
                return code;
            } catch (DuplicateKeyException e) {
                // Either the code collided, or another request just saved the same longUrl
                Optional<Url> raced = repository.findByLongUrl(longUrl);
                if (raced.isPresent()) {
                    return raced.get().getShortCode();
                }
                // otherwise it was a short code collision: try a new code
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    public String getLongUrl(String shortCode) {
        totalRequests.incrementAndGet();

        String cachedUrl = redisTemplate.opsForValue().get(shortCode);
        if (cachedUrl != null) {
            cacheHits.incrementAndGet();
            return cachedUrl;
        }

        cacheMisses.incrementAndGet();
        databaseHits.incrementAndGet();

        Optional<Url> url = repository.findByShortCode(shortCode);
        if (url.isPresent()) {
            cache(shortCode, url.get().getLongUrl());
            return url.get().getLongUrl();
        }
        return null;
    }

    private void cache(String shortCode, String longUrl) {
        redisTemplate.opsForValue().set(shortCode, longUrl, Duration.ofMinutes(cacheTtlMinutes));
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public long getTotalRequests() { return totalRequests.get(); }

    public long getCacheHits() { return cacheHits.get(); }

    public long getCacheMisses() { return cacheMisses.get(); }

    public long getDatabaseHits() { return databaseHits.get(); }

    public double getHitRatio() {
        long total = totalRequests.get();
        if (total == 0) return 0;
        return (cacheHits.get() * 100.0) / total;
    }

    public void resetStats() {
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        databaseHits.set(0);
    }
}