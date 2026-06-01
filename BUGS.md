# Bug Log

This file tracks bugs that are useful for understanding how the application evolved. It is separate from `PROGRESS.md`, which tracks checklist status, and `DESIGN-DECISIONS.md`, which records architectural and implementation rationale.

## Analytics history identity mismatch

### What the bug was

`/analytics/me/history` could return an empty history even though Kafka ingestion, ClickHouse persistence, and global charts were working.

The root cause was an identity mismatch in smoke data and test assumptions:

* Stored Analytics playback rows used a placeholder user id such as `analytics-smoke-user`.
* Authenticated history requests resolve the user from the JWT `sub` claim.
* Auth issues UUID user ids in JWT `sub`, so the history query looked for a UUID while the smoke rows were stored under a non-canonical placeholder id.

Because global charts aggregate by `song_id` and `event_type`, they still worked. Only per-user history was affected because it filters by `user_id`.

### How it was found

The issue was found during live Analytics validation after the service was already able to:

* consume playback events from Kafka;
* persist playback rows in ClickHouse;
* compute global charts from `play.started` rows.

The failing behavior was specific to `GET /analytics/me/history`: stored playback rows existed, but the endpoint returned no rows for the authenticated user because the stored `user_id` did not match the JWT subject.

### How it was fixed

Analytics now consistently treats the Auth-issued UUID in JWT `sub` as the canonical user identity:

* `UserPrincipalResolver` validates that the JWT subject is present and UUID-form.
* `PlaybackEventConsumer` ignores playback events whose `userId` is not UUID-form.
* Analytics tests were updated to use UUID-form user ids instead of placeholders like `analytics-smoke-user` or `user-1`.
* Smoke logic was corrected so inserted ClickHouse playback rows use the same UUID as the JWT `sub`.
* Documentation now calls out that manual smoke data must use the same canonical UUID in both JWT `sub` and playback event `userId`.

The fix intentionally preserved existing Kafka and ClickHouse behavior. The single-broker Kafka Docker Compose configuration was not changed.

### Event-type clarification

History remains inclusive of stored playback events:

* `play.started`
* `play.ended`
* `play.skipped`

Global charts continue to count only `play.started`, because chart rankings need one consistent play-count signal.

### Validation

Validation performed after the fix:

* `docker compose build analytics-service`
* `docker compose up -d --build kafka analytics-db analytics-service`
* Live smoke inserted `play.started` and `play.ended` rows using a UUID user id matching the JWT `sub`.
* `GET /analytics/me/history` returned both matching rows.
* `GET /analytics/charts/global` still returned the smoke song from `play.started` aggregation.

### Files affected

* `services/analytics-service/src/main/java/com/benchmark/analytics/security/UserPrincipalResolver.java`
* `services/analytics-service/src/main/java/com/benchmark/analytics/messaging/PlaybackEventConsumer.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/controller/AnalyticsControllerIntegrationTest.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventConsumerTest.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/persistence/AnalyticsEventRepositoryTest.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/service/AnalyticsServiceTest.java`
* `services/analytics-service/README.md`
* `PROGRESS.md`
* `DESIGN-DECISIONS.md`

## Analytics Kafka deserialization blocked consumer offset

### What the bug was

Analytics could get stuck on one older or incompatible Kafka record in the `playback-events` topic. When that happened, later valid playback events were not consumed, so ClickHouse remained missing fresh rows and `/analytics/me/history` stayed empty for newly published events.

The logs showed two related failures:

* `com.benchmark.streaming.messaging.PlaybackEvent` was not in the Analytics consumer's trusted packages.
* The listener error handler could not process `SerializationException` directly because the value deserializer was a plain `JsonDeserializer`.

The bad record had a Spring Kafka type header from Streaming: `com.benchmark.streaming.messaging.PlaybackEvent`. Analytics only trusted and owned `com.benchmark.analytics.messaging.PlaybackEvent`, so deserialization failed before the listener could validate or ignore the record. Because the failure happened before listener invocation, the consumer kept retrying the same offset.

### How it was found

A manual WSL smoke test started `auth-service`, `kafka`, `analytics-db`, and `analytics-service`. Basic service behavior worked:

* `/actuator/health` returned `UP`.
* unauthenticated Analytics calls returned `401`;
* a valid Auth JWT was accepted.

After fresh JSON playback events were published to Kafka, `/analytics/me/history` still remained empty. Inspecting `docker compose logs --no-color --tail=100 analytics-service` showed the consumer repeatedly failing at Kafka offset `0` on the older incompatible record. Resetting the Analytics consumer group offset past that record allowed fresh events to be consumed, confirming that one bad record was blocking later valid records.

### How it was fixed

Analytics Kafka consumer configuration now uses Spring Kafka `ErrorHandlingDeserializer` for both keys and values:

* key delegate: `StringDeserializer`;
* value delegate: `JsonDeserializer`;
* default value type: `com.benchmark.analytics.messaging.PlaybackEvent`;
* trusted package remains scoped to `com.benchmark.analytics.messaging`;
* producer type headers are ignored with `spring.json.use.type.headers=false`.

A `DefaultErrorHandler` with no retries was added for the Analytics listener container. With `ErrorHandlingDeserializer`, malformed records are converted into recoverable listener-container errors and skipped, allowing the consumer to advance to later valid records.

This avoids broadening trust to Streaming implementation packages while still accepting valid playback-event JSON that carries an old Streaming type header.

### Regression test

Added an embedded-Kafka integration test that:

* publishes malformed bytes to the Analytics playback-events topic;
* publishes a valid playback event JSON record after it;
* attaches the old `com.benchmark.streaming.messaging.PlaybackEvent` type header to the valid record;
* verifies the valid later record is persisted.

The existing validation that Analytics stores only supported event types with UUID user ids remains in place.

### Validation

Validation performed after the fix:

* `docker compose build analytics-service`

The Docker build runs the Analytics Maven test suite, including the new embedded-Kafka deserialization robustness test.

Useful manual smoke command:

```bash
docker compose up -d --build kafka analytics-db analytics-service
docker compose logs --no-color --tail=100 analytics-service
```

### Files affected

* `services/analytics-service/src/main/resources/application.yml`
* `services/analytics-service/src/main/java/com/benchmark/analytics/config/KafkaConsumerConfig.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java`
* `services/analytics-service/README.md`
* `BUGS.md`

## Baseline scale smoke failed before services were ready and Streaming Kafka producer rejected timeout config

### What the bug was

The baseline scale smoke command could report every k6 check as failed when run with:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js
```

The first failure mode was startup ordering: Compose started dependencies for the one-off k6 container, but k6 could begin traffic before the gateway and backend services were ready.

After readiness was fixed, the remaining failed checks were Streaming-only. `/stream/{songId}` and `/stream/{songId}/ended` returned `500` because Kafka rejected the Streaming producer configuration: `delivery.timeout.ms` was lower than the required `linger.ms + request.timeout.ms` relationship.

### How it was found

The pasted k6 baseline output showed `0%` checks passing and `http_req_failed` at `100%`. Gateway logs showed early `502` responses while services were still coming up. Once health-gated startup was added, the smoke run improved to `80%` checks passing, and gateway logs showed only Streaming requests failing. Streaming logs then showed Kafka's producer config exception for `delivery.timeout.ms`.

### How it was fixed

Docker Compose now defines health checks for backend application services and the gateway. The gateway waits for backend services to be healthy, and k6 waits for the gateway to be healthy before sending benchmark traffic. The health checks use `127.0.0.1` inside containers so Alpine `wget` does not try an IPv6 localhost path that is not listening.

Streaming Kafka producer configuration now exposes `STREAMING_KAFKA_REQUEST_TIMEOUT_MS` and defaults `STREAMING_KAFKA_DELIVERY_TIMEOUT_MS` to `120000`, keeping delivery timeout safely above the default request timeout plus linger.

### Validation

Validation performed after the fix:

* `docker compose config --quiet`
* `docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml config --quiet`
* `docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d`
* `docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js`
* `docker compose ps`
* `docker compose config --services`

The final rerun passed all k6 checks: `890/890` checks succeeded and `http_req_failed` was `0.00%`.

### Files affected

* `docker-compose.yml`
* `.env.example`
* `PROGRESS.md`
* `DESIGN-DECISIONS.md`
* `BUGS.md`

## 100k benchmark command removed scaled replicas and k6 fell back to default executor

### What the bug was

The documented 100k benchmark command did not actually run the intended mixed workload. The environment startup command used `docker-compose.scale-100k.yml`, but the following k6 `run` command used only the base Compose file. Docker Compose reconciled the project back toward the base topology and removed the extra scaled replicas before starting k6.

The same command also passed `K6_DURATION=10m`. `K6_DURATION` is a reserved k6 environment option, so k6 replaced the script's custom scenario configuration with the default executor. Because `mixed-user-journey.js` only exported named scenario functions, k6 failed before load execution with:

```text
function 'default' not found in exports
```

### How it was found

A pasted 100k benchmark run showed scaled services being removed, for example extra Auth, Catalog, Streaming, Search, Recommendation, Analytics, and Playlist containers. Immediately afterward, k6 reported the missing `default` export instead of running the four named scenarios.

### How it was fixed

The benchmark documentation now uses the same Compose override files for both `up` and `run`, so k6 is launched against the scaled profile without shrinking the topology:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm ...
```

The mixed workload duration variable was renamed from `K6_DURATION` to `BENCHMARK_DURATION` to avoid colliding with k6's reserved environment handling. A default export guard was added to `mixed-user-journey.js` so accidental default-executor fallback reports a useful error. The setup user registration call now treats `201 Created` and `409 Conflict` as expected statuses so repeated benchmark runs do not inflate `http_req_failed`.

### Validation

Validation performed after the fix:

* `docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet`
* `docker compose run --rm --no-deps -e BENCHMARK_DURATION=10m k6 inspect /scripts/mixed-user-journey.js`
* `docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e BENCHMARK_DURATION=3s -e K6_AUTH_LOGIN_RATE=1 -e K6_CATALOG_SEARCH_ITER_RATE=1 -e K6_STREAMING_SESSION_RATE=1 -e K6_PLAYLIST_MUTATION_ITER_RATE=1 -e K6_PREALLOCATED_VUS=20 -e K6_MAX_VUS=50 k6 run /scripts/mixed-user-journey.js`

The short 100k-profile sanity run used all four named scenarios, passed `48/48` checks, and reported `http_req_failed: 0.00%`.

### Files affected

* `load-generator/k6/mixed-user-journey.js`
* `load-generator/k6/README.md`
* `README.md`
* `SCALABILITY.md`
* `.env.example`
* `BUGS.md`

## 100k benchmark generated many dropped iterations and high-cardinality k6 metrics

### What the bug was

The corrected 100k benchmark profile ran the intended four k6 scenarios, but the load generator could not sustain the requested arrival rates. k6 logged `Insufficient VUs` for streaming, catalog/search, and auth, and reported `dropped_iterations: 950826`. That means the run did not actually generate the full requested 100k-profile workload, even though backend latency and `http_req_failed` thresholds passed.

k6 also warned that the run produced more than 100,000 unique metric time series. Dynamic URL paths such as stream song ids and playlist ids were being recorded as separate metric names, which can consume excess memory and distort observability during longer benchmark runs.

### How it was found

A 10-minute 100k-profile run showed:

* `streaming_sessions` capped at `275` active VUs while targeting `2000` iterations per second.
* `catalog_search` capped at `125` active VUs while targeting `400` iterations per second.
* `auth_logins` capped at `50` active VUs while targeting `50` iterations per second.
* `dropped_iterations` close to one million.
* a k6 warning about `100554` unique time series.

### How it was fixed

The mixed workload now supports scenario-specific VU controls:

* `K6_AUTH_PREALLOCATED_VUS` / `K6_AUTH_MAX_VUS`
* `K6_CATALOG_SEARCH_PREALLOCATED_VUS` / `K6_CATALOG_SEARCH_MAX_VUS`
* `K6_STREAMING_PREALLOCATED_VUS` / `K6_STREAMING_MAX_VUS`
* `K6_PLAYLIST_PREALLOCATED_VUS` / `K6_PLAYLIST_MAX_VUS`

The 100k benchmark command now allocates substantially more VU headroom for the streaming-heavy workload. Dynamic request paths now set stable k6 `name` tags such as `GET /stream/:songId` and `POST /playlists/:playlistId/tracks`, and the script omits the raw `url` system tag to reduce metric cardinality.

Follow-up validation showed that the k6 and gateway containers also needed scale-profile resource overrides. The base Compose defaults give k6 only `0.50` CPU and `512m` memory, which is not enough for thousands of VUs and can produce dropped iterations even when the backend profile is scaled. The 100k and 1M scale overrides now raise k6 and gateway CPU/memory limits.

A later 100k run still saturated the profile: auth login success dropped heavily, catalog browse success dropped, and `dropped_iterations` stayed near one million. The gateway logs showed slow Auth login and Catalog browse responses while Search remained fast. The benchmark script now creates a fresh user pool per run with `K6_RUN_ID`, preventing old persisted benchmark users from affecting Auth login checks. The 100k profile now scales Auth to four replicas with more CPU and a larger DB pool because BCrypt password verification is CPU-bound. The gateway also intentionally caches protected Catalog reads by overriding Spring's no-store response headers for `/catalog/`, which is safe for the current Catalog API because the responses are user-neutral.

Another 100k run with the larger k6 VU allocation still reported about one million dropped iterations, p95 latency above eight seconds, and `http_req_failed` above the benchmark threshold. This means the host/profile is above its sustainable ceiling. The mixed workload now has `K6_RATE_SCALE` so the same traffic mix can be run at controlled fractions of the 100k target, and the script has an explicit `dropped_iterations` threshold so a run with missed arrivals cannot be mistaken for a successful throughput result.

### Validation

Validation performed after the fix:

* `docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet`
* `docker compose run --rm --no-deps k6 inspect /scripts/mixed-user-journey.js`

The full 100k-profile run should be repeated and considered valid only if `dropped_iterations` is near zero. If the host still cannot allocate enough k6 VUs, lower the requested rates or run k6 from a larger host before drawing backend throughput conclusions.

### Files affected

* `load-generator/k6/mixed-user-journey.js`
* `load-generator/k6/README.md`
* `README.md`
* `SCALABILITY.md`
* `.env.example`
* `BUGS.md`
