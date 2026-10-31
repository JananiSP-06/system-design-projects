package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CacheController {

    @Autowired
    UrlService service;

    @GetMapping("/cache/stats")
    public Map<String, Object> stats() {

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Total Requests", service.getTotalRequests());
        map.put("Cache Hits", service.getCacheHits());
        map.put("Cache Misses", service.getCacheMisses());
        map.put("Database Hits", service.getDatabaseHits());
        map.put("Hit Ratio", String.format("%.2f %%", service.getHitRatio()));
        return map;
    }

    @PostMapping("/cache/reset")
    public String reset() {
        service.resetStats();
        return "reset";
    }
}