# Scalability Plan — 1,000,000 Users
## Docker Compose · Engineering Reference · Cost-Aware Edition

> **Scope:** Covers how to scale the existing eight-service music-streaming benchmark to
> 1 million registered users (~100 K DAU, ~20 K peak-concurrent users).
> Every recommendation is anchored to a concrete service, table, or config key in this
> repository. Cloud cost is treated as a first-class design constraint alongside throughput.
> No Kubernetes, Helm, or external orchestrators are introduced.

---

## Table of Contents

1. [Traffic Shape Assumptions](#1-traffic-shape-assumptions)
2. [Runtime Profile Separation (Cost-Awareness)](#2-runtime-profile-separation-cost-awareness)
3. [Load Balancer and Reverse Proxy Layer](#3-load-balancer-and-reverse-proxy-layer)
4. [Horizontal vs Vertical Scaling per Service](#4-horizontal-vs-vertical-scaling-per-service)
5. [Recommended Replica Counts](#5-recommended-replica-counts)
6. [Database Optimization — PostgreSQL](#6-database-optimization--postgresql)
7. [Database Optimization — OpenSearch](#7-database-optimization--opensearch)
8. [Database Optimization — ClickHouse](#8-database-optimization--clickhouse)
9. [Database Optimization — Redis](#9-database-optimization--redis)
10. [Database Optimization — MongoDB](#10-database-optimization--mongodb)
11. [Kafka Bottlenecks and Partition Strategy](#11-kafka-bottlenecks-and-partition-strategy)
12. [Caching Strategy](#12-caching-strategy)
13. [Backpressure, Retries, and Graceful Degradation](#13-backpressure-retries-and-graceful-degradation)
14. [Prometheus Metrics and Grafana Dashboards](#14-prometheus-metrics-and-grafana-dashboards)
15. [k6 Load-Test Phases](#15-k6-load-test-phases)
16. [Recommended Scaling Order](#16-recommended-scaling-order)
17. [Risks, Assumptions, and Trade-offs](#17-risks-assumptions-and-trade-offs)

---

## 1. Traffic Shape Assumptions

| Metric | Estimate | Rationale |
|---|---:|---|
| Registered users | 1,000,000 | Target |
| DAU (10 % of registered) | 100,000 | Industry norm for streaming apps |
| Peak concurrent users | 20,000 | 20 % of DAU active at the same time |
| Avg. songs streamed per session | 10 | ~30 min session × 3 min/song |
| Playback events/second (peak) | ~40,000 | 20 K users × 2 events/song average |
| Auth logins/second (peak) | ~500 | Session start + 1 h token refresh |
| Catalog/search requests/second | ~4,000 | ~20 % of active users browsing at once |
| Playlist mutations/second | ~200 | Lower-frequency write operation |

**Traffic classification by service:**

- **Hot path (highest RPS):** `streaming-service` (~40 K req/s for events, ~20 K manifest/segment requests), `search-service` (~4 K req/s)
- **Medium path:** `catalog-service` (~2 K req/s browsing), `auth-service` (~500 req/s bursts)
- **Async/write path:** `analytics-service`, `recommendation-service`, `notification-service` (Kafka consumers — not user-facing HTTP)
- **Low-frequency writes:** `playlist-service` (~200 req/s)

This classification drives replica counts, resource limits, and cost prioritization throughout this plan.

---

## 2. Runtime Profile Separation (Cost-Awareness)

Running all services at full benchmark capacity continuously is the primary avoidable cost driver
in a Docker-based benchmarking environment. This section defines four distinct runtime profiles
and specifies which resources are required for each.

### Profile A — Normal Backend Runtime (Development / CI)

Intended for: service validation, unit/integration tests, smoke checks.

| Resource | Allocation |
|---|---|
| All 8 application services | 1 replica each |
| All infrastructure (PG, Kafka, OpenSearch, ClickHouse, Redis, Mongo) | 1 instance each, minimum heap |
| Prometheus + Grafana | Running |
| Load generator | **Not started** (profile: `load-test` is opt-in) |
| nginx-lb | Not required (single replicas reachable via host ports) |

**Cost note:** The current `docker-compose.yml` already gates the load-generator behind a
`profiles: [load-test]`. This is the correct behavior and must not be changed. Do not add
the load generator to the default profile.

### Profile B — Smoke Test (Pre-Benchmark Validation)

Intended for: confirming all services start and connect before a full benchmark run.

| Resource | Change from Profile A |
|---|---|
| k6 | `K6_VUS=5`, `K6_DURATION=2m` |
| All replicas | Still 1 each |
| nginx-lb | Start it; 1 VU per upstream is sufficient to verify routing |

**Cost note:** The smoke run uses 5 VUs for 2 minutes. Total compute cost is negligible.
The goal is error detection, not throughput measurement.

### Profile C — Calibration Run (Single-Instance Baseline)

Intended for: establishing p50/p99 latency and throughput baseline on 1-replica setup.
Results define the benchmark zero-point against which scaling improvements are measured.

| Resource | Setting |
|---|---|
| k6 | `K6_VUS=50`, `K6_DURATION=10m` |
| Replicas | **1 per service** (mandatory — do not scale during calibration) |
| All resource limits | At their `docker-compose.yml` defaults (1 CPU / 512 MB per app service) |

**Cost note:** Calibration is a one-time or per-major-change run. Keep results versioned in
`load-generator/results/baseline/`. Do not re-run calibration before every benchmark session
— only when the service implementation or resource limits change.

### Profile D — Full Benchmark Run (Scaled Deployment)

Intended for: measuring peak throughput, scalability evidence for the thesis.

| Resource | Setting |
|---|---|
| k6 | `K6_VUS=1000–10000`, stages per §15 |
| Replicas | Per §5 (streaming ×8–10, etc.) |
| nginx-lb | Required |
| Prometheus retention | `--storage.tsdb.retention.time=15d` (current, sufficient) |
| Host RAM | ≥ 32 GB required |

**Cost note:** Full benchmark runs are expensive in CPU and memory. Run Profile D only after
Profile B (smoke) and Profile C (calibration) are validated and stable. The k6 Phase 5 peak
test (10 K VUs, 30 min) is the most resource-intensive operation. Do not leave it running
unattended.

### Cost saving: Idle container prevention

Services that are purely Kafka consumers (`analytics-service`, `recommendation-service`,
`notification-service`) consume CPU even when the Kafka topic has no messages.
Under Profile A/B, their CPU usage is near-zero, but they still consume ~256–512 MB RAM.
This is acceptable — they are required services and cannot be removed.

What must **not** happen: leaving a full Profile D deployment (8 streaming replicas, 4
catalog replicas, etc.) running between benchmark sessions. Tear down with
`docker compose down` after each run. Data is preserved by named volumes.

---

## 3. Load Balancer and Reverse Proxy Layer

### Why a dedicated LB is required

`docker compose up --scale streaming-service=8` creates eight containers. Docker's embedded
DNS resolver (`127.0.0.11`) returns all IPs, but only if the client **re-queries DNS for
every connection**. Spring Boot services that make outbound HTTP calls (and nginx when used
as an API proxy) perform a single DNS lookup at startup and cache the result — after scaling,
they keep routing to the original single container.

A dedicated `nginx-lb` service with `resolver 127.0.0.11 valid=10s` re-resolves DNS every
10 seconds, automatically picking up newly started replicas.

### Recommended approach: dedicated nginx-lb container

Add a `nginx-lb` service to `docker-compose.yml` that:

- Listens on host port **80** (or a configurable `NGINX_LB_HOST_PORT`)
- Defines one `upstream` block per scalable service using the Docker internal hostname
- Sets `resolver 127.0.0.11 valid=10s` for dynamic replica discovery
- Uses **least-connections** (`least_conn`) for streaming and recommendation (long-lived or
  variable-cost requests) and default round-robin for auth, catalog, search, playlist,
  and notification (short-lived, similar cost)
- Enforces per-IP rate limits (`limit_req_zone`) to prevent a single k6 worker or
  misbehaving client from saturating one service

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

### Cost trade-off

`nginx-lb` adds one container (64 MB RAM, <0.1 CPU idle). The benefit is that all
application services lose their host port mappings (`8081`–`8088`), which reduces external
exposure and simplifies port management when replicas are running. The container is
lightweight and its cost is negligible relative to the services it routes to.

### What the LB does NOT need to do

- TLS termination — out of scope for a Docker benchmarking environment
- Auth header injection — each service validates JWTs independently via the shared RSA public key
- Service discovery — Docker DNS is sufficient inside `music-net`

---

## 4. Horizontal vs Vertical Scaling per Service

### Services that scale horizontally (stateless application layer)

| Service | Why stateless | Complication | Resolution | Cost note |
|---|---|---|---|---|
| **streaming-service** | No database; Kafka producer; HLS manifest computed per-request | Kafka partitions must exist before multiple producers contend | Increase partition count on `playback-events` to 12; producers are independent | Highest cost/replica (CPU-bound); scale 8–10 × only for full benchmark runs |
| **auth-service** | JWT verification uses RSA public key from shared `jwt-keys` volume (read-only after first boot) | Key generation race on fresh volume with multiple replicas | Key generation code checks file existence before writing; only one replica writes keys | BCrypt is CPU-expensive; 4–6 replicas needed only at 500 req/s login bursts; use 1–2 in Profile A/B |
| **catalog-service** | Read-heavy pagination over `catalog-db`; no local state | Connection pool pressure: N replicas × HikariCP pool size = N×pool DB connections | PgBouncer in front of `catalog-db`; tune `HIKARI_MAXIMUM_POOL_SIZE` | Moderate traffic; 4 replicas for full benchmark; 1 in Profile A/B/C |
| **search-service** | Reads from OpenSearch; no local state | All replicas hit a single-node OpenSearch; heap pressure grows linearly | Expand OpenSearch heap to 2 GB, then add a second OpenSearch node | 4–6 replicas needed at 4 K req/s; each replica is cheap (no DB connections) |
| **playlist-service** | CRUD against `playlist-db`; Kafka producer is idempotent per request | Same connection pool pressure as catalog | PgBouncer in front of `playlist-db` | Low-frequency writes; 3 replicas at full benchmark; 1 in Profile A/B/C |
| **notification-service** | Kafka consumer; writes to MongoDB | Consumer parallelism capped by `playlist-events` partition count | Pre-create `playlist-events` with 6 partitions | Lowest traffic; 2 replicas max; 1 in Profile A/B/C |
| **analytics-service** | Kafka consumer; batch-writes to ClickHouse | Consumer parallelism capped by `playback-events` partition count; ClickHouse hates single-row inserts | Pre-create `playback-events` with 12 partitions; implement batch inserts | 4 replicas; highest priority code fix (batch inserts) before scaling |
| **recommendation-service** | Kafka consumer; Redis shared cache | Cache stampede possible when multiple replicas compute the same recommendation simultaneously | Redis `SETNX`-style lock or accept low-probability double-compute | 3 replicas; Redis hides most latency; scale conservatively |

### Services that scale mostly vertically (stateful infrastructure)

These are infrastructure containers. Running `--scale` on them either requires unique config
per node (Kafka broker ID) or produces split-brain data (two PostgreSQL primaries sharing a
volume). They must be scaled carefully using explicit named service definitions.

| Service | Scaling approach | Cost note |
|---|---|---|
| `auth-db`, `catalog-db`, `playlist-db`, `recommendation-db` | Vertical (more RAM + `shared_buffers`); optional read replica as a separate named service | Adding a read replica doubles the PG memory cost for that database — justified only for catalog-db given its read-heavy workload |
| `opensearch` | Add `opensearch-node2` as a separate named service; same `cluster.name`, cross-linked seed hosts | An additional OpenSearch node adds ~2 GB JVM + OS overhead; defer until heap consistently exceeds 75 % |
| `clickhouse` | Vertical (CPU + memory limits); write batching in analytics-service reduces pressure more than adding nodes | Second ClickHouse node via `ReplicatedMergeTree` + ClickHouse Keeper is complex in Compose and not needed for benchmarking |
| `redis` | Vertical (increase `maxmemory`); Redis Cluster in Compose requires 6 named nodes | Single-node Redis with tuned `maxmemory` and LRU eviction handles up to ~500 K DAU; Redis Cluster cost is not justified at this scale |
| `mongodb` | Vertical (RAM + indexes); HA replica set requires 3 named nodes | Notification writes are low-frequency; single node is last to bottleneck; replica set adds ~512 MB × 2 with no throughput benefit |
| `kafka` | Add `kafka2`, `kafka3` with different `KAFKA_BROKER_ID`; increase replication factor | Single-broker is sufficient for Phase 1–3 load tests; 3-broker cluster adds ~1 GB JVM × 3; defer to immediately before Phase 4/5 |
| `zookeeper` | Single node is sufficient for 3 Kafka brokers in a benchmark | Do not add Zookeeper replicas; Zookeeper is being replaced by KRaft in newer Confluent versions |

---

## 5. Recommended Replica Counts

Based on the traffic shape in §1, with a 3-broker Kafka cluster and PgBouncer in place.
Counts are given for **full benchmark (Profile D)** and **normal runtime / smoke (Profile A/B)**.

| Service | Profile D replicas | Profile A/B replicas | Primary driver |
|---|---:|---:|---|
| `streaming-service` | **8–10** | 1 | Highest request rate; stateless; CPU-bound per HLS segment generation |
| `auth-service` | **4–6** | 1 | BCrypt (~100 ms/verify) + RS256 signing; bursts at login time |
| `search-service` | **4–6** | 1 | 4 K req/s browsing; each query blocks on OpenSearch round-trip |
| `catalog-service` | **4** | 1 | Moderate browsing load; IO-bound on `catalog-db` |
| `analytics-service` | **4** (≤ partition count) | 1 | Kafka consumer; parallelism capped by partition count |
| `recommendation-service` | **3** | 1 | Redis hides most latency; scale conservatively |
| `playlist-service` | **3** | 1 | Lower write frequency; connection pool pressure manageable |
| `notification-service` | **2** (≤ partition count) | 1 | `playlist-events` volume is low; 2 is sufficient |

> **Hard constraint for Kafka consumers:** `analytics-service`, `recommendation-service`,
> and `notification-service` each form a consumer group. A replica beyond the partition
> count of its consumed topic receives no partitions and runs idle — wasting RAM and CPU.
> Always set partition count ≥ intended maximum replica count before scaling.

**Cost saving:** Profile A/B runs at 1 replica per service. Switching from Profile A to
Profile D costs approximately 20 GB additional RAM and 12–16 additional CPU cores (on a
32 GB / 16-core host). Do not leave Profile D running between benchmark sessions.

---

## 6. Database Optimization — PostgreSQL

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

**Connection pooling:** 4–6 `auth-service` replicas × 10 HikariCP connections = 40–60
connections. PostgreSQL default `max_connections=100` can handle this, but PgBouncer in
transaction mode (pool size = 20) reduces overhead significantly.

**Cost trade-off:** PgBouncer adds one container (~64 MB) but allows PostgreSQL to run with
lower `max_connections` (less shared memory). For auth-db, PgBouncer is optional at this
replica count — add it if `auth-service` replica count exceeds 8.

---

### catalog-db

**Hotspot:** Paginated song browsing — `findAll(Pageable)` → `SELECT ... LIMIT ? OFFSET ?`.
At `OFFSET 10000`, PostgreSQL scans and discards 10,000 rows.

**Index strategy:**
```sql
CREATE INDEX idx_songs_title       ON songs(title);
CREATE INDEX idx_songs_genre       ON songs(genre);
CREATE INDEX idx_songs_artist      ON songs(artist);
CREATE INDEX idx_songs_genre_title ON songs(genre, title);
```

**Pagination fix:** Replace `OFFSET`-based pagination with keyset (cursor) pagination:
```sql
-- Instead of: SELECT * FROM songs ORDER BY id LIMIT 50 OFFSET 5000
-- Use:        SELECT * FROM songs WHERE id > :lastSeenId ORDER BY id LIMIT 50
```
This requires a monotonic cursor in the API response. The current max page size is 100;
keyset pagination eliminates the O(n) scan cost at large offsets.

**Connection pooling:** 4 replicas × 10 connections = 40. Add PgBouncer (transaction mode,
pool size = 15) in front of `catalog-db`.

**Read/write split:** The catalog is seeded from a CSV at startup and is largely read-only.
A named `catalog-db-replica` service (PostgreSQL streaming replication) allows
`catalog-service` replicas to route `SELECT` queries to the replica and writes to the
primary. Requires a `secondaryDataSource` bean in the service.

**Cost trade-off:** A read replica for catalog-db doubles the memory cost of that database
instance (~512 MB). It is justified when `catalog-db` sequential-scan rate or connection
saturation appears in Grafana after Phase 3/4 load tests — not before.

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

**Cost trade-off:** No read replica needed. Playlist reads are personalized (cannot be
cached meaningfully) and write frequency is low (~200/s).

---

### recommendation-db

**Hotspot:** Per-user play-history lookup:
`SELECT song_id, played_at FROM play_history WHERE user_id = ? ORDER BY played_at DESC LIMIT 100`

**Index strategy:**
```sql
CREATE INDEX idx_play_history_user_played ON play_history(user_id, played_at DESC);
```
This composite index satisfies both the `WHERE` filter and the `ORDER BY` sort in a single
scan, eliminating a filesort.

**Write pattern:** Every `playback-events` Kafka message causes one INSERT into
`play_history`. At 40 K events/second peak this is significant. Options:
1. Batch inserts in the Kafka consumer (accumulate 500 events, flush every 2 s)
2. Accept the write load on a vertically tuned PostgreSQL with `synchronous_commit = off`
   (acceptable data loss: a few seconds of history — not user-facing)

**Cost trade-off:** `synchronous_commit = off` reduces disk I/O and improves write
throughput at the cost of up to 1 write-ahead-log flush interval (~200 ms) of data loss
on crash. For a benchmark environment, this trade-off is acceptable.

---

## 7. Database Optimization — OpenSearch

### Current state

Single-node, `OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m`. Songs index seeded with batch
size 500 at startup. Queries use `match` on title/artist.

### Heap pressure

At the expected song catalog size and moderate query concurrency, 512 MB heap triggers
frequent GC pauses and eventually OOM. Increase to at least **2 GB**, ideally **4 GB**
(never exceed 31 GB — compressed object pointers break above that).

```yaml
# docker-compose.yml — opensearch service
environment:
  OPENSEARCH_JAVA_OPTS: "-Xms2g -Xmx2g"
```

**Cost trade-off:** 2 GB heap reservation adds ~2 GB to Docker's memory accounting.
The alternative — GC pauses at 512 MB under 4 K req/s — produces a service that fails
load tests, which is a worse outcome for the thesis benchmark.

### Shard strategy

Default single shard does not parallelize queries. For the expected index size (~500 MB):

```json
PUT /songs
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 0
  }
}
```

Set `number_of_replicas=0` on a single-node cluster (replicas cannot be placed on the
same node — OpenSearch marks them `UNASSIGNED` and reports `yellow` health, which breaks
the health check). Raise to `number_of_replicas=1` only after adding `opensearch-node2`.

### Adding a second OpenSearch node (compose sketch)

```yaml
# SKETCH — not final; only add when heap consistently > 75 %
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
  networks: [music-net]
```

**Cost trade-off:** Second OpenSearch node costs ~2 GB JVM + OS overhead. Defer until
Phase 4/5 Grafana metrics show sustained heap pressure above 75 %.

### Query optimizations

- **Avoid deep pagination:** `from: 10000` forces OpenSearch to score all 10,000+
  documents. Use `search_after` with a sort key (`_score`, `song_id`).
- **Request cache:** Add `"request_cache": true` in index settings. Frequently repeated
  queries (popular genre browsing) return from cache without hitting shards.
- **Field mapping:** Ensure `title` and `artist` are `text` (analyzed) with a `keyword`
  sub-field for exact-match and aggregation. Avoid `fielddata` on text fields.

---

## 8. Database Optimization — ClickHouse

### Critical: write batching in analytics-service

The `analytics-service` currently writes one row per Kafka message (individual `INSERT`
over JDBC). ClickHouse's `MergeTree` engine creates a new *part* per insert. At peak
throughput this triggers constant background merges, high CPU, and `Too many parts`
rejections.

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

**Cost trade-off:** Batching increases per-event write latency from near-zero to up to
5 seconds. For analytics (listen history, charts), 5 s lag is acceptable. The benefit is
a significant reduction in ClickHouse CPU and I/O, which translates directly to lower
infrastructure cost and more stable performance under load.

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

- **Partition by month:** Dropping old data is `ALTER TABLE DROP PARTITION '202401'` —
  instant, no heavy DELETE scans. Prevents unbounded disk growth across long benchmark runs.
- **Sort/primary key `(user_id, song_id, event_time)`:** Per-user analytics queries skip
  unrelated granules instantly.
- **`LowCardinality(String)` for event_type:** Values like `play.started`, `play.ended`,
  `play.skipped` compress to ~1–2 bytes each.

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

**Cost trade-off:** The 4 g hard limit prevents host OOM under batch write storms.
The 2 g reservation ensures ClickHouse gets its working set paged in at startup.

---

## 9. Database Optimization — Redis

### Current state

Single node, `redis:7.2-alpine` with `redis-data` volume (AOF persistence).
`recommendation-service` stores `daily-mix:{userId}` and `similar:{songId}` keys with
3600 s TTL. Current compose has no explicit `maxmemory` or eviction policy.

### Memory sizing

At 100 K DAU with ~1 KB per recommendation entry:
- `daily-mix:{userId}`: 100 K keys × 1 KB = ~100 MB
- `similar:{songId}`: ~50 K unique songs × 1 KB = ~50 MB
- Total: ~150–200 MB in steady state (keys expire after 1 h)

Set an explicit memory ceiling and eviction policy:
```
maxmemory 512mb
maxmemory-policy allkeys-lru
```

`allkeys-lru` evicts the least-recently-used keys when memory is full, which aligns
with the TTL-based recommendation workload and prevents unbounded memory growth.

**Cost trade-off:** Without `maxmemory`, Redis can grow to consume all available host
RAM (via large key churn or TTL misconfiguration), starving other containers. The 512 MB
cap is a hard cost guard. `allkeys-lru` ensures the cache stays useful under pressure
rather than refusing writes.

### AOF performance

`appendfsync always` writes every command to disk synchronously, adding 1–2 ms latency
per write. Change to `appendfsync everysec` — acceptable data loss of 1 second of cache
writes, tolerable for a recommendation cache (data can be recomputed from PostgreSQL).

### Cache stampede mitigation

With 3 `recommendation-service` replicas, two workers can simultaneously compute the
same user's recommendation when a key expires. Mitigate with a short lock:

```java
// ILLUSTRATIVE — not final
String lockKey = "lock:daily-mix:" + userId;
Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
if (Boolean.TRUE.equals(locked)) {
    // compute and cache
} else {
    Thread.sleep(200);
    return redis.opsForValue().get("daily-mix:" + userId);
}
```

**Cost trade-off:** Without stampede mitigation, N replicas compute the same result
simultaneously under cache expiry, temporarily multiplying CPU cost by N. The lock adds
a small latency increase (~200 ms wait in the fallback path) but prevents the CPU spike.

---

## 10. Database Optimization — MongoDB

### Current state

Single node, `mongo:7.0`, Spring Data `auto-index-creation: true`.

### Write pattern

`notification-service` inserts one document per `playlist-events` Kafka message.
Playlist events are low-frequency (~200/s peak). Single-node MongoDB with default
WiredTiger cache (50 % of available RAM) handles this comfortably.

### Index strategy

Spring Data's `auto-index-creation` creates indexes for `@Indexed` annotations in entity
classes. Verify these indexes exist:

```javascript
// In mongosh — verify index on notification collection
db.notifications.getIndexes()
// Must include: { userId: 1 } and { playlistId: 1, createdAt: -1 }
```

If not present, add explicit `@Indexed` annotations in the notification entity class.

### Scaling considerations

MongoDB is the last bottleneck in this system. Notification writes are infrequent and
documents are small. Vertical scaling (increase WiredTiger cache via
`--wiredTigerCacheSizeGB`) is sufficient to 1 M users. A replica set requires three
named compose services — justified only for fault tolerance, not throughput.

**Cost trade-off:** MongoDB replica set (3 nodes) costs ~512 MB × 3 = 1.5 GB extra RAM.
At ~200 writes/second with no read load, this cost is not justified. Add a replica set
only if the benchmark requires MongoDB write fault tolerance evidence.

---

## 11. Kafka Bottlenecks and Partition Strategy

### Critical issue: 1-partition default

With `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` and no explicit partition configuration,
Kafka creates topics with 1 partition. A 1-partition topic is consumed by exactly **1
consumer instance per consumer group** — regardless of how many replicas are running.
Extra replicas sit idle, wasting RAM.

### Required: pre-create topics with adequate partitions

Create topics before any service starts (via an `init-kafka` container):

```bash
# SKETCH — run once as part of compose startup
kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic playback-events --partitions 12 --replication-factor 3

kafka-topics.sh --bootstrap-server kafka:9092 --create \
  --topic playlist-events --partitions 6 --replication-factor 3
```

**Partition count rationale:**
- `playback-events` → 12 partitions: consumed by `analytics-service` (4 replicas = 3
  partitions each) and `recommendation-service` (3 replicas = 4 partitions each)
- `playlist-events` → 6 partitions: consumed by `notification-service` (2 replicas = 3
  partitions each); headroom for up to 6 replicas

**Cost trade-off:** Pre-creating topics with 12 partitions at replication factor 3 (on
a 3-broker cluster) adds storage overhead proportional to message volume × 3. For a
24-hour benchmark with log retention of 24 h, this is bounded and acceptable.

### Single broker vs 3-broker cluster

The current single-broker setup means a restart causes consumer lag and in-flight message
loss. For sustained throughput at Phase 4/5:

Define `kafka`, `kafka2`, `kafka3` as three separate compose services, each with a unique
`KAFKA_BROKER_ID`. Set:

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
KAFKA_DEFAULT_REPLICATION_FACTOR: 3
```

> **Cannot use `--scale kafka=3`:** each broker requires a unique `KAFKA_BROKER_ID` and
> different advertised listener ports — these must be hardcoded per service definition.

**Cost trade-off:** A 3-broker cluster uses ~1 GB JVM × 3 = 3 GB extra RAM. Defer to
immediately before Phase 4/5 load tests. Single broker is sufficient for Phases 1–3.

### Consumer lag as a scaling signal

Consumer lag on `playback-events` is the primary indicator that analytics or
recommendation capacity needs to increase. Track via `kafka-exporter`:

```
kafka_consumergroup_lag{topic="playback-events", consumergroup="analytics-service"}
kafka_consumergroup_lag{topic="playback-events", consumergroup="recommendation-service"}
```

**Alert threshold:** lag > 10,000 messages sustained for 60 s → add consumer replica
(up to the partition count limit).

### Producer configuration

Both `streaming-service` and `playlist-service` produce to Kafka. Add explicit retry
configuration:

```yaml
spring.kafka.producer.retries: 3
spring.kafka.producer.properties.retry.backoff.ms: 1000
spring.kafka.producer.properties.max.in.flight.requests.per.connection: 1
```

`max.in.flight.requests.per.connection=1` prevents out-of-order delivery on retry.

---

## 12. Caching Strategy

### Currently cached

Only `recommendation-service` uses Redis. No other service caches anything.

### Recommended additions

**catalog-service — popular song pages:**
The first 3–5 pages of any genre (e.g., `GET /songs?genre=rock&page=0`) are requested
by a large fraction of active users. These pages are stable (catalog rarely changes).
Cache them in Redis:

```
Key:   catalog:genre:{genre}:page:{page}:size:{size}
TTL:   300s
Value: JSON array of song summaries
```

This avoids repeated `SELECT ... WHERE genre = ? ORDER BY title LIMIT 50 OFFSET 0` calls
on every browse session.

**Cost trade-off:** Popular-page caching adds ~50–100 MB to Redis memory (bounded by TTL
and genre count) and eliminates catalog-db round-trips for the most common queries.
Savings in DB CPU and reduced connection pressure outweigh the Redis memory cost.

**search-service — repeated queries:**
Top search terms are queried many times per session. Cache the serialized JSON response:

```
Key:   search:q:{normalized_query}:page:{page}
TTL:   60s
Value: JSON search result
```

Cache normalization: lowercase and trim the query string before computing the key.

**Cost trade-off:** 60 s TTL bounds cache size naturally. OpenSearch CPU savings for the
top 100 queries during a benchmark run are significant at 4 K req/s.

**auth-service — JWT validation pattern:**
The ARCHITECTURE.md requirement is that services validate JWTs locally using the shared
RSA public key (not via an HTTP call to auth-service). Verify that no service makes a
per-request HTTP call to `auth-service`. If any does, replace with local RSA verification.
This eliminates a cross-service call on every protected request — the largest possible
latency and cost reduction in the auth path.

### What NOT to cache

- **Playlist contents** (`playlist-service`): personalized per user, high mutation rate.
  Staleness bugs under concurrent mutation make caching dangerous.
- **Notification state** (`notification-service`): documents are write-once, rarely read
  via API; MongoDB query cost is low.
- **ClickHouse analytics results**: ClickHouse has its own result cache (`query_cache`).
  Do not add a Redis layer in front of it — double-caching wastes Redis memory.

---

## 13. Backpressure, Retries, and Graceful Degradation

### Current resilience gaps

| Service | Gap |
|---|---|
| `streaming-service` | Kafka producer failures logged but not retried at application level |
| `recommendation-service` | Redis unavailability falls back to compute (correct) but no circuit breaker on DB calls |
| `analytics-service` | Single-row inserts to ClickHouse; no batching; slow ClickHouse → lag build-up |
| `notification-service` | No retry on MongoDB insert failure |
| All services | No circuit breakers; a slow dependency causes thread pool exhaustion |

### Kafka producer retries

See §11 for producer retry configuration. Critical addition: when `streaming-service`
send callback reports failure after retries, log at `ERROR` with the full event payload
and increment a `playback_event_drop_total` Prometheus counter. This allows post-hoc
analysis of message loss without reprocessing.

**Cost trade-off:** Logging large event payloads under failure increases disk I/O.
Use structured logging with a bounded payload size (trim to essential fields) to prevent
log volume runaway under sustained producer failure.

### Circuit breakers (Resilience4j)

Add Resilience4j to `recommendation-service` for Redis and PostgreSQL call paths:

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

For `catalog-service` and `playlist-service`, wrap JPA repository calls in circuit
breakers that return HTTP 503 with `Retry-After: 5` when the DB is saturated, rather
than queuing requests indefinitely (which exhausts Tomcat's thread pool).

**Cost trade-off:** Circuit breakers add a small library overhead (~few MB JVM
heap). The benefit is that a failing dependency stops consuming threads, preventing
cascading failures that require all services to be restarted.

### Rate limiting at nginx-lb

Add token-bucket rate limits:

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

This protects `auth-service` from brute-force and `streaming-service` from runaway k6
workers consuming all capacity.

**Cost trade-off:** Rate limiting at the LB prevents a k6 misconfiguration from driving
services to OOM. The 10 MB zone sizes are negligible.

### Graceful degradation under ClickHouse pressure

If `analytics-service` consumers lag significantly (Kafka lag > 50 K events), increase
batch size and reduce flush frequency — do not restart the service.

```java
// ILLUSTRATIVE
int batchSize = lag > 50_000 ? 5000 : 1000;
```

Implement a dead-letter pattern: after 3 failed processing attempts of the same Kafka
message, route to a `playback-events-dlq` topic and continue. Without this, a single
malformed event can stall the entire consumer group.

**Cost trade-off:** A DLQ topic adds minimal storage overhead (~negligible relative
to the main topic). The benefit is that consumer groups never stall — sustained message
loss is observable and actionable rather than silent.

---

## 14. Prometheus Metrics and Grafana Dashboards

### Additional exporters required

The current `prometheus.yml` scrapes only Spring Boot actuator endpoints. Add:

| Exporter | Target | Why |
|---|---|---|
| `prom/postgres-exporter` | One instance per PostgreSQL DB (4 total) | Connection pool saturation, sequential scans, lock waits |
| `oliver006/redis_exporter` | `redis:6379` | Cache hit rate, memory usage, connected clients |
| `danielqsj/kafka-exporter` | `kafka:9092` | Consumer group lag — the most critical Kafka metric |
| `percona/mongodb_exporter` | `mongodb:27017` | Collection scan rate, connection count |

ClickHouse exposes a native Prometheus endpoint at `:9363/metrics` — add directly to
`prometheus.yml` without a separate exporter.

OpenSearch exposes metrics via the `_prometheus/metrics` endpoint (with the
`opensearch-prometheus-exporter` plugin) or via `opensearch_exporter`.

**Cost trade-off:** Each exporter adds one container (~64–128 MB RAM). At 4 exporters,
this is ~256–512 MB total — acceptable overhead given that these exporters provide the
data that drives all scaling decisions. Scraping at 15 s intervals (current config) is
sufficient and does not generate significant Prometheus storage growth.

### Scaling-decision dashboards in Grafana

Create or extend `infra/grafana/dashboards/overview.json` with the following panel groups.

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
redis_used_memory_bytes                                  → Memory vs maxmemory
```

#### Panel group: Infrastructure

```
opensearch_jvm_mem_heap_used_in_bytes / opensearch_jvm_mem_heap_max_in_bytes  → OS heap %
clickhouse_query_duration_ms_quantiles{quantile="0.99"}  → ClickHouse p99 query latency
```

### Scaling thresholds (use as Grafana alert rules)

| Metric | Threshold | Action |
|---|---|---|
| `process_cpu_usage` > 0.70 for 5 min | Service CPU saturated | Add replica (if stateless) |
| `hikaricp_connections_active / max` > 0.80 | Pool exhaustion imminent | Add PgBouncer or increase pool capacity |
| `kafka_consumergroup_lag` > 10,000 for 60 s | Consumer falling behind | Add consumer replica (≤ partition count) |
| OpenSearch heap > 75 % | GC pressure | Increase `OPENSEARCH_JAVA_OPTS` heap |
| `redis_used_memory_bytes` > 80 % of `maxmemory` | Cache near full | Increase `maxmemory` or reduce TTLs |
| `http_server_requests_seconds_max{quantile="0.99"}` > 2 s | Latency SLO breach | Investigate per-service bottleneck |

**Cost saving:** Use alert rules to trigger scaling decisions reactively rather than
pre-provisioning at full replica count. This avoids paying for idle replicas during
ramp-up phases. k6 ramp stages (§15) exist precisely to drive gradual load increase.

---

## 15. k6 Load-Test Phases

The current `load-generator/scripts/main.js` is Phase 0 (health probe only). Build the
following phases in order. Do not skip to a higher-VU phase before the previous phase
is stable with p99 latency under 500 ms.

### Phase 1 — Smoke test (baseline, pre-scaling)

```javascript
// Target: verify basic functionality with minimal load BEFORE any scaling changes
export const options = { vus: 5, duration: '2m' };
// Flow: register → login → get catalog page 0
// Services hit: auth-service, catalog-service
// Goal: confirm no errors; establish baseline p50/p99; measure DB connection usage
// Cost: negligible — 5 VUs for 2 min
```

Run on the **single-instance** deployment (Profile C). Record results as the benchmark
zero-point.

### Phase 2 — Core streaming flow

```javascript
export const options = { vus: 50, duration: '5m' };
// Flow: login → search("rock") → GET /catalog/songs/{id} → GET /stream/{songId}/manifest
//       → GET /stream/{songId}/segments/0..2 → emit complete event
// Services: auth, search, catalog, streaming
// Watch: streaming-service CPU, Kafka send rate, playback-events consumer lag
// Cost: 50 VUs for 5 min; moderate CPU on streaming-service
```

### Phase 3 — Kafka pipeline validation

```javascript
export const options = { vus: 200, duration: '10m' };
// Flow: Phase 2 flow + create playlist → add song → modify playlist
// Watch: analytics consumer lag, recommendation cache hit rate, notification insert rate
// Goal: confirm end-to-end Kafka pipeline without lag at 200 concurrent users
// Cost: ~200 concurrent JVM threads across services; ClickHouse write pressure visible
```

### Phase 4 — Scaling ramp (replicas in place)

```javascript
export const options = {
  stages: [
    { duration: '5m',  target: 1000 },
    { duration: '10m', target: 1000 },
    { duration: '5m',  target: 0 },
  ]
};
// Full user flow with 1–2 s think-time between steps
// Run AFTER: nginx-lb, PgBouncer, 12-partition Kafka topics, and service replicas
// Watch: which service p99 climbs first — that is the next bottleneck
// Cost: 1000 VUs peak; full Profile D required; ~22 GB RAM in use
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
// Simulate 20 K concurrent sessions (10 K in k6; steady-state overlap adds effective 2×)
// Watch: Kafka lag, DB connection pool saturation (alert if > 80 %), streaming p99 > 1 s
// Expected bottleneck: catalog-db connections, OpenSearch heap
// Cost: most expensive run; 30 min duration; do not repeat without reviewing Phase 4 results
```

### Phase 6 — Soak test

```javascript
export const options = { vus: 2000, duration: '2h' };
// Goal: detect memory leaks, ClickHouse part accumulation, Redis memory creep,
//       Kafka partition rebalance events, JVM GC drift
// Cost: 2 h × full Profile D; run only once for soak evidence; plan for post-soak teardown
```

### Test ordering principle

Run phases in order (1 → 6). Add replicas or tune infrastructure **between** phases,
guided by Grafana metrics from the previous phase. Do not optimize services that did not
bottleneck in the previous phase — this wastes time and produces misleading benchmark results.

**Cost principle:** Phases 1–3 can run on any host with 16 GB RAM and 8 cores. Phases 4–6
require at least 32 GB RAM. Schedule Phase 4/5/6 deliberately and tear down the full Profile D
deployment immediately after.

---

## 16. Recommended Scaling Order

Sequence the work so that each step unblocks the next and low-impact services are not
optimized first.

| Step | Action | Prerequisite | Cost Impact | Performance Impact |
|---|---|---|---|---|
| 1 | Pre-create Kafka topics with 12/6 partitions via `init-kafka` container | Kafka healthy | None (storage grows with message volume) | Unlocks all consumer horizontal scaling |
| 2 | Add `nginx-lb` service to `docker-compose.yml` | None | +64 MB RAM | Enables traffic distribution across future replicas |
| 3 | Add PostgreSQL indexes to all 4 DB instances (via Flyway migrations) | Databases running | None | Reduces query cost before adding load |
| 4 | Add PgBouncer in front of `catalog-db` and `playlist-db` | PostgreSQL running | +64 MB × 2 RAM | Prevents connection exhaustion when replicas are added |
| 5 | Scale `streaming-service` to 8 replicas | nginx-lb in place | +512 MB × 7 RAM, +7 CPU cores | Highest ROI: handles majority of user traffic |
| 6 | Scale `auth-service` to 4 replicas | nginx-lb, `jwt-keys` volume populated | +512 MB × 3 RAM | Second-highest traffic; BCrypt is CPU-expensive |
| 7 | Scale `search-service` to 4–6 replicas; increase OpenSearch heap to 2 GB | OpenSearch healthy | +2 GB OpenSearch heap, +512 MB × 3–5 RAM | Unblocks browsing throughput |
| 8 | Scale `catalog-service` to 4 replicas | PgBouncer for catalog-db | +512 MB × 3 RAM | Moderate impact; browsing shared with search |
| 9 | Expand Kafka to 3 brokers | Zookeeper stable | +1 GB × 2 RAM | Required for fault tolerance and replication factor 3 |
| 10 | Scale `analytics-service` to 4 replicas; implement ClickHouse batch inserts | 12 partitions on playback-events, 3-broker Kafka | +512 MB × 3 RAM | Prevents Kafka lag under Phase 4/5 load |
| 11 | Scale `recommendation-service` to 3 replicas | 12 partitions on playback-events | +512 MB × 2 RAM | Lag reduction; cache hit rate improvement |
| 12 | Scale `playlist-service` to 3 replicas | PgBouncer for playlist-db | +512 MB × 2 RAM | Noticeable only above 5 K concurrent users |
| 13 | Scale `notification-service` to 2 replicas | 6 partitions on playlist-events | +512 MB × 1 RAM | Lowest traffic; scale last |
| 14 | Add `opensearch-node2`; set `number_of_replicas=1` | Step 7 complete, heap > 75 % | +2 GB JVM + OS | Required when search-service replicas saturate single-node OS |
| 15 | Add Redis `maxmemory` + LRU; add catalog/search result caching | Redis healthy | Controlled by maxmemory cap | Reduces DB round-trips for repeated popular queries |

---

## 17. Risks, Assumptions, and Trade-offs

### Risk 1 — JWT key generation race condition

**Risk:** If multiple `auth-service` replicas start simultaneously on a fresh environment
(no pre-populated `jwt-keys` volume), all replicas may attempt to write RSA key files at
the same time. The last writer wins, but replicas that already loaded different keys will
fail to verify tokens signed by others.

**Mitigation:** Key generation code must use an atomic file-existence check (`Files.exists`
before writing). Ensure `docker compose up --scale auth-service=4` is preceded by a single
`auth-service` instance completing startup before replicas are added. Auth-service must retain
`container_name` in single-instance mode to enforce this.

---

### Risk 2 — Kafka partition count is immutable after write

**Risk:** Kafka partitions can be increased (via `--alter`) but not decreased. Increasing
partitions on an existing topic does not redistribute existing messages — only new messages
go to new partitions. Decreasing requires recreating the topic (message loss).

**Mitigation:** Pre-create topics with target partition counts (12 and 6) before any service
writes to them. Use an `init-kafka` container that runs and exits before dependent services
start. Set `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` to prevent accidental 1-partition creation.

---

### Risk 3 — Docker DNS and nginx upstream caching

**Risk:** nginx resolves upstream hostnames once at config load time by default. After
`docker compose up --scale streaming-service=8`, nginx does not automatically discover new
replicas unless configured with `resolver 127.0.0.11 valid=10s` and dynamic upstream
variable substitution.

**Mitigation:** Mandatory in the `nginx-lb` config (use `set $upstream` variable pattern,
not static `upstream` block, if the nginx version requires it). Without this, all 8 streaming
replicas receive traffic only from k6 (which connects directly) but not from the nginx proxy
path — tests would appear to scale but real user traffic would not.

---

### Risk 4 — Host memory exhaustion

**Risk:** At full Profile D:
- 8–10 streaming replicas × 512 MB = 4–5 GB
- 4 catalog replicas × 512 MB = 2 GB
- OpenSearch 2 GB JVM + OS overhead
- ClickHouse 4 GB limit
- 4 PostgreSQL instances ~256 MB each = 1 GB
- Redis 512 MB + MongoDB 512 MB = 1 GB
- Kafka × 3 ~1 GB JVM each = 3 GB
- Prometheus + Grafana + exporters ~1 GB
- **Total: ~19–22 GB**

**Mitigation:** Run full Profile D only on a host with ≥ 32 GB RAM. Set explicit
`deploy.resources.limits` on all infrastructure containers that currently have none
(Kafka, Zookeeper, ClickHouse, MongoDB).

---

### Risk 5 — ClickHouse "Too many parts" under individual inserts

**Risk:** Without batching in `analytics-service`, ClickHouse creates one part per Kafka
message. At 40 K events/s this produces 40 K parts/s. ClickHouse logs
`Too many parts (N). Merges are processing significantly slower than inserts` and
eventually throttles or rejects inserts.

**Mitigation:** Batch inserts (Step 10 in scaling order) must be implemented **before**
running Phase 4/5 load tests. This is the highest-priority code change in the analytics
pipeline.

---

### Risk 6 — Single-broker Kafka message loss during restart

**Assumption:** For benchmarking, a single Kafka broker restart causing temporary message
loss or consumer re-connection delay is acceptable. The 3-broker expansion (Step 9) is
recommended before final throughput tests but not critical for Phases 1–3.

**Trade-off:** A 3-broker cluster with replication factor 3 requires three separate compose
service definitions (`kafka`, `kafka2`, `kafka3`), each with unique `BROKER_ID` and listener
ports. This adds operational complexity but is necessary for accurate 1 M user throughput
benchmarking.

---

### Risk 7 — `--scale` on stateful containers shares a volume

**Risk:** `docker compose up --scale catalog-db=2` creates two PostgreSQL containers both
using the same `catalog-db-data` volume. The second container fails because PostgreSQL locks
its data directory.

**Mitigation:** Never use `--scale` on database services. Any read replica must be defined
as a separate named service with its own volume (e.g., `catalog-db-replica`) configured with
`primary_conninfo` pointing to the primary.

---

### Risk 8 — Idle Kafka consumer replicas (cost risk)

**Risk:** If `playback-events` is created with 1 partition (auto-create default) and
`analytics-service` is scaled to 4 replicas, 3 replicas sit idle — consuming ~512 MB × 3
RAM with zero throughput benefit.

**Mitigation:** Step 1 in the scaling order (pre-create topics) must run before scaling
any consumer service. Disable auto topic creation to prevent this from happening silently.

---

### Assumption: JVM startup time under concurrent scaling

All Spring Boot services have a 60 s `start_period` in their health checks. When scaling
from 1 to 8 replicas of `streaming-service`, all 8 containers start simultaneously. With
a 4–8 CPU host, 8 concurrent JVM startups contend for CPU. Allow 90–120 s for all
replicas to pass health checks before directing load at them.

---

### Trade-off: Redis single-node vs Redis Cluster

Redis Cluster in Docker requires 6 named nodes (3 primaries + 3 replicas) with cluster-bus
ports, node meet commands, and slot allocation — significant operational overhead. For the
recommendation cache workload (~200 MB total at 100 K DAU), single-node Redis with
`maxmemory 512mb` and LRU eviction handles the load. Plan for Redis Cluster only if
total cache footprint exceeds 80 % of single-node `maxmemory` under sustained load — not
before that data point is observed in Grafana.

---

### Trade-off: PgBouncer deferral

Transaction-mode PgBouncer requires that Flyway schema migrations use a separate direct
datasource URL (Flyway does not support DDL in transaction-mode pool). This adds code
complexity (two datasource beans per service). At current pool sizing (HikariCP pool × replicas
stays within PG `max_connections=200`), PgBouncer is not an immediate bottleneck. Add when
HikariCP saturation appears in Grafana (>80 % `hikaricp_connections_active / max`).

---

---

## 18. Implementation Status (2026-06-15)

The first scaling implementation delivers the following changes. Each item references the
affected file so the rationale can be traced directly to the code.

### Implemented

| Area | Change | File(s) |
|---|---|---|
| **Kafka topics** | `init-kafka` one-shot service creates `playback-events` (12 partitions) and `playlist-events` (6 partitions) before producers/consumers start. `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` prevents accidental 1-partition creation. | `docker-compose.yml` |
| **nginx-lb** | Dedicated nginx LB on port 80. `resolver 127.0.0.11 valid=10s` picks up new replicas within 10 s. Per-path rate limiting (auth 20 r/s, stream 200 r/s, API 500 r/s). `set $upstream` variable pattern forces DNS re-query on each request. | `infra/nginx-lb/nginx.conf`, `docker-compose.yml` |
| **App service replicas** | All 8 services use `deploy.replicas: ${SERVICE_REPLICAS:-1}`. Default is 1 (Profile A/B). Profile D values documented in `.env.example`. `container_name` removed from 7 services to enable `--scale`; auth-service keeps it to prevent JWT key race. | `docker-compose.yml`, `.env.example` |
| **Host ports removed** | All 8 app services no longer bind host ports (8081–8088). All API traffic enters on port 80 via nginx-lb. Databases and observability keep host ports for direct debugging. | `docker-compose.yml` |
| **JWT key dependency** | All 7 services with `jwt-keys:ro` now `depends_on: auth-service: condition: service_healthy`, guaranteeing RSA key files exist before JWT consumers load them. | `docker-compose.yml` |
| **init-kafka dependency** | All 5 Kafka-connected services `depends_on: init-kafka: condition: service_completed_successfully`, preventing producer/consumer startup before topics exist. | `docker-compose.yml` |
| **OpenSearch heap** | Increased from 512 m to `${OPENSEARCH_HEAP:-1g}` (default 1 g; set 2 g+ for Profile D). | `docker-compose.yml`, `.env.example` |
| **Redis tuning** | Added `--maxmemory ${REDIS_MAXMEMORY:-256mb} --maxmemory-policy allkeys-lru --appendfsync everysec`. Memory cap prevents host OOM; LRU eviction keeps cache useful under pressure; everysec reduces write latency. | `docker-compose.yml`, `.env.example` |
| **PostgreSQL tuning** | Each PostgreSQL instance uses `command: postgres -c shared_buffers=... -c work_mem=... -c max_connections=200` tuned per workload. `catalog-db` gets 256 MB shared_buffers (largest read dataset). | `docker-compose.yml`, `.env.example` |
| **HikariCP tuning** | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10` and `MINIMUM_IDLE=2` set via env on all DB-connected services. Configurable via `HIKARI_MAX_POOL_SIZE` and `HIKARI_MIN_IDLE`. | `docker-compose.yml`, `.env.example` |
| **Infrastructure resource limits** | Kafka (2 CPU / 2 g), Zookeeper (0.5 CPU / 512 m), ClickHouse (2 CPU / 4 g, reservation 2 g), MongoDB (1 CPU / 1 g), OpenSearch (2 CPU / 3 g), Redis (0.5 CPU / 512 m), nginx-lb (0.5 CPU / 128 m), init-kafka (0.2 CPU / 256 m) now all have explicit limits. | `docker-compose.yml` |
| **ClickHouse batch insert** | `BatchEventBuffer` accumulates Kafka-consumed events and flushes to ClickHouse via `jdbcTemplate.batchUpdate()` in batches of 500 or every 5 seconds. `@EnableScheduling` added to application class. Eliminates "one part per insert" anti-pattern. | `BatchEventBuffer.java`, `PlaybackEventConsumer.java`, `AnalyticsRepository.java`, `AnalyticsService.java`, `AnalyticsServiceApplication.java` |
| **ClickHouse schema** | Table DDL updated: `event_type` uses `LowCardinality(String)`, `PARTITION BY toYYYYMM(toDateTime(occurred_at))` added, `ORDER BY` extended to `(user_id, song_id, occurred_at)`. | `SchemaInitializer.java` |
| **Prometheus DNS SD** | All 8 app services switched from `static_configs` to `dns_sd_configs` (`type: A`, `port: 8080`) so Prometheus auto-discovers replica IPs. | `infra/prometheus/prometheus.yml` |
| **Prometheus exporters** | Added `postgres-exporter` (×4), `redis-exporter`, `kafka-exporter`, `mongodb-exporter` as compose services + scrape configs. ClickHouse native metrics endpoint (`:9363`) added directly. | `docker-compose.yml`, `infra/prometheus/prometheus.yml` |
| **Grafana scaling dashboard** | New `scaling.json` dashboard with 14 panels: request rate, error rate, p99/p95 latency, JVM heap, CPU, HikariCP saturation, Kafka consumer lag per group, Redis hit rate/memory, OpenSearch heap, ClickHouse query latency. | `infra/grafana/dashboards/scaling.json` |
| **k6 full user journey** | `main.js` replaced with full user journey: register → login → browse → search → stream → playlist ops → recommendations → analytics. Four selectable scenarios via `K6_SCENARIO` (smoke/streaming/full/peak). All requests route through `NGINX_LB_URL`. | `load-generator/scripts/main.js` |
| **`.env.example` updated** | All new variables documented with defaults and explanations: replica counts per profile, Kafka partition counts, HikariCP settings, Redis maxmemory, OpenSearch heap, infrastructure limits, exporter versions, analytics batch tuning. | `.env.example` |
| **`application.yml` updated** | Analytics service batch properties added: `analytics.batch.size` and `analytics.batch.flush-interval-ms`. | `services/analytics-service/src/main/resources/application.yml` |

### Not yet implemented (deferred — see COST-AWARE-DECISIONS.md)

| Area | Reason deferred |
|---|---|
| PgBouncer | Adds code complexity (two datasource beans per service for Flyway compatibility). Current pool sizing (10 × replicas) stays within `max_connections=200`. Add when HikariCP saturation appears in Grafana. |
| Second Kafka broker | Single broker sufficient for Phases 1–3. Add `kafka2`, `kafka3` immediately before Phase 4 to avoid introducing 2 GB RAM overhead prematurely. |
| OpenSearch second node | Add when heap consistently exceeds 75 % under Phase 4+ load. Currently `number_of_replicas=0` set to prevent yellow cluster status on single node. |
| Resilience4j circuit breakers | `recommendation-service` has Redis fail-open fallback. Defer until Phase 3 Redis failure behavior is validated in Grafana. |
| Catalog Redis page cache | PostgreSQL indexes cover the read path at current scale. Defer until `pg_stat_user_tables_seq_scan` shows persistent sequential scans under Phase 3+ load. |

*Last updated: 2026-06-15*
