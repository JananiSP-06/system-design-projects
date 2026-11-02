package com.example.webcrawler.controller;

import com.example.webcrawler.model.CrawlRequest;
import com.example.webcrawler.service.CrawlerService;
import com.example.webcrawler.validation.UrlValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;
    private final UrlValidator urlValidator;

    public CrawlerController(CrawlerService crawlerService, UrlValidator urlValidator) {
        this.crawlerService = crawlerService;
        this.urlValidator = urlValidator;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startCrawler(
            @RequestBody CrawlRequest request) {

        Map<String, String> response = new HashMap<>();

        String url = request == null ? null : request.getUrl();

        if (url == null || !urlValidator.isValid(url.trim())) {
            response.put("error", "A valid http or https URL is required");
            return ResponseEntity.badRequest().body(response);
        }

        if ("RUNNING".equals(crawlerService.getStatus())) {
            response.put("error", "A crawl is already running");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        crawlerService.startCrawling(url.trim());

        response.put("message", "Crawler started successfully");
        response.put("url", url.trim());

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {

        Map<String, String> response = new HashMap<>();
        response.put("status", crawlerService.getStatus());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/visited")
    public ResponseEntity<Object> getVisitedUrls() {
        return ResponseEntity.ok(crawlerService.getVisitedUrls());
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearData() {

        Map<String, String> response = new HashMap<>();

        if ("RUNNING".equals(crawlerService.getStatus())) {
            response.put("error", "Cannot clear while a crawl is running");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        crawlerService.clearData();

        response.put("message", "Crawler data cleared");
        return ResponseEntity.ok(response);
    }
}