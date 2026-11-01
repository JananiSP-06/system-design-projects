# UCS3513 - Lab Exercise 6: API Rate Limiter using Token Bucket + Redis

## 1. Start Redis

```bash
docker compose up -d
docker ps                 # confirm rate-limiter-redis is Up
```

## 2. Run the Spring Boot app

```bash
mvn spring-boot:run
```

App starts on `http://localhost:8080`. Default config (in `application.yml`):
capacity = 5 tokens, refill-rate = 1 token/second, client identified via
the `X-Client-Id` header (falls back to remote IP if absent).

## 3. Normal traffic (well under the limit)

```bash
curl -H "X-Client-Id: clientA" http://localhost:8080/api/ping
```

Run it once every couple of seconds - every request should return `200`
because tokens are refilling faster than you're spending them.

## 4. Burst traffic (exceeding the limit)

```bash
chmod +x burst_test.sh
./burst_test.sh 10 clientA
```

Fires 10 concurrent requests from the same client. With capacity=5 you
should see the first ~5 succeed (`200`) and the rest fail (`429`).

## 5. Inspect response headers

```bash
curl -i -H "X-Client-Id: clientA" http://localhost:8080/api/ping
```

Look for `X-RateLimit-Capacity` and `X-RateLimit-Remaining` in the
response headers.

## 6. Inspect the bucket directly in Redis (optional)

```bash
docker exec -it rate-limiter-redis redis-cli
> HGETALL ratelimit:clientA
```

## 7. Change bucket capacity / refill rate

Edit `src/main/resources/application.yml`:

```yaml
rate-limiter:
  capacity: 5
  refill-rate: 1
```

Restart the app and repeat the burst test to see the effect.
