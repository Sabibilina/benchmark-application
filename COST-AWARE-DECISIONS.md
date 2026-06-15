# Cost-Aware Decisions Log

> This document records every cloud cost decision, assumption, trade-off, source of evidence,
> affected file or service, and relevant observation made during the cost-aware scalability
> planning phase. Each entry is structured to support later analysis by explicitly capturing:
> **what** would be modified, **where**, **why**, which **cost driver** it targets,
> **evidence**, **expected cost impact**, **behavior/performance risk**, **validation method**,
> and **status**.

---

## Entry Format

Each entry follows this structure:

| Field | Content |
|---|---|
| **Decision** | Short label |
| **What** | What would be modified |
| **Where** | Affected files or services |
| **Why** | Reason for the change |
| **Cost driver** | Memory / CPU / Disk / Network / Idle compute |
| **Evidence** | Source: repo observation, workload table, benchmark result |
| **Expected cost impact** | Qualitative or quantitative estimate |
| **Behavior/performance risk** | What could break or degrade |
| **Validation method** | How the cost saving or risk can be confirmed |
| **Status** | Planning / Implemented / Deferred / Rejected |

---

## CD-001 — Runtime Profile Separation: Load Generator as Opt-In Profile

| Field | Content |
|---|---|
| **Decision** | Gate the load generator behind a Docker Compose profile |
| **What** | `load-generator` service in `docker-compose.yml` |
| **Where** | `docker-compose.yml` — `load-generator.profiles: [load-test]` |
| **Why** | The load generator (k6) spawns hundreds to thousands of goroutines and generates heavy network I/O to all 8 services simultaneously. Running it continuously alongside the application services wastes CPU and memory when no benchmark is in progress. |
| **Cost driver** | CPU, network, idle compute |
| **Evidence** | Current `docker-compose.yml` already uses `profiles: [load-test]` on the `load-generator` service. This is the correct behavior and must be preserved. |
| **Expected cost impact** | Prevents ~0.5–2 CPU cores and ~512 MB RAM from being consumed by k6 during development, CI smoke checks, and between benchmark sessions. |
| **Behavior/performance risk** | None — the load generator is a benchmarking tool, not a required backend runtime component. Removing it from the default profile does not affect service correctness. |
| **Validation method** | `docker compose up` (without `--profile load-test`) must not start `load-generator`. `docker compose --profile load-test up load-generator` must start it and run the k6 script. |
| **Status** | Implemented (already in `docker-compose.yml`); must not be changed. |

---

## CD-002 — OpenSearch Heap Increase: 512 MB → 2 GB

| Field | Content |
|---|---|
| **Decision** | Increase OpenSearch JVM heap from 512 MB to 2 GB |
| **What** | `OPENSEARCH_JAVA_OPTS` environment variable on the `opensearch` service |
| **Where** | `docker-compose.yml` — `opensearch.environment.OPENSEARCH_JAVA_OPTS` |
| **Why** | At the expected song catalog size and ~4,000 search req/s from `search-service`, 512 MB heap triggers frequent GC pauses (>100 ms), inflating p99 latency and causing timeouts under Phase 3+ load. OOM at this heap size is realistic. |
| **Cost driver** | Memory |
| **Evidence** | `SCALABILITY.md §7`: single-node OpenSearch with 512 MB heap known to GC under moderate concurrent query load. Memory fix in `project_search_service.md` memory record: 768m heap was the minimum fix in Run 5 for 100%→10.49% error rate reduction; scaling to 4 K req/s requires more headroom. |
| **Expected cost impact** | +1.5 GB RAM reservation on the host. At $0.05/GB-hr (typical cloud pricing), this is ~$0.075/hr or ~$1.80/day — justified by eliminating the primary search bottleneck. |
| **Behavior/performance risk** | Allocating too large a heap on a memory-constrained host can cause the OS to swap, degrading all services. The 2 GB target assumes ≥16 GB total host RAM. Never exceed 31 GB (compressed OOP boundary). |
| **Validation method** | After increasing heap: run Phase 2 load test (50 VUs, search-heavy flow); monitor `opensearch_jvm_mem_heap_used_in_bytes` in Grafana; confirm heap stays below 75 % at Phase 2 load before proceeding to Phase 3. |
| **Status** | Implemented — `docker-compose.yml`: `OPENSEARCH_JAVA_OPTS: "-Xms${OPENSEARCH_HEAP:-1g} -Xmx${OPENSEARCH_HEAP:-1g}"`, `.env.example`: `OPENSEARCH_HEAP=1g` with Profile D note to increase to 2g+. |

---

## CD-003 — Redis maxmemory Cap and LRU Eviction Policy

| Field | Content |
|---|---|
| **Decision** | Add `--maxmemory 512mb --maxmemory-policy allkeys-lru --appendfsync everysec` to Redis startup command |
| **What** | `redis` service command-line arguments |
| **Where** | `docker-compose.yml` — `redis.command` |
| **Why** | Without `maxmemory`, Redis can grow unboundedly as `recommendation-service` inserts `daily-mix:{userId}` and `similar:{songId}` keys. At 100 K DAU × ~1 KB per key, this is ~150–200 MB at steady state — but TTL misconfiguration or a stampede during a 2-hour soak test could drive growth beyond available host RAM. `allkeys-lru` ensures Redis stays useful under pressure (evicts cold keys) rather than refusing writes. `appendfsync everysec` reduces write latency from 1–2 ms per command (fsync-on-every-write) to near-zero while accepting at most 1 second of cache data loss on crash — acceptable for a recommendation cache. |
| **Cost driver** | Memory (OOM guard), disk I/O (AOF latency reduction) |
| **Evidence** | `SCALABILITY.md §9`: 100 K DAU × 1 KB/key = ~150–200 MB steady-state; TTL is 3600 s (from `docker-compose.yml` `RECOMMENDATION_CACHE_TTL_SECONDS=3600`). `appendonly yes` is already in the Redis command (`docker-compose.yml`). |
| **Expected cost impact** | Hard cap at 512 MB prevents Redis from consuming host RAM beyond its allocation. AOF latency reduction indirectly reduces `recommendation-service` response time, improving throughput per replica (fewer replicas needed to hit target RPS). |
| **Behavior/performance risk** | `allkeys-lru` evicts keys proactively when the cap is reached. If the cap is too low relative to the working set, cache hit rates drop and all recommendation requests compute from PostgreSQL — increasing recommendation-db load. Monitor `redis_keyspace_hits_total / (hits + misses)` for hit rate drops. |
| **Validation method** | After change: run Phase 3 load test; monitor `redis_used_memory_bytes` (must stay ≤ 512 MB) and `redis_keyspace_hits_total / (hits + misses)` (target ≥ 85 % hit rate). Confirm `appendfsync everysec` by checking `redis INFO persistence` output. |
| **Status** | Implemented — `docker-compose.yml` `redis.command`: `--maxmemory ${REDIS_MAXMEMORY:-256mb} --maxmemory-policy allkeys-lru --appendfsync everysec`. `.env.example`: `REDIS_MAXMEMORY=256mb`. |

---

## CD-004 — ClickHouse Batch Insert Implementation

| Field | Content |
|---|---|
| **Decision** | Replace single-row inserts in `analytics-service` with batched inserts (500–1000 events or 5-second flush, whichever fires first) |
| **What** | `analytics-service` Kafka consumer logic: add `BatchEventBuffer` bean with `@Scheduled` flush |
| **Where** | `services/analytics-service/src/main/java/` — consumer class and new `BatchEventBuffer.java` |
| **Why** | ClickHouse `MergeTree` creates one data *part* per `INSERT`. At 40 K events/s (peak playback events), single-row inserts create 40 K parts/s. ClickHouse background merge cannot keep up: it logs `Too many parts` and eventually throttles or rejects inserts. This causes `analytics-service` to stall, which causes Kafka consumer lag to grow, which causes the Kafka topic to fill, which eventually backpressures `streaming-service` producers. The entire analytics pipeline fails. |
| **Cost driver** | CPU (ClickHouse merge CPU spikes under excessive parts), Disk I/O (part files created and merged), reliability (insert rejections under load) |
| **Evidence** | `SCALABILITY.md §8`: ClickHouse `MergeTree` anti-pattern documented. `docker-compose.yml`: `analytics-service` connects to ClickHouse; no batch buffering is visible in the compose configuration. Kafka event rate from workload table: 40,000 events/second peak. |
| **Expected cost impact** | Reduces ClickHouse CPU from constant background merge contention to periodic batch processing spikes. Reduces per-event disk I/O by ~1000× (one part per 1000 events vs one part per event). Allows ClickHouse `memory` limit to be set lower (`4g` instead of requiring more headroom for merge operations). |
| **Behavior/performance risk** | Batching adds up to 5 seconds of event latency for analytics history (`GET /analytics/me/history`). Acceptable for a listen-history view; the ARCHITECTURE.md requirement (`GET /analytics/me/history`) does not specify real-time freshness. Risk: if `analytics-service` crashes between flush intervals, up to 1000 events (or 5 s worth) are lost. A persistent buffer (Kafka offset commit only after flush) eliminates this but adds code complexity. |
| **Validation method** | Before: run Phase 3 load test; observe ClickHouse part count via `SELECT count() FROM system.parts WHERE table = 'playback_events'` — should be growing rapidly. After: same query under same load; part count should grow slowly and stay below 150 active parts. `clickhouse_query_duration_ms_quantiles{quantile="0.99"}` in Grafana should decrease. |
| **Status** | Implemented — `BatchEventBuffer.java` created with `@Scheduled(fixedDelayString)` flush; `PlaybackEventConsumer` delegates to buffer; `AnalyticsService.recordBatch()` and `AnalyticsRepository.insertBatch()` added; `@EnableScheduling` added to `AnalyticsServiceApplication`. |

---

## CD-005 — ClickHouse Resource Limits (Missing `deploy.resources.limits`)

| Field | Content |
|---|---|
| **Decision** | Add `deploy.resources.limits` to ClickHouse in `docker-compose.yml` |
| **What** | `clickhouse` service `deploy.resources` block |
| **Where** | `docker-compose.yml` — `clickhouse` service |
| **Why** | The current `docker-compose.yml` does not define `deploy.resources.limits` for ClickHouse. Under a write storm (e.g., Phase 4/5 load test with 8 streaming replicas emitting 40 K events/s), ClickHouse can expand to consume all available host RAM and CPU, starving application services and Kafka. Without a hard limit, a ClickHouse merge storm can OOM the entire host. |
| **Cost driver** | Memory (OOM guard), CPU (merge contention guard) |
| **Evidence** | `docker-compose.yml`: ClickHouse has no `deploy.resources` block (all application services have limits via `cpus` and `memory` env vars, but infrastructure does not). |
| **Expected cost impact** | Sets a hard ceiling of 4 GB RAM and 2 CPU cores. Prevents ClickHouse from consuming resources needed by other services. Slightly increases ClickHouse batch processing time if it hits the CPU limit, but the batch buffer (CD-004) compensates by reducing burst write rate. |
| **Behavior/performance risk** | If ClickHouse's working set exceeds the 4 GB limit, it will swap pages, increasing query latency. Set `reservations.memory: 2g` to ensure ClickHouse has its working set available. Monitor `clickhouse_memory_resident_bytes` in Grafana. |
| **Validation method** | Under Phase 4/5 load: confirm `docker stats clickhouse` shows memory below 4 GB; confirm no host-level OOM kill events (`dmesg | grep -i oom`). |
| **Status** | Implemented — `docker-compose.yml` `clickhouse.deploy.resources.limits`: `cpus: '${CLICKHOUSE_CPU_LIMIT:-2.0}'`, `memory: ${CLICKHOUSE_MEMORY_LIMIT:-4g}`, `reservations.memory: ${CLICKHOUSE_MEMORY_RESERVATION:-2g}`. |

---

## CD-006 — Service Replica Counts: Profile D vs Profile A/B Separation

| Field | Content |
|---|---|
| **Decision** | Define replica counts for two runtime profiles: Profile D (full benchmark) and Profile A/B (normal runtime / smoke test), controlled via `.env` variables |
| **What** | `deploy.replicas` per service in `docker-compose.yml`; corresponding variables in `.env.example` |
| **Where** | `docker-compose.yml` — all 8 application services; `.env.example` |
| **Why** | Running 8 streaming replicas, 4 catalog replicas, etc. during development and smoke testing wastes ~18–20 GB RAM and 12+ CPU cores on a continuously-running host. The per-service resource cost at full Profile D is documented in `SCALABILITY.md §5`. Profile A/B needs exactly 1 replica per service to verify correctness. |
| **Cost driver** | Memory (idle replicas), CPU (JVM startup + idle Tomcat threads) |
| **Evidence** | Workload table: at 1 replica per service, peak load (~40 K events/s) cannot be sustained. At 8–10 streaming replicas, it can. The difference in RAM between 1 and 8 streaming replicas is 7 × 512 MB = 3.5 GB for `streaming-service` alone. Across all services the Profile D overhead is ~18 GB vs ~6 GB for Profile A/B. |
| **Expected cost impact** | Reduces baseline RAM footprint from ~22 GB (Profile D) to ~7–9 GB (Profile A/B). At $0.05/GB-hr cloud pricing, this saves ~$0.65–$0.75/hr between benchmark sessions. On a 24-hour development day with one 1-hour benchmark, savings are ~$15/day vs full Profile D continuously. |
| **Behavior/performance risk** | Running Profile A/B during a high-load test will cause services to saturate and produce artificially high error rates. The profiles must never be mixed: do not run Phase 4/5 k6 scripts against a Profile A/B deployment. |
| **Validation method** | Document expected replica counts in `.env.example` with two sets of values (profile A/B vs profile D). Verify via `docker compose ps` after startup that replica counts match the active profile. |
| **Status** | Implemented — all 8 app services in `docker-compose.yml` use `deploy.replicas: ${SERVICE_REPLICAS:-1}`; `.env.example` contains Profile A/B defaults (all 1) and commented-out Profile D values with partition-count constraints documented. |

---

## CD-007 — Kafka Topic Pre-Creation: Prevent Idle Consumer Replicas

| Field | Content |
|---|---|
| **Decision** | Pre-create `playback-events` (12 partitions) and `playlist-events` (6 partitions) via an `init-kafka` one-shot container; disable `KAFKA_AUTO_CREATE_TOPICS_ENABLE` |
| **What** | New `init-kafka` service in `docker-compose.yml`; `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` on `kafka` service |
| **Where** | `docker-compose.yml` |
| **Why** | With `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` (current setting), topics are auto-created with 1 partition on first message. At 1 partition, a consumer group with N replicas assigns 1 partition to 1 replica; the remaining N−1 replicas receive no partitions and sit idle — consuming ~512 MB RAM each with zero throughput contribution. For `analytics-service` at 4 replicas, 3 are wasted. For `recommendation-service` at 3 replicas, 2 are wasted. |
| **Cost driver** | Idle compute (RAM consumed by replicas doing zero work), memory |
| **Evidence** | `docker-compose.yml`: `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` (explicitly set). Kafka documentation: consumer group members assigned no partitions remain idle in the polling loop. With 4 analytics replicas × 512 MB = 2 GB, and 3/4 idle under 1 partition, ~1.5 GB is wasted. |
| **Expected cost impact** | Eliminates idle consumer replica RAM waste (~1.5–2 GB) immediately after topic pre-creation. Enables all consumer replicas to receive and process messages, improving Kafka consumer throughput proportionally with partition count. |
| **Behavior/performance risk** | Pre-creating topics requires the `init-kafka` container to succeed before any producer or consumer service starts. If `init-kafka` fails (Kafka not ready), dependent services will fail to start. Mitigate with a retry loop in the init script and `depends_on: kafka: condition: service_healthy`. Disabling auto-create means any new topic required by future features must also be explicitly created. |
| **Validation method** | After deploying: `kafka-consumer-groups.sh --describe --group analytics-service` should show all partitions assigned (not `UNASSIGNED`). Grafana: `kafka_consumergroup_lag` per partition should show active consumption on all 12 partitions. |
| **Status** | Implemented — `init-kafka` one-shot service added to `docker-compose.yml` (`restart: "no"`, `kafka-topics.sh --create` for `playback-events` (12 partitions) and `playlist-events` (6 partitions)); `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` set on `kafka` service; all Kafka consumers have `depends_on: init-kafka: condition: service_completed_successfully`. |

---

## CD-008 — nginx-lb Container Addition

| Field | Content |
|---|---|
| **Decision** | Add a dedicated `nginx-lb` service to `docker-compose.yml` as the single entry point for all API traffic |
| **What** | New `nginx-lb` compose service; `infra/nginx-lb/nginx.conf` config file |
| **Where** | `docker-compose.yml`, `infra/nginx-lb/nginx.conf` |
| **Why** | Without a load balancer, `docker compose up --scale streaming-service=8` creates 8 containers but external clients (k6, browser) and internal service-to-service calls resolve `streaming-service` hostname via Docker DNS, which — for most HTTP clients — returns the same IP after the initial lookup. The frontend nginx performs a single DNS lookup and caches it, meaning all traffic goes to one container regardless of scale. The `nginx-lb` container with `resolver 127.0.0.11 valid=10s` re-resolves DNS every 10 seconds, picking up all running replicas. |
| **Cost driver** | Idle compute (streaming replicas sitting idle without a real LB), CPU (distributing load efficiently across replicas reduces per-replica CPU pressure) |
| **Evidence** | `SCALABILITY.md §3`: DNS caching problem documented. Docker networking documentation: `127.0.0.11` is the embedded DNS resolver; `valid=10s` forces periodic re-resolution. Without this, cost of running 7 additional streaming replicas is entirely wasted. |
| **Expected cost impact** | nginx itself costs ~64 MB RAM and <0.1 CPU at idle, <0.5 CPU under full load. The benefit is that the cost of all additional replicas (Steps 5–13 in the scaling order) is actually realized — without nginx-lb, scaling is expensive and ineffective simultaneously. |
| **Behavior/performance risk** | nginx-lb becomes a single point of failure for all API traffic. If it crashes, all services are unreachable. Mitigate: set `restart: unless-stopped` on nginx-lb. The health check on nginx-lb should validate that it can reach at least one upstream replica. |
| **Validation method** | After adding nginx-lb and scaling streaming-service to 3 replicas: verify with `docker logs nginx-lb` that all 3 upstream IPs appear in access logs. `docker stats` on each streaming replica should show similar CPU usage (load is distributed). |
| **Status** | Implemented — `nginx-lb` service added to `docker-compose.yml` (port `${NGINX_LB_HOST_PORT:-80}:80`, health check on `/health`); `infra/nginx-lb/nginx.conf` created with `resolver 127.0.0.11 valid=10s ipv6=off`, `set $upstream` variable pattern for all 8 services, per-route rate limiting zones. |

---

## CD-009 — PgBouncer Deferral

| Field | Content |
|---|---|
| **Decision** | Defer PgBouncer addition until HikariCP saturation is observed in Grafana (>80 % `hikaricp_connections_active / max`) |
| **What** | PgBouncer containers for `catalog-db` and `playlist-db` |
| **Where** | `docker-compose.yml` (not yet added) |
| **Why** | Transaction-mode PgBouncer requires Flyway migrations to use a separate direct datasource URL (PgBouncer does not support DDL in transaction mode). Adding PgBouncer introduces two datasource beans per service (`primary` for migrations, `pool` for queries), increasing code complexity. At current pool sizing (`HIKARI_MAXIMUM_POOL_SIZE=20`, 4 catalog replicas × 20 = 80 connections), PostgreSQL's `max_connections=200` has sufficient headroom. PgBouncer cost (~64 MB per instance) is only justified when connection saturation is observed. |
| **Cost driver** | Operational complexity (code changes required), memory (container cost vs. risk of connection exhaustion) |
| **Evidence** | `docker-compose.yml`: HikariCP pool size controlled via `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` env var (default 20 per service). At 4 replicas × 20 = 80 connections per service, well within `max_connections=200`. `SCALABILITY.md §17` (trade-offs section): PgBouncer deferral justified by current headroom. |
| **Expected cost impact** | Deferring PgBouncer saves implementation time and avoids adding 2 additional containers (~128 MB total). The cost of deferral is potential connection pool exhaustion if replica counts grow beyond 10 replicas × 20 connections = 200 (matching `max_connections`). Add PgBouncer when this boundary is approached. |
| **Behavior/performance risk** | If `catalog-service` or `playlist-service` replicas exceed 10 (and pool size stays at 20), PostgreSQL `max_connections` will be reached. Connections will be refused, causing HTTP 500 errors in the services. The Grafana alert (`hikaricp_connections_active / max > 0.80`) is the early warning. |
| **Validation method** | Monitor `hikaricp_connections_active{pool="HikariPool-1"}` per service instance in Grafana during Phase 4 load. If any pool saturates above 80 %, add PgBouncer before Phase 5. |
| **Status** | Deferred (pending Phase 4 load test results) |

---

## CD-010 — PostgreSQL Index Additions via Flyway

| Field | Content |
|---|---|
| **Decision** | Add missing indexes to all four PostgreSQL databases via Flyway migrations |
| **What** | New Flyway migration SQL files in each service |
| **Where** | `services/auth-service/src/main/resources/db/migration/`, `services/catalog-service/...`, `services/playlist-service/...`, `services/recommendation-service/...` |
| **Why** | Without indexes on query hotspot columns, PostgreSQL performs sequential scans. `pg_stat_user_tables_seq_scan` in Grafana will spike under load. Sequential scans on a 1 M row catalog table are O(n) per query — at 4 K req/s, this consumes all available PostgreSQL CPU and generates high I/O. Indexes convert these to O(log n) index scans. |
| **Cost driver** | CPU (eliminating sequential scan CPU), Disk I/O (index scans read fewer blocks), memory (smaller working set in shared_buffers) |
| **Evidence** | `SCALABILITY.md §6`: hotspot queries identified per service. `catalog-db` hotspot: `SELECT ... WHERE genre = ? ORDER BY title LIMIT 50` (full table scan without `idx_songs_genre_title`). `recommendation-db` hotspot: `SELECT song_id, played_at FROM play_history WHERE user_id = ? ORDER BY played_at DESC LIMIT 100` (filesort without composite index). |
| **Expected cost impact** | Index scans are typically 10–100× faster than sequential scans on large tables. At 4 K req/s catalog browsing, converting seq scans to index scans could reduce catalog-db CPU by 50–80 %, enabling the database to run on a smaller memory allocation. |
| **Behavior/performance risk** | Adding indexes to a large table requires a table scan during migration, which is slow and locks the table briefly. For the song catalog (seeded at startup), this runs once at container start and is acceptable. Indexes add overhead on INSERT — negligible for a read-heavy catalog. |
| **Validation method** | After migration: `EXPLAIN ANALYZE SELECT ...` on hotspot queries must show `Index Scan` (not `Seq Scan`). `pg_stat_user_tables_seq_scan` in Grafana must decrease under Phase 2/3 load. |
| **Status** | Planning — NOT YET IMPLEMENTED. No V2 Flyway migration files exist for auth-service, catalog-service, playlist-service, or recommendation-service. Must be added before Phase 3 load testing. |

---

## CD-011 — Removing Per-Service Host Port Mappings After nginx-lb

| Field | Content |
|---|---|
| **Decision** | Remove host port mappings (`8081`–`8088`) from all 8 application services once `nginx-lb` is in place; retain host ports for databases and observability |
| **What** | `ports` sections on all application services in `docker-compose.yml` |
| **Where** | `docker-compose.yml` |
| **Why** | When application services bind host ports and are also scaled via replicas, each replica needs a distinct host port (Docker cannot bind the same host port to multiple containers). This either prevents scaling or requires complex host port arithmetic. Removing host ports and routing all traffic through `nginx-lb:80` cleanly supports arbitrary replica counts without port conflicts. It also reduces the host's external attack surface. |
| **Cost driver** | Operational (port conflict prevention), security (reduced exposure) |
| **Evidence** | `docker-compose.yml`: each application service currently binds one host port (`${AUTH_SERVICE_HOST_PORT:-8081}:8080`, etc.). `docker compose up --scale streaming-service=2` would fail with a port binding conflict on `8083`. |
| **Expected cost impact** | No direct cost change; enables clean horizontal scaling, which is required for all cost-effective replica optimization steps (CD-006). |
| **Behavior/performance risk** | After removing host ports, services are only reachable via `nginx-lb:80` (or the Docker internal hostname from other containers). Direct debugging (`curl localhost:8082`) requires using `docker exec` or temporarily re-adding a port for that service. This is an acceptable trade-off in a benchmark environment. |
| **Validation method** | After change: `docker compose up --scale streaming-service=3` must succeed without port binding errors. `curl http://localhost/api/catalog/songs` through nginx-lb must return 200. |
| **Status** | Implemented — all 8 app service `ports` sections removed from `docker-compose.yml`; all API traffic routes through `nginx-lb:80`; `container_name` retained only on `auth-service` (prevents accidental `--scale` on the RSA key writer). |

---

## CD-012 — Prometheus Scrape Interval (No Change Justified)

| Field | Content |
|---|---|
| **Decision** | Retain current 15-second Prometheus scrape interval (`scrape_interval: 15s` in `prometheus.yml`) |
| **What** | `infra/prometheus/prometheus.yml` scrape configuration |
| **Where** | `infra/prometheus/prometheus.yml` |
| **Why** | 15 s is a reasonable interval for capacity planning and scaling decisions. Reducing to 5 s would triple Prometheus storage write rate and increase time-series storage by 3×. The current `--storage.tsdb.retention.time=15d` would then generate 3× the disk usage over the retention window. Increasing to 60 s would make fast-moving metrics (Kafka consumer lag spikes, JVM GC pauses) invisible in Grafana. 15 s is the correct balance. |
| **Cost driver** | Disk (Prometheus TSDB storage), CPU (Prometheus scrape overhead) |
| **Evidence** | `docker-compose.yml`: `--storage.tsdb.retention.time=15d`. `prometheus.yml`: `scrape_interval: 15s`. At 15 s and ~500 time series (8 services + exporters), estimated storage is ~50–100 MB/day — well within typical disk budgets. |
| **Expected cost impact** | No change from current. Explicitly documented to prevent future changes that optimize cost at the expense of observability fidelity. |
| **Behavior/performance risk** | None at current configuration. |
| **Validation method** | Monitor `prometheus_tsdb_storage_blocks_bytes` to confirm storage growth is within expectation (~50–100 MB/day). |
| **Status** | No change — confirmed correct. |

---

## CD-013 — Infrastructure Resource Limits (Currently Missing)

| Field | Content |
|---|---|
| **Decision** | Add explicit `deploy.resources.limits` to all infrastructure containers that currently lack them: Kafka, Zookeeper, ClickHouse, MongoDB |
| **What** | `deploy.resources` blocks on `kafka`, `zookeeper`, `clickhouse`, `mongodb` services |
| **Where** | `docker-compose.yml` |
| **Why** | These four services have no resource limits in the current `docker-compose.yml`. Under Phase 4/5 load, a Kafka write storm can cause the JVM to expand heap indefinitely. A ClickHouse merge storm can consume all host CPU. MongoDB under a rapid insert burst can consume available RAM. Without limits, one failing infrastructure component can OOM the entire host — taking all services down simultaneously. |
| **Cost driver** | Memory (OOM guard), CPU (runaway process guard) |
| **Evidence** | `docker-compose.yml`: Kafka, Zookeeper, ClickHouse, MongoDB have no `deploy.resources` blocks. Application services all have `deploy.resources.limits`. Inconsistency confirmed by inspection. |
| **Expected cost impact** | Hard ceilings prevent infrastructure components from consuming host resources needed by application service replicas. Enables predictable memory budget planning for Profile D (see CD-006). Recommended limits: Kafka 2 GB / 2 CPU; Zookeeper 512 MB / 0.5 CPU; ClickHouse 4 GB / 2 CPU; MongoDB 1 GB / 1 CPU. |
| **Behavior/performance risk** | If Kafka is capped below its working set (heap + page cache for log segments), it may slow down under high write throughput. Monitor `kafka_server_bytes_in_per_sec` and host page cache hit rate. If Kafka consistently hits its CPU limit, increase the cap (benchmark host is the source of truth, not a hypothetical cloud instance). |
| **Validation method** | Under Phase 3/4 load: `docker stats kafka` must show memory below the configured limit; `docker events` must not show `oom-kill` events for any container. |
| **Status** | Implemented — `deploy.resources.limits` added to `zookeeper` (0.5 CPU / 512m), `kafka` (2 CPU / 2g), `clickhouse` (2 CPU / 4g + 2g reservation), `mongodb` (1 CPU / 1g) in `docker-compose.yml`; all limits parameterised via `.env.example`. |

---

## CD-014 — Second OpenSearch Node Deferral

| Field | Content |
|---|---|
| **Decision** | Defer adding `opensearch-node2` until Grafana shows OpenSearch heap consistently above 75 % during Phase 4 or higher load tests |
| **What** | New `opensearch-node2` compose service (not yet added) |
| **Where** | `docker-compose.yml` (not yet added) |
| **Why** | A second OpenSearch node adds ~2 GB JVM + OS overhead per node. The songs index with `number_of_replicas=0` on a single node already uses 3 shards for parallel query execution. A second node is only needed when: (a) single-node heap consistently exceeds 75 % (GC pressure degrades latency), or (b) single-node CPU saturates under Phase 4/5 query load. Neither condition is known to occur at the current expected index size. |
| **Cost driver** | Memory (2 GB per node), CPU |
| **Evidence** | `SCALABILITY.md §7`: single-node with 2 GB heap and 3 shards supports the expected query load. A second node must also increase `number_of_replicas` from 0 to 1, which doubles storage and doubles the indexing write load. |
| **Expected cost impact** | Deferring saves ~2 GB RAM and one compose service. The second node becomes necessary only if Phase 4/5 Grafana data shows sustained heap > 75 % on `opensearch`. |
| **Behavior/performance risk** | Deferring the second node means the OpenSearch cluster has no shard replica redundancy (`number_of_replicas=0`). An OpenSearch node restart during a benchmark run causes temporary search service unavailability. Acceptable in a benchmark environment where node restarts are controlled events. |
| **Validation method** | After Phase 4 load test: check `opensearch_jvm_mem_heap_used_in_bytes / opensearch_jvm_mem_heap_max_in_bytes` in Grafana. If sustained above 75 % for more than 5 minutes, proceed to add `opensearch-node2`. |
| **Status** | Deferred (pending Phase 4 load test results) |

---

## CD-015 — Avoid Redis Cluster (Single-Node Sufficient)

| Field | Content |
|---|---|
| **Decision** | Use single-node Redis with `maxmemory 512mb` and LRU eviction; do not implement Redis Cluster |
| **What** | Redis configuration (single node vs 6-node cluster) |
| **Where** | `docker-compose.yml` — `redis` service |
| **Why** | Redis Cluster in Docker Compose requires 6 named nodes (3 primaries + 3 replicas), cluster-bus port configuration (16379+), `redis-cli --cluster create` initialization, and application changes to use `LettuceClusterClient` or `JedisCluster`. The operational cost is high. The workload justification is weak: `recommendation-service` stores ~150–200 MB of TTL-keyed data at 100 K DAU, well within a single-node 512 MB cap. |
| **Cost driver** | Memory (6 × 512 MB = 3 GB for cluster vs 512 MB single), operational complexity |
| **Evidence** | `docker-compose.yml`: single Redis instance. `recommendation-service` environment: `RECOMMENDATION_CACHE_TTL_SECONDS=3600`. Workload table: 100 K DAU × ~1 KB/key = ~150–200 MB steady-state. `SCALABILITY.md §9`: single-node Redis handles up to ~500 K DAU. |
| **Expected cost impact** | Single-node Redis at 512 MB vs Redis Cluster at 3 GB saves ~2.5 GB RAM — freeing capacity for additional service replicas. |
| **Behavior/performance risk** | Single-node Redis is a single point of failure for the recommendation cache. If Redis goes down, `recommendation-service` falls back to computing recommendations directly from PostgreSQL (existing behavior per `docker-compose.yml` `SPRING_DATA_REDIS_HOST` configuration — if Redis is unreachable, the service must degrade gracefully). The circuit breaker (CD-004 related) must be in place. |
| **Validation method** | Stop Redis container during Phase 3 load test; confirm `recommendation-service` returns 200 (not 500) by computing directly from PostgreSQL. Confirm Redis restart recovers automatically. |
| **Status** | Confirmed correct — single-node is the right choice at this scale. |

---

## CD-016 — ClickHouse Table Partitioning by Month

| Field | Content |
|---|---|
| **Decision** | Partition the `playback_events` table in ClickHouse by `toYYYYMM(event_time)` |
| **What** | ClickHouse table DDL in `analytics-service` schema or initialization script |
| **Where** | `services/analytics-service/src/main/resources/` (schema SQL or Flyway migration) |
| **Why** | Without partitioning, all playback events are in a single partition set. After a 2-hour soak test at 40 K events/s, the table contains ~288 M rows. `GET /analytics/me/history` queries scan a large fraction of these rows without per-month partitioning. With `PARTITION BY toYYYYMM(event_time)`, queries filtered to the current month touch only ~1/12 of the data. Old benchmark run data can be dropped instantly via `ALTER TABLE DROP PARTITION` without heavy DELETE scans. |
| **Cost driver** | Disk (bounded partition growth; instant drop of old data), CPU (partition pruning reduces per-query scan cost) |
| **Evidence** | `SCALABILITY.md §8`: partitioning strategy documented. Workload: 40 K events/s × 7200 s (2-hour soak) = 288 M rows in a single run. |
| **Expected cost impact** | Monthly partitioning bounds disk growth to current-month data during benchmarking. Dropping the previous month's partition after a benchmark run reclaims disk space instantly. Reduces per-query CPU for `GET /analytics/me/history` by pruning irrelevant month partitions. |
| **Behavior/performance risk** | Partition key must be included in the `ORDER BY` or `PRIMARY KEY` or be derivable from it. Wrong partition key causes all queries to scan all partitions. Validate with `EXPLAIN` on the history query. |
| **Validation method** | After soak test: `SELECT count() FROM system.parts WHERE table = 'playback_events'` should show a modest number of parts (< 150) despite high insert volume. `EXPLAIN` on `GET /analytics/me/history` should show partition pruning to the current month. |
| **Status** | Implemented — `SchemaInitializer.java` updated: `event_type LowCardinality(String)`, `PARTITION BY toYYYYMM(toDateTime(occurred_at))`, `ORDER BY (user_id, song_id, occurred_at)`. |

---

## CD-017 — k6 Phase Sequencing as Cost Control

| Field | Content |
|---|---|
| **Decision** | Require Phase 1 → Phase 2 → Phase 3 completion (stable, p99 < 500 ms) before running Phase 4 or higher; treat Phase 5 and Phase 6 as explicitly scheduled, not default |
| **What** | k6 test execution protocol documented in `SCALABILITY.md §15` |
| **Where** | `load-generator/scripts/main.js`, operational runbook |
| **Why** | Running Phase 5 (10 K VUs, 30 min) or Phase 6 (soak, 2 h) without first validating Phase 1–3 is expensive and produces misleading results. Phase 5 on an unoptimized deployment drives all services to saturation simultaneously — the benchmark fails to isolate individual service bottlenecks and requires a full teardown and re-run (wasting the entire Phase 5 compute cost). Phase 6 costs 2 hours of full Profile D runtime; repeated runs are expensive. |
| **Cost driver** | Compute (CPU × time for each full benchmark run), operational (time to diagnose failures in unsequenced runs) |
| **Evidence** | `SCALABILITY.md §15`: phase ordering principle. Benchmark methodology principle: each phase should confirm the previous phase's bottleneck is resolved before introducing higher load. |
| **Expected cost impact** | Reducing wasted full-scale benchmark runs from typical 2–3 re-runs (due to undetected bottlenecks) to 1–2 saves significant compute time. Phase 5 at 10 K VUs for 30 min on a 32 GB host costs the same as leaving all services running for 30 min — the sequencing protocol prevents running this unnecessarily. |
| **Behavior/performance risk** | No technical risk from sequencing. Operational risk: if Phase 3 results are prematurely declared "stable" without checking Kafka lag, Phase 4 will fail due to consumer lag accumulation. Phase 3 completion criteria must include a Kafka lag check (all consumer groups at 0 lag after test end). |
| **Validation method** | Before running Phase 4: confirm in Grafana that all `kafka_consumergroup_lag` metrics are back to 0. Confirm `http_server_requests_seconds_max{quantile="0.99"}` for all services is below 500 ms in Phase 3 results. |
| **Status** | Implemented — `load-generator/scripts/main.js` selects scenario via `K6_SCENARIO` env var (smoke/streaming/full/peak); `K6_SCENARIO=full` default in `.env.example`; phase sequencing constraints documented in `SCALABILITY.md §15`. |

---

## CD-018 — Recommendation Service: Defer Resilience4j Circuit Breakers

| Field | Content |
|---|---|
| **Decision** | Defer Resilience4j circuit breaker addition to `recommendation-service` until Redis failure behavior is observed in Phase 3 testing |
| **What** | Resilience4j library addition and `@CircuitBreaker` annotation on Redis and PostgreSQL call paths |
| **Where** | `services/recommendation-service/pom.xml`, service implementation classes |
| **Why** | The `recommendation-service` currently falls back to direct computation when Redis is unavailable (the existing `SPRING_DATA_REDIS_HOST` configuration means an unreachable Redis causes Spring Data Redis operations to throw exceptions, which must be caught). If the existing catch-and-compute fallback is working correctly, adding Resilience4j is optimization, not requirement. Adding it prematurely increases code complexity, testing surface, and JAR size. |
| **Cost driver** | Operational complexity (premature optimization cost), JAR size (Resilience4j dependency) |
| **Evidence** | `docker-compose.yml`: Redis is `recommendation-service`'s dependency. Architecture requirement M-23: circuit breaker or equivalent. Current implementation behavior (fallback on Redis unavailability) satisfies "equivalent" if validated. |
| **Expected cost impact** | Deferral saves implementation and testing time. If Phase 3 testing reveals Redis failures cascade to service failures (instead of graceful fallback), add Resilience4j at that point. |
| **Behavior/performance risk** | Without a formal circuit breaker, Redis failures cause exception-per-request overhead. If Redis is degraded (slow, not down), requests queue waiting for Redis timeout before falling back. Resilience4j's half-open state prevents this. Evaluate in Phase 3. |
| **Validation method** | During Phase 3: inject Redis latency (`docker exec redis redis-cli DEBUG SLEEP 1`) and observe `recommendation-service` p99 latency in Grafana. If p99 exceeds 2 s due to Redis blocking, add Resilience4j. |
| **Status** | Deferred (pending Phase 3 Redis failure testing) |

---

## CD-019 — Catalog Redis Page Caching Deferral

| Field | Content |
|---|---|
| **Decision** | Defer adding Redis-backed page cache to `catalog-service` until `catalog-db` sequential scan rate or connection saturation appears under Phase 3+ load |
| **What** | Redis caching layer in `catalog-service` (`CatalogCacheService` bean or equivalent) |
| **Where** | `services/catalog-service/src/main/java/` |
| **Why** | `catalog-service` already relies on PostgreSQL indexes (`idx_songs_genre_title`, `idx_songs_title`) for efficient query execution (see CD-010). Adding Redis page caching before confirming index efficacy under load risks wasting implementation effort on a problem that doesn't exist at the actual query volume. The cache is justified only if `catalog-db` CPU or connection saturation appears in Grafana. |
| **Cost driver** | Redis memory (new key namespace; bounded by page count × page size × TTL), implementation time |
| **Evidence** | Workload table: ~4 K catalog/search req/s. With 4 `catalog-service` replicas and proper indexes, catalog-db should handle this load. Cache benefit materializes primarily if the same page is requested repeatedly by many users — e.g., first page of "rock" genre. |
| **Expected cost impact** | If implemented, adds ~50–100 MB to Redis memory (bounded by TTL=300s and genre count). If deferred and not needed, saves Redis memory and implementation complexity. |
| **Behavior/performance risk** | Deferral risk: without caching, catalog-db CPU grows linearly with catalog browse traffic. If Phase 3 shows `pg_stat_user_tables_seq_scan` still rising despite indexes, catalog caching becomes urgent. |
| **Validation method** | After Phase 3: check `pg_stat_user_tables_seq_scan` on `catalog-db` in Grafana. If sequential scans are near zero (indexes are being used), defer catalog caching. If scans persist (query plan not using indexes), fix the index strategy first, then consider caching. |
| **Status** | Deferred (pending Phase 3 load test and Grafana review) |

---

## CD-020 — 3-Broker Kafka Cluster Deferral

| Field | Content |
|---|---|
| **Decision** | Defer adding `kafka2` and `kafka3` broker services until immediately before Phase 4 load tests |
| **What** | New `kafka2`, `kafka3` compose services with unique `KAFKA_BROKER_ID` values and `KAFKA_ADVERTISED_LISTENERS` on different ports |
| **Where** | `docker-compose.yml` |
| **Why** | A 3-broker cluster adds ~1 GB JVM × 2 additional brokers = ~2 GB RAM overhead and increases compose startup time. For Phases 1–3 (5–200 VUs), single-broker Kafka is sufficient: message throughput is low enough that broker restarts during a test are unlikely, and replication factor 3 is not required for sub-Phase-4 load. |
| **Cost driver** | Memory (~2 GB additional RAM), compose complexity (3 service definitions required because `--scale kafka=3` cannot be used — each broker needs a unique BROKER_ID) |
| **Evidence** | `SCALABILITY.md §11`: single-broker sufficient for Phases 1–3. At Phase 4 (1 K VUs), Kafka write throughput increases to the point where broker disk I/O becomes a constraint and replication factor 3 provides write durability needed for accurate benchmark measurements. |
| **Expected cost impact** | Deferring saves 2 GB RAM during Phases 1–3 development and testing. 3-broker cluster is required before Phase 4 to avoid broker-restart-induced consumer lag affecting benchmark results. |
| **Behavior/performance risk** | Single-broker: any Kafka broker restart during Phase 3 or lower tests causes consumer lag accumulation until reconnect (~10–30 s). Acceptable for Phase 1–3 where test duration is 2–10 minutes. Not acceptable for Phase 4 (20-minute run) or Phase 6 (2-hour soak). |
| **Validation method** | Add 3-broker cluster before Phase 4. Verify via `kafka-topics.sh --describe` that all topics have `Isr: 1,2,3` (all 3 brokers in the in-sync replica set). |
| **Status** | Deferred (add immediately before Phase 4) |

---

---

## Validation Step — 2026-06-15

> These entries document everything found and fixed during the validation pass against ARCHITECTURE.md, the approved scaling plan, and the current repository.

---

## VL-001 — docker-compose.yml Syntax Validation

| Field | Content |
|---|---|
| **What** | `docker compose config --quiet` on the default profile and `--profile load-test` |
| **Where** | `docker-compose.yml` |
| **Why** | Compose YAML errors (undefined variables, wrong types, missing fields) prevent any service from starting, making every downstream cost or scaling decision moot. |
| **Result** | PASS — both `docker compose config --quiet` and `docker compose --profile load-test config --quiet` exit 0 with no errors. |
| **Remaining risk** | None for syntax. Semantic correctness (health check paths, dependency ordering) requires a live run. |
| **Status** | Validated |

---

## VL-002 — All 8 Backend Services Present

| Field | Content |
|---|---|
| **What** | `docker compose config --services` output checked against ARCHITECTURE.md §3 list |
| **Where** | `docker-compose.yml` |
| **Why** | Horizontal scaling changes (removing host ports, adding nginx-lb, changing depends_on chains) must not silently drop a required service from the compose graph. |
| **Result** | PASS — all 8 application services confirmed present: auth-service, catalog-service, streaming-service, playlist-service, search-service, analytics-service, recommendation-service, notification-service. Infrastructure: zookeeper, kafka, init-kafka, auth-db, catalog-db, playlist-db, recommendation-db, opensearch, clickhouse, redis, mongodb, auth-db-exporter, catalog-db-exporter, playlist-db-exporter, recommendation-db-exporter, redis-exporter, kafka-exporter, mongodb-exporter, prometheus, grafana, nginx-lb, load-generator (profile=load-test). |
| **Remaining risk** | None. |
| **Status** | Validated |

---

## VL-003 — Grafana Dashboard JSON Validation

| Field | Content |
|---|---|
| **What** | `python3 -c "import json; json.load(open(...))"` on `infra/grafana/dashboards/scaling.json` |
| **Where** | `infra/grafana/dashboards/scaling.json` |
| **Why** | Grafana silently ignores invalid JSON dashboards; a parse error means zero observability panels are loaded, making it impossible to validate any cost or scaling claim against live data. |
| **Result** | PASS — 14 panels loaded cleanly: request rate, error rate, p99/p95 latency, JVM heap, CPU, HikariCP active/saturation, Kafka lag (analytics; recommendation+notification), Redis hit rate, Redis memory, OpenSearch heap, ClickHouse p99 query latency. |
| **Remaining risk** | Panel PromQL expressions must be validated against a live Prometheus instance. Some metric names (e.g. `hikaricp_connections_active`) may differ by Spring Boot version. |
| **Status** | Validated |

---

## VL-004 — nginx `/notifications/` Path Bug (FIXED)

| Field | Content |
|---|---|
| **What** | `location /notifications/` in nginx.conf did not match `GET /notifications` requests |
| **Where** | `infra/nginx-lb/nginx.conf` |
| **Why** | notification-service controller is `@RequestMapping("/notifications")` + bare `@GetMapping`, which maps to the URI `/notifications` (no trailing slash). nginx `location /notifications/` is a prefix match that requires the URI to start with `/notifications/`; the request path `/notifications` does not start with `/notifications/`, so nginx returned 404. Any k6 or frontend call to `GET /notifications` failed silently behind nginx. |
| **Cost driver** | No cost impact; correctness issue that removes a required endpoint from the benchmark flow. |
| **Evidence** | grep of notification-service controller: `@RequestMapping("/notifications")` + `@GetMapping` (no path). nginx prefix match semantics: `location /X/` does not match URI `/X`. |
| **Fix** | Changed `location /notifications/` → `location /notifications` in `infra/nginx-lb/nginx.conf`. |
| **Result** | FIXED — `GET /notifications` and all sub-paths (future) now route correctly to `notification-service:8080`. |
| **Remaining risk** | None for routing. If notification-service adds sub-path endpoints, the prefix match `location /notifications` will continue to work. |
| **Status** | Fixed |

---

## VL-005 — k6 catalogFlow Missing Auth Headers (FIXED)

| Field | Content |
|---|---|
| **What** | `catalogFlow(token)` in `load-generator/scripts/main.js` made catalog HTTP calls without `Authorization: Bearer` headers |
| **Where** | `load-generator/scripts/main.js` — `catalogFlow()` function |
| **Why** | catalog-service `SecurityConfig` configures `.anyRequest().authenticated()` — all catalog endpoints (including `GET /catalog/songs`) require a valid JWT. The k6 function accepted `token` as a parameter but never used it, causing all catalog requests to return 401 during the load test. All latency measurements for the catalog flow would be timing 401 rejections, not actual catalog query performance. This invalidates the benchmark results for catalog-related cost decisions (CD-006, CD-013). |
| **Cost driver** | Benchmark validity — invalid load-test results make cost decisions based on catalog throughput untrustworthy. |
| **Evidence** | catalog-service `SecurityConfig.java`: `.anyRequest().authenticated()`. k6 `catalogFlow` function: `http.get(BASE_URL + '/catalog/songs?page=0&size=20')` — no headers argument. |
| **Fix** | Added `authHeaders(token)` as the second argument to both `http.get()` calls in `catalogFlow`. |
| **Result** | FIXED — catalog calls now include `Authorization: Bearer <token>` header. All 8 API flows in the load test now send valid JWTs. |
| **Remaining risk** | The 409-fallback login path in the VU default function doesn't retry catalog after login; if both registration AND login fail, the VU returns early without touching catalog. This is pre-existing guard logic and not introduced by scalability work. |
| **Status** | Fixed |

---

## VL-006 — auth-service container_name Conflicts with Profile D replica=4 (FIXED)

| Field | Content |
|---|---|
| **What** | `.env.example` Profile D section contained `# AUTH_SERVICE_REPLICAS=4`, which if uncommented would fail at `docker compose up` because `container_name: auth-service` prevents multiple replicas |
| **Where** | `.env.example` — Profile D replica counts section |
| **Why** | auth-service has `container_name: auth-service` (intentionally, to prevent accidental `--scale`) and `deploy.replicas: ${AUTH_SERVICE_REPLICAS:-1}`. Docker cannot assign the same name to multiple containers; attempting replicas > 1 with a fixed container_name causes Docker to error. An operator following the Profile D instructions and uncommenting `AUTH_SERVICE_REPLICAS=4` would get a startup failure with no obvious diagnostic. Additionally, auth-service must stay at 1 replica because it writes RSA key files to the `jwt-keys` volume at startup; a second replica would race to overwrite them, potentially corrupting the keys that all other services depend on. |
| **Cost driver** | Operational safety — incorrect Profile D values cause deployment failures that waste benchmark session time (compute cost). |
| **Evidence** | docker-compose.yml auth-service stanza: `container_name: auth-service` AND `deploy.replicas: ${AUTH_SERVICE_REPLICAS:-1}`. Docker Compose behavior: `container_name` + `replicas > 1` fails at runtime. RSA key write behavior: auth-service writes `private.pem` and `public.pem` to the `jwt-keys` volume on first start. |
| **Fix** | Removed `# AUTH_SERVICE_REPLICAS=4` from the Profile D section. Added a comment explaining that auth-service is intentionally absent from Profile D replica scaling (container_name constraint + RSA key race). |
| **Result** | FIXED — Profile D section no longer contains a value that would silently break the deployment. |
| **Remaining risk** | auth-service is a BCrypt + RS256 bottleneck at 1 replica. Under Phase 4+ load, auth endpoint latency may increase. Operators can rate-limit registration and pre-issue tokens for the load test to reduce auth-service pressure (documented in SCALABILITY.md §14). |
| **Status** | Fixed |

---

## VL-007 — analytics-service Unit Test Coverage for New Batch Code (FIXED)

| Field | Content |
|---|---|
| **What** | No unit tests existed for `BatchEventBuffer` or `AnalyticsService.recordBatch()` — both introduced by the scalability implementation |
| **Where** | `services/analytics-service/src/test/java/com/musicstreaming/analytics/unit/` |
| **Why** | Untested scalability code that handles batching of 40K events/s is a correctness risk. If the synchronized drain pattern, null-filter logic, or batch-size threshold has a regression, it will not be caught until Phase 3+ load testing — which wastes a full benchmark run and the associated compute time. |
| **Cost driver** | Operational cost — late detection of batch logic bugs requires full benchmark reruns. |
| **Evidence** | `BatchEventBuffer.java` added; `AnalyticsService.recordBatch()` added. No corresponding test files existed. The existing `AnalyticsServiceTest` only covered `recordEvent()`, `getHistory()`, and `getGlobalCharts()`. |
| **Fix** | Created `BatchEventBufferTest.java` (8 tests covering: below-batch-size no-flush, batch-size-triggered flush, buffer-cleared-after-flush, null-userId filtered, null-songId filtered, scheduledFlush on empty buffer, scheduledFlush drains pending, buffer cleared after scheduledFlush). Added 4 `recordBatch` tests to `AnalyticsServiceTest` (valid events passed through, null userId filtered, null songId filtered, all-filtered produces empty insertBatch call). |
| **Result** | PASS — 21/21 unit tests pass (8 BatchEventBufferTest + 13 AnalyticsServiceTest). `mvn test -Dtest="AnalyticsServiceTest,BatchEventBufferTest"` exits BUILD SUCCESS. |
| **Remaining risk** | Integration-level concurrency testing (two threads calling `add()` simultaneously) is not covered; the `synchronized(buffer)` block handles this correctly but it is not exercised by single-threaded Mockito tests. |
| **Status** | Fixed and validated |

---

## VL-008 — Prometheus DNS SD Config Validation

| Field | Content |
|---|---|
| **What** | `prometheus.yml` scrape config — all 8 app services use `dns_sd_configs` (type A, port 8080); 8 infrastructure exporters use `static_configs` |
| **Where** | `infra/prometheus/prometheus.yml` |
| **Why** | If DNS SD is misconfigured (wrong type, wrong port, wrong job name), Prometheus will fail to discover replica targets as services scale. This would make the Grafana dashboard panels empty and invalidate all cost decisions that depend on observability data. |
| **Result** | PASS — all 8 `dns_sd_configs` jobs use `type: A` (correct for Docker embedded DNS), `port: 8080` (matches Spring Boot default), and `metrics_path: /actuator/prometheus` (matches Spring Boot Actuator default). Infrastructure exporters use `static_configs` (correct for singleton containers). ClickHouse metrics use path `/metrics` on port 9363 (ClickHouse native Prometheus exporter default). |
| **Remaining risk** | Prometheus DNS SD type A returns a set of IPs; if Docker DNS caches IPs across replica restarts, Prometheus may scrape stale targets. The `valid=10s` TTL mitigates this in nginx but Prometheus DNS SD has its own refresh interval (default 30s from `dns_sd_configs.refresh_interval`). Not configured explicitly — acceptable at current scale. |
| **Status** | Validated |

---

## VL-009 — k6 Script Scenario Configuration Inspection

| Field | Content |
|---|---|
| **What** | k6 `main.js` scenario selection logic, VU flows, auth header propagation, BASE_URL binding |
| **Where** | `load-generator/scripts/main.js` |
| **Why** | An incorrect k6 scenario config (e.g., missing auth headers, wrong BASE_URL, broken scenario selection) produces invalid benchmark results that cannot inform cost decisions. |
| **Result** | PASS (after VL-005 fix) — `BASE_URL` correctly reads `NGINX_LB_URL` env var; scenario selection via `K6_SCENARIO` env correctly maps to smoke/streaming/full/peak configs; all API flows (catalog, search, stream, playlist, analytics, recommendations) now correctly include `Authorization: Bearer <token>` headers. Thresholds: p95 < 2000ms, p99 < 5000ms, error rate < 5%. |
| **Remaining risk** | The `smoke` scenario returns early after catalogFlow without exercising stream/playlist/analytics flows. This is by design — smoke verifies basic connectivity only. Ensure `full` or `peak` scenario is used for CD-002 through CD-016 validation. |
| **Status** | Validated |

---

*Last updated: 2026-06-15 — validation pass complete; 4 issues found, all fixed.*

---

## Session 9 Entries — Monitoring, Load Generator & Integration Fixes

---

## CD-021 — OpenSearch Prometheus Exporter Addition

| Field | Content |
|---|---|
| **Decision** | Add `prometheuscommunity/elasticsearch-exporter` as `opensearch-exporter` service; add scrape job to `prometheus.yml` |
| **What** | New `opensearch-exporter` compose service; new `opensearch` job in `infra/prometheus/prometheus.yml` |
| **Where** | `docker-compose.yml`, `infra/prometheus/prometheus.yml` |
| **Why** | OpenSearch heap, JVM GC, index stats, and thread pool metrics are entirely missing from the monitoring stack. Without an OpenSearch exporter, operators cannot observe the most critical OpenSearch signals (heap %, GC duration, query thread pool rejection rate). SCALABILITY.md §14 explicitly lists OpenSearch as a required scrape target. SCALABILITY.md §16 Step 7 ("defer second OpenSearch node until heap consistently > 75 %") cannot be validated without this metric. |
| **Cost driver** | Memory (one additional container ~64–128 MB), scrape load (15 s interval, lightweight REST API poll) |
| **Evidence** | `infra/prometheus/prometheus.yml` has no OpenSearch scrape job. SCALABILITY.md §14: "OpenSearch exposes metrics via the `_prometheus/metrics` endpoint (with the opensearch-prometheus-exporter plugin) or via `opensearch_exporter`." `docker-compose.yml` has no `opensearch-exporter` service. |
| **Expected cost impact** | +64–128 MB RAM for the exporter container. Negligible CPU (REST polling vs active compute). The cost of NOT having this exporter is undetectable OpenSearch heap saturation, which can cause Phase 3+ benchmarks to fail without an observable root cause — wasting the entire benchmark run cost. |
| **Behavior/performance risk** | The `elasticsearch_exporter` uses the `_cluster/health`, `_nodes/stats`, and `_stats` REST endpoints. On a heavily loaded OpenSearch cluster, these endpoints add minor read latency. At 15 s scrape intervals, impact is negligible. Risk: if OpenSearch `DISABLE_SECURITY_PLUGIN=true` is not set, the exporter's unauthenticated requests will fail (not applicable here — security is disabled). |
| **Validation method** | After adding: `curl http://localhost:9090/api/v1/query?query=elasticsearch_jvm_memory_used_bytes` must return non-empty results. Grafana panel for OpenSearch heap must show a live percentage. |
| **Status** | Planning — must be implemented in Session 9 generation step |

---

## CD-022 — overview.json Dashboard Population (S-03 Compliance)

| Field | Content |
|---|---|
| **Decision** | Replace the empty `infra/grafana/dashboards/overview.json` stub with a populated 8-panel system overview dashboard meeting S-03 requirements |
| **What** | `infra/grafana/dashboards/overview.json` — full replacement with panels for traffic, latency, error rate, top tracks, service health, JVM heap, Kafka consumer lag, and load generator VU count |
| **Where** | `infra/grafana/dashboards/overview.json` |
| **Why** | S-03 requirement: "The monitoring stack should include an admin dashboard for observability. Grafana should expose dashboards for traffic, latency, error rate, and top tracks." The current file is an explicit placeholder comment: `"description": "Music Streaming System — overview dashboard. Panels are added in Phase 10."` and has zero panels. Without this dashboard, S-03 is unfulfilled. |
| **Cost driver** | No infrastructure cost. Grafana renders dashboards on demand; 8 additional panels add ~8 additional PromQL queries per browser refresh — negligible. |
| **Evidence** | `infra/grafana/dashboards/overview.json`: `"panels": []`. ARCHITECTURE.md requirement S-03. |
| **Expected cost impact** | Zero additional cloud cost. The dashboard makes previously-computed Prometheus data visible without additional scraping. |
| **Behavior/performance risk** | The "top tracks" panel requires a Prometheus metric aggregating play counts by `song_id`. If `analytics-service` does not emit a `playback_event_total{song_id}` counter, the top-tracks panel must use a Prometheus recording rule or a ClickHouse-sourced alternative. Recording rule approach: `sum(rate(playback_event_total[1h])) by (song_id)` — this works only if the metric exists. If it does not, the panel will show "No data" and must be marked as a placeholder with a note until analytics-service is confirmed to emit the metric. |
| **Validation method** | After generation: open `http://localhost:3001` → Music Streaming — System Overview dashboard → all 8 panels must render without "No data" (except possibly top-tracks if metric is absent). Panels for traffic, latency, error rate, service health must all show live data after k6 smoke run. |
| **Status** | Planning — must be implemented in Session 9 generation step |

---

## CD-023 — k6 Phase 4 Ramp and Phase 5 Isolation

| Field | Content |
|---|---|
| **Decision** | Add Phase 4 `ramp` scenario (0→1000 VU, 20 min) to `main.js`; create separate `phase5-peak.js` for Phase 5 (0→10000 VU, 30 min); update `load-generator/.env.example` to replace stale per-service URLs with `NGINX_LB_URL` |
| **What** | `load-generator/scripts/main.js`, `load-generator/scripts/phase5-peak.js` (new), `load-generator/.env.example` |
| **Where** | `load-generator/` directory |
| **Why** | SCALABILITY.md §15 defines Phase 4 at 1000 VU (ramping stages) and Phase 5 at 10000 VU. The current `peak` scenario tops at 500 VU — insufficient for Phase 4 validation. Phase 5 at 10000 VU is explicitly excluded from `main.js` to prevent accidental invocation (accidental Phase 5 invocation on an under-resourced host causes OOM across all services and wastes ~30 min of compute). The `load-generator/.env.example` still lists old per-service host port URLs (`AUTH_SERVICE_URL=http://localhost:8081`) which are invalid — host ports were removed when nginx-lb was added. |
| **Cost driver** | Phase 4: ~20 CPU-minutes of k6 goroutine overhead + full Profile D service cost; Phase 5: ~30 CPU-minutes + peak Profile D cost (most expensive single operation). Isolation of Phase 5 into a separate file prevents accidental invocation. |
| **Evidence** | `load-generator/scripts/main.js` peak scenario: `{ duration: '5m', target: 500 }`. SCALABILITY.md §15 Phase 4: `{ duration: '5m', target: 1000 }, { duration: '10m', target: 1000 }, { duration: '5m', target: 0 }`. `load-generator/.env.example`: stale `AUTH_SERVICE_URL=http://localhost:8081`. |
| **Expected cost impact** | Phase 4 add: no new cost for Phases 1–3. Phase 5 isolation prevents ~30-min full-scale benchmark waste from accidental invocation. `.env.example` fix prevents load-test failures from wrong URL (which would waste the entire scheduled benchmark run). |
| **Behavior/performance risk** | Phase 4 ramp (1000 VU) requires Profile D deployment (all replicas, nginx-lb, 3-broker Kafka) to be in place. Running Phase 4 against a Profile A/B deployment (1 replica each) will drive all services to saturation and produce invalid benchmark results. The `K6_SCENARIO=ramp` documentation must include a prerequisite checklist. |
| **Validation method** | `K6_SCENARIO=ramp K6_VUS=1000 docker compose --profile load-test up load-generator` — must start and reach 1000 VU without k6 startup errors. p99 latency in Grafana must be observed rising linearly with VU ramp to confirm load is reaching services. |
| **Status** | Planning — must be implemented in Session 9 generation step |

---

## CD-024 — M-22 / M-23 Architectural Compliance: No New Code Required

| Field | Content |
|---|---|
| **Decision** | M-22 (inter-service HTTP retry) and M-23 (circuit breaker or equivalent) are satisfied architecturally; no new code is required for inter-service HTTP calls because no synchronous inter-service HTTP calls exist |
| **What** | Documentation of compliance rationale; Kafka producer retry config in `streaming-service` and `playlist-service` application.yml; `@CircuitBreaker` on Redis path in `recommendation-service` (see CD-025) |
| **Where** | `services/streaming-service/src/main/resources/application.yml`, `services/playlist-service/src/main/resources/application.yml`, `scripts/verify-integration.sh` (compliance assertion) |
| **Why** | Code audit confirmed: no `RestTemplate`, `WebClient`, `FeignClient`, or equivalent HTTP client is used for service-to-service calls. All inter-service data flows are: (a) Kafka producers/consumers, (b) local RSA JWT validation (no HTTP call to auth-service), (c) independent per-service data stores. M-22 applies to "inter-service HTTP calls" — which do not exist. The retry behavior that applies to the actual cross-service data path (Kafka producers) must be explicitly configured via `spring.kafka.producer.retries` and `retry.backoff.ms` (currently undocumented in application.yml files). M-23 is satisfied by Kafka's producer-consumer decoupling: a failing consumer cannot cascade-fail an upstream producer. |
| **Cost driver** | No cost — this is a documentation and minor config decision, not an infrastructure addition |
| **Evidence** | grep for `RestTemplate\|WebClient\|FeignClient` across all 8 service source trees: no results. All pom.xml files: no `spring-cloud-openfeign`, no `spring-webflux` (WebClient), no `spring-web` beyond what's included transitively for MVC. ARCHITECTURE.md M-03: "JWT verification must use a shared verification configuration" (local RSA key, not HTTP call). |
| **Expected cost impact** | None — documentation decision. Adding Kafka producer retry properties has no cost impact at the infrastructure level. |
| **Behavior/performance risk** | Documentation risk: if a future developer adds inter-service HTTP calls without adding retry/circuit-breaker, M-22/M-23 compliance breaks silently. The `verify-integration.sh` script should include an assertion that no services make unexpected cross-service HTTP calls (verifiable via nginx-lb access log inspection during the smoke run). |
| **Validation method** | After adding producer retry config: `docker exec streaming-service env | grep KAFKA` to confirm properties are loaded. During Phase 2/3: introduce a Kafka broker restart; observe that streaming-service producer retries and events eventually appear in analytics-service history (no permanent loss). |
| **Status** | Planning — Kafka producer retry config must be added to streaming-service and playlist-service application.yml in Session 9 generation step. M-22/M-23 compliance argument must be documented in `scripts/verify-integration.sh` comments and the system verification deliverable. |

---

## CD-025 — Resilience4j on Redis Call Path (recommendation-service)

| Field | Content |
|---|---|
| **Decision** | Add `resilience4j-spring-boot3` to `recommendation-service` and annotate Redis read/write operations with `@CircuitBreaker(name = "redis", fallbackMethod = "computeDirectly")` |
| **What** | `services/recommendation-service/pom.xml`, `RecommendationService.java`, `application.yml` (Resilience4j config), unit test update |
| **Where** | `services/recommendation-service/` |
| **Why** | CD-018 deferred this pending Phase 3 evidence. As of Session 9, Phase 3 has not been run. The circuit breaker on the Redis path serves two purposes: (a) formal M-23 compliance evidence for the thesis, and (b) protection against Redis slow-response cascading to `recommendation-service` thread pool exhaustion (SCALABILITY.md §13 documents this risk). The implementation cost is low (one dependency, one annotation, one config block, one test case). |
| **Cost driver** | JAR size (+~500 KB for Resilience4j spring boot starter), memory (+negligible — circuit breaker state is in-process), operational (adds one config block to application.yml) |
| **Evidence** | SCALABILITY.md §13: "recommendation-service Redis unavailability falls back to compute (correct) but no circuit breaker on DB calls." ARCHITECTURE.md M-23: "circuit breaker or equivalent failure-isolation mechanism." CD-018 Status: Deferred. |
| **Expected cost impact** | No infrastructure cost. Circuit breaker prevents Redis slow-response from blocking Tomcat threads — under Redis degradation, this reduces `recommendation-service` CPU usage (fewer threads blocked). The fallback computation (from PostgreSQL) is more expensive per-request than a Redis hit but cheaper than thread-pool exhaustion. |
| **Behavior/performance risk** | If the circuit breaker `failureRateThreshold` is set too low (e.g., 10 %), transient Redis latency spikes open the breaker and all requests fall back to PostgreSQL — multiplying PostgreSQL load. Recommendation: `failureRateThreshold: 50`, `slidingWindowSize: 10`, `waitDurationInOpenState: 30s`. Half-open state allows gradual Redis recovery without flooding PostgreSQL. |
| **Validation method** | Unit test: mock Redis to throw `RedisConnectionException`; assert fallback method is called and returns non-null. Phase 3 test: `docker stop redis`; query `/recommend/daily-mix`; assert 200 (not 500). Grafana: `recommendation-service` p99 latency should not spike above 2 s when Redis is down (fallback must stay within threshold). |
| **Status** | Planning — must be implemented in Session 9 generation step |

---

## CD-026 — Grafana Datasource UID Fix

| Field | Content |
|---|---|
| **Decision** | Add `uid: prometheus` to `infra/grafana/provisioning/datasources/prometheus.yml` |
| **What** | `infra/grafana/provisioning/datasources/prometheus.yml` — add `uid: prometheus` field |
| **Where** | `infra/grafana/provisioning/datasources/prometheus.yml` |
| **Why** | Both `scaling.json` and the planned `overview.json` panels reference the datasource as `{"type": "prometheus", "uid": "prometheus"}`. The current provisioning file has no `uid` field, so Grafana assigns an auto-generated UID. When the dashboard JSON references `uid: prometheus` and the actual datasource has UID `abc123xyz`, all panel datasource references resolve to "No data" silently. This makes all monitoring dashboards non-functional at startup — a critical observability failure. |
| **Cost driver** | No cost — a one-line config fix |
| **Evidence** | `infra/grafana/provisioning/datasources/prometheus.yml`: no `uid:` field present. `infra/grafana/dashboards/scaling.json`: `"datasource": {"type": "prometheus", "uid": "prometheus"}`. Grafana provisioning docs: `uid` field makes the datasource referenceable by UID in JSON dashboards. |
| **Expected cost impact** | None. Prevents dashboard panels from showing "No data" at startup, which would require manual Grafana UI editing — operational time cost. |
| **Behavior/performance risk** | If `uid: prometheus` is already assigned to a different datasource in Grafana's internal database (e.g., from a previous non-provisioned configuration), the provisioning may fail silently or override it. In a fresh `grafana-data` volume this is not an issue. |
| **Validation method** | After fix: start the stack; open Grafana → Data Sources → Prometheus datasource UID must be `prometheus`. Open scaling.json dashboard; all panels must show live data rather than "No data." |
| **Status** | Planning — must be fixed in Session 9 generation step |

---

## CD-027 — System Verification Deliverable: Compose-Up Shell Script

| Field | Content |
|---|---|
| **Decision** | Implement the system verification deliverable (ARCHITECTURE.md §6) as `scripts/verify-integration.sh` — a shell script that starts the compose stack, waits for health, runs k6 smoke, and asserts cross-service Kafka event flow and JWT validation |
| **What** | `scripts/verify-integration.sh` (new file) |
| **Where** | `scripts/verify-integration.sh` |
| **Why** | ARCHITECTURE.md §6 requires "automated testing at both service and system level so that the integrated behavior of the platform can be checked repeatedly." The existing per-service tests (Testcontainers, unit) do not test cross-service behavior. The system must demonstrate: (a) services start and connect, (b) JWT issued by auth-service is accepted by catalog/streaming/playlist/analytics, (c) Kafka event from streaming-service reaches analytics-service history. |
| **Cost driver** | Compute: running the full compose stack for the smoke verification takes ~5–10 minutes. Disk: no additional storage beyond the running containers. This is a one-time-per-validation-cycle cost. During CI: the script should run only on `main` merges, not on every PR (cost savings). |
| **Evidence** | ARCHITECTURE.md §6: "Verification should include automated testing at both service and system level." PROGRESS.md Phase 10 system verification items: all unchecked. |
| **Expected cost impact** | Running the script costs ~10 min of full-stack compute time. Compared to running Phase 2/3 load tests (~15–30 min), this is the lowest-cost integration verification option. Not embedding in a heavy Java test framework avoids the overhead of spinning up a second Testcontainers environment. |
| **Behavior/performance risk** | The script depends on Docker Compose being available and the host having sufficient RAM for all services (~8–10 GB for Profile A/B). On a low-RAM CI host, services may not start. Add a RAM check at the top of the script: `[ $(free -g | awk 'NR==2{print $2}') -ge 8 ] || exit 1`. |
| **Validation method** | `bash scripts/verify-integration.sh` must exit 0 with all steps PASS. The k6 smoke step must produce 0 HTTP errors (k6 exit code 0). The cross-service Kafka assertion must produce a non-empty analytics history response within 30 s of the stream call. |
| **Status** | Planning — must be implemented in Session 9 generation step |

---

---

## Session 9 — Implementation Status Updates (2026-06-15)

| Entry | Status before generation | Status after generation | Notes |
|---|---|---|---|
| CD-021 | Planning | **Implemented** | `opensearch-exporter` service added to `docker-compose.yml`; `opensearch` scrape job added to `prometheus.yml`; `docker compose config --quiet` PASS |
| CD-022 | Planning | **Implemented (partial)** | `overview.json` populated with 8 panels; panels 1–4, 5, 6, 8 fully operational; panel 7 (top tracks) is a placeholder — no `playback_event_total` counter in analytics-service (DD-012) |
| CD-023 | Planning | **Implemented** | `main.js` has 6 scenarios: smoke/streaming/full/burst/ramp/soak; `phase5-peak.js` created; `.env.example` updated; `K6_RAMP_TARGET` wired to load-generator service env |
| CD-024 | Planning | **Implemented** | `streaming-service` and `playlist-service` application.yml: `retries: 3`, `retry.backoff.ms: 1000`, `max.in.flight.requests.per.connection: 1` added; M-22/M-23 architectural compliance documented in DESIGN-DECISIONS DD-004 |
| CD-025 | Planning | **Implemented** | `resilience4j-spring-boot3:2.1.0` + `spring-boot-starter-aop` in pom.xml; `@CircuitBreaker(name="redis")` on `getDailyMix` and `getSimilarSongs`; `resilience4j.circuitbreaker.instances.redis` in application.yml; 3 fallback tests added to `RecommendationServiceTest` |
| CD-026 | Planning | **Implemented** | `uid: prometheus` added to `infra/grafana/provisioning/datasources/prometheus.yml` |
| CD-027 | Planning | **Implemented** | `scripts/verify-integration.sh` created; 7 steps; executable; awaits live run against compose stack |

### Implementation cost impacts observed

| Change | Measured/estimated impact | Risk |
|---|---|---|
| `opensearch-exporter` container | +128 MB RAM ceiling; <0.1 CPU at 15s scrape interval | None — lightweight REST poller |
| `overview.json` 8 panels | Zero infrastructure cost; 8 extra Prometheus queries per Grafana browser session | Negligible |
| `resilience4j-spring-boot3:2.1.0` | +~500 KB JAR size in recommendation-service image; +negligible JVM heap for circuit breaker state object | None at current scale |
| `spring-boot-starter-aop` | +~200 KB JAR; enables Spring AOP proxy for @CircuitBreaker (required) | Adds CGLIB proxy overhead per annotated bean call — unmeasurable at this scale |
| Kafka producer `retries: 3` | No steady-state cost; adds retry latency only on Kafka broker failure events | If broker is down and retries exhaust, message is lost (acks=all still required for in-flight durability) |
| `phase5-peak.js` | Zero cost until explicitly invoked | Accidental invocation (now prevented by separate file) costs ~30 min full Profile D compute |
| `catalog-service V2` migration | One-time cost: table scan at startup to build 2 new indexes on songs table. At ~10K songs: <5 s. At 1M songs: ~30–60 s startup delay. | Not applicable — catalog dataset is bounded by the Kaggle CSV size |
| `scripts/verify-integration.sh` k6 smoke step | ~10 min full-stack runtime per verification run | None — Profile A/B footprint only |

*Last updated: 2026-06-15 — Session 9 generation complete.*

---

## Session 9 — Validation Results (2026-06-15)

### Validation checks performed

| Check | Result | Notes |
|---|---|---|
| `docker compose config --quiet` (default profile) | PASS | |
| `docker compose --profile load-test config --quiet` | PASS | |
| `docker compose config --services` — opensearch-exporter present | PASS | 30 services confirmed |
| `overview.json` — 8 panels, valid JSON, datasource UIDs | PASS | panel 7 is documented placeholder |
| `scaling.json` — 14 panels, valid JSON, datasource UIDs | PASS after fix | **BUG FIXED** panel 13 PromQL used `opensearch_*` but elasticsearch_exporter emits `elasticsearch_*`; corrected to `elasticsearch_jvm_memory_used_bytes{area="heap"} / elasticsearch_jvm_memory_max_bytes{area="heap"} * 100` |
| Grafana datasource UID — `uid: prometheus` in provisioning | PASS | |
| Prometheus — `opensearch` scrape job (`opensearch-exporter:9114`) | PASS | |
| streaming-service Kafka producer retry config | PASS | |
| playlist-service Kafka producer retry config | PASS | |
| recommendation-service Resilience4j config | PASS | |
| `@CircuitBreaker(name="redis")` on `getDailyMix` / `getSimilarSongs` | PASS | |
| `RESILIENCE4J_REDIS_FAILURE_RATE` / `WAIT_DURATION` in docker-compose.yml | PASS after fix | **GAP FIXED** — vars were missing from recommendation-service environment block; added with defaults matching application.yml |
| `opensearch-exporter` on `music-net` network | PASS | `networks: [music-net]` confirmed |
| k6 M-21 flows (all 8 user flows) | PASS | registration, login, catalog, search, stream, playlist, analytics, recommendations all exercised |
| k6 Phase 5 isolation (not selectable via K6_SCENARIO in main.js) | PASS | `phase5-peak.js` is a standalone file; no reference to it in `main.js` |
| k6 soak scenario present in main.js | PASS | grep confirmed at lines 82, 88, 241, 246 |
| `verify-integration.sh` — `docker compose` v2 syntax | PASS | `$COMPOSE --profile load-test run --rm` correctly places `--profile` before subcommand |
| `RecommendationServiceTest` — 9 tests, all PASS | PASS after fix | **FIXED** Mockito inline mock maker incompatible with Spring Data Redis on Java 26; switched to `ByteBuddyMockMaker` (subclass); `lenient()` stub in `@BeforeEach`; all 9 tests green |

### Validation cost impact

| Fix | Cost impact |
|---|---|
| scaling.json panel 13 PromQL correction | Zero — panel JSON edit only; no runtime cost change |
| RESILIENCE4J env vars in docker-compose.yml | Zero — env var wiring only; tunable at runtime, zero if not overridden |
| Mockito mock maker switch | Zero — test infrastructure only; no production artifact change |

*Session 9 validation complete. All checks pass. Phase 10 implementation is complete pending live stack run (`bash scripts/verify-integration.sh`).*

---

## Post-validation — Backend Test Suite Cleanup (2026-06-15)

**Finding:** Java 26 + Mockito 5.x inline mock maker incompatibility affected 4 additional services (auth-service, catalog-service, streaming-service, playlist-service). `ByteBuddyMockMaker` config file added to `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` in all 4 services, matching the fix already applied to recommendation-service.

**Result:** 191 tests across all 8 services, 0 failures. See DESIGN-DECISIONS.md DD-015 for full rationale.

**Cost impact:** Zero — test infrastructure only, no production artifact changes.
