# System Design Projects

A collection of hands-on system design projects, each in its own folder
with its own README and instructions to run it.

| # | Project | Tech | Concepts |
|---|---------|------|----------|
| 1 | [URL Shortener](./url-shortener) | Spring Boot, MongoDB, Redis | Caching (cache-aside + write-through), TTL, unique short codes, hit/miss metrics |
| 2 | [Rate Limiter](./rate-limiter) | Spring Boot, Redis, Lua | Token bucket, atomic Redis scripts, HTTP 429, per-client limits |
| 3 | [MongoDB Sharding](./mongo-sharding) | MongoDB, Docker Compose | Shards, config server, mongos, range sharding, chunk pre-splitting |
| 4 | [Autocomplete](./autocomplete) | Spring Boot, Redis, Trie | Prefix search, Top-K ranking, caching with TTL, cache invalidation, JMeter load testing |
| 5 | [Consistent Hashing](./consistent-hashing) | Spring Boot, MongoDB, Docker Compose | Hash ring, virtual nodes, minimal data movement on add/remove node, multi-database routing |
| 6 | [Web Crawler](./web-crawler) | Spring Boot, jsoup, Redis | BFS crawling, URL frontier queue, deduplication with Redis, async execution |
