package com.example.autocomplete.controller;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.service.AutocompleteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/autocomplete")
public class AutocompleteController {

    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    /**
     * GET /api/autocomplete?prefix=app&topK=5
     */
    @GetMapping
    public ResponseWrapper suggestions(
            @RequestParam String prefix,
            @RequestParam(required = false) Integer topK) {

        long start = System.nanoTime();
        List<SearchTerm> suggestions = autocompleteService.getSuggestions(prefix, topK);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        return new ResponseWrapper(prefix, suggestions, elapsedMs);
    }

    /**
     * POST /api/autocomplete/record  { "term": "application" }
     * Simulates a user actually selecting/searching a term, bumping its frequency.
     */
    @PostMapping("/record")
    public Map<String, String> record(@RequestBody Map<String, String> body) {
        String term = body == null ? null : body.get("term");
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "term must not be blank");
        }
        autocompleteService.recordSearch(term);
        return Map.of("status", "recorded", "term", term);
    }

    /** Simple health check for Docker/JMeter setup verification. */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    // Small response DTO
    public record ResponseWrapper(String prefix, List<SearchTerm> suggestions, long responseTimeMs) {
    }
}