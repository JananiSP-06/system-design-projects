# URL Shortener (Spring Boot + MongoDB + Redis)

Shortens long URLs and redirects short codes back to the original.
MongoDB is the source of truth; Redis is a cache in front of it.

## Architecture

```
Client ──► Spring Boot ──► Redis (cache, TTL 30 min)
                │               │ miss
                └───────────────► MongoDB (persistent store)
```

- **Shorten**: validate URL, return the existing code if the URL was already
  shortened, otherwise generate a random 7-char base62 code, save to Mongo
  (unique indexes on `shortCode` and `longUrl`), and write it to Redis.
- **Redirect**: look up Redis first (hit), fall back to Mongo (miss),
  then repopulate Redis with a TTL.
- **Stats**: `/cache/stats` reports requests, hits, misses, DB hits, hit ratio.

## Prerequisites
- Java 17+
- Docker (for Mongo and Redis)

## Run

```bash
docker compose up -d
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

App runs on http://localhost:8083.

## API

Shorten:
```bash
curl -X POST http://localhost:8083/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com/some/long/path"}'
# {"shortUrl":"http://localhost:8083/aB3dE9x"}
```

Redirect:
```bash
curl -i http://localhost:8083/aB3dE9x
```

Cache stats / reset:
```bash
curl http://localhost:8083/cache/stats
curl -X POST http://localhost:8083/cache/reset
```

Invalid input returns `400`; unknown code returns `404`.

## Inspect Redis
```bash
docker exec -it urlshortener-redis redis-cli
> KEYS *
> TTL aB3dE9x
```

## Configuration (`application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `server.port` | 8083 | App port |
| `app.base-url` | http://localhost:8083 | Prefix for returned short URLs |
| `app.cache-ttl-minutes` | 30 | Redis entry lifetime |

## Possible improvements
- Expiring / custom aliases
- Click analytics
- Rate limiting per client
- Counter-based IDs for horizontal scaling
- Testcontainers for integration tests