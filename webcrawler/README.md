# Web Crawler (Spring Boot + jsoup + Redis)

A breadth-first web crawler exposed through a REST API. It starts from a seed
URL, follows links on the **same host**, and records every crawled page in
Redis. Crawling runs in the background so the API returns immediately.

## Architecture

```
POST /api/crawler/start
        │
        ▼
 CrawlerController ── validates URL, rejects if a crawl is running
        │
        ▼
 CrawlerService  (@Async, crawlerTaskExecutor thread pool)
        │
        ▼
 WebCrawler ─────────────────────────────────────────────┐
   1. seed URL → UrlQueue (in-memory FIFO)               │
   2. poll URL → fetch + parse HTML with jsoup           │
   3. record page in Redis set "crawler:visited"         │
   4. extract <a href> links → same-host, valid,         │
      not yet queued → back into the queue               │
   5. repeat until queue is empty or max pages reached ◄─┘
```

| Component | Role |
|-----------|------|
| `CrawlerController` | REST endpoints, input checks, `409` while a crawl runs |
| `CrawlerService` | Starts the crawl on a background thread |
| `WebCrawler` | BFS crawl loop, link extraction, visited tracking |
| `UrlQueue` | Thread-safe in-memory FIFO frontier |
| `UrlValidator` | Accepts only `http`/`https` URLs with a host |
| `AsyncConfig` | Thread pool (2 core, 5 max, queue 20) |
| `RedisConfig` | String-only `RedisTemplate` |
| Redis | Stores the set of visited URLs (`crawler:visited`) |

## How it behaves
- **Breadth-first** order, using a FIFO queue.
- **Same host only**: links to other domains are ignored.
- **Deduplication**: `#fragment` is stripped, and each URL is queued only once
  per crawl.
- **Page limit**: stops after `crawler.max-pages` pages (default 10).
- **Politeness delay** of `crawler.delay-ms` (default 500 ms) between requests.
- **One crawl at a time**: starting a second one returns `409 Conflict`.
- **Fresh start each crawl**: the queue and visited set are reset when a crawl
  begins, so you can crawl repeatedly without calling `/clear`.
- A page that fails to load is logged and skipped; the crawl continues.

## Prerequisites
- Docker Desktop (running), or JDK 17+, Maven and a local Redis

## Run

**With Docker (recommended):**
```bash
docker compose up --build
```
Starts Redis (6379) and the crawler (8082). The jar is built inside Docker.

**Without Docker for the app:**
```bash
docker run -d --name webcrawler-redis -p 6379:6379 redis:7-alpine
mvn spring-boot:run
```

> Port 6379 is also used by the other projects in this repo. Run one at a time.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/crawler/start` | Start a crawl (`202 Accepted`) |
| GET | `/api/crawler/status` | `IDLE`, `RUNNING`, `COMPLETED` or `FAILED` |
| GET | `/api/crawler/visited` | URLs crawled so far (from Redis) |
| DELETE | `/api/crawler/clear` | Clear the queue and visited set |

**Start a crawl**
```
POST http://localhost:8082/api/crawler/start
Content-Type: application/json

{ "url": "https://quotes.toscrape.com" }
```
```json
{ "message": "Crawler started successfully", "url": "https://quotes.toscrape.com" }
```
- Invalid or missing URL returns `400`.
- A crawl already running returns `409`.

## Try it in Postman
1. `POST /api/crawler/start` with the body above.
2. `GET /api/crawler/status` until it says `COMPLETED`.
3. `GET /api/crawler/visited` to see the crawled URLs (up to the page limit).
4. Start another crawl, e.g. `https://books.toscrape.com`. It works without
   calling `/clear`.
5. Watch the app logs for `Crawling: ...` lines.
6. Inspect Redis:
   ```bash
   docker exec -it webcrawler-redis redis-cli SMEMBERS crawler:visited
   ```

> `https://example.com` only links to another host, so it crawls just one page.
> Use a site with many internal links, such as the `toscrape.com` practice
> sites, which are built for crawling.

## Configuration (`application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `server.port` | 8082 | App port |
| `crawler.max-pages` | 10 | Pages crawled per run |
| `crawler.delay-ms` | 500 | Delay between requests |
| `spring.data.redis.host` / `port` | localhost / 6379 | Redis connection |

In Docker, `SPRING_DATA_REDIS_HOST=redis` overrides the host.

## Limitations and possible improvements
- The URL queue is in memory, so it is lost on restart and cannot be shared
  between instances. A Redis list would make the crawl distributed.
- Single-threaded crawl loop. Several worker threads pulling from a shared
  Redis queue would raise throughput.
- No `robots.txt` handling. Respect it before crawling real sites.
- No depth limit and no content storage; only URLs are recorded.
- Any host is accepted as a seed, including `localhost` and private IPs
  (SSRF risk if exposed publicly). Block private ranges before deploying.
- URL normalisation is minimal (fragment only). Trailing slashes, query-string
  ordering and `www.` variants still count as different URLs.