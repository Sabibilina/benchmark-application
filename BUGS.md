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

```powershell
docker compose up -d --build kafka analytics-db analytics-service
docker compose logs --no-color --tail=100 analytics-service
```

### Files affected

* `services/analytics-service/src/main/resources/application.yml`
* `services/analytics-service/src/main/java/com/benchmark/analytics/config/KafkaConsumerConfig.java`
* `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java`
* `services/analytics-service/README.md`
* `BUGS.md`

## Frontend CORS blocked browser API calls

### What the bug was

The frontend could render in the browser, but login and register failed with a generic `Network Error`. The backend Auth endpoints were reachable from non-browser clients, so the failure was specific to browser-enforced cross-origin requests from `http://localhost:5173` to backend service ports such as `http://localhost:8081`.

The same missing CORS support affected the other browser-facing backend services: Catalog, Playlist, Streaming, Search, Analytics, and Recommendation. Notification was not changed because the current Notification Service does not expose a browser-facing HTTP API.

### How it was found

The issue was reported after trying login/register from the frontend in a browser. Inspecting the Spring Security configuration across services showed that CSRF was disabled and JWT/public endpoint authorization was configured, but no service enabled a CORS configuration source. Browser preflight requests would therefore fail before the frontend could call protected or public endpoints successfully.

### How it was fixed

Each browser-facing Spring service now defines a local `CorsConfigurationSource` and enables CORS in its `SecurityFilterChain`.

The CORS policy is shared by convention:

* allowed origins come from `FRONTEND_ALLOWED_ORIGINS`;
* the default origins are `http://localhost:5173` and `http://127.0.0.1:5173`;
* allowed methods are `GET`, `POST`, `PATCH`, `DELETE`, and `OPTIONS`;
* allowed headers are `Authorization` and `Content-Type`;
* credentials are not enabled because authentication uses bearer JWT headers, not cookies.

Docker Compose and `.env.example` now expose `FRONTEND_ALLOWED_ORIGINS` so local browser origins can be configured without code changes.

### Validation

Validation performed after the fix:

* `docker compose build auth-service catalog-service playlist-service streaming-service search-service analytics-service recommendation-service`
* `docker compose up -d auth-db auth-service catalog-db catalog-service playlist-db playlist-service kafka streaming-service search-opensearch search-service analytics-db analytics-service recommendation-db recommendation-redis recommendation-service frontend`
* CORS preflight requests from `Origin: http://localhost:5173` returned HTTP `200` and `Access-Control-Allow-Origin: http://localhost:5173` for Auth, Catalog, Streaming, Playlist, Search, Analytics, and Recommendation.
* A browser-style `POST /auth/register` request from `Origin: http://localhost:5173` returned HTTP `201` with `Access-Control-Allow-Origin: http://localhost:5173`.

### Files affected

* `.env.example`
* `docker-compose.yml`
* `services/auth-service/src/main/java/com/benchmark/auth/config/CorsConfig.java`
* `services/auth-service/src/main/java/com/benchmark/auth/config/SecurityConfig.java`
* `services/catalog-service/src/main/java/com/benchmark/catalog/config/CorsConfig.java`
* `services/catalog-service/src/main/java/com/benchmark/catalog/config/SecurityConfig.java`
* `services/playlist-service/src/main/java/com/benchmark/playlist/config/CorsConfig.java`
* `services/playlist-service/src/main/java/com/benchmark/playlist/config/SecurityConfig.java`
* `services/streaming-service/src/main/java/com/benchmark/streaming/config/CorsConfig.java`
* `services/streaming-service/src/main/java/com/benchmark/streaming/config/SecurityConfig.java`
* `services/search-service/src/main/java/com/benchmark/search/config/CorsConfig.java`
* `services/search-service/src/main/java/com/benchmark/search/config/SecurityConfig.java`
* `services/analytics-service/src/main/java/com/benchmark/analytics/config/CorsConfig.java`
* `services/analytics-service/src/main/java/com/benchmark/analytics/config/SecurityConfig.java`
* `services/recommendation-service/src/main/java/com/benchmark/recommendation/config/CorsConfig.java`
* `services/recommendation-service/src/main/java/com/benchmark/recommendation/config/SecurityConfig.java`
* `BUGS.md`
