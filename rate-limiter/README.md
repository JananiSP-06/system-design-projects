# API Rate Limiter (Token Bucket + Redis)

A Spring Boot API rate limiter using the **token bucket** algorithm, with
state stored in Redis so limits are shared across app instances.

## How it works

```
Client ──► RateLimitInterceptor ──► TokenBucketRateLimiter ──► Redis (Lua script)
                │                                                  │
                ├── allowed  ──► Controller (200)                  │
                └── rejected ──► 429 Too Many Requests  ◄──────────┘
```

- Each client gets a bucket (a Redis hash `ratelimit:<clientId>`) holding
  `tokens` and `last_refill`.
- Every request costs 1 token. Tokens refill continuously at `refill-rate`
  per second, up to `capacity`.
- If a token is available the request passes. Otherwise it gets `429`.
- The check, refill and update run as **one Lua script**, so concurrent
  requests can't cause race conditions.
- Clients are identified by the `X-Client-Id` header, falling back to the
  caller's IP.

## Prerequisites
- Java 17+
- Maven 3.8+
- Docker Desktop (running)

## Run

```bash
docker compose up -d
mvn spring-boot:run
```

App runs on http://localhost:8080. Only `/api/**` routes are rate limited.

## Test

Normal traffic (one request every couple of seconds, always `200`):
```bash
curl -i -H "X-Client-Id: clientA" http://localhost:8080/api/ping
```

Burst traffic (10 concurrent requests, so about 5 succeed and the rest get `429`):
```bash
chmod +x burst_test.sh
./burst_test.sh 10 clientA
```

Different clients have independent buckets:
```bash
./burst_test.sh 10 clientB
```

Response headers:
- `X-RateLimit-Capacity`: bucket size
- `X-RateLimit-Remaining`: tokens left after this request

## Postman
- `GET http://localhost:8080/api/ping`
- Header: `X-Client-Id: clientA`
- Click Send repeatedly. After about 5 quick requests you'll get `429`.
  Wait a few seconds and it works again.

## Inspect Redis
```bash
docker exec -it rate-limiter-redis redis-cli
> HGETALL ratelimit:clientA
```

## Configuration (`application.yml`)

| Property | Default | Meaning |
|----------|---------|---------|
| `rate-limiter.capacity` | 5 | Max tokens (max burst size) |
| `rate-limiter.refill-rate` | 1 | Tokens added per second (sustained rate) |
| `rate-limiter.header-name` | X-Client-Id | Header that identifies the client |

Restart the app after changing these.

## Key files

| File | Purpose |
|------|---------|
| `token_bucket.lua` | Atomic refill + consume logic in Redis |
| `TokenBucketRateLimiter` | Runs the Lua script, returns allowed/remaining |
| `RateLimitInterceptor` | Applies the limiter, sets headers, returns 429 |
| `RateLimiterProperties` | Binds config from `application.yml` |

## Possible improvements
- `Retry-After` header on 429 responses
- Fail-open or fail-closed behaviour when Redis is down
- Rate limit by authenticated user or API key instead of a spoofable header
- Use Redis `TIME` inside the script to avoid clock drift between instances
- Other algorithms: sliding window, leaky bucket, fixed window
