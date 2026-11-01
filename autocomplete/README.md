# Autocomplete Search System (Trie + Redis + Spring Boot)

A search autocomplete service. A **Trie** finds all terms under a typed prefix
and ranks them by popularity (Top-K). A **Redis** cache sits in front of the
Trie so repeated prefixes are served without traversing the tree.

## Architecture

```
GET /api/autocomplete?prefix=app
        │
        ▼
 AutocompleteController
        │
        ▼
 AutocompleteService ──► Redis  (key: autocomplete:<prefix>:<topK>, TTL 300s)
        │                  │ HIT  → return cached JSON
        │ MISS             │
        ▼                  │
      Trie ── DFS + sort by frequency, take Top-K ──► store in Redis ─┘
```

- The dataset (`term,frequency`) is loaded into the Trie at startup from
  `src/main/resources/data/search_terms.csv`.
- `POST /record` bumps a term's frequency and clears the cached results for
  every prefix of that term, so rankings update immediately.
- If Redis is unavailable, the service logs a warning and answers from the
  Trie instead of failing.

## Project structure

```
autocomplete/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── jmeter/autocomplete-load-test.jmx
└── src/
    ├── main/java/com/example/autocomplete/
    │   ├── AutocompleteApplication.java
    │   ├── trie/{Trie,TrieNode}.java
    │   ├── model/SearchTerm.java
    │   ├── service/AutocompleteService.java
    │   └── controller/AutocompleteController.java
    ├── main/resources/{application.yml, data/search_terms.csv}
    └── test/java/.../TrieTest.java
```

## Prerequisites
- Docker Desktop (running), or JDK 17+ and Maven plus a local Redis

## Run

**With Docker (recommended):**
```bash
docker compose up --build
```
Starts Redis (port 6379) and the app (port 8080).

**Without Docker for the app:**
```bash
docker run -d --name autocomplete-redis -p 6379:6379 redis:7-alpine
mvn spring-boot:run
```

> Ports 8080 and 6379 are also used by other projects in this repo.
> Run one project at a time.

## API

**Suggestions**
```
GET http://localhost:8080/api/autocomplete?prefix=app&topK=5
```
```json
{
  "prefix": "app",
  "suggestions": [
    {"term": "app", "frequency": 200},
    {"term": "apple", "frequency": 120},
    {"term": "application", "frequency": 85},
    {"term": "app store", "frequency": 60},
    {"term": "apply", "frequency": 45}
  ],
  "responseTimeMs": 1
}
```

**Record a search** (bumps frequency, invalidates related cache entries)
```
POST http://localhost:8080/api/autocomplete/record
Content-Type: application/json

{ "term": "apple" }
```
A missing or blank `term` returns `400`.

**Health**
```
GET http://localhost:8080/api/autocomplete/health
```

## Test with Postman
1. Send `GET /api/autocomplete?prefix=app&topK=5` twice.
   The app log shows `Cache MISS` then `Cache HIT`.
2. Inspect the cached entry:
   ```bash
   docker exec -it autocomplete-redis redis-cli GET "autocomplete:app:5"
   ```
3. `POST /record` with `{"term":"apple"}`, then repeat the GET:
   apple's frequency goes up by 1 and the cache is rebuilt.
4. Try prefixes like `ban`, `car`, `cat`, `dog`, `em`.

Unit tests (no Redis needed): `mvn test`

## Load test with JMeter
Open `jmeter/autocomplete-load-test.jmx` in JMeter. It runs a GET thread group
(100 users, 10s ramp-up, 20 loops) plus a POST `/record` group.
Or build your own: Thread Group, then HTTP Request (`localhost:8080`,
`/api/autocomplete`, `prefix=app`, `topK=5`), then Summary Report.

Run it twice, once with an empty cache and once warmed up, and compare average
response time, throughput and error rate.

> With only about 50 terms the Trie lookup takes microseconds, so Redis may not
> look faster; the extra network hop can even make it slower. The cache pays off
> with large datasets (100k+ terms) and expensive prefix scans.

## Configuration (`application.yml`)

| Property | Default | Meaning |
|----------|---------|---------|
| `autocomplete.cache.ttl-seconds` | 300 | Cache entry lifetime |
| `autocomplete.default-top-k` | 5 | Results returned if `topK` is omitted |
| `autocomplete.dataset-path` | data/search_terms.csv | Dataset loaded at startup |
| `REDIS_HOST` / `REDIS_PORT` | localhost / 6379 | Redis connection |

## Analysis

### a) Role of each component
- **Trie**: a tree where each edge is one character. Terms sharing a prefix
  share a path from the root, so related terms are grouped by structure.
- **Prefix**: the partial string typed so far. It is the key used to navigate
  the Trie to the relevant subtree.
- **Redis cache**: an in-memory store in front of the Trie. Popular prefixes
  are answered without re-traversing the tree.
- **Top-K**: only the K most frequent matches are returned, keeping responses
  small and relevant.

### b) How the Trie finds suggestions
1. Walk one node per character of the prefix (`a → p → p`). If a character has
   no child, return an empty list.
2. From the prefix node, run a depth-first search over the subtree.
3. Every node marked end-of-word is a completion; collect it with its frequency.
4. Sort by frequency (descending) and return the top K.

### c) Cache hit
1. The controller receives the request and the service builds the key
   `autocomplete:app:5`.
2. Redis returns the stored JSON, which is parsed into `SearchTerm` objects.
3. The result is returned directly. **The Trie is never touched.**

### d) Cache miss
1. Redis returns nothing for the key.
2. The service calls `trie.searchByPrefix(prefix, k)`.
3. The result is written to Redis as JSON with a TTL, so the next request hits.
4. The list is returned to the caller.

### e) Trie vs linear search

| | Linear search | Trie |
|---|---|---|
| Approach | Scan every term and check the prefix | Walk to the prefix node, explore only its subtree |
| Time | O(N × L) | O(P + M), where P is prefix length and M the matching nodes |
| Scales with dataset size | Slower as N grows | Mostly independent of total size |
| Memory | Lower | Higher (a node per character) |
| Best for | Small, static data | Large, frequently queried data |

## Limitations and possible improvements
- The Trie lives in each app instance's memory. Several instances would each
  hold their own copy, so a recorded search is not shared between them.
- The Trie uses plain `HashMap`s; concurrent `record` and `search` calls are
  not strictly thread-safe (`ConcurrentHashMap` would fix this).
- Search collects every match under the prefix before sorting. Storing the
  Top-K at each node would make short prefixes much faster.
- Ties in frequency are returned in no guaranteed order.
- Possible extensions: typo tolerance, personalised suggestions, time-decayed
  frequencies.