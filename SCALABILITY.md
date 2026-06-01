# Docker Compose Scalability Plan For 1,000,000 Users

## Scope

This plan describes how to benchmark and tune the current music streaming system toward a 1,000,000-user workload while staying fully inside Docker Compose.

The deployment must remain centered on:

- Dockerfiles
- `docker-compose.yml`
- Compose override files and profiles
- container CPU/memory limits
- Prometheus, Grafana, and k6

Kubernetes, Helm, Nomad, Swarm, and other orchestrators are out of scope.

## Target Scalability Plan

This table is the canonical target workload for Docker-based benchmarking. Service counts, k6 rates, Kafka partitions, database tuning, and dashboard interpretation should be evaluated against these numbers.

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

The previous scalability document used ranges for some 1M-profile service counts and did not include this exact workload table. It has been corrected so the target constants above are explicit and drive the implemented Compose and k6 benchmark profiles.

## Implemented First Version

The first serious scalability implementation keeps the system Docker Compose-only and adds benchmark-oriented infrastructure without changing the service boundaries.

Implemented files:

- `config/nginx/gateway.conf`
- `config/nginx/proxy_params`
- `docker-compose.scale-baseline.yml`
- `docker-compose.scale-100k.yml`
- `docker-compose.scale-1m.yml`
- `load-generator/k6/smoke.js`
- `load-generator/k6/mixed-user-journey.js`
- `config/grafana/dashboards/backend-scalability.json`
- `config/prometheus/prometheus.yml`
- `docker-compose.yml`
- database scaling index migrations for Catalog, Playlist, and Recommendation

Decisions:

- **Nginx gateway was added as the Docker-only traffic entrypoint.** This is necessary because scaled Compose replicas cannot all bind the same host ports. The gateway exposes one stable port, routes by path to the existing services, and lets app replicas stay internal on `benchmark-network`.
- **Direct service ports remain in the base Compose file for development.** The scale override files remove app host ports with Compose `!reset []`, so benchmark runs can use `docker compose up --scale` safely without breaking the simpler developer path.
- **Different scale profiles were encoded as Compose overrides.** Baseline, 100k, and 1M profiles express different resource and replica expectations for services instead of scaling all services equally.
- **Streaming receives the highest replica count.** It is stateless and expected to carry the heaviest request volume from stream starts, segment fetches, and the target of about 40,000 playback events per second.
- **Search, Catalog, Recommendation, and Analytics receive medium/high replica counts.** These are read-heavy or event-heavy services with backend-specific bottlenecks, so replicas are useful but must be paired with OpenSearch, PostgreSQL, Redis, Kafka, and ClickHouse tuning.
- **Auth and Notification are scaled more modestly.** Auth targets about 500 login requests per second and is not on every request because JWT validation is local, while Notification is internal and can tolerate lag.
- **Kafka topic initialization was added.** The `kafka-init` service creates playback and playlist topics with configurable partitions, making consumer parallelism repeatable across benchmark runs.
- **Bounded DB and Kafka client tuning was added through environment variables.** Hikari pool sizes, connection timeouts, Kafka producer batching, listener concurrency, and JVM memory percentages are now profile-tunable without code changes.
- **Redis max-memory and eviction policy are now explicit.** This makes recommendation cache behavior repeatable and prevents hidden memory growth during long benchmark runs.
- **Prometheus now scrapes gateway, Kafka, Redis, and container metrics in addition to Spring Boot metrics.** This gives the minimum signal needed to decide whether the next bottleneck is request routing, service code, Kafka lag, Redis cache pressure, or container saturation.
- **A Grafana backend scalability dashboard was provisioned.** It focuses on request rate, p95 latency, error rate, top tracks, container CPU, JVM heap, Kafka lag, Redis hit/miss behavior, gateway connections, and Hikari active connections.
- **k6 scripts now target the gateway by default.** This keeps load-test traffic on the same gateway path used during scaled benchmark runs.
- **Database index migrations were added only for existing hot paths.** Catalog gets filter/sort indexes, Playlist gets owner/track lookup indexes, and Recommendation gets positive interaction lookup indexes. No new behavior was invented.

Validation performed:

- `docker compose config --quiet`
- `docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml config --quiet`
- `docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet`
- `docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config --quiet`
- `node --experimental-vm-modules` syntax parsing for `load-generator/k6/mixed-user-journey.js` and `load-generator/k6/smoke.js`

Recommended first benchmark command:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js
```

Recommended scaled benchmark command shape:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml up -d --build --scale streaming-service=6 --scale catalog-service=3 --scale search-service=3 --scale recommendation-service=3 --scale analytics-service=2 --scale auth-service=4 --scale playlist-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=50 -e K6_AUTH_PREALLOCATED_VUS=100 -e K6_AUTH_MAX_VUS=250 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_CATALOG_SEARCH_PREALLOCATED_VUS=300 -e K6_CATALOG_SEARCH_MAX_VUS=700 -e K6_STREAMING_SESSION_RATE=2000 -e K6_STREAMING_PREALLOCATED_VUS=1500 -e K6_STREAMING_MAX_VUS=3000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 -e K6_PLAYLIST_PREALLOCATED_VUS=100 -e K6_PLAYLIST_MAX_VUS=250 k6 run /scripts/mixed-user-journey.js
```

## Assumptions

- "1,000,000 users" means registered users with a smaller concurrent active population during a benchmark window.
- The first target should be a realistic concurrency ramp rather than 1,000,000 simultaneous streaming sessions on one host.
- JWT validation is local in every protected service, so Auth Service is not called on every request.
- The Streaming Service simulates audio with generated segment payloads and does not serve real audio files.
- The current architecture remains fixed: Auth, Catalog, Streaming, Playlist, Search, Analytics, Recommendation, Notification, Kafka, PostgreSQL, OpenSearch, ClickHouse, Redis, MongoDB, Prometheus, Grafana, and k6.
- Docker Compose benchmarking may require multiple host sizes or repeated runs with different resource limits. Compose can model scaling behavior, but one machine may not physically sustain the final target.

## Traffic Shape

Different services should not scale equally because user traffic is uneven.

The canonical target assumes 100,000 DAU, 20,000 peak concurrent users, about 40,000 playback events per second, about 500 login requests per second, about 4,000 combined catalog/search requests per second, and about 200 playlist mutations per second.

| Workload | Expected Pressure | Main Services |
| --- | --- | --- |
| Registration/login | Bursty, lower than playback traffic | Auth, Auth PostgreSQL |
| Home/discovery load | Frequent reads, cacheable | Recommendation, Analytics global charts, Catalog |
| Catalog browsing | Read-heavy, paginated | Catalog, Catalog PostgreSQL |
| Search | Read-heavy, bursty, CPU/index-heavy | Search, OpenSearch |
| Stream start | Very frequent during active listening | Streaming, Kafka |
| Segment fetches | Highest request volume if simulated segments are requested repeatedly | Streaming |
| Playlist edits | Moderate read/write, consistency-sensitive | Playlist, Playlist PostgreSQL, Kafka if events enabled |
| History/charts | Mixed user reads and aggregate reads | Analytics, ClickHouse |
| Recommendation updates | Async event-driven plus cached reads | Recommendation, Redis, PostgreSQL, Kafka |
| Notifications | Mostly async internal writes, low public traffic | Notification, MongoDB, Kafka |

## Load Balancer / Reverse Proxy

A reverse proxy layer is recommended before scaling service replicas with Docker Compose.

Role:

- expose a stable external port for k6
- route `/auth`, `/catalog`, `/stream`, `/playlists`, `/search`, `/analytics`, and `/recommend` to internal service replica groups
- provide round-robin load distribution across `docker compose up --scale` replicas
- centralize request logging, timeouts, body size limits, and simple rate limiting
- avoid publishing host ports from every scaled replica

Docker-only fit:

- use Nginx, HAProxy, or Traefik as a Compose service on `benchmark-network`
- keep services addressable by Compose DNS names
- configure upstreams by service name and internal port
- expose only the proxy to k6 for user-facing traffic

Trade-off:

- Nginx static upstreams are simple but may need reloads or explicit replica aliases.
- Traefik integrates more naturally with Docker labels, but adds another moving part.
- For benchmarking, HAProxy or Nginx is predictable and easy to observe.

Recommended baseline:

- Add a `gateway`/`reverse-proxy` Compose service before major scale tests.
- Keep direct service host ports available only in debug profiles.
- k6 should target the proxy, not individual service ports.

## Horizontal vs Vertical Scaling

| Component | Scaling Pattern | Why |
| --- | --- | --- |
| Auth Service | Mostly horizontal, modest count | Stateless after DB lookup; login/register are bursty, but JWT validation is local elsewhere. |
| Catalog Service | Horizontal for reads, DB tuned vertically first | Service is stateless; catalog DB read pressure grows with browsing and lookup traffic. |
| Streaming Service | Aggressive horizontal | Stateless, high request rate, segment payload generation and Kafka publishing can be spread across replicas. |
| Playlist Service | Horizontal with DB constraints | Stateless service layer, but writes and ordering operations depend on PostgreSQL consistency. |
| Search Service | Horizontal service layer, OpenSearch vertical/replica tuning | Service is stateless; OpenSearch is the real bottleneck for query CPU, heap, shards, and refresh behavior. |
| Analytics Service | Horizontal consumers and API reads, ClickHouse vertical first | API is stateless, but event ingestion depends on Kafka partitions and ClickHouse write throughput. |
| Recommendation Service | Horizontal reads/consumers, Redis/PostgreSQL tuned separately | Daily mix and similar endpoints benefit from cache; Kafka consumers need partition-aware scaling. |
| Notification Service | Low horizontal count, mostly async | Internal consumer workload is lower priority unless playlist events become heavy. |
| PostgreSQL DBs | Mostly vertical in Compose | Stateful single containers are simpler for Docker benchmarking; add indexes and connection limits before replicas. |
| OpenSearch | Vertical first, then multiple nodes only if Compose host resources allow | Search backend needs heap, file cache, shard discipline, and careful storage. |
| ClickHouse | Vertical first | Append/aggregation store benefits from CPU, RAM, disk throughput, batch inserts. |
| Redis | Vertical first | Cache should stay memory-resident; single-node Redis is acceptable for Compose benchmarking. |
| MongoDB | Vertical first | Notification pressure is not expected to dominate the 1M-user benchmark. |
| Kafka | Vertical first, partition/topic tuning, then multi-broker only if needed | Event throughput and consumer lag determine whether single broker is enough. |

## Starting Scale Profiles

These are starting points for benchmark profiles, not final capacity guarantees.

### Profile A: Baseline Functional Load

Use this to validate dashboards and scripts.

| Service | Instances |
| --- | ---: |
| Auth | 1 |
| Catalog | 1 |
| Streaming | 2 |
| Playlist | 1 |
| Search | 1 |
| Analytics | 1 |
| Recommendation | 1 |
| Notification | 1 |
| Kafka | 1 |
| PostgreSQL DBs | 1 each |
| OpenSearch | 1 |
| ClickHouse | 1 |
| Redis | 1 |
| MongoDB | 1 |

### Profile B: Moderate Pre-Target Calibration

Use this after endpoint-specific tests pass. This is not the canonical 1,000,000-registered-user target; it is a lower-pressure calibration profile for finding obvious bottlenecks before attempting the target rates.

| Service | Instances | Notes |
| --- | ---: | --- |
| Auth | 2 | Handles login bursts; registration is lower volume. |
| Catalog | 3 | Read-heavy browsing benefits from replicas. |
| Streaming | 6 | Highest request volume from stream starts and segments. |
| Playlist | 2 | Moderate interactive writes. |
| Search | 3 | Query-heavy and CPU-sensitive. |
| Analytics | 2 | API reads plus Kafka consumer work. |
| Recommendation | 3 | Cache-backed discovery reads. |
| Notification | 1 | Internal, lower pressure. |
| Kafka | 1 | Increase partitions before brokers. |
| OpenSearch | 1 larger container | Tune heap/resources first. |
| ClickHouse | 1 larger container | Batch/event throughput test. |
| Redis | 1 larger container | Ensure cache memory is sufficient. |

### Profile C: 1M Registered / Target Benchmark

Use this only after Profile B bottlenecks are understood. The profile is aligned with the target plan: 1,000,000 registered users, 100,000 DAU, 20,000 peak concurrent users, about 40,000 playback events per second, about 500 auth logins per second, about 4,000 combined catalog/search requests per second, and about 200 playlist mutations per second.

| Service | Instances | Notes |
| --- | ---: | --- |
| Auth | 4 | Targets about 500 login requests per second; JWT validation stays local elsewhere. |
| Catalog | 8 | Shares the about 4,000 catalog/search requests per second browsing pressure with Search. |
| Streaming | 20 | Primary scale target for 20,000 concurrent users and about 40,000 playback events per second. |
| Playlist | 4 | Targets about 200 playlist mutations per second while limiting DB connection pressure. |
| Search | 8 | Shares the about 4,000 catalog/search requests per second pressure and protects OpenSearch with timeouts. |
| Analytics | 8 | Consumes the playback-event stream and serves history/charts while ClickHouse handles persistence. |
| Recommendation | 8 | Cache-heavy read path plus playback-event consumers. |
| Notification | 2 | Increase only if Kafka lag or Mongo writes rise. |
| Kafka | 1 larger broker, then 3 brokers if single broker saturates | Compose can run 3 brokers, but complexity rises. |
| OpenSearch | 1 large node, then 2-3 nodes if host resources allow | Keep shard count low and measured. |
| ClickHouse | 1 large node | Prefer batching before adding nodes. |
| Redis | 1 large node | Watch memory and eviction. |
| MongoDB | 1 | Usually not the bottleneck. |

## Stateless Service Scaling

These services are safe to scale with `docker compose up --scale` once traffic goes through a reverse proxy:

- Auth Service
- Catalog Service
- Streaming Service
- Playlist Service
- Search Service
- Analytics Service API instances
- Recommendation Service API instances
- Notification Service consumers, with care for Kafka group behavior

Important:

- Do not publish fixed host ports on scaled replicas.
- Use the reverse proxy as the stable entry point.
- Keep all replicas on `benchmark-network`.
- Ensure every scaled service has bounded DB connection pools. Otherwise, horizontal scaling can overload databases faster than it improves throughput.

## Stateful Component Scaling

### PostgreSQL: Auth, Catalog, Playlist, Recommendation

PostgreSQL containers should mostly scale vertically in this Docker Compose benchmark.

Pressure points:

- Auth: unique email lookup and password hash verification.
- Catalog: paginated reads, song ID lookups, optional sorting.
- Playlist: per-user playlist lists, track ordering, write transactions.
- Recommendation: durable interaction summaries and affinity lookups.

Optimizations:

- Add indexes for all hot lookup paths:
  - Auth: unique email.
  - Catalog: song ID, genre, year, BPM, and sort columns used by pagination.
  - Playlist: user ID, playlist ID plus owner, playlist track `(playlist_id, position)`, liked song uniqueness.
  - Recommendation: user-song interaction by user ID, song ID, and affinity query fields.
- Use keyset pagination where offset pagination becomes slow.
- Set Hikari pool sizes per service based on DB capacity, not replica count alone.
- Avoid calling Catalog from Playlist or Streaming on hot paths unless a real validation requirement is added.
- Keep JWT validation local and avoid Auth DB lookups after login.

Read/write split:

- For this Compose benchmark, prefer single primary PostgreSQL containers first.
- If read pressure dominates Catalog or Recommendation, a Compose read-replica experiment can be added later, but it increases benchmark complexity and consistency questions.

### OpenSearch

OpenSearch is the key Search Service bottleneck.

Pressure points:

- text query CPU
- filter combinations
- heap pressure
- segment merges
- refresh frequency during indexing
- large result windows

Optimizations:

- Keep shard count low for the Kaggle catalog size; too many shards waste heap.
- Use keyword subfields for exact filters and aggregations.
- Limit `size` and enforce `SEARCH_MAX_PAGE_SIZE`.
- Avoid deep pagination; prefer `search_after` if deep result navigation is needed.
- Index catalog data once at startup for baseline tests, then freeze indexing during search load tests.
- Add a cache layer only for repeated anonymous/global search queries; user-specific search should avoid unnecessary cache fragmentation.

Scaling:

- First increase container memory and OpenSearch heap.
- Then add service replicas for Search API.
- Only add OpenSearch nodes if single-node OpenSearch is the measured bottleneck and the Docker host has enough RAM.

### ClickHouse

ClickHouse is the Analytics Service bottleneck for event-heavy workloads.

Pressure points:

- high-frequency playback event inserts
- per-user history reads
- global chart aggregations
- small insert batches causing too many parts

Optimizations:

- Batch inserts from Analytics Kafka consumers where possible.
- Keep `play.started` chart aggregation scoped; global chart endpoint should use limit and cache or pre-aggregate under high load.
- Partition by time if event volume becomes large.
- Order by fields that support history and chart queries.
- Keep `/analytics/me/history` paginated.
- Cache global chart responses briefly if k6 shows repeated chart reads.

Scaling:

- Increase ClickHouse CPU, memory, and disk throughput first.
- Scale Analytics API replicas separately from Kafka consumer throughput only if the implementation allows it cleanly.
- Increase Kafka partitions before increasing consumer replicas beyond partition count.

### Redis

Redis backs Recommendation cache and should stay memory-resident.

Pressure points:

- daily mix cache reads
- similar song cache reads
- invalidation after playback events
- memory eviction

Optimizations:

- Cache `daily-mix` by user ID with short TTL.
- Cache `similar/:songId` by song ID because hot songs repeat heavily.
- Use bounded TTLs to prevent unbounded growth.
- Track hit ratio; if hit ratio is low, reduce caching scope instead of adding memory blindly.
- Avoid caching per-request combinations that have low reuse.

Scaling:

- Use one larger Redis container first.
- Set max memory and eviction policy explicitly for benchmark repeatability.
- Redis clustering is not recommended for the first Compose-only 1M-user benchmark unless single-node Redis is proven to be the blocker.

### Kafka

Kafka is the backbone for playback and playlist events.

Pressure points:

- Streaming producer throughput
- playback event topic partitions
- Analytics and Recommendation consumer lag
- broker disk I/O
- large bursts from simulated playback starts/skips/ends

Optimizations:

- Increase `playback-events` partitions before increasing consumer replicas.
- Set consumer group IDs per service so Analytics and Recommendation each receive all playback events.
- Monitor lag per consumer group.
- Batch producer sends where possible but preserve event correctness.
- Keep event payloads small.
- Use bounded producer timeouts and retries.

Scaling:

- Start with the current single broker and tune CPU/memory.
- Increase topic partitions to match desired consumer parallelism.
- Add brokers only after single broker CPU, network, or disk I/O is the measured bottleneck.
- A 3-broker Compose profile can be useful for benchmarking, but it increases startup and storage complexity.

### MongoDB

MongoDB backs Notification Service and is unlikely to dominate early scale tests.

Pressure points:

- playlist event fan-in
- notification insert rate
- unread notification query if a protected notification read API is enabled later

Optimizations:

- Index user ID, read/unread state, and creation time if read API is added.
- Keep notification payloads small.
- Use TTL cleanup if old notifications do not need indefinite retention.

Scaling:

- Keep one MongoDB container for initial 1M-user benchmark.
- Scale Notification consumers only if Kafka lag appears on notification topics.

## Service-Specific Plan

### Auth Service

Pattern:

- horizontally scale modestly
- vertically tune Auth PostgreSQL if login bursts become slow

Why:

- Auth is critical but not on every protected request because services validate JWTs locally.
- Password hashing is CPU-expensive, so login/register bursts can saturate Auth replicas.

Plan:

- Start with 2 replicas for calibration runs and 4 replicas for the 1M target profile.
- Keep registration/login rate separate from normal browsing/streaming tests.
- Tune password hashing cost only deliberately; lowering cost improves benchmark throughput but weakens realism.
- Add DB index verification for email uniqueness and lookup.

Degradation:

- If Auth is overloaded, existing logged-in users should continue because JWT validation is local.
- Rate-limit registration/login at the proxy during extreme tests.

### Catalog Service

Pattern:

- horizontally scale service replicas
- vertically tune Catalog PostgreSQL

Why:

- Catalog is read-heavy and stateless at the application layer.
- The database becomes the bottleneck if every browse/detail request hits PostgreSQL.

Plan:

- Start 3 replicas for calibration runs and 8 replicas for the 1M target profile.
- Enforce page size limits.
- Add indexes for genre, year, BPM, ID, and common sort fields.
- Consider short-lived response caching for top pages and stable catalog slices.
- Avoid cross-service catalog lookups from hot Streaming paths.

Degradation:

- Serve smaller page sizes under pressure.
- Disable expensive sorts if they exceed latency thresholds.

### Streaming Service

Pattern:

- aggressive horizontal scaling
- Kafka throughput tuning

Why:

- It is stateless and receives the highest request volume.
- Segment payload generation can consume CPU and network.
- Every stream interaction can emit Kafka events.

Plan:

- Start with 6 replicas for calibration runs and 20 replicas for the 1M target profile.
- Keep segment size and segment count configurable per scenario.
- Test stream descriptor and segment endpoints separately.
- Watch Kafka producer latency and broker throughput.
- Keep event publishing async from the user-facing latency path as much as possible.

Degradation:

- Reduce segment size/count in benchmark profiles if infrastructure is testing control-plane behavior rather than raw bandwidth.
- Return controlled errors when Kafka is unavailable if events cannot be safely buffered.

### Playlist Service

Pattern:

- horizontal service scale with careful PostgreSQL tuning

Why:

- Service instances are stateless, but track ordering and liked songs need transactional consistency.
- Concurrent writes to the same playlist can create lock contention.

Plan:

- Start with 2 replicas for calibration runs and 4 replicas for the 1M target profile.
- Index `(user_id)`, `(playlist_id)`, `(playlist_id, position)`, and uniqueness for liked songs.
- Keep playlist list responses paginated if user libraries grow.
- Avoid validating every song ID against Catalog synchronously in write paths unless required.

Degradation:

- Use bounded retries for transient DB conflicts.
- Return conflict responses for concurrent reorder collisions if optimistic locking is added later.

### Search Service

Pattern:

- horizontal Search Service replicas
- OpenSearch vertical/index tuning first

Why:

- Search API is stateless, but OpenSearch is CPU and heap sensitive.
- Query shapes are bursty and can create hotspots around popular genres or broad text terms.

Plan:

- Start with 3 Search replicas for calibration runs and 8 replicas for the 1M target profile.
- Tune OpenSearch heap and memory before adding OpenSearch nodes.
- Enforce max page size and reject pathological BPM/year ranges.
- Cache very hot global searches only if query repetition is proven by metrics.
- Keep startup indexing out of steady-state search load measurements.

Degradation:

- Set search timeouts.
- Return partial or empty results with a clear error only when OpenSearch is unhealthy.
- Circuit-break OpenSearch calls to protect Search replicas.

### Analytics Service

Pattern:

- scale API replicas and Kafka consumers according to partitions
- tune ClickHouse vertically and batch writes

Why:

- Analytics has two distinct pressures: event ingestion and read queries.
- ClickHouse is strong for this workload but can suffer from small insert pressure and heavy aggregate queries.

Plan:

- Start with 2 replicas for calibration runs and 8 replicas for the 1M target profile.
- Increase Kafka partitions for `playback-events`.
- Batch ClickHouse inserts where possible.
- Cache `/analytics/charts/global` briefly because many users may request the same chart.
- Keep `/analytics/me/history` paginated and user-scoped.

Degradation:

- If consumer lag rises, history may be eventually consistent.
- Preserve stream-start latency by letting analytics lag rather than blocking Streaming.
- Serve cached global charts when ClickHouse is slow.

### Recommendation Service

Pattern:

- horizontal API scale
- Redis cache tuning
- Kafka consumer partition-aware scale

Why:

- Discovery endpoints can be frequent on home load.
- Recommendation quality can tolerate short cache TTLs and eventual consistency.
- Redis can absorb repeated reads for daily mix and similar songs.

Plan:

- Start with 3 replicas for calibration runs and 8 replicas for the 1M target profile.
- Cache `daily-mix` per user and `similar` per song.
- Track Redis hit ratio and memory usage.
- Increase Kafka partitions if playback interaction processing lags.
- Keep PostgreSQL queries indexed by user/song.

Degradation:

- Serve cached recommendations if PostgreSQL is slow.
- Fall back to popular/global tracks if personalized computation times out.
- Prefer stale-but-fast recommendations over blocking page load.

### Notification Service

Pattern:

- low horizontal scale
- MongoDB vertical tuning if needed

Why:

- It is internal and not part of the primary playback/search path.
- It consumes event traffic, but notification requirements are less latency-sensitive.

Plan:

- Keep 1 replica initially.
- Scale to 2 only if Kafka lag or Mongo insert latency appears.
- Add indexes only when a read API is introduced.

Degradation:

- Allow notification lag under high load.
- Do not let notification failures affect playlist or streaming operations.

## Backpressure, Retries, Circuit Breakers, And Degradation

Required resilience behavior should be explicit before large tests.

| Path | Backpressure / Protection |
| --- | --- |
| Proxy to services | connection limits, request timeouts, max body size, per-route rate limits |
| Service to PostgreSQL | bounded Hikari pools, query timeouts, indexes, no unbounded retries |
| Service to OpenSearch | query timeout, circuit breaker, max page size |
| Streaming to Kafka | bounded producer timeout, retry with cap, metrics on send failures |
| Kafka consumers | monitor lag, tune poll/batch size, scale only up to partition count |
| Analytics to ClickHouse | batch inserts, timeout writes, tolerate eventual consistency |
| Recommendation to Redis | short timeout, fallback to DB or default recommendations |
| Recommendation to PostgreSQL | cache hot reads, bounded query limits |

Retries:

- Retry only transient failures.
- Use exponential backoff with jitter.
- Do not retry non-idempotent writes blindly.
- Keep retry budgets low during load tests so failures surface quickly.

Circuit breakers:

- Search should circuit-break OpenSearch.
- Recommendation should circuit-break Redis/PostgreSQL read paths and fall back where possible.
- Analytics API should avoid cascading failures from slow ClickHouse queries.

Graceful degradation:

- Search: timeout rather than saturate threads.
- Recommendation: cached or generic recommendations.
- Analytics charts: stale cached global chart.
- Notifications: delayed delivery.
- Streaming: protect segment latency and Kafka producer queues.

## Database Lookup And Caching Strategy

### Avoid Unnecessary Cross-Service Calls

- Protected services must validate JWTs locally and must not call Auth per request.
- Streaming should not call Catalog for every stream start unless a missing-song validation requirement is added.
- Playlist should store song IDs and avoid synchronous Catalog validation on every mutation unless explicitly required.
- Recommendation should consume playback events asynchronously rather than calling Analytics on request.
- Analytics should read ClickHouse directly for history/charts rather than asking Streaming or Recommendation.

### Cache Candidates

| Data | Cache Location | Rationale |
| --- | --- | --- |
| Recommendation daily mix | Redis | Per-user read after home load; TTL controls staleness. |
| Similar songs | Redis | Song-level cache has high reuse for popular tracks. |
| Global charts | Analytics in-memory or Redis if introduced for analytics | Same result for many users; short TTL is acceptable. |
| Catalog first pages / popular filters | Catalog in-memory or proxy cache | Catalog data is mostly static after startup ingestion. |
| Search hot queries | Search local cache only if repetition is measured | Search query cardinality may be too high for broad caching. |

Do not cache:

- login responses beyond JWT issuance
- playlist writes
- user history writes
- event production acknowledgements

## Metrics And Grafana Dashboards

Prometheus and Grafana should decide scaling thresholds, not intuition.

### Service Metrics

Track per service:

- request rate by endpoint
- p50/p95/p99 latency by endpoint
- 4xx/5xx rates
- JVM heap/non-heap memory
- GC pause time
- thread pool usage
- container CPU and memory
- DB connection pool active/idle/pending
- retry count and circuit breaker state

### Infrastructure Metrics

PostgreSQL:

- active connections
- slow queries
- lock waits
- transaction rate
- buffer/cache hit ratio
- CPU and disk I/O

OpenSearch:

- query latency
- heap usage
- GC pressure
- rejected thread-pool tasks
- segment count
- refresh/merge time

ClickHouse:

- insert rate
- query latency
- parts count
- memory usage
- disk throughput

Redis:

- hit ratio
- memory usage
- evictions
- command latency
- connected clients

Kafka:

- broker CPU/network/disk
- producer request latency
- topic throughput
- partition count
- consumer group lag for Analytics, Recommendation, Notification

MongoDB:

- insert rate
- query latency
- lock/queue indicators
- storage growth

Proxy:

- upstream latency
- upstream error rate
- active connections
- route-level request rate

### Scaling Thresholds

Use thresholds like these to trigger scale experiments:

- p95 endpoint latency exceeds target for 5 minutes.
- service CPU is above 75% sustained.
- JVM GC pauses increase p99 latency.
- DB connection pool pending requests are non-zero under steady load.
- Kafka consumer lag grows continuously for more than 5 minutes.
- Redis evictions occur during normal recommendation traffic.
- OpenSearch rejected tasks are non-zero.
- ClickHouse parts count or insert latency rises continuously.

## k6 Load-Test Phases

Run phases in order. Do not jump to a full mixed 1M-user scenario before isolating bottlenecks.

### Phase 1: Smoke And Baseline

Goal:

- prove the proxy, auth, JWT propagation, and main endpoints work under low load

Flows:

- register/login
- browse catalog
- search
- start stream
- fetch a few segments
- create playlist
- add track
- read history
- get daily mix

### Phase 2: Endpoint Isolation

Goal:

- identify individual service saturation points

Tests:

- Auth login burst
- Catalog paginated browse
- Search query/filter mix
- Streaming descriptor-only
- Streaming segment-heavy
- Playlist write/reorder
- Analytics history and global charts
- Recommendation daily mix/similar

### Phase 3: Event Pipeline

Goal:

- measure Streaming to Kafka to Analytics/Recommendation lag

Tests:

- high-rate `play.started`
- mixed started/ended/skipped
- chart freshness under lag
- recommendation update lag

Metrics:

- producer latency
- Kafka throughput
- consumer lag
- ClickHouse insert latency
- Recommendation DB/cache update latency

### Phase 4: Read-Heavy User Journey

Goal:

- model many logged-in users browsing without extreme segment bandwidth

Mix:

- 5% login
- 25% catalog
- 25% search
- 20% recommendations
- 15% history/charts
- 10% playlist reads

### Phase 5: Streaming-Heavy User Journey

Goal:

- stress the highest-volume path

Mix:

- stream start
- segment fetches
- ended/skipped events
- background analytics/recommendation consumption

Vary:

- segment size
- segment count
- active listeners
- Kafka partitions
- Streaming replicas

### Phase 6: Full Mixed Benchmark

Goal:

- approximate the 1M-user system profile with realistic active concurrency

Mix:

- login/refresh sessions
- discovery home
- catalog browsing
- search
- streaming
- playlist operations
- history/charts
- recommendations

Increase:

- virtual users
- arrival rate
- dataset/user cardinality
- event volume

Stop conditions:

- sustained p95/p99 latency breach
- unbounded Kafka lag
- DB connection pool exhaustion
- OpenSearch rejected tasks
- Redis eviction spike
- ClickHouse insert/query instability

## Recommended Scaling Order

1. Add and validate the reverse proxy entry point.
2. Establish baseline dashboards and alerts.
3. Tune Streaming Service replicas and Kafka producer behavior.
4. Tune Kafka partitions and consumer lag for Analytics and Recommendation.
5. Tune ClickHouse ingestion and Analytics chart queries.
6. Tune Search Service and OpenSearch query/index behavior.
7. Tune Recommendation Redis caching and PostgreSQL lookup paths.
8. Tune Catalog PostgreSQL indexes and pagination.
9. Tune Playlist PostgreSQL concurrency and write behavior.
10. Tune Auth login burst behavior.
11. Tune Notification only if Kafka lag or MongoDB pressure appears.

Why this order:

- Streaming and Kafka are the highest-volume path.
- Analytics and Recommendation depend on the playback event pipeline.
- Search and Recommendation are likely high-frequency user-facing reads.
- Catalog and Playlist matter, but are less likely than streaming/search/event ingestion to dominate the first 1M-user bottleneck.
- Auth is important, but local JWT validation keeps it out of the normal per-request path.
- Notification is intentionally internal and should degrade without affecting core flows.

## Compose Benchmark Profiles

Use Compose override files to keep experiments repeatable:

- `docker-compose.scale-baseline.yml`
- `docker-compose.scale-100k.yml`
- `docker-compose.scale-1m.yml`
- `docker-compose.observability.yml` if dashboards need additional exporters

Each profile should set:

- service replica counts where Compose supports them
- CPU and memory limits
- JVM options per service
- Kafka topic partition counts
- OpenSearch heap
- ClickHouse memory limits
- Redis max memory and eviction policy
- proxy routing and timeouts

Example commands, non-final:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml up -d --scale streaming-service=6 --scale search-service=3 --scale catalog-service=3
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm k6 run /scripts/mixed-user-journey.js
```

Target-shape command, resource-heavy:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml up -d --build --scale auth-service=4 --scale catalog-service=8 --scale streaming-service=20 --scale playlist-service=4 --scale search-service=8 --scale analytics-service=8 --scale recommendation-service=8 --scale notification-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=500 -e K6_CATALOG_SEARCH_ITER_RATE=2000 -e K6_STREAMING_SESSION_RATE=20000 -e K6_PLAYLIST_MUTATION_ITER_RATE=100 k6 run /scripts/mixed-user-journey.js
```

In the target k6 command, each catalog/search iteration issues one catalog request and one search request, so 2,000 iterations per second models about 4,000 combined catalog/search requests per second. Each streaming iteration starts a stream and then emits an ended or skipped terminal event, so 20,000 streaming iterations per second models about 40,000 playback events per second. Each playlist mutation iteration creates a playlist and adds a track, so 100 iterations per second models about 200 playlist mutations per second.

For any scaled profile, treat `dropped_iterations` as a load-generator capacity signal, not backend throughput. A valid benchmark run should have near-zero dropped iterations. If k6 reports `Insufficient VUs`, raise the scenario-specific VU limits for the saturated scenario before interpreting service latency or throughput. Playback-heavy runs usually need the largest `K6_STREAMING_PREALLOCATED_VUS` and `K6_STREAMING_MAX_VUS` values. The 100k and 1M scale overrides also raise k6 and gateway container CPU/memory limits, because the base local defaults are intentionally too small for thousands of VUs. The 100k profile gives Auth more replicas and CPU than the first attempt because password verification is CPU-bound, and it lets Nginx cache protected Catalog reads because those responses are user-neutral.

When a profile fails with high dropped iterations, do not keep raising k6 VUs as the first response. Use `K6_RATE_SCALE` to run the same traffic mix at lower fractions of the target shape, then increase toward `1.0` only while `dropped_iterations` remains zero and latency/error thresholds pass. This distinguishes the Docker host's sustainable throughput from the aspirational target shape.

## Risks And Trade-Offs

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Single Docker host cannot physically model 1M active users | Benchmark may hit host CPU/network before app design limit | Treat 1M as registered-user target and run active-concurrency profiles; document host specs. |
| Compose scaling with fixed host ports fails | Replicas cannot bind the same port | Route through proxy and avoid host ports on scaled services. |
| Horizontal app scaling overloads databases | More replicas create too many DB connections | Set bounded pool sizes and tune DB indexes first. |
| Kafka single broker saturates | Playback events lag | Increase partitions and broker resources; consider 3-broker Compose profile only after measurement. |
| OpenSearch heap pressure | Search p99 latency spikes | Tune heap, shard count, query limits, and add service-level timeouts. |
| ClickHouse small insert pressure | Analytics lag and query instability | Batch inserts and monitor parts count. |
| Redis memory pressure | Recommendation cache becomes unstable | Set max memory, TTLs, eviction policy, and monitor hit ratio. |
| Global charts become hot | ClickHouse receives repeated identical aggregate queries | Short TTL cache for global chart endpoint. |
| Segment traffic dominates all other signals | Benchmark measures network bandwidth more than service behavior | Separate descriptor-only and segment-heavy tests. |
| Notification lag | Delayed in-app notifications | Accept lag as graceful degradation unless notification becomes user-facing critical. |

## Success Criteria Before Claiming 1M Readiness

The system is ready to claim a successful 1M-user Docker benchmark only when:

- k6 scripts cover the required flows from `REQUIREMENTS.md`.
- all traffic enters through the Compose reverse proxy.
- Prometheus captures service, JVM, container, Kafka, Redis, OpenSearch, ClickHouse, PostgreSQL, and MongoDB metrics.
- Grafana dashboards show latency, error rate, top tracks, saturation, and queue lag.
- Streaming-heavy and read-heavy tests pass separately before the mixed test.
- Kafka consumer lag stabilizes instead of growing without bound.
- p95/p99 latency targets are defined and met for each critical endpoint.
- database connection pools do not exhaust under steady load.
- no service depends on per-request Auth calls.
- every benchmark run records replica counts, CPU/memory limits, dataset size, k6 profile, and host hardware.

## Final Recommendation

Proceed with Docker Compose scalability testing, but do it in stages. The first implementation step should be a Docker-only reverse proxy and benchmark profile structure, followed by k6 scripts that isolate Streaming/Kafka, Search/OpenSearch, Analytics/ClickHouse, and Recommendation/Redis before running the full mixed user journey.
