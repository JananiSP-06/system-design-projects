package com.example.webcrawler.service;

import com.example.webcrawler.crawler.WebCrawler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CrawlerService {

    private final WebCrawler webCrawler;

    public CrawlerService(WebCrawler webCrawler) {
        this.webCrawler = webCrawler;
    }

    @Async("crawlerTaskExecutor")
    public void startCrawling(String url) {

        webCrawler.crawl(url);
    }

    public String getStatus() {

        return webCrawler.getStatus();
    }

    public Object getVisitedUrls() {

        return webCrawler.getVisitedUrls();
    }

    public void clearData() {

        webCrawler.clearCrawlerData();
    }
}