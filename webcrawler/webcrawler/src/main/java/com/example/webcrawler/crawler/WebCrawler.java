package com.example.webcrawler.crawler;

import com.example.webcrawler.queue.UrlQueue;
import com.example.webcrawler.validation.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WebCrawler {

    private static final Logger log = LoggerFactory.getLogger(WebCrawler.class);
    private static final String VISITED_KEY = "crawler:visited";

    private final UrlQueue urlQueue;
    private final UrlValidator urlValidator;
    private final RedisTemplate<String, String> redisTemplate;
    private final int maxPages;
    private final long delayMs;

    // Only one crawl may run at a time (the queue and Redis set are shared).
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String status = "IDLE";

    public WebCrawler(
            UrlQueue urlQueue,
            UrlValidator urlValidator,
            RedisTemplate<String, String> redisTemplate,
            @Value("${crawler.max-pages:10}") int maxPages,
            @Value("${crawler.delay-ms:500}") long delayMs) {

        this.urlQueue = urlQueue;
        this.urlValidator = urlValidator;
        this.redisTemplate = redisTemplate;
        this.maxPages = maxPages;
        this.delayMs = delayMs;
    }

    public void crawl(String seedUrl) {

        if (!running.compareAndSet(false, true)) {
            log.warn("A crawl is already running; ignoring request for {}", seedUrl);
            return;
        }

        try {
            status = "RUNNING";

            String seed = normalize(seedUrl);
            if (seed == null || !urlValidator.isValid(seed)) {
                status = "FAILED";
                log.warn("Invalid URL: {}", seedUrl);
                return;
            }

            // Every crawl starts from a clean slate.
            urlQueue.clear();
            redisTemplate.delete(VISITED_KEY);

            Set<String> queued = new HashSet<>();
            String seedHost = URI.create(seed).getHost();

            urlQueue.addUrl(seed);
            queued.add(seed);

            log.info("Starting crawl of {} (max pages: {})", seed, maxPages);

            int crawled = 0;

            while (!urlQueue.isEmpty() && crawled < maxPages) {

                String currentUrl = urlQueue.getUrl();
                if (currentUrl == null) {
                    break;
                }

                try {
                    log.info("Crawling: {}", currentUrl);

                    Document document = Jsoup.connect(currentUrl)
                            .userAgent("Mozilla/5.0")
                            .timeout(10000)
                            .get();

                    crawled++;
                    redisTemplate.opsForSet().add(VISITED_KEY, currentUrl);

                    for (Element link : document.select("a[href]")) {

                        String discovered = normalize(link.absUrl("href"));

                        if (discovered == null || !urlValidator.isValid(discovered)) {
                            continue;
                        }

                        if (!seedHost.equalsIgnoreCase(URI.create(discovered).getHost())) {
                            continue;
                        }

                        if (queued.add(discovered)) {
                            urlQueue.addUrl(discovered);
                            log.debug("Discovered: {}", discovered);
                        }
                    }

                } catch (Exception e) {
                    log.warn("Failed to crawl {} : {}", currentUrl, e.getMessage());
                }

                // Politeness delay between requests
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (crawled >= maxPages) {
                log.info("Maximum crawl limit reached: {}", maxPages);
            }

            status = "COMPLETED";
            log.info("Crawling completed. Pages crawled: {}", crawled);

        } catch (Exception e) {
            status = "FAILED";
            log.error("Crawl failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    /** Trim and drop the #fragment so /page and /page#x count as one URL. */
    private String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String result = url.trim();
        int hash = result.indexOf('#');
        if (hash >= 0) {
            result = result.substring(0, hash);
        }
        return result.isEmpty() ? null : result;
    }

    public Set<String> getVisitedUrls() {
        Set<String> members = redisTemplate.opsForSet().members(VISITED_KEY);
        return members != null ? members : new HashSet<>();
    }

    public String getStatus() {
        return status;
    }

    public void clearCrawlerData() {
        if (running.get()) {
            log.warn("Cannot clear data while a crawl is running");
            return;
        }
        urlQueue.clear();
        redisTemplate.delete(VISITED_KEY);
        status = "IDLE";
        log.info("Crawler data cleared.");
    }
}