# Scalability Plan — 1,000,000 Users
## Docker Compose — Engineering Reference

> **Scope:** This document covers how to scale the existing eight-service music-streaming
> application to support 1 million registered users (target: ~100 K Daily Active Users (DAU), ~20 K peak-concurrent
> users). Every recommendation is anchored to a concrete service, table, or config key in
> this repository. No Kubernetes, Helm, or external orchestrators are introduced.

---

## Table of Contents

1. [Traffic Shape Assumptions](#1-traffic-shape-assumptions)
2. [Load Balancer and Reverse Proxy Layer](#2-load-balancer-and-reverse-proxy-layer)
3. [Horizontal vs Vertical Scaling per Service](#3-horizontal-vs-vertical-scaling-per-service)
4. [Recommended Replica Counts](#4-recommended-replica-counts)
5. [Database Optimization — PostgreSQL](#5-database-optimization--postgresql)
6. [Database Optimization — OpenSearch](#6-database-optimization--opensearch)
7. [Database Optimization — ClickHouse](#7-database-optimization--clickhouse)
8. [Database Optimization — Redis](#8-database-optimization--redis)
9. [Database Optimization — MongoDB](#9-database-optimization--mongodb)
10. [Kafka Bottlenecks and Partition Strategy](#10-kafka-bottlenecks-and-partition-strategy)
11. [Caching Strategy](#11-caching-strategy)
12. [Backpressure, Retries, and Graceful Degradation](#12-backpressure-retries-and-graceful-degradation)
13. [Prometheus Metrics and Grafana Dashboards](#13-prometheus-metrics-and-grafana-dashboards)
14. [k6 Load-Test Phases](#14-k6-load-test-phases)
15. [Recommended Scaling Order](#15-recommended-scaling-order)
16. [Risks, Assumptions, and Trade-offs](#16-risks-assumptions-and-trade-offs)

---

## 1. Traffic Shape Assumptions

| Metric | Estimate | Rationale |
|---|---|---|
| Registered users | 1 000 000 | Target |
| DAU (10 % of registered) | 100 000 | Industry norm for streaming apps |
| Peak concurrent users | 20 000 | 20 % of DAU at the same time |
| Avg. songs streamed per session | 10 | ~30 min session × 3 min/song |
| Playback events per second (peak) | ~40 000 | 20 K users × 2 events/song average |
| Auth logins per second (peak) | ~500 | Session start, 1 h token refresh |
| Catalog/search requests per second | ~4 000 | ~20 % of active users browsing at once |
| Playlist mutations per second | ~200 | Lower-frequency write operation |

The **streaming-service** and **search-service** face the highest steady-state request rates.
**auth-service** faces short bursts at login time. **analytics-service** and
**recommendation-service** are paced by Kafka consumers, not user HTTP traffic.

---

## 2. Load Balancer and Reverse Proxy Layer

### Why a dedicated LB is required

`docker compose up --scale streaming-service=8` creates eight containers all named
`streaming-service` (internally `streaming-service-1` … `streaming-service-8`). Docker's
embedded DNS resolver (`127.0.0.11`) returns all IPs for the service name, but only if the
client **re-queries DNS for every connection**. Without a dedicated load balancer, any proxy that resolves a service hostname once at startup will keep routing to the original single container after scaling.

### Recommended approach: dedicated nginx LB container

Add an `nginx-lb` service to `docker-compose.yml` that:

- Listens on host port **80** (replacing the individual `8081`–`8088` host port mappings on
  application services once it is in place).
- Defines one `upstream` block per scalable service using Docker's internal hostname.
- Sets `resolver 127.0.0.11 valid=10s` so that nginx re-resolves DNS every 10 seconds,
  picking up newly started replicas automatically.
- Uses **least-connections** (`least_conn`) load-balancing for streaming and
  recommendation (long-lived or variable-cost requests) and default round-robin for auth,
  catalog, search, playlist, and notification (short-lived, similar cost).
- Enforces per-IP rate limits (`limit_req_zone`) to prevent a single k6 worker or misbehaving
  client from saturating one service.

```nginx
# ILLUSTRATIVE — not final config
upstream streaming_backend {
    least_conn;
    server streaming-service:8080;
    keepalive 64;
}

server {
    listen 80;
    resolver 127.0.0.11 valid=10s;

    location /api/stream/ {
        proxy_pass http://streaming_backend/;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
}
```

### What the LB does NOT need to do

- TLS termination — out of scope for a Docker benchmarking environment.
- Auth header injection — each service validates JWTs independently.
- Service discovery — Docker DNS is sufficient inside `music-net`.

---

## 3. Horizontal vs Vertical Scaling per Service

### Services that scale horizontally (stateless application layer)

| Service | Why stateless | Complication | Resolution |
|---|---|---|---|
| **streaming-service** | No database; produces Kafka events; HLS manifest is computed per-request | Kafka partitions must exist before multiple producers contend | Increase default partition count on `playback-events`; producers are independent |
| **auth-service** | JWT verification uses RSA public key from the shared `jwt-keys` volume (read-only after first boot) | Key generation race if multiple replicas start simultaneously for the first time | Code must check file existence before writing; only one replica writes keys |
| **catalog-service** | Read-heavy pagination over `catalog-db`; no local state | Connection pool pressure: N replicas × 10 HikariCP connections = N×10 DB connections | PgBouncer in front of `catalog-db` |
| **search-service** | Reads from OpenSearch; no local state | All replicas hit a single-node OpenSearch; heap pressure grows linearly | Expand OpenSearch heap, then add a second OpenSearch node |
| **playlist-service** | CRUD against `playlist-db`; Kafka producer is idempotent per request | Same connection pool pressure as catalog | PgBouncer in front of `playlist-db` |
| **notification-service** | Kafka consumer; writes to MongoDB | Consumer parallelism is capped by Kafka partition count for `playlist-events` | Pre-create `playlist-events` with 6 partitions |
| **analytics-service** | Kafka consumer; batch-writes to ClickHouse | Consumer parallelism capped by `playback-events` partition count; ClickHouse hates single-row inserts | Pre-create `playback-events` with 12 partitions; batch inserts in consumer |
| **recommendation-service** | Kafka consumer; Redis shared cache | Cache stampede possible when multiple replicas compute the same recommendation simultaneously; partition cap | Redis `SETNX`-style lock or accept low-probability double-compute; 6 partitions on `playback-events` |

### Services that scale mostly vertically (stateful infrastructure)

These are infrastructure containers. Simply running `--scale` on them either requires
unique config per node (Kafka broker ID) or produces split-brain data (two separate PostgreSQL
primaries with the same volume). They must be scaled carefully with explicit service definitions.

| Service | Scaling approach | Note |
|---|---|---|
| `auth-db`, `catalog-db`, `playlist-db`, `recommendation-db` | Vertical (more RAM + shared_buffers); optional read replica as a named separate service | Each additional replica is a new compose service with its own volume; app must be configured to route reads to the replica URL |
| `opensearch` | Add `opensearch-node2` as a separate named service in compose; same `cluster.name`, cross-linked seed hosts | Must set `number_of_replicas=1` after adding second node |
| `clickhouse` | Vertical (CPU + memory limits); write batching in analytics-service reduces pressure more than adding nodes | Second node possible via `ReplicatedMergeTree` + ClickHouse Keeper, but complex in compose |
| `redis` | Vertical (increase `maxmemory`); Redis Cluster in compose requires 6 named nodes (3 primaries + 3 replicas) | For benchmarking, single-node Redis with tuned `maxmemory` and LRU eviction is sufficient up to ~500 K DAU |
| `mongodb` | Vertical (RAM + indexes); for HA add a named `mongodb-rs` replica set with 3 named nodes | Notification writes are low-frequency; single node is last to bottleneck |
| `kafka` | Add `kafka2`, `kafka3` services with different `KAFKA_BROKER_ID`; increase `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3` | Cannot use `--scale kafka=3` because each broker needs unique identity |
| `zookeeper` | Single node is sufficient for 3 Kafka brokers in a benchmark environment | Production would want 3 zookeeper nodes |

---

## 4. Recommended Replica Counts

Based on the traffic shape in Section 1, with a 3-broker Kafka cluster and PgBouncer in place:

| Service | Replicas | Primary driver |
|---|---|---|
| `streaming-service` | **8–10** | Highest request rate; stateless; CPU-bound per HLS segment generation |
| `auth-service` | **4–6** | BCrypt (~100 ms/verify) and RS256 JWT signing are expensive; bursts at login time |
| `search-service` | **4–6** | 4 K req/s browsing; each query blocks on OpenSearch round-trip |
| `catalog-service` | **4** | Moderate browsing load; IO-bound on `catalog-db` |
| `analytics-service` | **4** (≤ partition count) | Kafka consumer; scale up to `playback-events` partition count |
| `recommendation-service` | **3** (≤ partition count) | Redis hides most latency; scale conservatively |
| `playlist-service` | **3** | Lower write frequency; connection pool pressure manageable |
| `notification-service` | **2** (≤ partition count) | `playlist-events` volume is low; 2 is sufficient |

> **Hard constraint for Kafka consumers:** `analytics-service`, `recommendation-service`, and
> `notification-service` each form a consumer group. Adding a replica beyond the partition
> count of their consumed topic results in an idle container — it is assigned no partitions.
> Always set partition count ≥ intended replica count before scaling.

---

## 5. Database Optimization — PostgreSQL

### auth-db

**Hotspot:** `SELECT * FROM users WHERE username = ?` (login) and
`SELECT * FROM users WHERE email = ?` (registration uniqueness check).

**Index strategy:**
```sql
-- REQUIRED — add to Flyway migration if not already present
CREATE UNIQUE INDEX idx_users_username ON users(username);
CREATE UNIQUE INDEX idx_users_email    ON users(email);
```

**Write pattern:** Low write rate (registration only). No read replica needed at 1 M users;
vertical scaling (increase `shared_buffers` to 256 MB, `work_mem` to 16 MB) is sufficient.

**Connection pooling:** With 4–6 `auth-service` replicas × 10 HikariCP connections = 40–60
connections. PostgreSQL default `max_connections=100` can handle this, but adding PgBouncer
in transaction mode (pool size = 20) reduces overhead significantly.

---

### catalog-db

**Hotspot:** Paginated song browsing — the catalog-service currently uses Spring Data JPA
`findAll(Pageable)` which translates to `SELECT ... LIMIT ? OFFSET ?`. At `OFFSET 10000`,
PostgreSQL must scan and discard 10 000 rows before returning results.

**Index strategy:**
```sql
-- Support ORDER BY title, genre, artist (common sort keys)
CREATE INDEX idx_songs_title  ON songs(title);
CREATE INDEX idx_songs_genre  ON songs(genre);
CREATE INDEX idx_songs_artist ON songs(artist);

-- Composite for filtered browsing (genre filter + title sort)
CREATE INDEX idx_songs_genre_title ON songs(genre, title);
```

**Pagination fix:** Replace `OFFSET`-based pagination with keyset (cursor) pagination:
```sql
-- Instead of: SELECT * FROM songs ORDER BY id LIMIT 50 OFFSET 5000
-- Use:        SELECT * FROM songs WHERE id > :lastSeenId ORDER BY id LIMIT 50
```
This requires a monotonic cursor in the API response and a client that passes `?cursor=<lastId>`.
The current max page size is 100; keyset pagination eliminates the O(n) scan cost.

**Connection pooling:** With 4 replicas × 10 connections = 40 connections. Add PgBouncer
(transaction mode, pool size = 15) in front of `catalog-db`. The compose service listens on
port 5433 on the host; PgBouncer can use port 5433 externally and the real DB moves to an
internal-only port (e.g., 5433 internally).

**Read/write split:** The catalog is seeded from a CSV at startup and is largely read-only.
A named `catalog-db-replica` service (PostgreSQL streaming replication) allows
catalog-service replicas to route `SELECT` queries to the replica and writes (which are
rare post-seed) to the primary. The app needs a `secondaryDataSource` bean routing to the
replica URL.

---

### playlist-db

**Hotspot:** `SELECT * FROM playlists WHERE user_id = ?` (user's playlist list) and
`SELECT * FROM playlist_songs WHERE playlist_id = ? ORDER BY position` (song ordering).

**Index strategy:**
```sql
CREATE INDEX idx_playlists_user_id          ON playlists(user_id);
CREATE INDEX idx_playlist_songs_playlist_id ON playlist_songs(playlist_id, position);
```

**Connection pooling:** 3 replicas × 10 connections = 30. PgBouncer pool size = 10 is
sufficient.

---

### recommendation-db

**Hotspot:** Per-user play-history lookup used to compute similarity scores:
`SELECT song_id, played_at FROM play_history WHERE user_id = ? ORDER BY played_at DESC LIMIT 100`

**Index strategy:**
```sql
CREATE INDEX idx_play_history_user_played ON play_history(user_id, played_at DESC);
```

This composite index satisfies both the `WHERE user_id = ?` filter and the `ORDER BY played_at DESC`
sort in a single index scan, eliminating a filesort.

**Write pattern:** Every `playback-events` Kafka message causes one INSERT into `play_history`.
At 40 K events/second peak this is significant. Options:
1. Batch inserts in the Kafka consumer (accumulate 500 events, flush every 2 s).
2. Accept the write load on a vertically tuned PostgreSQL with `synchronous_commit = off`
   for the recommendation-db (acceptable data loss: a few seconds of history, not user-facing data).

---

## 6. Database Optimization — OpenSearch

### Current state

Single-node, `OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m`. Songs index is seeded with batch size
500 at startup. Queries use `match` on title/artist.

### Heap pressure

At 1 M songs and moderate query concurrency, 512 MB heap will trigger frequent GC pauses and
eventually OOM. Increase to at least **2 GB**, ideally **4 GB** (half the node's available RAM,
never exceed 31 GB to keep compressed object pointers active):

```yaml
# docker-compose.yml — opensearch service
environment:
  OPENSEARCH_JAVA_OPTS: "-Xms2g -Xmx2g"
```

### Shard strategy

Default single shard does not parallelize queries. For 1 M songs (~500 MB index):

```json
PUT /songs
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 0
  }
}
```

Set `number_of_replicas=0` for a single-node cluster (replicas cannot be placed on the same
node as the primary — OpenSearch will leave them `UNASSIGNED` and report `yellow` status).
Once `opensearch-node2` is added, raise to `number_of_replicas=1`.

### Adding a second node (compose sketch)

```yaml
# SKETCH — not final
opensearch-node2:
  image: opensearchproject/opensearch:2.13.0
  environment:
    - cluster.name=music-cluster
    - node.name=opensearch-node2
    - discovery.seed_hosts=opensearch,opensearch-node2
    - cluster.initial_cluster_manager_nodes=opensearch,opensearch-node2
    - OPENSEARCH_JAVA_OPTS=-Xms2g -Xmx2g
    - DISABLE_SECURITY_PLUGIN=true
  volumes:
    - opensearch-data2:/usr/share/opensearch/data
  networks:
    - music-net
```

`opensearch` (the original service) must also add
`discovery.seed_hosts=opensearch,opensearch-node2` and `cluster.initial_cluster_manager_nodes`.

### Query optimizations

- **Avoid deep pagination:** `from: 10000` forces OpenSearch to score all 10 000+ documents.
  Use `search_after` with a sort key (e.g., `_score`, `song_id`) instead.
- **Request cache:** Add `"request_cache": true` to the index settings. Frequently repeated
  queries (popular genre browsing) return from cache without hitting shards.
- **Field mapping:** Ensure `title` and `artist` are `text` (analyzed) with a `keyword`
  sub-field for exact-match and aggregation. Avoid `fielddata` on text fields.

---

## 7. Database Optimization — ClickHouse

### Critical: write batching in analytics-service

The `analytics-service` currently writes one row per Kafka message (individual `INSERT` over
JDBC). ClickHouse uses a `MergeTree` storage engine that creates a new *part* per insert.
Flushing tens of thousands of individual parts per second triggers constant background merges,
high CPU, and eventual insert rejections (`Too many parts`).

**Required change:** Accumulate events in the consumer and flush as a batch:

```java
// ILLUSTRATIVE — not final
private final List<PlaybackEvent> buffer = new ArrayList<>();

@KafkaListener(topics = "playback-events")
public void consume(PlaybackEvent event) {
    buffer.add(event);
    if (buffer.size() >= 1000) flush();
}

@Scheduled(fixedDelay = 5000)
public void flushPeriodically() { flush(); }

private synchronized void flush() {
    if (buffer.isEmpty()) return;
    clickHouseRepository.insertBatch(buffer);
    buffer.clear();
}
```

Target: batches of **500–1000 events** or a **5-second timer**, whichever fires first.

### Table design

```sql
-- ILLUSTRATIVE — confirm against actual schema
CREATE TABLE playback_events (
    event_time  DateTime,
    user_id     String,
    song_id     String,
    event_type  LowCardinality(String),
    duration_ms UInt32
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, song_id, event_time);
```

- **Partition by month:** Each month becomes one partition set. Dropping old data is `ALTER TABLE
  DROP PARTITION '202401'` — instant, no heavy DELETE scans.
- **Sort/primary key `(user_id, song_id, event_time)`:** Per-user analytics queries skip
  unrelated granules instantly.
- **`LowCardinality(String)` for event_type:** Values like `play.started`, `play.ended`,
  `play.skipped` are repeated billions of times. This type uses dictionary encoding and
  compresses them to ~1–2 bytes each.

### Resource limits

ClickHouse has no `deploy.resources.limits` in the current `docker-compose.yml`. Without
limits, it can consume all host RAM under a write storm. Add:

```yaml
deploy:
  resources:
    limits:
      cpus: "2.0"
      memory: 4g
    reservations:
      memory: 2g
```

---

## 8. Database Optimization — Redis

### Current state

Single node, `redis:7.2-alpine` with `redis-data` volume (RDB + AOF persistence).
`recommendation-service` stores `daily-mix:{userId}` and `similar:{songId}` keys with 3600 s TTL.

### Memory sizing

At 100 K DAU with 1 KB per recommendation entry:
- `daily-mix:{userId}`: 100 K keys × 1 KB = ~100 MB
- `similar:{songId}`: ~50 K unique songs × 1 KB = ~50 MB
- Total: ~150–200 MB in steady state (keys expire after 1 h)

Set an explicit memory ceiling and eviction policy:

```
# redis.conf (or command-line args)
maxmemory 512mb
maxmemory-policy allkeys-lru
```

`allkeys-lru` evicts the least-recently-used keys when memory is full, which aligns perfectly
with the TTL-based recommendation workload.

### AOF performance

`appendfsync always` writes every command to disk synchronously. This adds 1–2 ms latency per
write. Change to `appendfsync everysec` (flush every second) — acceptable data loss of 1
second of cache writes, which is tolerable for a recommendation cache.

### Cache stampede mitigation

With 3 `recommendation-service` replicas, two workers can simultaneously compute the same
user's recommendation when the key expires (cache miss race). Mitigate with a short lock:

```java
// ILLUSTRATIVE — not final
String lockKey = "lock:daily-mix:" + userId;
Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
if (Boolean.TRUE.equals(locked)) {
    // compute and cache
} else {
    // brief wait, then re-read from cache
    Thread.sleep(200);
    return redis.opsForValue().get("daily-mix:" + userId);
}
```

---

## 9. Database Optimization — MongoDB

### Current state

Single node, `mongo:7.0`, `auto-index-creation: true` (Spring Data).

### Write pattern

`notification-service` inserts one document per `playlist-events` Kafka message. Playlist
events are low-frequency (~200/s peak). Single-node MongoDB with default WiredTiger cache
(50 % of available RAM) handles this comfortably.

### Index strategy

Spring Data's `auto-index-creation` creates indexes for `@Indexed` annotations in entity
classes. Verify these indexes exist (critical queries):

```javascript
// In mongosh — verify index on notification collection
db.notifications.getIndexes()
// Must include: { userId: 1 } and { playlistId: 1, createdAt: -1 }
```

If not present, add explicit `@Indexed` annotations in the notification entity class.

### Scaling considerations

MongoDB is the last bottleneck in this system. Notification writes are infrequent and
documents are small. Vertical scaling (increase WiredTiger cache via `--wiredTigerCacheSizeGB`)
is sufficient to 1 M users. A replica set (for HA) requires three named compose services
(`mongodb`, `mongodb-rs1`, `mongodb-rs2`) — justified only for fault tolerance, not throughput.

---

## 10. Kafka Bottlenecks and Partition Strategy

### Critical issue: 1-partition default

With `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` and no explicit partition configuration, Kafka
creates topics with 1 partition. A topic with 1 partition is consumed by exactly **1 consumer
instance** per consumer group — regardless of how many replicas of analytics-service or
recommendation-service are running. Extra replicas sit idle, assigned to no partition.

### Required: pre-create topics with adequate partitions

Create topics before any service starts (via an `init-kafka` container or a startup script):

```bash
# SKETCH — run once as part of compose startup
kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic playback-events \
  --partitions 12 \
  --replication-factor 3

kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic playlist-events \
  --partitions 6 \
  --replication-factor 3
```

**Partition count rationale:**
- `playback-events` → 12 partitions: consumed by `analytics-service` (4 replicas, 4 partitions
  each) and `recommendation-service` (3 replicas; each group gets 12 partitions among 3
  workers = 4 partitions per worker).
- `playlist-events` → 6 partitions: consumed by `notification-service` (2 replicas = 3
  partitions per worker). Headroom for scaling to 6 replicas.

### Single broker vs 3-broker cluster

The current single-broker setup (replication factor 1) means a broker restart causes consumer
lag to accumulate and any in-flight messages are at risk. For a benchmark that requires
sustained throughput:

Define `kafka`, `kafka2`, `kafka3` as three separate compose services (each with a unique
`KAFKA_BROKER_ID` and different `KAFKA_ADVERTISED_LISTENERS` port). Set:

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
KAFKA_DEFAULT_REPLICATION_FACTOR: 3
```

> **Cannot use `--scale kafka=3`** because each broker requires a unique `KAFKA_BROKER_ID`
> (`1`, `2`, `3`) and unique advertised listener ports. These must be hardcoded per service
> definition.

### Consumer lag as a scaling signal

Consumer lag on `playback-events` is the primary indicator that analytics or recommendation
capacity needs to increase. The `kafka-exporter` Prometheus exporter exposes:

```
kafka_consumergroup_lag{topic="playback-events", consumergroup="analytics-service"}
kafka_consumergroup_lag{topic="playback-events", consumergroup="recommendation-service"}
```

**Alert threshold:** lag > 10 000 messages sustained for 60 s → add consumer replica
(up to partition count limit).

### Producer configuration

Both `streaming-service` and `playlist-service` use `acks=all`. This ensures messages are
replicated before acknowledgement. Under a 3-broker cluster with replication factor 3, this
is correct and does not need change.

Add explicit retry configuration in producer properties:

```yaml
spring.kafka.producer.retries: 3
spring.kafka.producer.properties.retry.backoff.ms: 1000
spring.kafka.producer.properties.max.in.flight.requests.per.connection: 1
```

`max.in.flight.requests.per.connection=1` prevents out-of-order delivery on retry, which
matters for analytics ordering.

---

## 11. Caching Strategy

### Currently cached

Only `recommendation-service` uses Redis. No other service caches anything.

### Recommended additions

**catalog-service — popular song pages:**
The first 3–5 pages of any genre (e.g., `GET /songs?genre=rock&page=0`) are requested by a
large fraction of active users. These pages are stable (catalog rarely changes). Cache them
in Redis with a TTL of 5 minutes:

```
Key:   catalog:genre:{genre}:page:{page}:size:{size}
TTL:   300s
Value: JSON array of song summaries
```

This avoids repeated `SELECT ... WHERE genre = ? ORDER BY title LIMIT 50 OFFSET 0` calls that
hit catalog-db on every browse session.

**search-service — repeated queries:**
Top search terms (e.g., "Taylor Swift", "Beethoven") are queried millions of times per day.
OpenSearch has a built-in **request cache** for the entire response of identical queries — but
it is only triggered for queries that produce the same shard-level request. Additionally, the
`search-service` can cache the serialized JSON response in Redis:

```
Key:   search:q:{normalized_query}:page:{page}
TTL:   60s
Value: JSON search result
```

Cache normalization: lowercase and trim the query string before computing the key.

**auth-service — token introspection:**
If other services validate JWT tokens by calling `auth-service` directly (rather than
verifying the RSA signature locally), that creates N×M cross-service calls. The correct
pattern is for each service to verify the JWT locally using the RSA public key from the
`jwt-keys` volume. If the current implementation already does this, no change is needed. If
any service makes an HTTP call to `auth-service` to validate tokens, replace it with local
RSA verification.

### What NOT to cache

- Playlist contents (`playlist-service`): personalized per user, high mutation rate.
  Cache here introduces staleness bugs; the DB write load is manageable.
- Notification state (`notification-service`): documents are write-once, rarely read via
  the service API; MongoDB query cost is low.
- ClickHouse analytics results: ClickHouse has its own result cache. Do not add a Redis layer
  in front of it.

---

## 12. Backpressure, Retries, and Graceful Degradation

### Current resilience gaps

| Service | Gap |
|---|---|
| `streaming-service` | Kafka producer failures are logged but not retried at application level |
| `recommendation-service` | Redis unavailability falls back to compute (correct) but no circuit breaker on DB calls |
| `analytics-service` | Single-row inserts to ClickHouse; no batching; slow ClickHouse = slow consumer = lag build-up |
| `notification-service` | No retry on MongoDB insert failure |
| All services | No circuit breakers; a slow dependency causes thread pool exhaustion via blocking waits |

### Kafka producer retries

Already covered in Section 10. The key addition: `streaming-service` is a critical producer —
if its send callback reports failure after retries, log at `ERROR` level with the full event
payload (so the event can be replayed from logs) and increment a `playback_event_drop_total`
Prometheus counter.

### Circuit breakers (Resilience4j)

Add Resilience4j to `recommendation-service` for the Redis and PostgreSQL call paths:

```java
// ILLUSTRATIVE
@CircuitBreaker(name = "redis", fallbackMethod = "computeDirectly")
public List<Song> getRecommendations(String userId) {
    return redisCache.get("daily-mix:" + userId);
}

public List<Song> computeDirectly(String userId, Exception e) {
    return similarityEngine.compute(userId);  // bypass Redis
}
```

This prevents a Redis outage from blocking all recommendation threads. The fallback computes
directly from PostgreSQL.

For `catalog-service` and `playlist-service`, wrap the JPA repository calls in circuit
breakers that return HTTP 503 with `Retry-After: 5` when the DB is saturated, rather than
queuing requests indefinitely (which exhausts Tomcat's thread pool).

### Rate limiting at nginx LB

Add token-bucket rate limits in the `nginx-lb` configuration:

```nginx
# ILLUSTRATIVE
limit_req_zone $binary_remote_addr zone=streaming:10m rate=50r/s;
limit_req_zone $binary_remote_addr zone=auth:10m       rate=10r/s;

location /api/stream/ {
    limit_req zone=streaming burst=200 nodelay;
}
location /api/auth/ {
    limit_req zone=auth burst=20 nodelay;
}
```

This protects `auth-service` from brute-force and `streaming-service` from runaway k6 workers
consuming all capacity.

### Graceful degradation under ClickHouse pressure

If `analytics-service` consumers are lagging significantly (Kafka lag > 50 K events), the
correct response is to increase batch size and reduce flush frequency — not to restart the
service. Implement a dynamic batch size based on the measured lag:

```java
// ILLUSTRATIVE
int batchSize = lag > 50_000 ? 5000 : 1000;
```

The notification and recommendation services should implement a dead-letter pattern: after 3
failed processing attempts of the same Kafka message, route the message to a
`playback-events-dlq` topic and continue. Without this, a single malformed event can stall the
entire consumer.

---

## 13. Prometheus Metrics and Grafana Dashboards

### Additional exporters required

The current `prometheus.yml` only scrapes Spring Boot actuator endpoints. Add:

| Exporter | Target | Why |
|---|---|---|
| `prom/postgres-exporter` | One instance per PostgreSQL DB (4 total) | Connection pool saturation, sequential scans, lock waits |
| `oliver006/redis_exporter` | `redis:6379` | Cache hit rate, memory usage, connected clients |
| `danielqsj/kafka-exporter` | `kafka:9092` | Consumer group lag (the most critical Kafka metric) |
| `percona/mongodb_exporter` | `mongodb:27017` | Collection scan rate, connection count |

ClickHouse exposes a native Prometheus endpoint at `:9363/metrics` — add it directly to
`prometheus.yml` without a separate exporter.

OpenSearch exposes metrics via the `_prometheus/metrics` endpoint with the
`opensearch-prometheus-exporter` plugin (or via `opensearch_exporter`).

### Scaling-decision dashboards in Grafana

Create (or extend `overview.json` with) the following panels:

#### Panel group: Service Health

```
http_server_requests_seconds_count{uri!~".*actuator.*"}  → Request rate per service
http_server_requests_seconds_max{quantile="0.99"}        → p99 latency per service
process_cpu_usage                                         → CPU per replica
jvm_memory_used_bytes / jvm_memory_max_bytes             → JVM heap pressure
```

#### Panel group: Database Connection Pools

```
hikaricp_connections_active / hikaricp_connections_max   → Pool saturation ratio (alert at 0.8)
pg_stat_activity_count{state="active"}                   → Active DB queries per PG instance
pg_stat_user_tables_seq_scan                             → Sequential scans (index missing → alert)
```

#### Panel group: Kafka

```
kafka_consumergroup_lag{topic="playback-events", consumergroup="analytics-service"}
kafka_consumergroup_lag{topic="playback-events", consumergroup="recommendation-service"}
kafka_consumergroup_lag{topic="playlist-events", consumergroup="notification-service"}
kafka_topic_partition_current_offset - kafka_topic_partition_oldest_offset  → Topic depth
```

#### Panel group: Caches

```
redis_keyspace_hits_total / (redis_keyspace_hits_total + redis_keyspace_misses_total)  → Hit rate
redis_used_memory_bytes                                  → Memory usage vs maxmemory
```

#### Panel group: Infrastructure

```
opensearch_jvm_mem_heap_used_in_bytes / opensearch_jvm_mem_heap_max_in_bytes  → OS heap
clickhouse_query_duration_ms_quantiles{quantile="0.99"}  → ClickHouse query latency
```

### Scaling thresholds (use as Grafana alert rules)

| Metric | Threshold | Action |
|---|---|---|
| `process_cpu_usage` > 0.70 for 5 min | Service CPU saturated | Add replica (if stateless) |
| `hikaricp_connections_active / max` > 0.80 | Pool exhaustion imminent | Add PgBouncer pool capacity |
| `kafka_consumergroup_lag` > 10 000 for 60 s | Consumer falling behind | Add consumer replica (≤ partition count) |
| OpenSearch heap > 75 % | GC pressure | Increase `OPENSEARCH_JAVA_OPTS` heap |
| `redis_used_memory_bytes` > 80 % of `maxmemory` | Cache near full | Increase `maxmemory` or reduce TTLs |
| `http_server_requests_seconds_max{quantile="0.99"}` > 2 s | Latency SLO breach | Investigate per-service bottleneck |

---

## 14. k6 Load-Test Phases

The current `load-generator/scripts/main.js` is Phase 0 (health probe only). Build the
following phases in order. Do not skip to a higher-VU phase before the previous phase is
stable and its p99 latency is under 500 ms.

### Phase 1 — Smoke test (baseline, pre-scaling)

```javascript
// Target: verify basic functionality with minimal load BEFORE any scaling changes
export const options = { vus: 5, duration: '2m' };
// Flow: register → login → get catalog page 0 → logout
// Services hit: auth-service, catalog-service
// Goal: establish p50/p99 baseline; confirm no errors; measure DB connection usage
```

Run this on the **single-instance** deployment. Record the results. These are your baseline
numbers to compare against after scaling.

### Phase 2 — Core streaming flow

```javascript
export const options = { vus: 50, duration: '5m' };
// Flow: login → search("rock") → get song details → GET /stream/{songId}/manifest
//       → GET /stream/{songId}/segments/0..2 → POST playback event
// Services hit: auth, search, catalog, streaming
// Watch: streaming-service CPU, Kafka producer send rate, playback-events consumer lag
```

### Phase 3 — Kafka pipeline validation

```javascript
export const options = { vus: 200, duration: '10m' };
// Flow: Phase 2 flow + create playlist → add song → modify playlist
// Watch: analytics consumer lag, recommendation cache hit rate (Grafana),
//        notification-service MongoDB insert rate
// Goal: confirm end-to-end Kafka pipeline handles 200 concurrent users without lag
```

### Phase 4 — Scaling ramp (with replicas in place)

```javascript
export const options = {
  stages: [
    { duration: '5m', target: 1000 },
    { duration: '10m', target: 1000 },
    { duration: '5m', target: 0 },
  ]
};
// Full user flow with 1–2 s think-time between steps
// Run AFTER adding nginx-lb, PgBouncer, 12-partition Kafka topics, and service replicas
// Watch: which service's p99 latency climbs first — that is the next bottleneck
```

### Phase 5 — Peak load

```javascript
export const options = {
  stages: [
    { duration: '10m', target: 10000 },
    { duration: '10m', target: 10000 },
    { duration: '10m', target: 0 },
  ]
};
// Target: simulate 20 K concurrent sessions (10 K in k6, assume 2× via steady-state overlap)
// Watch: Kafka lag, DB connection pool saturation (alert if > 80 %), streaming p99 > 1 s
// Expected bottleneck at this stage: catalog-db connections, OpenSearch heap
```

### Phase 6 — Soak test

```javascript
export const options = { vus: 2000, duration: '2h' };
// Goal: memory leaks, ClickHouse part accumulation (watch for "Too many parts" errors),
//       Redis memory creep (keys that are not expiring), Kafka partition rebalance events
// Check: JVM heap after 2 h should be similar to after 5 min; GC pause rate stable
```

### Test ordering principle

Run phases in order (1 → 6). Only add replicas or tune infrastructure **between** phases,
guided by Grafana metrics from the previous phase. Optimizing services that did not bottleneck
in Phase 2 before running Phase 3 wastes time and produces misleading benchmark results.

---

## 15. Recommended Scaling Order

Sequence the work so that each step unblocks the next and you do not optimize low-impact
services first.

| Step | Action | Prerequisite | Impact |
|---|---|---|---|
| 1 | Pre-create Kafka topics with 12/6 partitions | Kafka healthy | Unlocks all consumer horizontal scaling; without this, steps 6–8 are ineffective |
| 2 | Add `nginx-lb` service to `docker-compose.yml` | None | Enables traffic distribution across all future replicas |
| 3 | Add PostgreSQL indexes to all 4 DB instances (via Flyway migrations) | Databases running | Reduces query cost before adding load |
| 4 | Add PgBouncer in front of `catalog-db` and `playlist-db` | PostgreSQL running | Prevents connection exhaustion when catalog and playlist replicas are added |
| 5 | Scale `streaming-service` to 8 replicas | nginx-lb in place | Highest ROI: handles the majority of user traffic |
| 6 | Scale `auth-service` to 4 replicas | nginx-lb, jwt-keys volume populated | Second-highest traffic; BCrypt is expensive per login |
| 7 | Scale `search-service` to 4–6 replicas; increase OpenSearch heap to 2 GB | OpenSearch health check passing | Unblocks browsing throughput |
| 8 | Scale `catalog-service` to 4 replicas | PgBouncer for catalog-db | Moderate impact; browsing is shared with search |
| 9 | Expand Kafka to 3 brokers | Zookeeper stable | Required before Step 10 for fault tolerance and replication factor 3 |
| 10 | Scale `analytics-service` to 4 replicas; implement batch inserts to ClickHouse | 12 partitions on playback-events, 3-broker Kafka | Prevents Kafka lag from growing under Phase 4/5 load |
| 11 | Scale `recommendation-service` to 3 replicas | 12 partitions on playback-events | Lag reduction; cache hit rate improvement |
| 12 | Scale `playlist-service` to 3 replicas | PgBouncer for playlist-db | Lower-frequency writes; noticeable only above 5 K concurrent users |
| 13 | Scale `notification-service` to 2 replicas | 6 partitions on playlist-events | Lowest traffic; scale last |
| 14 | Add `opensearch-node2`; set `number_of_replicas=1` | Step 7 complete | Required once search-service replicas saturate single-node OpenSearch |
| 15 | Add Redis `maxmemory` and LRU policy; consider catalog and search result caching | Redis healthy | Reduces DB round-trips for repeated popular queries |

---

## 16. Risks, Assumptions, and Trade-offs

### Risk 1 — JWT key generation race condition

**Risk:** If multiple `auth-service` replicas start simultaneously on a fresh environment (no
pre-populated `jwt-keys` volume), all replicas may attempt to write RSA key files at the same
time. The last writer wins, but replicas that already loaded different keys will fail to verify
tokens signed by others.

**Mitigation:** The key generation code must use an atomic file-existence check (`Files.exists`
before writing). Ensure `docker compose up --scale auth-service=4` is preceded by a single
`auth-service` instance completing startup (and writing keys) before replicas are added.

### Risk 2 — Kafka partition count is immutable after write

**Risk:** Kafka partitions can be increased (via `--alter`) but not decreased. Increasing
partitions on an existing topic does not redistribute existing messages — only new messages go
to the new partitions. Decreasing would require recreating the topic (message loss).

**Mitigation:** Pre-create topics with target partition counts (12 and 6) before any service
writes to them. Do this as a compose `init-kafka` container that runs and exits before
dependent services start.

### Risk 3 — Docker DNS and nginx upstream caching

**Risk:** nginx resolves upstream hostnames once at config load time by default. After
`docker compose up --scale streaming-service=8`, nginx does not automatically discover new
replicas unless configured with `resolver 127.0.0.11 valid=10s` and a dynamic upstream.

**Mitigation:** Mandatory in the `nginx-lb` config. Without this, all 8 streaming replicas
receive traffic only from k6 (which connects directly to each container's port) but not from
the nginx proxy path. Tests would appear to scale but real user traffic would not.

### Risk 4 — Host memory exhaustion

**Risk:** With 8 streaming replicas (8 × 512 MB), 4 catalog replicas (4 × 512 MB), OpenSearch
(2 GB JVM + OS), ClickHouse (4 GB limit), 4 PostgreSQL instances (~256 MB each), Redis
(512 MB), MongoDB (512 MB), Kafka × 3 (1 GB JVM each), Prometheus + Grafana (512 MB each),
the total Docker memory ceiling approaches **22–24 GB**.

**Mitigation:** Run the full scaled deployment on a host with at least **32 GB RAM**. Set
explicit `deploy.resources.limits` on all infrastructure containers (Kafka, Zookeeper,
ClickHouse, MongoDB currently have none).

### Risk 5 — ClickHouse "Too many parts" under individual inserts

**Risk:** Without batching in `analytics-service`, ClickHouse will create one part per Kafka
message. At 40 K events/s this produces 40 K parts/s. ClickHouse will log
`Too many parts (N). Merges are processing significantly slower than inserts` and eventually
throttle or reject inserts.

**Mitigation:** Batch inserts (Step 10 in scaling order) must be implemented **before**
running Phase 4/5 load tests. This is the highest-priority code change in the analytics
pipeline.

### Risk 6 — Single-broker Kafka message loss during restart

**Assumption:** For benchmarking purposes, a single Kafka broker restart causing temporary
message loss or consumer re-connection delay is acceptable. The 3-broker expansion (Step 9
in scaling order) is recommended before final throughput tests but is not critical for Phase 1–3
load tests.

**Trade-off:** A 3-broker Kafka cluster with replication factor 3 requires defining three
separate compose services (`kafka`, `kafka2`, `kafka3`), each with unique `BROKER_ID` and
listener ports. This adds operational complexity but is necessary for accurate 1 M user
throughput benchmarking.

### Risk 7 — `--scale` and stateful containers share a volume

**Risk:** Using `docker compose up --scale catalog-db=2` would create two PostgreSQL
containers both trying to use the same `catalog-db-data` volume. The second container will
fail to start because PostgreSQL locks its data directory.

**Mitigation:** Never use `--scale` on database services. Any read replica must be defined
as a separate named service with its own volume (e.g., `catalog-db-replica`) and configured
with `primary_conninfo` pointing to the primary.

### Assumption: JVM startup time

All Spring Boot services have a 60 s `start_period` in their health checks. When scaling from
1 to 8 replicas of `streaming-service`, all 8 containers start simultaneously. With a 4 CPU
host, 8 concurrent JVM startups will contend for CPU. Allow 90–120 s for all replicas to
pass health checks before directing load at them.

### Trade-off: Redis single-node vs Redis Cluster

Redis Cluster in Docker requires 6 named nodes (3 primaries, 3 replicas) with cluster-bus
ports, node meet commands, and slot allocation. For the recommendation cache workload (single
hash key per user, TTL-based expiry, ~200 MB total), the operational cost of a Redis Cluster
is not justified. A single node with `maxmemory 512mb` and LRU eviction handles up to ~500 K
DAU. Plan for Redis Cluster only if total cache footprint exceeds 80 % of single-node
`maxmemory` under sustained load.

---

## 17. Implementation Status

The first scaling implementation (2026-05-21) delivers the following changes to the repository.
Each item references the affected file so the rationale can be traced directly to the code.

### Implemented

| Area | Change | File(s) |
|---|---|---|
| **Kafka partitions** | `init-kafka` one-shot service creates `playback-events` (12 partitions) and `playlist-events` (6 partitions) before any producer/consumer starts. `KAFKA_AUTO_CREATE_TOPICS_ENABLE` disabled to prevent accidental 1-partition creation. | `docker-compose.yml` |
| **nginx-lb** | Dedicated nginx load balancer on port 80. Uses `resolver 127.0.0.11 valid=5s` + `set $upstream` variables so new replicas are discovered within 5 s without an nginx reload. Per-path rate limiting (auth: 20 r/s, streaming: 200 r/s, API: 500 r/s). | `infra/nginx-lb/nginx.conf`, `docker-compose.yml` |
| **Service replicas** | `deploy.replicas` set per service: streaming 3, catalog 2, search 2, playlist 2, analytics 2, recommendation 2, notification 1, auth 1 (JWT key race constraint). All configurable via `.env`. | `docker-compose.yml`, `.env.example` |
| **container_name removed** | All 8 application services lose `container_name`, enabling `docker compose up --scale <service>=N`. Auth-service retains its name because it must stay at 1 on fresh volumes. | `docker-compose.yml` |
| **Service host ports removed** | Application services no longer bind host ports (8081–8088). All API traffic enters on port 80 via nginx-lb. Databases and observability keep host ports for direct debugging. | `docker-compose.yml` |
| **JWT key dependency** | All services that read `jwt-keys:ro` now depend on `auth-service: service_healthy`, guaranteeing RSA key files exist before consumers try to load them. | `docker-compose.yml` |
| **OpenSearch heap** | Increased from 512 m to 1 g (`OPENSEARCH_JAVA_OPTS`). Tunable via env var. | `docker-compose.yml`, `.env.example` |
| **Redis tuning** | Added `--maxmemory 512mb --maxmemory-policy allkeys-lru --appendfsync everysec`. Explicit memory ceiling prevents OOM; LRU eviction matches TTL-based recommendation cache; everysec reduces blocking write latency from 1–2 ms to near-zero. | `docker-compose.yml`, `.env.example` |
| **Resource limits** | All previously uncapped infrastructure (Kafka, Zookeeper, ClickHouse, Redis, MongoDB, OpenSearch, nginx-lb) now has explicit `deploy.resources.limits`. | `docker-compose.yml` |
| **PostgreSQL tuning** | Each PostgreSQL instance uses `command: postgres -c shared_buffers=... -c work_mem=...` tuned per workload. catalog-db gets 512 MB shared_buffers (largest read dataset). | `docker-compose.yml` |
| **HikariCP tuning** | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20` and `MINIMUM_IDLE=5` set via env on all DB-connected services. Pool per replica × replicas stays below PostgreSQL max_connections=200. | `docker-compose.yml` |
| **ClickHouse batch insert** | `BatchEventBuffer` accumulates Kafka-consumed events and flushes them to ClickHouse via `JdbcTemplate.batchUpdate()` in batches of 500 or every 5 seconds. Eliminates the "one part per insert" anti-pattern. | `BatchEventBuffer.java`, `PlaybackEventConsumer.java`, `AnalyticsRepository.java`, `AnalyticsService.java` |
| **`@EnableScheduling`** | Added to `AnalyticsServiceApplication` to activate the `@Scheduled` flush in `BatchEventBuffer`. | `AnalyticsServiceApplication.java` |
| **Prometheus exporters** | Added `postgres-exporter` (×4), `redis-exporter`, `kafka-exporter` as compose services. | `docker-compose.yml` |
| **Prometheus DNS-SD** | Scaled services (catalog, streaming, playlist, search, analytics, recommendation, notification) use `dns_sd_configs` so Prometheus auto-discovers replica pods. | `infra/prometheus/prometheus.yml` |
| **Grafana scaling dashboard** | New `scaling.json` dashboard with 19 panels covering: request rate, error rate, latency, JVM heap, CPU, HikariCP saturation, PostgreSQL connections/sequential scans, Kafka consumer lag per group, Redis hit rate/memory, OpenSearch heap. | `infra/grafana/dashboards/scaling.json` |
| **k6 multi-scenario script** | Full user journey implemented: register → login → browse → search → stream (manifest + segment + complete event) → playlist → recommendations → analytics → notifications. Six selectable scenarios via `K6_SCENARIO` env var. `NGINX_LB_URL` routes all load through nginx-lb. | `load-generator/scripts/main.js` |
| **`.env.example`** | Updated with all new variables: replica counts, partition counts, pool sizes, batch tuning, Redis maxmemory, OpenSearch heap. | `.env.example` |

### Not yet implemented (deferred)

| Area | Reason deferred |
|---|---|
| PgBouncer | Transaction-mode PgBouncer requires Flyway to use a separate direct datasource URL; adds code complexity. Current pool sizing (20 × replicas) stays within PG max_connections=200. Add when replicas × pool_size approaches 150. |
| Second Kafka broker | Single-broker is sufficient for benchmarking. Three-broker setup (kafka, kafka2, kafka3) requires separate compose service definitions with unique BROKER_IDs; deferred to avoid inflating startup complexity. |
| OpenSearch second node | Requires `opensearch-node2` compose service + cluster coordination. `number_of_replicas: 0` on songs index already set (single-node cannot host replica shards). Add node when OpenSearch heap consistently exceeds 75 %. |
| Resilience4j circuit breakers | Code changes in recommendation-service. Deferred; Redis fail-open fallback already provides graceful degradation. |
| Catalog Redis page cache | Requires code change in catalog-service. Deferred; existing PG indexes cover the read path at current scale. |

*Last updated: 2026-05-21*
