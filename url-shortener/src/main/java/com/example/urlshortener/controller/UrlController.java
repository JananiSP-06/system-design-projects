package com.example.urlshortener.controller;

import com.example.urlshortener.dto.UrlRequest;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;

@RestController
public class UrlController {

    @Autowired
    UrlService service;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping("/shorten")
    public UrlResponse shorten(@RequestBody UrlRequest request) {

        String longUrl = request == null ? null : request.getLongUrl();
        if (!isValidHttpUrl(longUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "longUrl must be a valid http or https URL");
        }

        String code = service.shortenUrl(longUrl.trim());
        return new UrlResponse(baseUrl + "/" + code);
    }

    @GetMapping("/{shortCode}")
    public void redirect(@PathVariable String shortCode,
                         HttpServletResponse response) throws IOException {

        String longUrl = service.getLongUrl(shortCode);

        if (longUrl == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.sendRedirect(longUrl);
    }

    private boolean isValidHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception e) {
            return false;
        }
    }
}