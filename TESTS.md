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

| Service | Test Files | Test Cases | Line Coverage | Branch Coverage | Function/Method Coverage | Coverage Assessment | Important Weak Paths |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Auth Service | 3 | 11 | 93.70% | 75.00% | 96.23% methods | Good coverage for registration, login, duplicate users, invalid payload validation, JWT creation/parsing, public/protected endpoint boundaries. | Password policy edge cases and key-loader failure paths are not deeply covered. |
| Catalog Service | 4 | 13 | 88.68% | 66.67% | 91.11% methods | Good coverage for CSV ingestion, pagination, song detail lookup, metadata conversion robustness, not-found behavior, health, JWT protection. | Real Kaggle-scale ingestion performance and unusual malformed metadata shapes still need broader data tests. |
| Playlist Service | 3 | 16 | 95.98% | 73.53% | 96.70% methods | Strong coverage for playlist CRUD, liked songs, duplicate add handling, invalid reorder inputs, track add/remove/reorder, cross-user isolation, invalid bearer tokens, health. | Concurrent playlist edits and persistence-level race cases still need stress coverage. |
| Streaming Service | 4 | 12 | 95.14% | 67.86% | 98.11% methods | Strong coverage for descriptor generation, segment generation, safe config defaults, play started/ended/skipped event publishing, JWT protection, invalid segments, health, and real Kafka publisher delivery through `PlaybackEventPublisherInfrastructureIT`. | Large-volume broker throughput, partitioning behavior, and consumer backpressure remain scalability-test concerns. |
| Search Service | 6 | 19 | 86.58% | 74.44% | 87.50% methods | Good coverage for query construction, CSV indexing input, startup indexing, combined filters, auth boundaries, bad BPM range, OpenSearch index creation, bulk indexing, search parsing, health, and real OpenSearch index/search behavior through `OpenSearchInfrastructureIT`. | Large indexing batches, index refresh pressure, and search latency under concurrent load remain scalability-test concerns. |
| Analytics Service | 6 | 15 | 88.20% | 70.00% | 88.89% methods | Good coverage for history, global charts, UUID identity validation, ClickHouse SQL/repository behavior, Kafka deserialization robustness, and real ClickHouse save/read/chart behavior through `AnalyticsClickHouseInfrastructureIT`. | ClickHouse write amplification, query latency, and retention/cleanup behavior remain scalability-test concerns. |
| Recommendation Service | 7 | 19 | 82.07% | 64.81% | 78.82% methods | Good coverage for daily mix/similar logic, cache behavior, JPA repository behavior, Kafka persistence/robustness, controller auth, UUID validation, and real Redis cache hit/evict/invalid-json behavior through `RecommendationCacheInfrastructureIT`. | Redis latency under concurrent cache churn and database/cache consistency under load remain scalability-test concerns. |
| Notification Service | 4 | 10 | 89.22% | 67.86% | 91.43% methods | Good coverage for notification creation, event mapping, consumer behavior, and embedded-Kafka playlist update consumption. | No browser-facing HTTP API exists in the current baseline, so no CORS/controller coverage applies. |

## Remaining Backend Risks

| Area | Recommendation |
| --- | --- |
| Search Service | Proceed to scalability testing with attention on larger indexing batches, index refresh pressure, and query latency under concurrent load. |
| Analytics Service | Proceed to scalability testing with attention on ClickHouse insert throughput, chart query latency, and data cleanup/retention behavior. |
| Recommendation Service | Proceed to scalability testing with attention on Redis latency during cache churn and consistency between Redis and PostgreSQL-backed recommendations. |
| Streaming Service | Proceed to scalability testing with attention on Kafka producer throughput, partition behavior, and downstream consumer backpressure. |

## Session 9 Monitoring And Load-Generator Smoke Checks

Commands used for the Session 9 validation pass:

```bash
docker compose config --quiet
docker compose --profile benchmark config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-smoke.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config --quiet
docker run --rm --entrypoint promtool -v "${PWD}/config/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus:v2.52.0 check config /etc/prometheus/prometheus.yml
docker run --rm -v "${PWD}/config/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t
docker compose run --no-deps --rm k6 inspect /scripts/smoke.js
docker compose run --no-deps --rm k6 inspect /scripts/mixed-user-journey.js
docker compose up -d --build
docker compose ps
docker compose exec -T prometheus wget -qO- http://localhost:9090/-/ready
docker compose exec -T gateway wget -qO- http://127.0.0.1:8080/health
docker compose exec -T prometheus wget -qO- "http://localhost:9090/api/v1/targets?state=active"
docker compose run --rm k6 run /scripts/smoke.js
docker compose run --no-deps --rm --entrypoint sh k6 -c "test -s /results/smoke-cost-summary.json && head -n 20 /results/smoke-cost-summary.json"
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic playback-events
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic playlist-events
```

| No. | Name | Description | Service Tested | Runner / Framework | Result |
| ---: | --- | --- | --- | --- | --- |
| 17 | Session 9 Compose configuration validation | Validates base, benchmark, smoke, calibration, 100k, and 1m Compose configurations. | Deployment environment | Docker Compose | Passed. Docker emitted local config access warnings but returned success. |
| 18 | Session 9 Prometheus config validation | Validates Prometheus scrape config syntax. | Monitoring | Prometheus `promtool` | Passed. |
| 19 | Session 9 gateway config validation | Validates Nginx gateway config syntax. | Gateway | `nginx -t` | Passed. |
| 20 | Session 9 k6 script inspection | Validates smoke and mixed workload script syntax with the pinned k6 image. | Load Generator | k6 inspect | Passed. |
| 21 | Session 9 live stack startup | Builds and starts the full Compose backend, infrastructure, gateway, monitoring, and load-generator environment. | Integrated system | Docker Compose | Passed. All eight backend services and required infrastructure were healthy or running. |
| 22 | Session 9 live Prometheus target check | Confirms Prometheus sees all eight backend service `/actuator/prometheus` targets as `up`. | Monitoring and all backend services | Prometheus API | Passed. |
| 23 | Session 9 k6 gateway smoke | Runs registration, login, catalog, search, recommendation, streaming, playlist, and history flows through the gateway. | Integrated system | k6 | Passed. 40 iterations, 400 HTTP requests, 100% checks, 0 failed requests, p95 1288 ms. |
| 24 | Session 9 k6 summary artifact check | Confirms the smoke run writes `/results/smoke-cost-summary.json`. | Load Generator | Docker Compose k6 container | Passed. |
| 25 | Session 9 Kafka topic check | Confirms default live topic creation for `playback-events` and `playlist-events`. | Kafka / messaging infrastructure | Kafka CLI | Passed. `playback-events` had 12 partitions; `playlist-events` had 3 partitions. |

## Final End-to-End Validation Pass

Final validation was performed on June 14, 2026 against the backend-only Docker Compose system.

Additional commands used:

```bash
docker compose build auth-service catalog-service streaming-service playlist-service search-service analytics-service recommendation-service notification-service
docker compose ps
docker compose exec -T prometheus wget -qO- "http://localhost:9090/api/v1/targets?state=active"
docker compose run --rm k6 run /scripts/smoke.js
```

| No. | Name | Description | Service Tested | Runner / Framework | Result |
| ---: | --- | --- | --- | --- | --- |
| 26 | Final backend image build | Confirms all eight backend service Dockerfiles build and include Maven `verify` stages. | All backend services | Docker Compose build | Passed. Build completed for Auth, Catalog, Streaming, Playlist, Search, Analytics, Recommendation, and Notification. Maven `verify` layers were cached because service source files were unchanged. |
| 27 | Final running-service inventory | Confirms all eight backend services and required infrastructure are running in Docker Compose. | Integrated deployment | Docker Compose | Passed. `docker compose ps` showed all eight backend services healthy plus required databases, Kafka, gateway, Prometheus, Grafana, Redis, OpenSearch, ClickHouse, and MongoDB running. |
| 28 | Final Prometheus target check | Confirms metrics are collected from all eight backend services. | Monitoring and all backend services | Prometheus API | Passed. Active Prometheus targets included Auth, Catalog, Streaming, Playlist, Search, Analytics, Recommendation, and Notification with `health: up`. |
| 29 | Final k6 end-to-end smoke | Confirms the load generator exercises the main backend flows through the gateway. | Integrated system | k6 | Passed. 33 iterations, 330 HTTP requests, 100% checks, 0 failed requests, p95 1657 ms. |

Final validation notes:

- The final smoke is correctness evidence, not a 100k or 1m capacity claim.
- Application endpoints are JWT-protected; operational health and Prometheus endpoints remain unauthenticated by design so Docker health checks and Prometheus scraping can function.
- The repository is backend-only. Frontend implementation, browser UI, frontend containers, frontend tests, and frontend metrics remain explicitly out of scope.

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
