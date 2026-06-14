# Tests

This file inventories the current test coverage by test role. Unit tests cover isolated service, helper, mapper, query-builder, and backend business behavior. Integration tests cover Spring application endpoints, persistence slices, Kafka flows.

## Current Coverage Summary

Backend coverage and validation commands used for the current review:

```bash
docker compose build auth-service catalog-service playlist-service streaming-service search-service analytics-service recommendation-service notification-service
docker build --target coverage --output "type=local,dest=coverage-output/auth-service" "services/auth-service"
docker build --target coverage --output "type=local,dest=coverage-output/catalog-service" "services/catalog-service"
docker build --target coverage --output "type=local,dest=coverage-output/playlist-service" "services/playlist-service"
docker build --target coverage --output "type=local,dest=coverage-output/streaming-service" "services/streaming-service"
docker build --target coverage --output "type=local,dest=coverage-output/search-service" "services/search-service"
docker build --target coverage --output "type=local,dest=coverage-output/analytics-service" "services/analytics-service"
docker build --target coverage --output "type=local,dest=coverage-output/recommendation-service" "services/recommendation-service"
docker build --target coverage --output "type=local,dest=coverage-output/notification-service" "services/notification-service"
```

Infrastructure confidence commands used for the current review:

```bash
docker compose up -d search-opensearch analytics-db recommendation-redis kafka
DOCKER_BUILDKIT=0 docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=OpenSearchInfrastructureIT services/search-service
DOCKER_BUILDKIT=0 docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=AnalyticsClickHouseInfrastructureIT services/analytics-service
DOCKER_BUILDKIT=0 docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=RecommendationCacheInfrastructureIT services/recommendation-service
DOCKER_BUILDKIT=0 docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=PlaybackEventPublisherInfrastructureIT services/streaming-service
```

Result: passed. The Java service Docker build path runs `mvn -q verify`, which executes default tests and generates JaCoCo XML/HTML reports. The infrastructure checks are explicit `*InfrastructureIT` runs against real Docker Compose services and are not included in the default JaCoCo totals.

Backend reports are exported under `coverage-output/<service>/jacoco/`.

Scalability readiness: GO. The backend has enough infrastructure confidence to proceed to scalability testing because the remaining pre-scale recommendations now have real OpenSearch, ClickHouse, Redis, and Kafka checks. Remaining risks are load-oriented rather than baseline correctness blockers.

## Final Validation Evidence

Final validation commands run for the submission pass:

```bash
docker compose config --quiet
docker compose config --services
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config --quiet
docker compose --env-file .env.cost-smoke.example -f docker-compose.yml -f docker-compose.cost-smoke.yml config --quiet
docker compose --env-file .env.cost-smoke.example -f docker-compose.yml -f docker-compose.cost-smoke.yml --profile observability config --quiet
docker compose --env-file .env.cost-smoke.example -f docker-compose.yml -f docker-compose.cost-smoke.yml --profile benchmark config --quiet
docker compose run --rm --no-deps gateway nginx -t
docker compose run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose run --rm --no-deps k6 inspect /scripts/smoke.js
docker compose run --rm --no-deps k6 inspect /scripts/mixed-user-journey.js
docker compose build auth-service catalog-service playlist-service streaming-service search-service analytics-service recommendation-service notification-service
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d --remove-orphans
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d --force-recreate gateway k6 cadvisor kafka-exporter redis-exporter nginx-exporter
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml ps
curl -i http://localhost:8080/health
curl -i http://localhost:8080/catalog/songs
curl -i http://localhost:9090/-/ready
curl -i http://localhost:3001/api/health
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm -e K6_SMOKE_HTTP_REQ_DURATION_P95_MS=6000 k6 run /scripts/smoke.js
curl -s 'http://localhost:9090/api/v1/query?query=up'
```

Final validation results:

| Area | Result | Evidence |
| --- | --- | --- |
| Base Compose configuration | Passed | `docker compose config --quiet` returned success. |
| Baseline, 100k, and 1M profile configuration | Passed | All three override combinations returned success. |
| Expected service inventory | Passed | `docker compose config --services` listed the 8 backend services plus required databases, Kafka, Redis, OpenSearch, ClickHouse, MongoDB, Prometheus, Grafana, k6, gateway, and exporters. |
| Gateway configuration | Passed | `nginx -t` inside the gateway container reported successful syntax validation. |
| Prometheus configuration | Passed | `promtool check config` reported the Prometheus config file is valid. |
| k6 script syntax/configuration | Passed | `k6 inspect` passed for `smoke.js` and `mixed-user-journey.js`. |
| Backend image builds and automated tests | Passed | `docker compose build auth-service catalog-service playlist-service streaming-service search-service analytics-service recommendation-service notification-service` completed successfully; each service Dockerfile ran Maven `verify`. |
| Backend-only scope | Passed | Compose service inventory contains no frontend runtime service; repository documentation keeps frontend only as explicitly out of scope. |
| Cost-smoke configuration | Passed | Base, observability-profile, and benchmark-profile cost-smoke Compose configurations returned success. |
| Docker support-container recovery | Passed | Stale gateway/k6/exporter containers attached to an old Docker network were force-recreated without deleting volumes; subsequent startup succeeded. |
| Full live baseline startup | Passed | Baseline profile started successfully; `docker compose ps` showed all eight backend services and required infrastructure running, with backend service health checks healthy. |
| Gateway, Prometheus, and Grafana live health | Passed | Gateway `/health`, Prometheus `/-/ready`, and Grafana `/api/health` returned HTTP 200. |
| Protected endpoint enforcement | Passed | Unauthenticated `GET /catalog/songs` through the gateway returned HTTP 401 with `WWW-Authenticate: Bearer`. |
| k6 baseline smoke | Passed | k6 smoke completed 17 iterations, 170 HTTP requests, 100% checks, 0 failed requests, and passed thresholds with `K6_SMOKE_HTTP_REQ_DURATION_P95_MS=6000`; it wrote `/results/smoke-cost-summary.json`. |
| Focused playback history verification | Passed | A fresh user registered and logged in through the gateway, streamed a unique song, sent the terminal playback event, and the same song appeared in that authenticated user's `/analytics/me/history` response. |
| Prometheus live collection | Passed | Prometheus `up` query showed all eight backend `/actuator/prometheus` targets plus gateway, Kafka, Redis, cAdvisor, and Prometheus targets up. |

| Service | Test Files | Test Cases | Line Coverage | Branch Coverage | Function/Method Coverage | Coverage Assessment | Important Weak Paths |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Auth Service | 3 | 11 | 93.70% | 75.00% | 96.23% methods | Good coverage for registration, login, duplicate users, invalid payload validation, JWT creation/parsing, public/protected endpoint boundaries. | Password policy edge cases and key-loader failure paths are not deeply covered. |
| Catalog Service | 4 | 13 | 88.68% | 66.67% | 91.11% methods | Good coverage for CSV ingestion, pagination, song detail lookup, metadata conversion robustness, not-found behavior, health, JWT protection. | Real Kaggle-scale ingestion performance and unusual malformed metadata shapes still need broader data tests. |
| Playlist Service | 3 | 16 | 95.98% | 73.53% | 96.70% methods | Strong coverage for playlist CRUD, liked songs, duplicate add handling, invalid reorder inputs, track add/remove/reorder, cross-user isolation, invalid bearer tokens, health. | Concurrent playlist edits and persistence-level race cases still need stress coverage. |
| Streaming Service | 4 | 12 | 95.14% | 67.86% | 98.11% methods | Strong coverage for descriptor generation, segment generation, safe config defaults, play started/ended/skipped event publishing, JWT protection, invalid segments, health, and real Kafka publisher delivery through `PlaybackEventPublisherInfrastructureIT`. | Large-volume broker throughput, partitioning behavior, and consumer backpressure remain scalability-test concerns. |
| Search Service | 6 | 19 | 86.58% | 74.44% | 87.50% methods | Good coverage for query construction, CSV indexing input, startup indexing, combined filters, auth boundaries, bad BPM range, OpenSearch index creation, bulk indexing, search parsing, health, and real OpenSearch index/search behavior through `OpenSearchInfrastructureIT`. | Large indexing batches, index refresh pressure, and search latency under concurrent load remain scalability-test concerns. |
| Analytics Service | 6 | 15 | 88.20% | 70.00% | 88.89% methods | Good coverage for history, global charts, UUID identity validation, ClickHouse SQL/repository behavior, Kafka deserialization robustness, and real ClickHouse save/read/chart behavior through `AnalyticsClickHouseInfrastructureIT`. | ClickHouse write amplification, query latency, and retention/cleanup behavior remain scalability-test concerns. |
| Recommendation Service | 7 | 19 | 82.07% | 64.81% | 78.82% methods | Good coverage for daily mix/similar logic, cache behavior, JPA repository behavior, Kafka persistence/robustness, controller auth, UUID validation, and real Redis cache hit/evict/invalid-json behavior through `RecommendationCacheInfrastructureIT`. | Redis latency under concurrent cache churn and database/cache consistency under load remain scalability-test concerns. |
| Notification Service | 4 | 10 | 89.22% | 67.86% | 91.43% methods | Good coverage for notification creation, event mapping, consumer behavior, and embedded-Kafka playlist update consumption. | No public Notification HTTP API exists in the current baseline, so no controller coverage applies. |

## Remaining Backend Risks

| Area | Recommendation |
| --- | --- |
| Search Service | Proceed to scalability testing with attention on larger indexing batches, index refresh pressure, and query latency under concurrent load. |
| Analytics Service | Proceed to scalability testing with attention on ClickHouse insert throughput, chart query latency, and data cleanup/retention behavior. |
| Recommendation Service | Proceed to scalability testing with attention on Redis latency during cache churn and consistency between Redis and PostgreSQL-backed recommendations. |
| Streaming Service | Proceed to scalability testing with attention on Kafka producer throughput, partition behavior, and downstream consumer backpressure. |

## Unit Tests

| No. | Name | Description | Service Tested | Runner / Framework | File |
| ---: | --- | --- | --- | --- | --- |
| 1 | `AuthServiceTest` | Validates registration/login service behavior with mocked dependencies. | Auth Service | JUnit 5 / Mockito | `services/auth-service/src/test/java/com/benchmark/auth/service/AuthServiceTest.java` |
| 2 | `JwtServiceTest` | Validates JWT creation and parsing behavior. | Auth Service | JUnit 5 | `services/auth-service/src/test/java/com/benchmark/auth/service/JwtServiceTest.java` |
| 3 | `CatalogServiceTest` | Validates catalog listing/detail/filter service behavior. | Catalog Service | JUnit 5 / Mockito | `services/catalog-service/src/test/java/com/benchmark/catalog/service/CatalogServiceTest.java` |
| 4 | `CatalogDatasetIngestionRunnerTest` | Validates CSV mapping and duplicate-skip ingestion behavior with mocked persistence. | Catalog Service | JUnit 5 / Mockito | `services/catalog-service/src/test/java/com/benchmark/catalog/ingestion/CatalogDatasetIngestionRunnerTest.java` |
| 5 | `StringMapConverterTest` | Validates Catalog metadata JSON conversion, empty metadata handling, and malformed JSON rejection. | Catalog Service | JUnit 5 | `services/catalog-service/src/test/java/com/benchmark/catalog/entity/StringMapConverterTest.java` |
| 6 | `PlaylistServiceTest` | Validates playlist create/update/delete/reorder service behavior. | Playlist Service | JUnit 5 / Mockito | `services/playlist-service/src/test/java/com/benchmark/playlist/service/PlaylistServiceTest.java` |
| 7 | `LikedSongsServiceTest` | Validates liked-song add/remove behavior and protected playlist semantics. | Playlist Service | JUnit 5 / Mockito | `services/playlist-service/src/test/java/com/benchmark/playlist/service/LikedSongsServiceTest.java` |
| 8 | `StreamingServiceTest` | Validates streaming service state transitions and event publishing calls. | Streaming Service | JUnit 5 / Mockito | `services/streaming-service/src/test/java/com/benchmark/streaming/service/StreamingServiceTest.java` |
| 9 | `DummySegmentServiceTest` | Validates deterministic dummy segment generation. | Streaming Service | JUnit 5 | `services/streaming-service/src/test/java/com/benchmark/streaming/service/DummySegmentServiceTest.java` |
| 10 | `PlaybackEventPublisherTest` | Validates playback event publishing payload behavior. | Streaming Service | JUnit 5 / Mockito | `services/streaming-service/src/test/java/com/benchmark/streaming/messaging/PlaybackEventPublisherTest.java` |
| 11 | `SearchServiceTest` | Validates search service request handling with mocked OpenSearch client behavior. | Search Service | JUnit 5 / Mockito | `services/search-service/src/test/java/com/benchmark/search/service/SearchServiceTest.java` |
| 12 | `OpenSearchQueryBuilderTest` | Validates OpenSearch query construction for text and filters. | Search Service | JUnit 5 | `services/search-service/src/test/java/com/benchmark/search/opensearch/OpenSearchQueryBuilderTest.java` |
| 13 | `OpenSearchIndexClientTest` | Validates OpenSearch REST client behavior against an in-process HTTP server. | Search Service | JUnit 5 / HTTP server | `services/search-service/src/test/java/com/benchmark/search/opensearch/OpenSearchIndexClientTest.java` |
| 14 | `CatalogCsvReaderTest` | Validates catalog CSV parsing for indexing input. | Search Service | JUnit 5 | `services/search-service/src/test/java/com/benchmark/search/indexing/CatalogCsvReaderTest.java` |
| 15 | `SearchIndexInitializerTest` | Validates search index initialization behavior. | Search Service | JUnit 5 / Mockito | `services/search-service/src/test/java/com/benchmark/search/indexing/SearchIndexInitializerTest.java` |
| 16 | `AnalyticsServiceTest` | Validates history/chart service rules, pagination, and invalid parameter handling. | Analytics Service | JUnit 5 / Mockito | `services/analytics-service/src/test/java/com/benchmark/analytics/service/AnalyticsServiceTest.java` |
| 17 | `AnalyticsEventRepositoryTest` | Validates ClickHouse repository SQL calls using a mocked `JdbcTemplate`. | Analytics Service | JUnit 5 / Mockito | `services/analytics-service/src/test/java/com/benchmark/analytics/persistence/AnalyticsEventRepositoryTest.java` |
| 18 | `AnalyticsSchemaInitializerTest` | Validates generated ClickHouse schema SQL and initializer execution. | Analytics Service | JUnit 5 / Mockito | `services/analytics-service/src/test/java/com/benchmark/analytics/persistence/AnalyticsSchemaInitializerTest.java` |
| 19 | `PlaybackEventConsumerTest` | Validates Analytics playback-event filtering and persistence handoff. | Analytics Service | JUnit 5 / Mockito | `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventConsumerTest.java` |
| 20 | `RecommendationServiceTest` | Validates daily-mix recommendation logic and limit validation. | Recommendation Service | JUnit 5 / Mockito | `services/recommendation-service/src/test/java/com/benchmark/recommendation/service/RecommendationServiceTest.java` |
| 21 | `RecommendationCacheTest` | Validates recommendation cache read/write behavior. | Recommendation Service | JUnit 5 / Mockito | `services/recommendation-service/src/test/java/com/benchmark/recommendation/service/RecommendationCacheTest.java` |
| 22 | `PlaybackEventConsumerTest` | Validates Recommendation playback-event consumer filtering and persistence handoff. | Recommendation Service | JUnit 5 / Mockito | `services/recommendation-service/src/test/java/com/benchmark/recommendation/messaging/PlaybackEventConsumerTest.java` |
| 23 | `NotificationServiceTest` | Validates notification service creation and retrieval behavior. | Notification Service | JUnit 5 / Mockito | `services/notification-service/src/test/java/com/benchmark/notification/service/NotificationServiceTest.java` |
| 24 | `NotificationEventMapperTest` | Validates playlist update event to notification mapping. | Notification Service | JUnit 5 | `services/notification-service/src/test/java/com/benchmark/notification/service/NotificationEventMapperTest.java` |
| 25 | `PlaylistUpdateEventConsumerTest` | Validates Notification playlist-update consumer behavior with mocked service dependencies. | Notification Service | JUnit 5 / Mockito | `services/notification-service/src/test/java/com/benchmark/notification/messaging/PlaylistUpdateEventConsumerTest.java` |

## Integration Tests

| No. | Name | Description | Service Tested | Runner / Framework | File |
| ---: | --- | --- | --- | --- | --- |
| 1 | `AuthControllerIntegrationTest` | Validates Auth HTTP registration/login flows through the Spring application context. | Auth Service | Spring Boot Test / MockMvc | `services/auth-service/src/test/java/com/benchmark/auth/controller/AuthControllerIntegrationTest.java` |
| 2 | `CatalogControllerIntegrationTest` | Validates Catalog HTTP listing/detail endpoints through the Spring application context. | Catalog Service | Spring Boot Test / MockMvc | `services/catalog-service/src/test/java/com/benchmark/catalog/controller/CatalogControllerIntegrationTest.java` |
| 3 | `PlaylistControllerIntegrationTest` | Validates protected playlist HTTP workflows through the Spring application context. | Playlist Service | Spring Boot Test / MockMvc | `services/playlist-service/src/test/java/com/benchmark/playlist/controller/PlaylistControllerIntegrationTest.java` |
| 4 | `StreamingControllerIntegrationTest` | Validates protected streaming HTTP endpoints and event-producing behavior through the Spring application context. | Streaming Service | Spring Boot Test / MockMvc | `services/streaming-service/src/test/java/com/benchmark/streaming/controller/StreamingControllerIntegrationTest.java` |
| 5 | `SearchControllerIntegrationTest` | Validates protected search HTTP endpoint behavior through the Spring application context. | Search Service | Spring Boot Test / MockMvc | `services/search-service/src/test/java/com/benchmark/search/controller/SearchControllerIntegrationTest.java` |
| 6 | `AnalyticsControllerIntegrationTest` | Validates protected Analytics history and global chart endpoints through the Spring application context. | Analytics Service | Spring Boot Test / MockMvc | `services/analytics-service/src/test/java/com/benchmark/analytics/controller/AnalyticsControllerIntegrationTest.java` |
| 7 | `PlaybackEventKafkaRobustnessIntegrationTest` | Validates Analytics Kafka consumer skips malformed/incompatible records and consumes later valid records. | Analytics Service | Spring Boot Test / Embedded Kafka | `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java` |
| 8 | `RecommendationControllerIntegrationTest` | Validates protected recommendation HTTP endpoint behavior through the Spring application context. | Recommendation Service | Spring Boot Test / MockMvc | `services/recommendation-service/src/test/java/com/benchmark/recommendation/controller/RecommendationControllerIntegrationTest.java` |
| 9 | `UserSongInteractionRepositoryTest` | Validates recommendation persistence mapping and repository behavior with a JPA test slice. | Recommendation Service | Spring Data JPA Test | `services/recommendation-service/src/test/java/com/benchmark/recommendation/repository/UserSongInteractionRepositoryTest.java` |
| 10 | `PlaybackEventKafkaPersistenceIntegrationTest` | Validates Recommendation Kafka ingestion persists playback interactions. | Recommendation Service | Spring Boot Test / Embedded Kafka | `services/recommendation-service/src/test/java/com/benchmark/recommendation/messaging/PlaybackEventKafkaPersistenceIntegrationTest.java` |
| 11 | `PlaybackEventKafkaRobustnessIntegrationTest` | Validates Recommendation Kafka consumer skips malformed/incompatible records and consumes later valid records. | Recommendation Service | Spring Boot Test / Embedded Kafka | `services/recommendation-service/src/test/java/com/benchmark/recommendation/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java` |
| 12 | `PlaylistUpdateKafkaIntegrationTest` | Validates Notification Kafka consumption of playlist update events through the Spring application context. | Notification Service | Spring Boot Test / Embedded Kafka | `services/notification-service/src/test/java/com/benchmark/notification/messaging/PlaylistUpdateKafkaIntegrationTest.java` |
| 13 | `OpenSearchInfrastructureIT` | Validates Search index creation, bulk indexing, and search results against the real Compose OpenSearch container. | Search Service | JUnit 5 / Docker Compose OpenSearch | `services/search-service/src/test/java/com/benchmark/search/opensearch/OpenSearchInfrastructureIT.java` |
| 14 | `AnalyticsClickHouseInfrastructureIT` | Validates Analytics schema creation, event persistence, history reads, and global chart aggregation against the real Compose ClickHouse container. | Analytics Service | JUnit 5 / Docker Compose ClickHouse | `services/analytics-service/src/test/java/com/benchmark/analytics/persistence/AnalyticsClickHouseInfrastructureIT.java` |
| 15 | `RecommendationCacheInfrastructureIT` | Validates Recommendation cache put/get/evict behavior and malformed JSON cleanup against the real Compose Redis container. | Recommendation Service | JUnit 5 / Docker Compose Redis | `services/recommendation-service/src/test/java/com/benchmark/recommendation/service/RecommendationCacheInfrastructureIT.java` |
| 16 | `PlaybackEventPublisherInfrastructureIT` | Validates Streaming publisher delivery, Kafka key, and JSON payload round-trip through the real Compose Kafka broker. | Streaming Service | JUnit 5 / Docker Compose Kafka | `services/streaming-service/src/test/java/com/benchmark/streaming/messaging/PlaybackEventPublisherInfrastructureIT.java` |
