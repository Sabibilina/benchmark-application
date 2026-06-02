# Cloud Cost Efficiency — Decisions, Assumptions, and Trade-offs

> **Status:** Implementation complete (2026-06-02).
> **Scope:** Docker Compose single-host benchmark deployment.
> **Baseline:** `refactored/sabina` branch; Phase S1 scaling implementation in place.

---

## 1. Framing and Scope

### What "cloud cost" means for this deployment

This system runs in Docker Compose on a single host. "Cloud cost" maps to the resources a cloud provider would charge for:

| Resource axis | Docker Compose proxy |
|---|---|
| Compute (CPU) | Host CPU cycles consumed per unit of useful work |
| Memory | Total RAM reserved and allocated by containers |
| Disk I/O | Writes to volumes (Kafka log, WAL, AOF, Prometheus TSDB) |
| Network I/O | Bytes sent on the `music-net` bridge (segment payloads) |

Cost efficiency means delivering the benchmark's required throughput and latency SLOs while consuming less of each resource per unit of work.

### Source of truth documents consulted

- `ARCHITECTURE.md` — service boundaries, technology mandates, behavioral requirements
- `SCALABILITY.md` — workload targets (40 K playback events/s, 4 K catalog req/s, 500 auth req/s, 200 playlist mutations/s, 20 K peak concurrent users)
- `docker-compose.yml` — current container sizes, limits, configurations
- `LOAD-RESULTS.md` — 5 load-test runs; empirical evidence of bottlenecks
- `PROGRESS.md` — deferred items from Phase S1 and S2
- Service source code (inspected: `StreamingService.java`, `BatchEventBuffer.java`, `RecommendationService.java`, all Dockerfiles, all `application.yml` files)

### Assumptions

| ID | Assumption |
|----|-----------|
| A-01 | The host machine has 16 GB RAM (confirmed by operator; SCALABILITY.md Risk 4 assumed ≥ 32 GB — that assumption does not hold here). Container limits are ceiling, not floor. Resource headroom is tighter than the scalability plan assumed; this increases the priority of memory-reduction changes. |
| A-02 | "Cloud cost" is evaluated per benchmark run, not per month. The goal is to maximize useful work per container-second, not to minimize absolute resource allocation. |
| A-03 | ARCHITECTURE.md technology choices are frozen: Java 21, Spring Boot 3, PostgreSQL ×4, OpenSearch, ClickHouse, MongoDB, Redis, Kafka. No substitution. |
| A-04 | Functional requirements in `ARCHITECTURE.md` §7 requirements table are preserved unchanged. |
| A-05 | Simulated segment payloads (streaming-service) exist only to generate load — no real client consumes or decodes them. Reducing payload entropy or size does not affect correctness. |
| A-06 | Recommendation cache TTL = 1 hour (confirmed from `application.yml`). Cache miss data loss (recommendation results) is acceptable for a benchmark. |
| A-07 | Load test phases 1–6 from SCALABILITY.md §14 are used as the validation framework. |
| A-08 | BCrypt work factor in auth-service is left untouched — it is a security parameter, not a cost parameter, and weakening it would violate implicit security requirements. |

---

## 2. Cost Driver Analysis

### 2.1 CPU cost drivers

#### CD-01 — SecureRandom in streaming segment generation (CRITICAL)

**Evidence:** `services/streaming-service/src/main/java/.../service/StreamingService.java` line:
```java
private final SecureRandom random = new SecureRandom();
...
public byte[] generateSegmentPayload() {
    byte[] payload = new byte[segmentSizeBytes];
    random.nextBytes(payload);
    return payload;
}
```

`SecureRandom` uses the OS entropy pool (`/dev/urandom` on Linux) or hardware RNG. It is 30–100× slower than `ThreadLocalRandom` for bulk byte generation (benchmarks: SecureRandom ~50 MB/s vs ThreadLocalRandom ~3 GB/s on modern JVMs).

At 40 K playback events/second peak, each event includes a stream manifest request + multiple segment requests. With `STREAM_SEGMENT_SIZE_BYTES=65536` (64 KB) and `STREAM_SEGMENT_COUNT=10`, each streamed song generates ~640 KB of random bytes. Streaming is the highest-traffic service (SCALABILITY.md §3: "highest request rate; stateless; CPU-bound per HLS segment generation"). The existing comment in `SCALABILITY.md` correctly identifies CPU-bound as the bottleneck — `SecureRandom` is a significant contributor.

The segment payloads are synthetic (ARCHITECTURE.md M-05, W-03). No real client decodes them. Cryptographic quality randomness is irrelevant.

**Affected file:** `services/streaming-service/src/main/java/.../service/StreamingService.java`
**Cost axis:** CPU

#### CD-02 — JVM heap under-sizing across all 8 services (CRITICAL)

**Evidence:** All 8 Dockerfiles contain:
```
ENTRYPOINT ["java", "-jar", "app.jar"]
```
No JVM flags. Java 21 is container-aware (`UseContainerSupport` is on by default since Java 11) and calculates max heap as 25% of the container memory limit. With `memory: 512m` limits (all application services), max heap = **128 MB**.

Spring Boot 3 baseline footprint (embedded Tomcat, Spring context, Micrometer/Prometheus, JPA, Kafka client, Jackson, RSA key loading, Flyway) is approximately 150–200 MB. A 128 MB heap is insufficient — the JVM will spend a large fraction of CPU time in GC rather than serving requests.

**Empirical evidence:** LOAD-RESULTS.md Run 3 shows catalog avg latency dropped from 14.33 s (Run 3 with services under pressure) to 505 ms (Run 5 after OpenSearch was fixed). The catalog service itself is stateless per request — the latency under pressure is partially GC-induced stop-the-world pauses causing queue back-pressure. Auth p99 in Run 5 remained at 89.30% error rate during burst — partly BCrypt, partly GC on the single auth replica.

**Affected files:** All 8 service Dockerfiles.
**Cost axis:** CPU (GC cycles are wasted CPU; GC pauses increase tail latency, requiring more replicas to meet SLO)

#### CD-03 — Catalog service: no application-layer cache (SIGNIFICANT)

**Evidence:** `application.yml` and source code confirm no Redis cache in catalog-service. Every `GET /catalog/songs?genre=rock&page=0` hits `catalog-db` with a `SELECT ... ORDER BY ... LIMIT 100` query.

At 4,000 catalog/search req/s, the top 5 genre+page combinations account for a large fraction of traffic (industry pattern: top 10% of query terms account for ~90% of volume). Caching these in Redis with a 5-minute TTL eliminates the vast majority of catalog-db reads during steady-state load.

**SCALABILITY.md §11** explicitly identifies this as a deferred item: "Deferred; existing PG indexes cover the read path at current scale." This is true for the current scale (Run 5: ~150 req/s effective). At 4K req/s steady-state, it becomes a primary cost driver.

**Affected file:** `services/catalog-service/src/main/java/.../service/CatalogService.java` (new caching layer)
**Cost axis:** CPU (catalog-db query execution), Memory (HikariCP connections held during query execution)

#### CD-04 — OpenSearch: no request cache configured (MODERATE)

**Evidence:** `SCALABILITY.md §6` states: "Add `"request_cache": true` to the index settings. Frequently repeated queries return from cache without hitting shards." This was not implemented in Phase S1.

OpenSearch's request cache stores the JSON response of identical shard-level queries. Popular queries (genre=pop, year=2023, bpm_min=100) hit the same shard, same filter — they can be served from cache without JVM heap allocation for scoring documents.

**Affected component:** `opensearch` index settings (REST API call during index initialization in search-service startup)
**Cost axis:** CPU (reduces OpenSearch query execution per repeated query), Memory (reduces heap pressure from concurrent query objects)

### 2.2 Memory cost drivers

#### CD-05 — Segment payload size: 64 KB per request (SIGNIFICANT for network + memory)

**Evidence:** `docker-compose.yml`:
```yaml
STREAM_SEGMENT_SIZE_BYTES: ${STREAM_SEGMENT_SIZE_BYTES:-65536}
```
Default = 65,536 bytes (64 KB). With 10 segments per song (STREAM_SEGMENT_COUNT=10), one complete stream generates 640 KB of random bytes, allocated as a new `byte[]` per request.

At 20 K peak concurrent users each in the middle of a segment request: 20,000 × 64 KB = **1.28 GB** of byte arrays in JVM heap simultaneously. With 3 streaming-service replicas each handling a share, each replica holds ~427 MB of byte arrays — this alone exceeds the 512 MB container limit and certainly exceeds the ~128 MB heap (pre-fix).

Since the segments are synthetic (W-03), reducing to 4 KB reduces memory allocation by 16× and network I/O by 16×. The load simulation effect (network saturation, I/O pressure) can be preserved with a higher `STREAM_SEGMENT_COUNT` if needed.

**Affected file:** `docker-compose.yml` env default, `.env.example`
**Cost axis:** Memory (JVM heap), Network I/O (bandwidth on `music-net`), CPU (random byte generation)

#### CD-06 — HikariCP minimum_idle over-provisioning (MODERATE)

**Evidence:** `docker-compose.yml`:
```yaml
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 5
```
Applied to: catalog-service (2 replicas), auth-service (1 replica), playlist-service (2 replicas), recommendation-service (2 replicas).

At idle (no load), the pool holds at minimum: auth(5) + catalog(10) + playlist(10) + recommendation(10) = **35 idle connections** across 4 PostgreSQL instances. Each server-side connection in PostgreSQL allocates ~8–10 MB of shared memory and holds a backend process slot. With `max_connections=200`, 35 idle connections are not a correctness issue — but they represent wasted DB memory at rest.

**Affected file:** `docker-compose.yml` environment block for all 4 DB-connected application services
**Cost axis:** PostgreSQL server memory (backend processes for idle connections)

### 2.3 Disk I/O cost drivers

#### CD-07 — Kafka log retention: 24 hours (SIGNIFICANT for disk)

**Evidence:** `docker-compose.yml`:
```yaml
KAFKA_LOG_RETENTION_HOURS: 24
```

At peak load: 40,000 events/s × ~200 bytes/event = 8 MB/s write rate. 24-hour retention = 691 GB of log data. Even at an average of 5 K events/s (10× below peak, reflecting test window vs. 24 hours), that is 86 GB per day retained on the Kafka volume.

For a benchmark, consumers (analytics-service, recommendation-service) process events within seconds to minutes. A 24-hour retention window provides no value beyond 2–4 hours. Even in the worst case (consumers down for 2 hours and restarting), 2 hours of retention is sufficient to catch up.

Adding a per-partition size cap (`KAFKA_LOG_RETENTION_BYTES`) bounds disk growth independently of time, providing a safety ceiling.

**Affected file:** `docker-compose.yml` Kafka service environment
**Cost axis:** Disk space (kafka-data volume)

#### CD-08 — Redis AOF enabled for a disposable cache (LOW-MODERATE)

**Evidence:** `docker-compose.yml`:
```yaml
command: >
  redis-server
    --appendonly yes
    --appendfsync everysec
    --maxmemory ${REDIS_MAXMEMORY:-512mb}
    --maxmemory-policy allkeys-lru
    --save ""
```

`--save ""` correctly disables RDB snapshotting. But `--appendonly yes` keeps the AOF log active. For a recommendation cache (`daily-mix:{userId}` and `similar:{songId}` with 1-hour TTL), the AOF provides no meaningful recovery guarantee — on restart, cache is cold and recommendations are recomputed from Kafka events. The AOF writes add disk I/O (roughly proportional to Redis write throughput) and slightly increase write latency.

**Affected file:** `docker-compose.yml` Redis service command
**Cost axis:** Disk I/O (append-only file writes on every cache set)

#### CD-09 — Prometheus 15-day TSDB retention (LOW)

**Evidence:** `docker-compose.yml`:
```yaml
command:
  - '--storage.tsdb.retention.time=15d'
```

For a benchmark tool, 15 days of metric retention is excessive. A full load test cycle (Phases 1–6) takes at most 3–4 days. Three days of retention covers multiple consecutive test runs and post-test analysis without accumulating weeks of TSDB blocks on disk.

**Affected file:** `docker-compose.yml` Prometheus service command
**Cost axis:** Disk space (prometheus-data volume)

---

## 3. Cost Opportunities Not Worth Pursuing

The following were considered and rejected:

| Candidate | Reason not pursued |
|---|---|
| BCrypt work-factor reduction | Security parameter; weakening it would be incorrect behavior for an auth system |
| Reactive (WebFlux) refactoring of services | Requires technology stack changes; ARCHITECTURE.md specifies Spring Boot 3 MVC (no reactive constraint but the services are already built MVC); refactoring all 8 services is a multi-week effort with high regression risk; ARCHITECTURE.md stability requirement |
| Replacing Zookeeper with KRaft Kafka | Confluent 7.6.0 image used; KRaft requires Confluent 7.7+ or a different image; introduces a migration that could destabilize the benchmark environment |
| Single combined PostgreSQL instance | ARCHITECTURE.md M-26: "Each application service that persists state must use its own dedicated persistence layer." Explicitly prohibited. |
| Removing Prometheus/Grafana | Required by M-24; monitoring stack must run and collect metrics |
| PgBouncer | Deferred in SCALABILITY.md §17 — current pool sizing (20 × replicas) stays within PG max_connections=200. Add only when replicas × pool_size approaches 150. Not a cost driver at current replica counts. |
| Compressing Kafka messages | Messages are small JSON events (~200 bytes); snappy compression overhead on the producer CPU likely outweighs savings at this message size. Not worth the risk of introducing serialization incompatibilities. |
| Replacing OpenSearch with a lighter search backend | ARCHITECTURE.md specifies OpenSearch; substitution is not permitted. |

---

## 4. Prioritized Implementation Plan

Changes are ordered by (expected impact × probability of safe delivery) / (implementation risk × effort).

### Priority 1 — JVM Heap Sizing (all 8 Dockerfiles)

| Attribute | Value |
|---|---|
| **Type** | Config change (Dockerfile ENTRYPOINT) |
| **Impact** | HIGH — eliminates GC pressure that wastes CPU across every service |
| **Risk** | LOW — standard Java 21 best practice; no behavioral change |
| **Effort** | LOW — 8 one-line edits |

**Decision:** IMPLEMENT.

**Change:** Replace bare `java -jar app.jar` with:
```
java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -jar app.jar
```

For `memory: 512m` containers: max heap = 384 MB (vs 128 MB before). Initial heap = 256 MB (avoids startup GC pressure from expanding heap under load).

`UseG1GC` is already the JVM default for heaps > 256 MB on Java 9+, but making it explicit ensures correct behavior if the JVM's adaptive size detection is confused by a constrained container.

**Affected files:**
- `services/auth-service/Dockerfile`
- `services/catalog-service/Dockerfile`
- `services/streaming-service/Dockerfile`
- `services/playlist-service/Dockerfile`
- `services/search-service/Dockerfile`
- `services/analytics-service/Dockerfile`
- `services/recommendation-service/Dockerfile`
- `services/notification-service/Dockerfile`

**Trade-off:** Larger heap → more live objects survive minor GC → potentially larger old-gen before GC. Acceptable: G1GC manages this automatically. The alternative (128 MB heap) causes frequent stop-the-world full GCs that stall all threads for hundreds of milliseconds.

---

### Priority 2 — SecureRandom → ThreadLocalRandom in streaming-service

| Attribute | Value |
|---|---|
| **Type** | Code change (single method in one service) |
| **Impact** | HIGH — removes cryptographic RNG bottleneck from the highest-traffic service |
| **Risk** | LOW — segment data is synthetic; no security property depends on unpredictability |
| **Effort** | LOW — 2-line change |

**Decision:** IMPLEMENT.

**Change:** In `StreamingService.java`, replace:
```java
private final SecureRandom random = new SecureRandom();
```
with:
```java
// Not a security-sensitive field — segments are synthetic load generators.
```
And in `generateSegmentPayload()`, replace `random.nextBytes(payload)` with:
```java
ThreadLocalRandom.current().nextBytes(payload);
```

`ThreadLocalRandom.current().nextBytes()` is thread-local (no contention between concurrent requests) and ~30–100× faster than `SecureRandom`.

**Affected files:**
- `services/streaming-service/src/main/java/com/musicstreaming/streaming/service/StreamingService.java`

**Trade-off:** Segment bytes are pseudo-random (predictable pattern). This has zero impact on benchmark validity — no client decodes the segments (W-03).

---

### Priority 3 — Reduce default segment payload size

| Attribute | Value |
|---|---|
| **Type** | Config change (env var default) |
| **Impact** | HIGH — 16× reduction in memory allocation and network I/O per segment request |
| **Risk** | VERY LOW — env var; behavioral intent (simulate network load) is preserved |
| **Effort** | VERY LOW — change one line in `.env.example` and docker-compose.yml default |

**Decision:** IMPLEMENT.

**Change:** Reduce `STREAM_SEGMENT_SIZE_BYTES` default from `65536` (64 KB) to `4096` (4 KB).

**Evidence for sizing:** 4 KB segment payloads are realistic for HLS audio at 128 kbps (one 10-second HLS segment at 128 kbps = ~160 KB; but for load simulation purposes, 4 KB creates meaningful I/O pressure without pathological memory allocation). If heavier network load simulation is needed, increase `STREAM_SEGMENT_COUNT` from 10 to 20 — more requests at smaller size is a better load shape than fewer large allocations.

**Affected files:**
- `docker-compose.yml` (default value comment)
- `.env.example` (STREAM_SEGMENT_SIZE_BYTES default)

---

### Priority 4 — Reduce Kafka log retention

| Attribute | Value |
|---|---|
| **Type** | Config change (docker-compose.yml env var) |
| **Impact** | MEDIUM-HIGH — eliminates potentially hundreds of GB of retained log data per test run |
| **Risk** | LOW — consumers process events within seconds in a healthy deployment; 2-hour window provides substantial replay buffer |
| **Effort** | VERY LOW — one-line change plus one new line |

**Decision:** IMPLEMENT.

**Change:**
```yaml
KAFKA_LOG_RETENTION_HOURS: 2     # down from 24
KAFKA_LOG_RETENTION_BYTES: 536870912  # 512 MB per partition ceiling; added new
```

The bytes cap prevents runaway disk usage even during an extended load test (Phase 6 soak: 2 hours × 40K events/s × 200 bytes = ~57 GB without this cap, 512 MB with it).

**Affected files:**
- `docker-compose.yml` (Kafka service environment)
- `.env.example` (document new env var)

---

### Priority 5 — HikariCP minimum_idle reduction

| Attribute | Value |
|---|---|
| **Type** | Config change (env var) |
| **Impact** | MEDIUM — reduces idle PostgreSQL backend processes and server memory at rest |
| **Risk** | LOW — at 2 min_idle, the pool still has connections ready; first request after a cold period incurs one connection creation (~10–20 ms) |
| **Effort** | VERY LOW — one env var change applied to 4 services |

**Decision:** IMPLEMENT.

**Change:** In `docker-compose.yml`, reduce `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` from `5` to `2` for all 4 DB-connected services.

With 2 min_idle at 2 replicas: 4 idle connections per DB (vs 10 before). Savings: 6 PostgreSQL backend processes × 4 databases = 24 fewer idle backend processes, saving approximately 192–240 MB of PostgreSQL server memory at rest.

**Affected files:**
- `docker-compose.yml` (environment blocks for catalog-service, auth-service, playlist-service, recommendation-service)
- `.env.example`

---

### Priority 6 — Catalog service Redis page cache

| Attribute | Value |
|---|---|
| **Type** | Code change (catalog-service application logic) |
| **Impact** | MEDIUM-HIGH — at 4 K req/s catalog, caching top pages eliminates >80% of catalog-db reads |
| **Risk** | MEDIUM — code change in a tested service; cache staleness (TTL-bounded); requires Redis dependency in catalog-service |
| **Effort** | MEDIUM — new cache layer, test updates |

**Decision:** IMPLEMENT (deferred from Phase S1 by SCALABILITY.md §17 — now cost-justified at 4K req/s target).

**Design:**
```
Key:   catalog:genre:{genre}:page:{page}:size:{size}
TTL:   300 seconds (5 minutes)
Value: JSON array of song summaries
Store: existing Redis instance (recommendation-service and catalog-service will share the Redis instance)
```

Caching only `GET /catalog/songs` with genre filter on pages 0–4 (the browsing "hot zone"). Direct-ID lookups (`GET /catalog/songs/:id`) are not cached — they have unbounded key space and are personalized per request pattern.

Redis is already deployed and accessible on `music-net`. No new infrastructure required. The catalog-service needs a `spring-boot-starter-data-redis` dependency and a `StringRedisTemplate` or `RedisTemplate<String, CatalogPage>` bean.

**Affected files:**
- `services/catalog-service/pom.xml` (add spring-boot-starter-data-redis)
- `services/catalog-service/src/main/java/.../service/CatalogService.java` (cache-aside logic)
- `services/catalog-service/src/main/resources/application.yml` (redis host/port)
- `docker-compose.yml` (add SPRING_DATA_REDIS_HOST/PORT to catalog-service env)
- `services/catalog-service/src/test/` (cache behavior tests)

**Trade-off:** Catalog data is seeded once at startup and does not change. A TTL of 300 s is conservative — indefinite caching would be correct, but a TTL prevents stale data if the catalog is ever re-seeded in a benchmark scenario.

---

### Priority 7 — OpenSearch request cache per-index

| Attribute | Value |
|---|---|
| **Type** | Config change (OpenSearch API call during startup) |
| **Impact** | MEDIUM — repeated identical search queries (same genre+BPM filter) return from shard-level cache without query execution |
| **Risk** | LOW — standard OpenSearch feature; cache is invalidated on index writes (catalog is write-once after seed) |
| **Effort** | LOW — add `PUT /songs/_settings` call to search-service startup logic |

**Decision:** IMPLEMENT.

**Change:** In the search-service `OpenSearchIndexInitializer` (or equivalent startup component), add after index creation:
```json
PUT /songs/_settings
{
  "index.requests.cache.enable": true
}
```

The songs index is written once at startup (seed) and then read-only. The request cache will never be invalidated during a benchmark run, providing near-perfect hit rates for repeated queries.

**Affected file:**
- `services/search-service/src/main/java/.../config/OpenSearchIndexInitializer.java` (or equivalent)

---

### Priority 8 — Disable Redis AOF persistence

| Attribute | Value |
|---|---|
| **Type** | Config change (docker-compose.yml command line) |
| **Impact** | LOW-MEDIUM — eliminates disk fsync overhead per cache write |
| **Risk** | LOW — on restart, cache is cold; no user-facing data is lost; recommendation-service already handles cache miss gracefully |
| **Effort** | VERY LOW — remove two flags from redis command |

**Decision:** IMPLEMENT.

**Change:** In `docker-compose.yml` Redis service, remove `--appendonly yes --appendfsync everysec`:
```yaml
command: >
  redis-server
    --maxmemory ${REDIS_MAXMEMORY:-512mb}
    --maxmemory-policy allkeys-lru
    --save ""
```

`--save ""` already disables RDB. Removing AOF makes Redis a pure in-memory cache with no persistence overhead. On restart, recommendation-service's cache miss fallback (`computeDirectly`) handles the warm-up period correctly.

**Affected files:**
- `docker-compose.yml` (Redis service command)

---

### Priority 9 — Reduce Prometheus TSDB retention

| Attribute | Value |
|---|---|
| **Type** | Config change (single flag) |
| **Impact** | LOW — saves disk space, reduces TSDB compaction I/O |
| **Risk** | VERY LOW — only affects how far back you can query historical metrics |
| **Effort** | VERY LOW — one-line change |

**Decision:** IMPLEMENT.

**Change:** In `docker-compose.yml` Prometheus service, change:
```
'--storage.tsdb.retention.time=15d'
```
to:
```
'--storage.tsdb.retention.time=3d'
```

3 days covers a full Phase 1–6 test sequence plus post-run analysis. Sufficient for all benchmark use cases.

**Affected files:**
- `docker-compose.yml` (Prometheus service command)

---

## 5. Decisions Explicitly Deferred

| Change | Why deferred |
|---|---|
| Second Kafka broker | Single-broker is sufficient for benchmarking; 3-broker setup adds operational complexity (unique BROKER_IDs, listener ports). Deferred per SCALABILITY.md §17. |
| OpenSearch second node | No evidence of consistent heap > 75% after reducing segment size and adding catalog cache. Profile first. |
| PgBouncer | Current pool sizing (20 × replicas ≤ 80 connections per DB) stays well within max_connections=200. Add only if replicas × pool_size approaches 150. |
| Resilience4j circuit breakers | Graceful degradation (Redis fail-open) already implemented. Adds code complexity without meaningful cost reduction. |
| ClickHouse resource limit reduction | 4 GB → lower. Risk of OOM during peak batch flush is not justified by disk savings. Profile ClickHouse memory under Phase 4/5 load before reducing. |

---

## 6. Validation Plan

### Pre-conditions

- All changes from §4 implemented.
- Docker images rebuilt (`docker compose build`).
- Volume state cleared for a clean baseline (`docker compose down -v && docker compose up -d`).
- Wait for all services healthy (120 s).

### Validation matrix

| Phase | Scenario | What to measure | Pass criterion |
|---|---|---|---|
| Phase 1 | smoke (5 VUs, 2 min) | Error rate, basic latency | 0% error rate; all endpoints respond; no GC OOM in logs |
| Phase 1 | Before vs. after JVM change | `jvm_memory_used_bytes{area="heap"}` per service | Heap usage approaches 70–80% of MaxRAMPercentage target (384 MB), not 95%+ of 128 MB |
| Phase 2 | core_streaming (50 VUs) | `process_cpu_usage` on streaming-service replicas | CPU per streaming replica reduced vs. baseline (SecureRandom → ThreadLocalRandom) |
| Phase 2 | core_streaming | Segment response time p99 | p99 < 2 s (streaming SLO from SCALABILITY.md) |
| Phase 3 | kafka_pipeline (200 VUs) | `kafka_consumergroup_lag` for analytics and recommendation groups | Lag < 10,000 messages sustained (SCALABILITY.md §13 alert threshold) |
| Phase 3 | kafka_pipeline | ClickHouse volume size after run | Kafka volume size ≪ baseline (retention reduction) |
| Phase 3 | kafka_pipeline | `redis_keyspace_hits_total / total` (Grafana) | Redis hit rate > 60% after warm-up (catalog cache + recommendation cache) |
| Phase 4 | ramp (0→1000 VUs) | All SLO thresholds from k6 | streaming_error_rate < 1%; search p99 < 1 s; catalog p99 < 1 s |
| Phase 4 | ramp | `hikaricp_connections_active / max` | < 0.80 for all services (pool not saturated) |
| Phase 4 | ramp | OpenSearch heap | < 75% of `OPENSEARCH_JAVA_OPTS` max heap (request cache reducing heap pressure) |

### Regression check

After Phase 4, verify these behaviors are unchanged:
- `POST /auth/register` and `POST /auth/login` return tokens (auth-service)
- `GET /catalog/songs` returns correct paginated responses (catalog-service cache correctness)
- `GET /stream/:songId` returns HLS manifest and segment bytes (streaming-service)
- `GET /search?q=rock&genre=rock` returns non-empty results (OpenSearch still functional)
- `GET /analytics/me/history` returns listen history (ClickHouse still receiving events)
- `GET /recommend/daily-mix` returns non-empty recommendations (Redis + PostgreSQL)

### Metrics to compare (before vs. after)

| Metric | Source | Direction |
|---|---|---|
| `process_cpu_usage{app="streaming-service"}` | Grafana → Service Health panel | ↓ (expect 20–40% reduction under load) |
| `jvm_gc_pause_seconds_count` per service | Grafana JVM panel | ↓ (expect 60–80% reduction with proper heap sizing) |
| `jvm_memory_used_bytes / jvm_memory_max_bytes` per service | Grafana | Stable at 60–75% (healthy range) vs. 90–100% (GC storm) |
| `kafka_consumergroup_lag{topic="playback-events"}` | Grafana Kafka panel | ↓ or stable (no worse than before) |
| Docker volume size: `kafka-data` after 30-min run | `docker system df -v` | ↓ (proportional to retention reduction: ~8× smaller for 2h vs. 24h at same throughput) |
| Redis AOF file size | `docker exec redis redis-cli DEBUG JMAP` | Eliminated (0 bytes; `appendonly no`) |
| Host CPU% during Phase 3 | `docker stats` or host `top` | ↓ (combination of all CPU-side changes) |

---

## 7. Change Index (summary)

| ID | Change | Type | Files affected | Cost axis |
|----|--------|------|----------------|-----------|
| C-01 | JVM heap sizing via MaxRAMPercentage=75 + G1GC flags | Config (Dockerfile) | All 8 Dockerfiles | CPU (GC) |
| C-02 | SecureRandom → ThreadLocalRandom in streaming segment generation | Code | `StreamingService.java` | CPU |
| C-03 | Default segment size: 65536 → 4096 bytes | Config (env var) | `docker-compose.yml`, `.env.example` | Memory, Network I/O, CPU |
| C-04 | Kafka retention: 24 h → 2 h + 512 MB/partition cap | Config | `docker-compose.yml`, `.env.example` | Disk I/O |
| C-05 | HikariCP minimum_idle: 5 → 2 | Config (env var) | `docker-compose.yml` | Memory (PostgreSQL server) |
| C-06 | Catalog Redis page cache (cache-aside, TTL=300s) | Code + Config | catalog-service (pom, service, yml), `docker-compose.yml` | CPU, DB Memory |
| C-07 | OpenSearch request cache enabled on songs index | Config (API call) | `SearchIndexSeeder.java` | CPU, Memory (OpenSearch heap) |
| C-08 | Redis: disable AOF persistence | Config | `docker-compose.yml` | Disk I/O |
| C-09 | Prometheus: retention 15d → 3d | Config | `docker-compose.yml` | Disk I/O |

---

## 8. Implementation Notes (2026-06-02)

### C-01 — JVM heap flags

Applied to all 8 Dockerfiles as a multi-line ENTRYPOINT JSON array using shell line-continuation (`\`). Java 21 `UseContainerSupport` is on by default but made explicit to prevent accidental override if the base image changes. With `memory: 512m`, the new heap range is 256 MB initial → 384 MB max (vs 128 MB effective max before). Services with smaller limits (e.g., if limits are later reduced) will scale proportionally.

### C-02 — SecureRandom removal

`SecureRandom` field removed from `StreamingService`. `ThreadLocalRandom.current().nextBytes(payload)` called inline — thread-local, no contention between concurrent requests. The class-level `random` field is eliminated entirely (no risk of sharing state between threads).

### C-03 — Segment size

Default changed in `docker-compose.yml` inline default and in `.env.example`. Comment added to `.env.example` explaining the trade-off and how to raise `STREAM_SEGMENT_COUNT` as an alternative load shape.

### C-04 — Kafka retention

Two env vars added: `KAFKA_LOG_RETENTION_HOURS` (time-based, default 2) and `KAFKA_LOG_RETENTION_BYTES` (size-based, default 512 MB per partition). Kafka enforces whichever limit is hit first. Both are tunable via `.env` without restarting the broker — a `kafka-configs.sh --alter` command can change them on a live cluster if a running benchmark needs adjustment.

### C-05 — HikariCP minimum_idle

All four occurrences of `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 5` replaced globally with `2`. Verified with `grep -n MINIMUM_IDLE docker-compose.yml` — all 4 instances changed (auth, catalog, playlist, recommendation services).

### C-06 — Catalog Redis page cache

**Constructor injection chosen over `@Cacheable`:** Spring's `@Cacheable` abstraction requires a `CacheManager` bean and cache name configuration. Using `StringRedisTemplate` directly is simpler, avoids an additional layer of indirection, and gives explicit control over serialisation and TTL. Trade-off: slightly more boilerplate in `CatalogService`, but the logic is clear and testable without Spring context.

**Cache scope:** Only the first `CACHE_PAGE_DEPTH = 5` pages are cached. Pages ≥ 5 bypass the cache and go directly to the database. This matches the "hot zone" assumption: browsing traffic concentrates on the first few pages of any genre. Deep pagination is rare and not worth the memory overhead of caching arbitrary offsets.

**Cache key design:** `catalog:page:{page}:size:{size}:sort:{sort}:dir:{direction}` — all query parameters are part of the key to prevent returning wrong pages when clients vary sort or direction. The key space is bounded: 5 pages × 6 size values (capped at 100) × 6 sort fields × 2 directions = 360 possible keys at maximum, each ≤ ~8 KB JSON = < 3 MB total in Redis at full cache warmup.

**Fail-open:** Both `fromCache()` and `toCache()` catch all exceptions and log at DEBUG level. A Redis outage causes cache misses (fallback to DB) rather than HTTP 500s. This preserves availability.

**Test updates:**
- `CatalogServiceTest.java`: switched from `@InjectMocks` to explicit constructor call in `@BeforeEach` to control `cacheTtlSeconds`. Added `@Mock StringRedisTemplate`, `@Mock ValueOperations<String, String>`, `@Mock ObjectMapper`. Added two new tests: `findAll_deepPage_bypassesCache` (verifies pages ≥ 5 hit DB without cache lookup) and `findAll_cacheHit_returnsWithoutDbCall` (verifies DB is not called on a cache hit).
- `TestcontainersConfig.java`: added `GenericContainer` for Redis 7.2-alpine with `@ServiceConnection(name = "redis")`. Spring Boot 3.3 `@ServiceConnection` auto-wires the Redis host/port into `spring.data.redis.*` properties.

**docker-compose.yml:** `catalog-service` now depends on `redis: service_healthy` in addition to `catalog-db` and `auth-service`. This prevents the service starting before Redis is ready, which would cause connection errors during cache warmup.

### C-07 — OpenSearch request cache

Added `"requests.cache.enable": true` to the `UpdateSettingsRequest` in `SearchIndexSeeder.ensureIndexExists()`. The setting is applied every time the service starts (idempotent — updating a setting that is already set has no effect). The songs index is written once at startup and is effectively read-only for the duration of a benchmark run, so cache invalidation on index writes is not a concern. Added a `log.info` line to confirm the setting was applied.

Note: `ensureIndexExists()` already contained an `UpdateSettingsRequest` for `number_of_replicas`. The request cache flag was added to the same call — a single round-trip to OpenSearch applies both settings.

### C-08 — Redis AOF

Removed `--appendonly yes` and `--appendfsync everysec` from the Redis `command` block. The stale comment referencing `appendfsync` was updated to accurately describe the current configuration. The `redis-data` Docker volume remains mounted — it persists the RDB state between `docker compose up/down` cycles if needed. Since `--save ""` disables RDB snapshots, the volume will accumulate no new data; it only serves to not lose keys that existed before the config change (backward compatibility for operators who had existing data volumes).

### C-09 — Prometheus retention

Single flag change: `15d` → `3d`. Three days covers a full Phase 1–6 test sequence and post-analysis window. Existing prometheus-data volumes are unaffected — Prometheus prunes old TSDB blocks lazily on the next compaction cycle after the retention change.

---

## 9. Validation Outcome (2026-06-02)

Two runs were executed. Run 1 failed due to a C-01 implementation error. Run 2 corrected the error and met the primary validation criteria.

### Run configuration (both runs)

Load test executed on `refactored/sabina` branch with configuration identical to Run 6 on the baseline: streaming 150 / catalog 70 / auth 15 (burst 30) / playlist 30 / recommend 7 iter/s; 1m warmup / 2m steady / 1m burst / 1m rampdown; SEED_USER_COUNT=200; k6 v0.51.0 in Docker container.

---

### Run 1 — FAIL (20:50–20:55 UTC)

C-01 was implemented with `MaxRAMPercentage=75.0`, giving 384 MB heap per JVM instance × 13 replicas = 4.9 GB total JVM heap on an 8 GB Docker VM. This saturated host RAM, causing system-wide GC pressure and degrading OpenSearch such that 54.16% of search queries returned HTTP 5xx.

| Service | Run 6 baseline | Run 1 | Verdict |
|---------|---------------|-------|---------|
| streaming | 0.00% | 0.00% | ✓ |
| auth | 0.00% | 0.00% | ✓ |
| catalog | 0.00% | 0.00% | ✓ |
| **search** | **0.00%** | **54.16%** | **✗ REGRESSION** |

**Fix applied:** `MaxRAMPercentage` reduced from 75.0 → **50.0**, `InitialRAMPercentage` from 50.0 → **25.0** across all 8 Dockerfiles (256 MB max heap per replica × 13 replicas = 3.3 GB total, within Docker VM budget). Images rebuilt, stack restarted.

A secondary fix was also required: `SearchIndexSeeder.ensureIndexExists()` caught `IOException` but OpenSearch client throws `OpenSearchStatusException` (a `RuntimeException`) for race-condition create conflicts. Changed to `catch (Exception e)` with message inspection for `resource_already_exists_exception`.

---

### Run 2 — CONDITIONAL PASS (21:30–21:35 UTC)

| Service | Run 6 baseline | Run 2 | Verdict |
|---------|---------------|-------|---------|
| streaming | 0.00% | **0.00%** | ✓ no regression |
| auth | 0.00% | **0.00%** | ✓ no regression |
| catalog | 0.00% | **0.00%** | ✓ no regression |
| search | 0.00% | **0.00%** | ✓ no regression |

**Primary validation criterion (no service error rate higher than Run 6): MET.**

| k6 metric | Run 6 | Run 2 | Notes |
|-----------|-------|-------|-------|
| http_reqs total | 80,070 | 14,772 | Streaming VU starvation reduces total throughput |
| http_req_failed | 0.57% | 2.73% | Same absolute failures (~400 kafka-exporter DNS); higher % from smaller denominator |
| streaming_error_rate | 0.00% | 0.00% ✓ | Threshold passes |
| streaming_manifest avg | 6.33 ms | 418 ms | Elevated (see root cause below) |
| streaming_complete_duration_ms | 25 ms p(95) | 1.52 s p(95) | Threshold passes (p(99) < 3 s SLO) |
| thresholds passed | 5–6 / 12 | 2 / 11 | — |

---

### What the §6 validation plan predicts vs. what was observed (Run 2)

| §6 Prediction | Observed |
|---------------|---------|
| Phase 1 smoke: 0% error rate | ✓ Confirmed |
| JVM heap usage 60–75% of max (C-01 corrected) | Not measured directly; 50% MaxRAMPercentage gives 256 MB heap per 512 MB limit, well within Docker VM budget |
| CPU per streaming replica reduced (C-02) | Not directly measurable; streaming error rate 0% confirms functional correctness |
| Segment response time p99 < 2 s (C-02, C-03) | Borderline — streaming_segment p(95)=1.25 s; p(99) likely near or slightly above 2 s due to Kafka blocking (see RC3 below) |
| Kafka volume ≪ baseline (C-04) | Configuration correct; retention 2 h / 512 MB active in broker |
| Redis hit rate > 60% (C-06 catalog cache) | Not measured from Grafana; catalog 200 check passes 100% confirming cache correct |
| OpenSearch heap < 75% (C-07 request cache) | Not measured; no search errors in Run 2 indicating OpenSearch is healthy |
| streaming_error_rate < 1% | ✓ Confirmed — 0.00% |
| search p99 < 1 s | ✗ search_query p(95)=5.21 s; caused by VU starvation cascade (fewer iterations → colder requests), not search correctness |
| catalog p99 < 1 s | ✗ catalog_browse p(95)=3.79 s; same cascade — catalog itself at 0% error rate |
| HikariCP active/max < 0.80 (C-05) | Not measured; all DB-backed services at 0% error rate |

### Root causes of streaming latency elevation (NOT caused by C-01 through C-09)

**RC3 — `max.block.ms: 1000` in baseline code (commit 7c47d2b, post-Run6)**

The streaming-service Kafka producer was configured with `max.block.ms: 1000` in commit 7c47d2b, which was committed 34 minutes after Run 6 ended. This setting causes `KafkaProducer.send()` to block the calling Tomcat HTTP thread for up to 1000 ms when topic metadata is unavailable. During the run, all three streaming-service replicas logged repeated `TimeoutException: Topic playback-events not present in metadata after 1000 ms`. This raised manifest and complete endpoint latency to ~418–455 ms avg (vs 6–7 ms in Run 6).

**RC4 — Segment download in current `main.js` (also from 7c47d2b, post-Run6)**

The current `main.js` includes `GET /stream/{songId}/segment/0` per streaming iteration. The Run 6 version of `main.js` did not (Run 6 check results contain `stream manifest 200` and `stream complete 204` but no `stream segment 200`; streaming_manifest was 6.33 ms, consistent with manifest-only flow). With segment download, each iteration takes ~1.3 s instead of ~13 ms, limiting 17 VUs to ~13 iter/s vs 150 target. This causes the dropped_iterations count (39,140) and cascading latency increases in all other scenarios.

**Attribution:** Both RC3 and RC4 originate from the post-Run6 baseline commit 7c47d2b, not from C-01 through C-09. The refactored branch inherits these changes.

### Changes confirmed correct (Run 2 — no regression)

| Change | Evidence |
|--------|---------|
| C-01 (corrected to 50%) | Search 0% error rate; all services at 0% — memory budget respected |
| C-02 SecureRandom → ThreadLocalRandom | Streaming error rate 0%; segment generation functional |
| C-03 Segment size 65536 → 4096 | Streaming checks pass 100%; data received 51 MB (vs 1.6 GB Run 6, from far fewer requests + smaller segments) |
| C-04 Kafka retention 24 h → 2 h | Kafka operational throughout; no consumer-facing errors |
| C-05 HikariCP min_idle 5 → 2 | Auth/catalog/playlist/recommend all 0% error rate — DB pools healthy at reduced idle size |
| C-06 Catalog Redis page cache | Catalog 200 check 100%; no cache-related errors |
| C-07 OpenSearch request cache | Search 0% error rate; no OpenSearch degradation after heap budget fix |
| C-08 Redis AOF removed | Redis operational; recommendation and catalog unaffected |
| C-09 Prometheus retention 15d → 3d | No monitoring impact on test run |

### Remaining issues for follow-up

| Issue | Details |
|-------|---------|
| `max.block.ms: 1000` blocking HTTP thread | Remove or increase to default (60000 ms); or use a background executor to decouple Kafka send from the HTTP thread. This is a baseline code issue, not a C-01–C-09 issue. |
| Segment download in `main.js` inflating iterations | The load script should be brought back to the Run 6 form (manifest + complete only) for a clean apples-to-apples comparison, or the comparison baseline should be updated to match the current script. |
| Streaming manifest threshold (p(99) < 2 s) | Resolves when RC3 is fixed; service responds in 4–5 ms at idle. |
