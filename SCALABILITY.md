# Scalability Plan

This document defines a Docker Compose-only scaling plan for the backend benchmark application. It is planning material, not an implementation record. The plan keeps the system inside the current architecture: eight backend services, dedicated persistence per stateful service, Kafka for events, Prometheus and Grafana for observability, and k6 for repeatable load generation.

No Kubernetes, Helm, Nomad, Swarm, Terraform, managed cloud services, or external orchestrator is part of this plan.

## Target Workload

| Metric | Estimate | Rationale |
|---|---:|---|
| Registered users | 1,000,000 | Target |
| DAU | 100,000 | 10% of registered users, industry norm for streaming apps |
| Peak concurrent users | 20,000 | 20% of DAU active at the same time |
| Avg. songs streamed per session | 10 | About 30 min/session at 3 min/song |
| Playback events per second, peak | ~40,000 | 20,000 users times 2 events/song average |
| Auth logins per second, peak | ~500 | Session start plus 1 hour token refresh |
| Catalog/search requests per second, peak | ~4,000 | About 20% of active users browsing at once |
| Playlist mutations per second, peak | ~200 | Lower-frequency write operation |

## Scope And Assumptions

- The application remains backend-only. Frontend UI, browser flows, and frontend runtime containers are out of scope.
- Services remain Java 21 Spring Boot applications built with Maven and run in Docker.
- Docker Compose remains the only deployment mechanism.
- A reverse proxy/load balancer is required for benchmark realism because clients and k6 should not target individual replicas directly.
- The target workload is a benchmark target, not a guarantee that one laptop can run full 1M-scale throughput.
- Scale profiles should distinguish normal runtime, smoke tests, calibration runs, and full benchmark runs so idle cost is not normalized into the default developer workflow.
- Required behavior must not be removed to reduce cost. Cost savings must come from right-sizing, batching, caching, tuning, and running expensive components only when they are needed.

## Traffic Shape

The workload is uneven, so services should not scale equally.

| Service | Dominant Load | Scaling Pattern | Initial Full-Benchmark Target | Cost-Aware Reasoning |
| --- | --- | --- | ---: | --- |
| Gateway | All external HTTP traffic | Horizontal if needed, but one tuned Nginx container is usually enough first | 1-2 | It is cheaper to tune worker connections and keep one gateway until metrics show saturation. A second gateway only helps if the host can distribute traffic to both. |
| Auth Service | Login/register bursts and token issuing | Horizontal, stateless app replicas | 3-4 | 500 logins/s is moderate. JWT validation is local in other services, so Auth should not be over-scaled for every protected request. |
| Catalog Service | Paginated metadata reads | Horizontal, mostly read-heavy | 4-6 | 4,000 browse/search-adjacent requests/s can create DB pressure. Add replicas gradually and rely on pagination and indexes before over-provisioning. |
| Streaming Service | Very high request/event generation | Horizontal, stateless | 8-12 | Streaming has the highest request/event path and is stateless, so replicas are cheap and effective. Segment payload size must be tuned to avoid artificial network cost. |
| Playlist Service | Lower-rate transactional writes | Horizontal modestly, DB-bound | 2-3 | 200 mutations/s is lower than streaming/search. Extra replicas can increase PostgreSQL contention, so scale only after DB/index evidence. |
| Search Service | Text/filter search | Horizontal app replicas, careful OpenSearch scaling | 4-6 | Search can be CPU and index-cache heavy. App replicas help route traffic, but OpenSearch capacity is usually the real cost driver. |
| Analytics Service | Kafka consumption, history reads, chart queries | Consumer replicas limited by Kafka partitions plus vertical ClickHouse tuning | 3-6 consumers | 40,000 playback events/s makes ingestion the critical path. Scale consumers only with partition count and ClickHouse insert capacity. |
| Recommendation Service | Read-heavy recommendations plus event consumption | Horizontal app replicas plus Redis cache | 3-5 | Caching reduces PostgreSQL and CPU work. Over-scaling without cache hit data wastes memory and connections. |
| Notification Service | Internal playlist-event consumption | Mostly vertical/small horizontal | 1-2 | Notification traffic is low and internal. Keep small unless Kafka lag or MongoDB writes prove bottleneck. |

## Docker Compose Scaling Model

Use Compose overrides and profiles to keep cost visible:

- `docker-compose.yml`: default local runtime with one application container per service, required infrastructure, Prometheus, Grafana, and k6.
- Smoke profile: minimal resources and short k6 smoke runs.
- Calibration profile: moderate replicas and resource limits used to find bottlenecks before full benchmark scale.
- Full benchmark profile: explicit per-service replica counts and larger infrastructure resource limits for the target workload.
- Cost-smoke profile: reduced observability retention, optional exporters, small heap/memory values, and minimal k6 duration for low-cost correctness checks.

Application replicas should be expressed with `docker compose up --scale <service>=N` or a Compose override that documents the intended count. Stateful infrastructure should not be blindly scaled with `--scale` because storage, clustering, partitions, and data ownership require deliberate configuration.

## Load Balancer / Reverse Proxy

A gateway is needed and should remain in Docker Compose. Its role is:

- expose one stable entrypoint to k6 and manual smoke tests,
- distribute HTTP traffic across application replicas by Docker service DNS,
- centralize simple routing, timeouts, buffering, and gateway metrics,
- avoid changing k6 scripts when app replica counts change.

The gateway should be tuned before being replicated:

- increase worker connections for high concurrency,
- keep upstream keepalive enabled for app services,
- use bounded proxy timeouts,
- expose Nginx metrics through the existing exporter,
- avoid caching authenticated user-specific responses unless explicitly safe.

Cost trade-off: a tuned single gateway avoids paying for idle gateway replicas. Add a second gateway only when gateway CPU, connection saturation, or queueing appears in Prometheus during calibration.

## Service-Specific Scaling Plan

### Auth Service

Auth should scale horizontally to 3-4 replicas for the full benchmark. It is stateless after issuing JWTs, and other services validate JWTs locally, so Auth does not sit on every protected request.

Optimization plan:

- keep user email uniquely indexed in PostgreSQL,
- avoid synchronous token validation calls from other services,
- keep password hashing cost realistic but not so high that benchmark login traffic becomes the only bottleneck,
- limit registration/login request payload sizes,
- monitor login latency, registration conflicts, DB connection pool saturation, and auth-db CPU.

Cost trade-off: scaling Auth beyond the login target wastes CPU because protected reads do not call Auth. Prefer a small replica count with a right-sized auth-db.

### Catalog Service

Catalog is read-heavy and should scale to 4-6 replicas after database indexes and pagination are confirmed. The catalog database should be protected from unbounded scans.

Optimization plan:

- keep pagination mandatory and enforce maximum page size,
- index song ID and common browse sort/filter columns used by the implemented API,
- avoid re-ingesting the dataset from every replica during scaled startup,
- consider a planned ingestion-owner mode so one replica seeds data and others start read-only after the DB is ready,
- monitor catalog-db slow queries, connection pool usage, page size distribution, and response latency.

Cost trade-off: additional Catalog replicas are cheaper than increasing PostgreSQL first only if DB CPU and I/O remain below saturation. Duplicate startup ingestion is a cost risk because it wastes CPU and DB writes.

### Streaming Service

Streaming has the highest request and event volume. It is stateless and should be scaled aggressively to 8-12 replicas for full benchmark runs.

Optimization plan:

- keep stream descriptor generation CPU-light,
- make dummy segment count and segment size profile-specific,
- batch or tune Kafka producer settings for playback events,
- set bounded Kafka producer timeouts so slow broker writes do not tie up request threads indefinitely,
- monitor request rate, p95/p99 latency, response sizes, Kafka producer latency, failed sends, and gateway upstream queueing.

Cost trade-off: segment payload size directly drives network and CPU cost. Full network simulation should be enabled only in benchmark profiles; smoke/calibration can use smaller payloads while still validating behavior.

### Playlist Service

Playlist mutations are lower-volume but transactional. Scale to 2-3 replicas and focus first on PostgreSQL indexes and connection limits.

Optimization plan:

- index playlist owner/user ID, playlist ID, and ordered track lookup columns,
- keep reorder operations bounded by playlist size validation,
- avoid unnecessary cross-service catalog lookups on every mutation unless required behavior later adds validation,
- keep Kafka playlist update events asynchronous,
- monitor DB locks, write latency, connection pool usage, mutation error rate, and Kafka publish latency.

Cost trade-off: too many Playlist replicas can increase database contention without improving throughput. Database correctness and indexes matter more than high replica counts.

### Search Service

Search should scale to 4-6 application replicas, but OpenSearch is the likely bottleneck. The index should be designed for text plus genre, BPM, and year filters.

Optimization plan:

- keep the service-level maximum page size enforced,
- use OpenSearch mappings suited to text fields plus keyword/numeric filter fields,
- index song ID for direct lookup and deduplication,
- avoid rebuilding the full index from every Search replica on startup,
- consider a single indexing-owner mode for scaled deployments,
- monitor OpenSearch JVM heap, query latency, rejected thread-pool tasks, refresh/merge pressure, index size, and search-service HTTP latency.

Cost trade-off: OpenSearch memory is expensive. Increase app replicas only when app CPU is saturated; increase OpenSearch memory/CPU when query or indexing metrics prove it is the bottleneck.

### Analytics Service

Analytics must absorb the playback event stream and serve history/chart reads. Scale consumers to 3-6 replicas only after the Kafka topic has enough partitions and ClickHouse insert throughput is validated.

Optimization plan:

- partition playback events by user or another stable key that preserves useful ordering,
- align Analytics consumer concurrency/replica count with Kafka partition count,
- batch ClickHouse inserts where possible,
- store only supported event types and canonical authenticated user IDs,
- index/order ClickHouse tables for user history and chart aggregation access patterns,
- keep global chart limits bounded,
- monitor Kafka lag, consumer rebalance frequency, ClickHouse insert latency, ClickHouse parts count, chart query latency, and history latency.

Cost trade-off: ClickHouse is a major memory/disk cost center. Batching and table-ordering improvements are cheaper than adding consumers that generate too many small inserts.

### Recommendation Service

Recommendation should scale to 3-5 replicas, with Redis absorbing repeated read traffic. Its Kafka consumer path should be scaled based on lag, not HTTP request rate alone.

Optimization plan:

- cache Daily Mix and similar-song responses with bounded TTL,
- keep recommendation limits capped,
- index durable user-song interaction tables by user ID and song ID,
- evict or refresh cache entries when relevant playback events arrive,
- monitor Redis hit ratio, Redis memory, recommendation-db query latency, Kafka lag, cache evictions, and HTTP latency.

Cost trade-off: Redis memory is cheaper than repeated PostgreSQL queries and recomputation until the hit ratio falls or memory pressure grows. A short TTL limits stale data risk and memory growth.

### Notification Service

Notification is internal and lower traffic. Keep it at 1 replica for normal runs and 2 replicas only for full benchmark or failure-isolation checks.

Optimization plan:

- consume playlist events asynchronously,
- keep MongoDB indexes on user ID, read state, created timestamp, and source event ID if used for idempotency,
- cap or batch internal notification writes if playlist-event volume grows,
- monitor Kafka lag, MongoDB insert latency, collection size, and document growth.

Cost trade-off: notification workload is not a primary target metric, so scaling it early is low value. Keep it small unless lag appears.

## Infrastructure Bottlenecks

### PostgreSQL

Affected services: Auth, Catalog, Playlist, Recommendation.

Risks:

- connection pool explosion when app replicas increase,
- missing indexes on user IDs, song IDs, playlist ownership, and recommendation interaction lookups,
- catalog startup ingestion repeated by multiple replicas,
- write contention in playlist mutations.

Plan:

- define service-specific pool limits so total connections fit each DB container,
- add or verify indexes matching high-frequency lookups,
- keep page sizes bounded,
- avoid read/write split in the first Docker-only plan unless metrics prove reads dominate and a replica setup is explicitly implemented.

Cost trade-off: right-sized connection pools and indexes are cheaper than increasing DB memory/CPU too early.

### OpenSearch

Risks:

- heap pressure during search and indexing,
- duplicate full-index rebuilds from multiple Search replicas,
- expensive broad queries without bounded size,
- refresh/merge pressure during startup indexing.

Plan:

- keep one OpenSearch node for local baseline and calibration,
- allow larger memory/CPU in benchmark profile,
- plan a multi-node Docker Compose OpenSearch profile only if single-node metrics prove it is the bottleneck,
- keep indexing controlled by one owner process or preloaded volume for high-replica runs.

Cost trade-off: OpenSearch nodes are memory-heavy. Avoid multi-node OpenSearch until metrics justify it.

### ClickHouse

Risks:

- too many small inserts from Analytics consumers,
- unbounded event retention,
- chart queries scanning unnecessary data,
- disk growth from 40,000 events/s benchmark traffic.

Plan:

- batch inserts,
- use ordering/partitioning aligned with user history and global chart queries,
- keep chart and history pagination limits bounded,
- document retention expectations for benchmark data volumes.

Cost trade-off: batching and retention controls reduce CPU, disk, and storage growth without changing required analytics behavior.

### Redis

Risks:

- unbounded recommendation cache growth,
- low hit ratio making Redis an added cost rather than a saving,
- cache churn under high playback event invalidation.

Plan:

- keep TTL configurable,
- monitor hit ratio and memory,
- use maxmemory policy in benchmark profiles if needed,
- keep cache keys scoped and compact.

Cost trade-off: Redis should reduce database and CPU cost; if hit ratio is poor, reduce TTL or cache fewer responses.

### MongoDB

Risks:

- notification collection growth,
- missing indexes for future retrieval,
- unnecessary scaling for low internal traffic.

Plan:

- keep MongoDB single-node in normal and calibration profiles,
- index idempotency and retrieval fields if retrieval is added,
- scale vertically only if internal event lag or insert latency appears.

Cost trade-off: MongoDB is not on the hottest path, so keep it small.

### Kafka

Risks:

- playback-events topic cannot absorb ~40,000 events/s,
- partitions too low for Analytics and Recommendation consumer scaling,
- broker disk growth from retention settings,
- consumers blocked by malformed records or slow writes.

Plan:

- increase playback-events partitions for benchmark profiles,
- align consumer counts with partitions,
- tune producer batching and linger for Streaming,
- keep retention lower in smoke/calibration profiles and explicit in full benchmark profiles,
- monitor broker bytes in/out, request latency, under-replicated partitions if multi-broker is introduced, disk usage, consumer lag, and dropped/error events.

Cost trade-off: a single broker is cheap and sufficient for local smoke, but full playback-event targets may require more broker CPU/disk or partition tuning. Retention must be sized to benchmark evidence needs, not left unbounded.

## Backpressure And Graceful Degradation

- Gateway should use bounded timeouts and expose 5xx/timeout metrics.
- Streaming should fail fast or return a controlled error if Kafka publishing is unavailable rather than tying up request threads indefinitely.
- Analytics, Recommendation, and Notification consumers should continue past malformed Kafka records and expose lag/error metrics.
- Inter-service HTTP calls must keep bounded retry with exponential backoff and circuit breaking where such calls exist.
- Recommendation can degrade to durable PostgreSQL-derived results or simple fallback recommendations when Redis is unavailable.
- Analytics chart endpoints should keep result limits bounded and avoid expensive unbounded scans.
- k6 should track dropped iterations separately from backend failures so host/load-generator saturation is not misread as application capacity.

Cost trade-off: bounded retries and graceful degradation prevent expensive cascading saturation where more replicas are added to hide a failure mode.

## Metrics And Dashboards

Prometheus and Grafana should guide scaling decisions. Required dashboards:

- Gateway: request rate, 4xx/5xx, upstream latency, active connections, retry/timeout symptoms.
- Spring services: request rate, p50/p95/p99 latency, error rate, JVM heap, GC pauses, CPU, thread pools, datasource pool usage.
- Kafka: topic bytes in/out, broker request latency, partition count, consumer lag by group, failed deserialization/error handler counts if exposed.
- PostgreSQL: connections, transaction rate, slow queries if exported, CPU/memory/container limits.
- OpenSearch: heap, CPU, query latency, rejected tasks, index refresh/merge pressure, disk.
- ClickHouse: insert rate, query latency, memory, disk, parts count, row count growth.
- Redis: memory, hit ratio, evictions, command latency.
- MongoDB: insert rate, collection size, query latency if exported.
- k6: request duration, failed request rate, dropped iterations, scenario-level throughput, VUs, and generated cost summary artifacts.

Scale-up thresholds should be based on sustained evidence, for example:

- app p95 latency above target while DB/broker are healthy,
- CPU above 70-80% for a sustained calibration window,
- JVM heap or GC pauses affecting p95 latency,
- database connection pool exhaustion,
- Kafka consumer lag growing continuously,
- OpenSearch rejected tasks or ClickHouse insert backlog.

## k6 Load-Test Phases

1. Smoke validation: 5 VUs, short duration, all main flows through the gateway. Goal: correctness and auth/persistence/messaging proof.
2. Single-service probes: isolate Auth login, Catalog browsing, Search queries, Streaming descriptor/ended events, Playlist mutations, Recommendation reads, and Analytics history/chart reads.
3. Mixed baseline: low realistic mix to confirm no obvious cross-service breakage.
4. Calibration ramp: gradually increase toward 10% of target rates and record first bottleneck.
5. 100k-DAU approximation: exercise the documented 100k DAU shape with reduced duration suitable for local/CI hardware.
6. 1M target profile: run the full target ratios only on a host with enough CPU, memory, disk, and Docker resources.
7. Soak run: hold a lower but stable load to observe Kafka lag, ClickHouse disk growth, Redis memory, and database connection stability.

Run order should always move from correctness to isolated bottleneck discovery to mixed load. Do not start with the full 1M profile because it can saturate the load generator or host before producing useful service evidence.

## Recommended Scaling Order

1. Tune k6 and gateway first so traffic generation and routing are not the bottleneck.
2. Scale Streaming Service and Kafka partitions/producer settings because playback events dominate the workload.
3. Tune Analytics ingestion and ClickHouse because 40,000 playback events/s creates the largest persistent write pressure.
4. Tune Search/OpenSearch and Catalog/PostgreSQL for the 4,000 browse/search requests/s path.
5. Tune Recommendation Redis caching and app replicas to reduce repeated DB work.
6. Tune Auth for the 500 login/s target after confirming JWT local validation avoids per-request Auth load.
7. Tune Playlist PostgreSQL for 200 mutations/s.
8. Tune Notification last unless Kafka lag shows it is blocking internal event processing.

This order avoids spending money on low-impact services before the dominant streaming/event/search paths are understood.

## Planned Compose Profiles

| Profile | Purpose | Cost Posture | Expected Components |
| --- | --- | --- | --- |
| Default | Developer/local correctness | Low | One replica per service, required infra, modest resource limits. |
| Smoke | Final correctness checks | Very low | One replica per service, short k6 smoke, minimal retention. |
| Calibration | Bottleneck discovery | Moderate | Gateway, selected scaled services, observability enabled, measured resource limits. |
| 100k | Mid-scale benchmark | Higher | Service-specific replicas, tuned Kafka/OpenSearch/ClickHouse, k6 mixed workload. |
| 1m | Target benchmark | Highest | Full planned replica counts, highest infrastructure limits, explicit host requirements and evidence capture. |
| Cost-smoke | Cheap regression check | Lowest practical | Required backend and infra only, optional exporters/profiled observability, short workload. |

## First Implementation Version

The first implementation version applies the plan in a practical Docker Compose form:

- `docker-compose.yml` now exposes application services only on the shared Docker network and routes external application traffic through the Nginx `gateway` service on `GATEWAY_HOST_PORT`. This removes host-port conflicts when application services are replicated.
- `config/nginx/nginx.conf` defines backend routes for Auth, Catalog, Streaming, Playlist, Search, Analytics, and Recommendation. Notification remains internal because the architecture does not require a public Notification API.
- `kafka-init` creates `playback-events` and `playlist-events` with configurable partition counts, avoiding accidental single-partition topics during benchmark runs.
- Kafka retention, segment size, heap, producer batching, producer compression, and producer timeouts are configurable through Compose environment variables.
- Redis cache memory policy is configurable so recommendation caching can reduce PostgreSQL/CPU cost without unbounded memory growth.
- PostgreSQL-backed services expose connection pool caps through environment variables so app replica growth does not silently exhaust database connections.
- Prometheus uses DNS service discovery for each backend service name so scaled Compose replicas can be discovered without rewriting scrape targets.
- Nginx, Kafka, Redis, and container exporters are placed behind the `benchmark` profile. This keeps normal runtime cheaper while preserving richer evidence collection for benchmark runs.
- `docker-compose.scale-smoke.yml`, `docker-compose.scale-calibration.yml`, `docker-compose.scale-100k.yml`, and `docker-compose.scale-1m.yml` express different service counts and resource envelopes instead of scaling all services equally.
- `load-generator/k6/scripts/smoke.js` and `mixed-user-journey.js` run through the gateway and write cost/performance summaries to the `k6-results` volume.

Cost trade-off: scaled profiles disable repeated Catalog ingestion and Search indexing with `CATALOG_INGESTION_ENABLED=false` and `SEARCH_INDEXING_ENABLED=false`. This avoids expensive duplicate startup work across replicas, but it assumes the default profile has already seeded the Catalog database and OpenSearch index, or that persisted volumes already contain that data.

## Risks And Trade-Offs

- Docker Compose does not provide production-grade orchestration or automatic rescheduling. This is acceptable because the project is a benchmarkable Docker-based system.
- Full 1M workload may exceed a single developer machine. The plan supports running smaller proportional profiles locally and full profiles only on adequately sized Docker hosts.
- Scaling stateful infrastructure is more expensive and riskier than scaling stateless app services.
- Kafka partition increases improve parallelism but can increase broker overhead and operational complexity.
- ClickHouse batching improves cost and throughput but can increase event visibility latency.
- Redis caching lowers database work but risks stale recommendations.
- More app replicas can worsen database pressure if connection pools are not bounded.
- Observability has cost. Prometheus/Grafana remain required, but retention and exporters should be profile-specific.

## Validation For Future Implementation

When implementation begins, each planned scaling change should be validated with:

- `docker compose config --quiet` for every profile and override,
- `docker compose config --services` to confirm backend-only service inventory,
- service image builds with Maven verification,
- k6 smoke through the gateway,
- profile-specific k6 runs with generated summaries,
- Prometheus target checks,
- dashboard JSON/provisioning checks,
- focused checks for Kafka lag, ClickHouse inserts, OpenSearch queries, Redis cache behavior, and PostgreSQL connection pools.
