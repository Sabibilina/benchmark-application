# Design Decisions

> Ordinary planning decisions made during each session. Cost decisions live in
> `COST-AWARE-DECISIONS.md`. Implementation status lives in `PROGRESS.md`.

---

## Session 9 — Monitoring, Load Generator & Integration Fixes

### DD-001 — OpenSearch Prometheus Exporter: elasticsearch_exporter (ES-compatible)

**Decision:** Use `prometheuscommunity/elasticsearch-exporter` (not an OpenSearch-native image) as the OpenSearch Prometheus exporter.

**Rationale:** OpenSearch exposes an Elasticsearch-compatible REST API. The `elasticsearch_exporter` connects via `--es.uri` and scrapes cluster health, node JVM, index stats, and thread pool metrics — identical to what an OpenSearch-specific exporter would provide. The OpenSearch-prometheus-exporter plugin alternative requires installing a plugin inside the running container, which complicates the compose image (custom Dockerfile or entrypoint patching). The `opensearch_exporter` community project has limited maintenance and no versioned releases on Docker Hub suitable for pinning. The `elasticsearch_exporter` is actively maintained and has a stable Docker image with pinnable versions.

**Trade-off:** Metric names use `elasticsearch_*` prefix rather than `opensearch_*`. Grafana panel PromQL must use `elasticsearch_*` names. This is a naming inconsistency, not a functional one.

**Affected files:**
- `docker-compose.yml` — new `opensearch-exporter` service
- `infra/prometheus/prometheus.yml` — new `opensearch-exporter` scrape job
- `infra/grafana/dashboards/overview.json` — OpenSearch heap panel uses `elasticsearch_jvm_memory_used_bytes`

---

### DD-002 — overview.json Dashboard: S-03 Panels

**Decision:** Populate `infra/grafana/dashboards/overview.json` with S-03-required panels: traffic, latency, error rate, and top tracks.

**Rationale:** The file currently exists as an empty stub (0 panels). S-03 requires Grafana to expose dashboards for traffic, latency, error rate, and top tracks. The `scaling.json` dashboard covers the scaling-decision metrics (HikariCP, Kafka lag, JVM heap); `overview.json` is the human-readable system health view for operators. The "top tracks" panel requires the analytics-service `GET /analytics/charts/global` or the ClickHouse `SELECT song_id, count() ... GROUP BY song_id ORDER BY count() DESC LIMIT 10` query exposed as a Prometheus custom metric. If no custom top-tracks metric is emitted by analytics-service, the top-tracks panel can use a Grafana table panel pointed at a Prometheus recording rule aggregating `playback_event_total` by `song_id`.

**Assumption:** analytics-service emits a `playback_event_total{song_id, event_type}` counter (or equivalent) via micrometer when events are persisted. If this counter does not exist, a recording rule will aggregate from the k6 test load using the ClickHouse-derived data; if neither source is available, the top-tracks panel is a placeholder with an explicit note.

**Panels planned for overview.json:**
1. Total request rate across all services (line)
2. HTTP error rate by service (line)
3. p99 latency by service (line)
4. Kafka consumer lag (total across all groups) (stat)
5. JVM heap % by service (gauge)
6. Top 10 songs by play count (table or bar chart — requires metric or recording rule)
7. Service health (up/down) (stat row)
8. Load generator VU count (if active) (stat)

**Affected files:**
- `infra/grafana/dashboards/overview.json` — full replacement with populated panels

---

### DD-003 — k6 Scenarios: Add Phase 4 Ramp and Phase 5 Peak

**Decision:** Add two new k6 scenarios: `ramp` (Phase 4: 0→1000 VU, 20 min) and `soak` (Phase 6: 2000 VU, 2 h). Existing `peak` scenario is retained but renamed `burst` to distinguish from Phase 5 semantics. Phase 5 (10000 VU) is documented but NOT wired into `main.js` by default — it requires a separately named script and explicit operator invocation.

**Rationale:** SCALABILITY.md §15 defines Phase 4 at 1000 VU (ramping) and Phase 5 at 10000 VU (peak). The current `peak` scenario tops at 500 VU (5-minute ramp). Phase 5 at 10000 VU is 20× higher than the current maximum; embedding it in the default `main.js` creates a risk of accidental invocation. Phase 5 is placed in a separate file `load-generator/scripts/phase5-peak.js` gated by explicit `K6_SCENARIO=phase5` and documentation warning. Phase 4 (`ramp`) is added to `main.js` as an opt-in scenario. Phase 6 (`soak`) is added as a scenario with a 2-hour duration and 2000 VU constant load.

**k6 VU budget per scenario:**
- smoke: 5 VU, 2 min — verification only
- streaming: 50 VU, 5 min — streaming flow
- full: configurable via K6_VUS/K6_DURATION — default 50 VU, 5 min
- ramp (Phase 4): 5m→1000, hold 10m, drain 5m = 20 min total
- burst (formerly peak): 5m→500, hold 5m, drain 5m
- soak (Phase 6): 2000 VU constant for 120 min
- phase5 (separate file): 10m→10000, hold 10m, drain 10m = 30 min total

**Affected files:**
- `load-generator/scripts/main.js` — add ramp, burst, soak scenario configs; update K6_SCENARIO choices
- `load-generator/scripts/phase5-peak.js` — NEW, separate file for Phase 5 only
- `load-generator/.env.example` — update K6_SCENARIO docs; add NGINX_LB_URL; remove stale per-service URLs

---

### DD-004 — M-22 / M-23 Compliance: Architecture-Level Satisfaction

**Decision:** M-22 and M-23 are satisfied at the architecture level, not the code level. No new retry or circuit-breaker code is required for inter-service HTTP calls because no synchronous inter-service HTTP calls exist.

**Evidence:** Code audit of all 8 service source trees confirmed no `RestTemplate`, `WebClient`, `FeignClient`, or equivalent HTTP client instantiation for service-to-service calls. All cross-service data flows use:
- Kafka (streaming-service → playback-events → analytics-service, recommendation-service; playlist-service → playlist-events → notification-service)
- Local RSA JWT validation (no per-request call to auth-service)
- Independent data stores (no shared databases, no data-service pattern)

**For M-22 (retry on inter-service HTTP calls):**
Retry is implemented at the Kafka producer level (`spring.kafka.producer.retries=3`, `retry.backoff.ms=1000` — documented in SCALABILITY.md §11 and targeted for implementation). This is the bounded retry behavior that applies to the actual cross-service data path.

**For M-23 (circuit breaker or equivalent):**
The Kafka producer-consumer decoupling IS the failure-isolation mechanism: a slow or failed downstream consumer (analytics-service, recommendation-service, notification-service) cannot cascade-fail the upstream producer (streaming-service, playlist-service) because Kafka buffers the message. Consumer lag grows, but no HTTP request queuing or thread-pool exhaustion occurs. This satisfies the "equivalent failure-isolation mechanism" clause in M-23.

Additionally, `recommendation-service` has a Redis fail-open pattern (Redis exception → compute directly from PostgreSQL). To make M-23 formally satisfied and measurable, Resilience4j is added to the Redis call path in `recommendation-service` as a thin annotation (see DD-005).

**This decision must be documented in the system verification deliverable** to explicitly justify M-22/M-23 compliance.

---

### DD-005 — Resilience4j on Redis Call Path (recommendation-service)

**Decision:** Add `io.github.resilience4j:resilience4j-spring-boot3` to `recommendation-service/pom.xml` and annotate the Redis `get`/`set` operations with `@CircuitBreaker(name = "redis", fallbackMethod = "computeDirectly")`.

**Rationale:** CD-018 deferred this, pending Phase 3 validation. As of Session 9, Phase 3 has not been run, but the requirement M-23 states "circuit breaker or equivalent." The architectural argument (DD-004) satisfies M-23 at the Kafka level, but for formal thesis documentation, having an explicit `@CircuitBreaker` on a call path provides unambiguous compliance evidence. The Redis call path is the only non-Kafka cross-boundary call in the system (Redis is shared infrastructure, not a peer service, but the call is still a network boundary crossing). The implementation cost is low: one dependency, one annotation, one fallback method.

**What this does NOT add:**
- Circuit breaker on the Kafka consumer path (Kafka consumers handle reconnect natively)
- Circuit breaker on PostgreSQL calls (Spring's connection pool handles timeout; acceptable per SCALABILITY.md §4)
- Circuit breaker on ClickHouse calls in analytics-service (batch buffer already provides backpressure)

**Affected files:**
- `services/recommendation-service/pom.xml` — add `resilience4j-spring-boot3` dep
- `services/recommendation-service/src/main/java/.../service/RecommendationService.java` — add `@CircuitBreaker`
- `services/recommendation-service/src/main/resources/application.yml` — add `resilience4j.circuitbreaker.instances.redis` config
- `services/recommendation-service/src/test/java/.../unit/RecommendationServiceTest.java` — add fallback test case

---

### DD-006 — System Verification Deliverable: Compose-Up Integration Script

**Decision:** The system verification deliverable (ARCHITECTURE.md §6) is implemented as a shell script `scripts/verify-integration.sh` that:
1. Starts the full compose stack
2. Waits for all 8 services to be healthy (polls `/actuator/health`)
3. Runs the k6 `smoke` scenario (5 VU, 2 min) via `docker compose --profile load-test run load-generator`
4. Asserts end-to-end Kafka event flow by: (a) posting a stream request to streaming-service, (b) waiting 10 s, (c) querying analytics-service history and asserting a non-empty response
5. Asserts JWT cross-service validation by attempting a protected catalog endpoint with an invalid token and expecting 401
6. Reports PASS/FAIL per step

**Rationale:** A separate Maven multi-module `system-tests/` would require a running compose stack and Java 21 tooling, adding complexity without benefit beyond what the shell script provides. The k6 smoke run already exercises all 8 services with real HTTP calls and JWT flow. The Kafka event flow assertion (step 4) is the critical cross-service behavior not covered by per-service tests. The JWT cross-service check (step 5) is a targeted regression guard for the shared RSA key setup.

**Alternative considered:** `@DockerComposeContainer` Testcontainers test. Rejected because: it starts a second compose environment (doubles resource usage), is harder to run in the same Docker network, and requires a Maven build step not aligned with the thesis validation workflow.

**Affected files:**
- `scripts/verify-integration.sh` — NEW
- `PROGRESS.md` — validation step added under Phase 10

---

### DD-007 — CD-010 Implementation: Flyway V2 Index Migrations

**Decision:** Add Flyway V2 migration files for hotspot indexes in auth-service, catalog-service, and recommendation-service. playlist-service already has V2 (playlist_tracks table). No V3 is needed for playlist-service indexes because the `playlist_id, position` index is part of the V2 create-table DDL.

**Index files to create:**
- `auth-service/src/main/resources/db/migration/V2__add_users_indexes.sql`
  - `CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);`
  - `CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email);`
- `catalog-service/src/main/resources/db/migration/V2__add_songs_indexes.sql`
  - `CREATE INDEX IF NOT EXISTS idx_songs_title ON songs(title);`
  - `CREATE INDEX IF NOT EXISTS idx_songs_genre ON songs(genre);`
  - `CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist_name);`
  - `CREATE INDEX IF NOT EXISTS idx_songs_genre_title ON songs(genre, title);`
- `recommendation-service/src/main/resources/db/migration/V3__add_play_history_index.sql`
  - `CREATE INDEX IF NOT EXISTS idx_play_history_user_played ON play_history(user_id, played_at DESC);`

**Note on auth-service:** The existing V1 migration likely creates `username` and `email` columns with `UNIQUE` constraint as part of the table DDL. If so, the index already exists implicitly and V2 is a no-op (the `IF NOT EXISTS` guard prevents errors).

**Note on catalog-service:** The `artist_name` column name must be verified against V1 DDL before generating V2. If the column is named `artist` the index is on `artist`; if `artist_name`, index is on `artist_name`.

**Affected files:**
- `services/auth-service/src/main/resources/db/migration/V2__add_users_indexes.sql` — ADD
- `services/catalog-service/src/main/resources/db/migration/V2__add_songs_indexes.sql` — ADD
- `services/recommendation-service/src/main/resources/db/migration/V3__add_play_history_index.sql` — ADD

---

### DD-008 — Kafka Producer Retry Configuration

**Decision:** Add explicit Kafka producer retry properties to `streaming-service` and `playlist-service` `application.yml` to formally satisfy M-22 at the messaging layer.

**Properties to set:**
```yaml
spring:
  kafka:
    producer:
      retries: 3
      properties:
        retry.backoff.ms: 1000
        max.in.flight.requests.per.connection: 1
```

`max.in.flight.requests.per.connection: 1` prevents out-of-order message delivery when retries occur — required for correct event ordering in the `playback-events` topic.

**Affected files:**
- `services/streaming-service/src/main/resources/application.yml`
- `services/playlist-service/src/main/resources/application.yml`

**Note:** These properties were specified in SCALABILITY.md §11 but their presence in service application.yml files has not been confirmed. This session must audit and add if missing.

---

---

## Session 9 — Implementation Findings (amendments to plan)

### DD-010 — auth-service V2 Migration Omitted

**Decision:** The `auth-service V2__add_users_indexes.sql` migration was NOT generated.

**Finding:** auth-service `V1__create_users_table.sql` declares `username VARCHAR(50) UNIQUE NOT NULL` and `email VARCHAR(255) UNIQUE NOT NULL`. In PostgreSQL, a column-level `UNIQUE` constraint creates an implicit B-tree index at DDL time (named `users_username_key` and `users_email_key` respectively). These indexes are already in use for `WHERE username = ?` and `WHERE email = ?` lookups.

**Why not generate V2:** Creating `CREATE UNIQUE INDEX idx_users_username ON users(username)` via V2 would add a SECOND unique index over the same column. PostgreSQL permits this but wastes storage and doubles write overhead on every INSERT and UPDATE to the users table. Two unique indexes on the same column provide zero additional query benefit.

**Consequence:** `pg_stat_user_indexes` shows `users_username_key` and `users_email_key` rather than `idx_users_username`. Grafana queries using index name patterns need no update — the postgres-exporter reports index stats regardless of name. No functional impact.

---

### DD-011 — recommendation-service V3 Migration Omitted

**Decision:** The `recommendation-service V3__add_play_history_index.sql` migration was NOT generated.

**Finding:** `V2__create_play_events.sql` already contains:
```sql
CREATE INDEX idx_play_events_user ON play_events (user_id, occurred_at DESC);
```
This composite index on `(user_id, occurred_at DESC)` exactly covers the hotspot query `SELECT ... FROM play_events WHERE user_id = ? ORDER BY occurred_at DESC LIMIT ?`. No additional index is needed.

**Consequence:** CD-010 status for recommendation-service is COMPLETE via V2 (not deferred). Only catalog-service required a new V2 migration.

---

### DD-012 — Top-Tracks Panel: Text Placeholder

**Decision:** Grafana `overview.json` panel 7 is a `text` type panel with instructions, not a live metric panel.

**Finding:** `analytics-service` uses `JdbcTemplate` directly and does not register any `Counter` or other Micrometer instrument for individual playback events. Spring Boot's automatic HTTP request instrumentation does not capture `song_id` as a label. No `playback_event_total{song_id}` metric exists in Prometheus.

**What the placeholder contains:** A Markdown note explaining that S-03 top-tracks is pending a one-line change in `AnalyticsService.recordBatch()` — incrementing a `Counter.builder("playback_event_total").tag("song_id", songId)` for each event. The PromQL expression to use once the counter is added is included in the panel text.

**S-03 compliance status:** Panels 1–4 (traffic, error rate, p99 latency, service health), panel 5 (Kafka lag), panel 6 (JVM heap), and panel 8 (load generator VUs) are fully operational. Panel 7 is a documented gap with a clear remediation path.

---

### DD-013 — load-generator docker-compose Service Env Cleanup

**Decision:** Removed the stale `AUTH_SERVICE_URL`, `CATALOG_SERVICE_URL`, etc. env vars from the `load-generator` service in `docker-compose.yml`. Only `NGINX_LB_URL`, `K6_VUS`, `K6_DURATION`, `K6_SCENARIO`, and `K6_RAMP_TARGET` remain.

**Reason:** These per-service URL env vars pointed to host-port addresses that no longer exist (host ports were removed when nginx-lb was added). The k6 script `main.js` uses only `NGINX_LB_URL`. Keeping stale vars is misleading and suggests an alternative direct-connection mode that the script does not support.

---

### DD-009 — Grafana Datasource UID Alignment

**Decision:** The Grafana provisioned datasource must use UID `prometheus` to match the UID hardcoded in `scaling.json` (`"datasource": {"type": "prometheus", "uid": "prometheus"}`). The `infra/grafana/provisioning/datasources/prometheus.yml` must declare `uid: prometheus` explicitly. If it uses a different UID (e.g., auto-generated), all scaling.json panel datasource references will silently fail to resolve.

**This is a validation item:** Read `infra/grafana/provisioning/datasources/prometheus.yml` and confirm `uid: prometheus` is set. If not, add it.

**Affected files:**
- `infra/grafana/provisioning/datasources/prometheus.yml` — verify/add `uid: prometheus`

---

## Session 9 — Validation Fixes

### DD-014 — scaling.json Panel 13: PromQL Metric Name Bug

**Decision:** Changed `scaling.json` panel 13 (OpenSearch JVM Heap %) PromQL from `opensearch_jvm_mem_heap_used_in_bytes / opensearch_jvm_mem_heap_max_in_bytes * 100` to `elasticsearch_jvm_memory_used_bytes{area="heap"} / elasticsearch_jvm_memory_max_bytes{area="heap"} * 100`.

**Finding:** The original expression used `opensearch_*` metric names, which are emitted by the OpenSearch Prometheus exporter plugin (installed inside the OpenSearch container). DD-001 selected `prometheuscommunity/elasticsearch_exporter` instead, which exposes `elasticsearch_*` metric names via the ES-compatible REST API. The panel would have shown no data at runtime. The correct JVM heap metrics from `elasticsearch_exporter` v1.7.0 are `elasticsearch_jvm_memory_used_bytes{area="heap"}` (used) and `elasticsearch_jvm_memory_max_bytes{area="heap"}` (max).

**Affected files:**
- `infra/grafana/dashboards/scaling.json` — panel 13 expr corrected

---

### DD-015 — recommendation-service Tests: Mockito Subclass Mock Maker

**Decision:** Added `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with `org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker` to switch from inline mocking to subclass mocking in the recommendation-service test module.

**Finding:** Mockito 5.x defaults to inline mock making (via ByteBuddy InlineInstrumentation). On Java 26, inline mocking of Spring Data Redis classes (`StringRedisTemplate`, `RedisAccessor`) fails with `MockitoException: Could not modify all classes`. This is because ByteBuddy's inline mock maker requires module access that Java 26 restricts by default, and the `--add-opens` flags alone do not resolve it for Spring infrastructure classes. Switching to `ByteBuddyMockMaker` (subclass-based, not inline) bypasses the instrumentation requirement.

**Additional fix:** Changed `@BeforeEach` stub from `when(redisTemplate.opsForValue())` to `lenient().when(redisTemplate.opsForValue())` to prevent `UnnecessaryStubbing` errors in the three fallback tests that call fallback methods directly and never invoke `opsForValue()`.

**Scope:** Applied to all 5 services that mock non-trivial classes and fail on Java 26: recommendation-service (Redis), auth-service (`JwtConfig`), catalog-service, streaming-service (`PlaybackEventPublisher`), playlist-service. The `ByteBuddyMockMaker` file alone is sufficient — no `--add-opens` argLine needed for services that don't mock Spring Data Redis classes directly. auth-service has no explicit surefire config and relies on defaults; the MockMaker file is picked up via the classpath.

**Affected files:**
- `services/recommendation-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` — NEW
- `services/recommendation-service/pom.xml` — surefire `--add-opens` argLine added
- `services/recommendation-service/src/test/java/.../unit/RecommendationServiceTest.java` — `lenient()` on `@BeforeEach` stub
