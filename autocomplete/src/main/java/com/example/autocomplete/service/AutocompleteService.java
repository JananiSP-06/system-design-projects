package com.example.autocomplete.service;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.trie.Trie;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(AutocompleteService.class);
    private static final String CACHE_KEY_PREFIX = "autocomplete:";
    private static final TypeReference<List<SearchTerm>> RESULT_TYPE = new TypeReference<>() {};

    private final Trie trie = new Trie();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${autocomplete.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${autocomplete.default-top-k:5}")
    private int defaultTopK;

    @Value("${autocomplete.dataset-path:data/search_terms.csv}")
    private String datasetPath;

    public AutocompleteService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** Load the dataset (term,frequency) into the Trie at application startup. */
    @PostConstruct
    public void loadDataset() {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(datasetPath).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { // skip header row: term,frequency
                    firstLine = false;
                    if (line.toLowerCase().startsWith("term")) continue;
                }
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                String term = parts[0].trim();
                long freq = Long.parseLong(parts[1].trim());
                trie.insert(term, freq);
                count++;
            }
            log.info("Loaded {} search terms into Trie from {}", count, datasetPath);
        } catch (Exception e) {
            log.error("Failed to load dataset from {}: {}", datasetPath, e.getMessage(), e);
        }
    }

    /**
     * 1) Normalize prefix
     * 2) Check Redis cache (if Redis is unavailable, fall through to the Trie)
     * 3) On cache miss, query the Trie, then populate the cache
     */
    public List<SearchTerm> getSuggestions(String prefix, Integer topK) {
        String normalizedPrefix = prefix.trim().toLowerCase();
        int k = (topK == null || topK <= 0) ? defaultTopK : topK;
        String cacheKey = CACHE_KEY_PREFIX + normalizedPrefix + ":" + k;

        // 1. Try Redis first
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache HIT for prefix '{}'", normalizedPrefix);
                return objectMapper.readValue(cached, RESULT_TYPE);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for '{}', falling back to Trie: {}", normalizedPrefix, e.getMessage());
        }

        // 2. Cache miss (or Redis unavailable) -> search the Trie
        log.info("Cache MISS for prefix '{}' -> searching Trie", normalizedPrefix);
        List<SearchTerm> results = trie.searchByPrefix(normalizedPrefix, k);

        // 3. Populate the cache (best effort)
        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(results),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("Redis write failed for '{}': {}", normalizedPrefix, e.getMessage());
        }

        return results;
    }

    /** Record that a term was chosen/searched, so future rankings adapt. */
    public void recordSearch(String term) {
        trie.recordSearch(term);
        invalidateCache(term.trim().toLowerCase());
    }

    /**
     * Remove cached results for every prefix of the term so the updated
     * frequency shows up. Uses SCAN (non-blocking) instead of KEYS.
     */
    private void invalidateCache(String normalizedTerm) {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                for (int i = 1; i <= normalizedTerm.length(); i++) {
                    String pattern = CACHE_KEY_PREFIX + escapeGlob(normalizedTerm.substring(0, i)) + ":*";
                    List<byte[]> keys = new ArrayList<>();
                    try (Cursor<byte[]> cursor = connection.keyCommands()
                            .scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                        while (cursor.hasNext()) {
                            keys.add(cursor.next());
                        }
                    }
                    if (!keys.isEmpty()) {
                        connection.keyCommands().del(keys.toArray(new byte[0][]));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Cache invalidation failed for '{}': {}", normalizedTerm, e.getMessage());
        }
    }

    /** Escape Redis glob characters so a term can't act as a pattern. */
    private String escapeGlob(String s) {
        return s.replaceAll("([\\\\*?\\[\\]])", "\\\\$1");
    }
}