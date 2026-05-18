# Tests

This file inventories the current test coverage by test role. Unit tests cover isolated service, helper, store, mapper, query-builder, and component behavior. Integration tests cover Spring application endpoints, persistence slices, Kafka flows, browser-level frontend flows, and frontend-origin CORS preflight behavior.

## Current Coverage Summary

Backend coverage and validation commands used for the current review:

```powershell
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

```powershell
docker compose up -d search-opensearch analytics-db recommendation-redis kafka
$env:DOCKER_BUILDKIT='0'; docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=OpenSearchInfrastructureIT services/search-service
$env:DOCKER_BUILDKIT='0'; docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=AnalyticsClickHouseInfrastructureIT services/analytics-service
$env:DOCKER_BUILDKIT='0'; docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=RecommendationCacheInfrastructureIT services/recommendation-service
$env:DOCKER_BUILDKIT='0'; docker build --target infra-test --network benchmark-network --build-arg INFRA_TEST=PlaybackEventPublisherInfrastructureIT services/streaming-service
```

Result: passed. The Java service Docker build path runs `mvn -q verify`, which executes default tests and generates JaCoCo XML/HTML reports. The infrastructure checks are explicit `*InfrastructureIT` runs against real Docker Compose services and are not included in the default JaCoCo totals.

Backend reports are exported under `coverage-output/<service>/jacoco/`. Frontend coverage is intentionally ignored for this review.

Scalability readiness: GO. The backend has enough infrastructure confidence to proceed to scalability testing because the remaining pre-scale recommendations now have real OpenSearch, ClickHouse, Redis, and Kafka checks. Remaining risks are load-oriented rather than baseline correctness blockers.

| Service | Test Files | Test Cases | Line Coverage | Branch Coverage | Function/Method Coverage | Coverage Assessment | Important Weak Paths |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Auth Service | 3 | 12 | 93.70% | 75.00% | 96.23% methods | Good coverage for registration, login, duplicate users, invalid payload validation, JWT creation/parsing, public/protected endpoint boundaries, and frontend CORS preflight. | Password policy edge cases and key-loader failure paths are not deeply covered. |
| Catalog Service | 4 | 14 | 88.68% | 66.67% | 91.11% methods | Good coverage for CSV ingestion, pagination, song detail lookup, metadata conversion robustness, not-found behavior, health, JWT protection, and frontend CORS preflight. | Real Kaggle-scale ingestion performance and unusual malformed metadata shapes still need broader data tests. |
| Playlist Service | 3 | 17 | 95.98% | 73.53% | 96.70% methods | Strong coverage for playlist CRUD, liked songs, duplicate add handling, invalid reorder inputs, track add/remove/reorder, cross-user isolation, invalid bearer tokens, health, and frontend CORS preflight. | Concurrent playlist edits and persistence-level race cases still need stress coverage. |
| Streaming Service | 4 | 13 | 95.14% | 67.86% | 98.11% methods | Strong coverage for descriptor generation, segment generation, safe config defaults, play started/ended/skipped event publishing, JWT protection, invalid segments, health, frontend CORS preflight, and real Kafka publisher delivery through `PlaybackEventPublisherInfrastructureIT`. | Large-volume broker throughput, partitioning behavior, and consumer backpressure remain scalability-test concerns. |
| Search Service | 6 | 20 | 86.58% | 74.44% | 87.50% methods | Good coverage for query construction, CSV indexing input, startup indexing, combined filters, auth boundaries, bad BPM range, OpenSearch index creation, bulk indexing, search parsing, health, frontend CORS preflight, and real OpenSearch index/search behavior through `OpenSearchInfrastructureIT`. | Large indexing batches, index refresh pressure, and search latency under concurrent load remain scalability-test concerns. |
| Analytics Service | 6 | 16 | 88.20% | 70.00% | 88.89% methods | Good coverage for history, global charts, UUID identity validation, ClickHouse SQL/repository behavior, Kafka deserialization robustness, frontend CORS preflight, and real ClickHouse save/read/chart behavior through `AnalyticsClickHouseInfrastructureIT`. | ClickHouse write amplification, query latency, and retention/cleanup behavior remain scalability-test concerns. |
| Recommendation Service | 7 | 20 | 82.07% | 64.81% | 78.82% methods | Good coverage for daily mix/similar logic, cache behavior, JPA repository behavior, Kafka persistence/robustness, controller auth, UUID validation, frontend CORS preflight, and real Redis cache hit/evict/invalid-json behavior through `RecommendationCacheInfrastructureIT`. | Redis latency under concurrent cache churn and database/cache consistency under load remain scalability-test concerns. |
| Notification Service | 4 | 10 | 89.22% | 67.86% | 91.43% methods | Good coverage for notification creation, event mapping, consumer behavior, and embedded-Kafka playlist update consumption. | No browser-facing HTTP API exists in the current baseline, so no CORS/controller coverage applies. |

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
| 26 | `authFlow.test.tsx` | Validates frontend login flow and in-memory token behavior. | Frontend | Vitest / React Testing Library | `frontend/src/__tests__/authFlow.test.tsx` |
| 27 | `apiClient.test.ts` | Validates Axios client bearer-token injection and public auth-call behavior. | Frontend | Vitest | `frontend/src/__tests__/apiClient.test.ts` |
| 28 | `apiModules.test.ts` | Validates feature API wrapper calls for Catalog, Playlist, Analytics, and Recommendation. | Frontend | Vitest / MSW | `frontend/src/__tests__/apiModules.test.ts` |
| 29 | `errorAndQueue.test.ts` | Validates API error normalization and queue store state updates. | Frontend | Vitest / Zustand | `frontend/src/__tests__/errorAndQueue.test.ts` |
| 30 | `playbackStore.test.ts` | Validates frontend playback store state transitions. | Frontend | Vitest / Zustand | `frontend/src/__tests__/playbackStore.test.ts` |
| 31 | `playlistReorder.test.tsx` | Validates playlist reorder payload construction. | Frontend | Vitest | `frontend/src/__tests__/playlistReorder.test.tsx` |
| 32 | `searchFilters.test.tsx` | Validates search filter normalization and rendered search results. | Frontend | Vitest / React Testing Library | `frontend/src/__tests__/searchFilters.test.tsx` |

## Integration Tests

| No. | Name | Description | Service Tested | Runner / Framework | File |
| ---: | --- | --- | --- | --- | --- |
| 1 | `AuthControllerIntegrationTest` | Validates Auth HTTP registration/login flows through the Spring application context. | Auth Service | Spring Boot Test / MockMvc | `services/auth-service/src/test/java/com/benchmark/auth/controller/AuthControllerIntegrationTest.java` |
| 2 | `corsPreflightAllowsFrontendOriginForAuthEndpoints` | Validates Auth CORS preflight support for the frontend origin. | Auth Service | Spring Boot Test / MockMvc | `services/auth-service/src/test/java/com/benchmark/auth/controller/AuthControllerIntegrationTest.java` |
| 3 | `CatalogControllerIntegrationTest` | Validates Catalog HTTP listing/detail endpoints through the Spring application context. | Catalog Service | Spring Boot Test / MockMvc | `services/catalog-service/src/test/java/com/benchmark/catalog/controller/CatalogControllerIntegrationTest.java` |
| 4 | `corsPreflightAllowsFrontendOriginForCatalogEndpoints` | Validates Catalog CORS preflight support for the frontend origin. | Catalog Service | Spring Boot Test / MockMvc | `services/catalog-service/src/test/java/com/benchmark/catalog/controller/CatalogControllerIntegrationTest.java` |
| 5 | `PlaylistControllerIntegrationTest` | Validates protected playlist HTTP workflows through the Spring application context. | Playlist Service | Spring Boot Test / MockMvc | `services/playlist-service/src/test/java/com/benchmark/playlist/controller/PlaylistControllerIntegrationTest.java` |
| 6 | `corsPreflightAllowsFrontendOriginForPlaylistEndpoints` | Validates Playlist CORS preflight support for the frontend origin. | Playlist Service | Spring Boot Test / MockMvc | `services/playlist-service/src/test/java/com/benchmark/playlist/controller/PlaylistControllerIntegrationTest.java` |
| 7 | `StreamingControllerIntegrationTest` | Validates protected streaming HTTP endpoints and event-producing behavior through the Spring application context. | Streaming Service | Spring Boot Test / MockMvc | `services/streaming-service/src/test/java/com/benchmark/streaming/controller/StreamingControllerIntegrationTest.java` |
| 8 | `corsPreflightAllowsFrontendOriginForStreamingEndpoints` | Validates Streaming CORS preflight support for the frontend origin. | Streaming Service | Spring Boot Test / MockMvc | `services/streaming-service/src/test/java/com/benchmark/streaming/controller/StreamingControllerIntegrationTest.java` |
| 9 | `SearchControllerIntegrationTest` | Validates protected search HTTP endpoint behavior through the Spring application context. | Search Service | Spring Boot Test / MockMvc | `services/search-service/src/test/java/com/benchmark/search/controller/SearchControllerIntegrationTest.java` |
| 10 | `corsPreflightAllowsFrontendOriginForSearchEndpoints` | Validates Search CORS preflight support for the frontend origin. | Search Service | Spring Boot Test / MockMvc | `services/search-service/src/test/java/com/benchmark/search/controller/SearchControllerIntegrationTest.java` |
| 11 | `AnalyticsControllerIntegrationTest` | Validates protected Analytics history and global chart endpoints through the Spring application context. | Analytics Service | Spring Boot Test / MockMvc | `services/analytics-service/src/test/java/com/benchmark/analytics/controller/AnalyticsControllerIntegrationTest.java` |
| 12 | `corsPreflightAllowsFrontendOriginForAnalyticsEndpoints` | Validates Analytics CORS preflight support for the frontend origin. | Analytics Service | Spring Boot Test / MockMvc | `services/analytics-service/src/test/java/com/benchmark/analytics/controller/AnalyticsControllerIntegrationTest.java` |
| 13 | `PlaybackEventKafkaRobustnessIntegrationTest` | Validates Analytics Kafka consumer skips malformed/incompatible records and consumes later valid records. | Analytics Service | Spring Boot Test / Embedded Kafka | `services/analytics-service/src/test/java/com/benchmark/analytics/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java` |
| 14 | `RecommendationControllerIntegrationTest` | Validates protected recommendation HTTP endpoint behavior through the Spring application context. | Recommendation Service | Spring Boot Test / MockMvc | `services/recommendation-service/src/test/java/com/benchmark/recommendation/controller/RecommendationControllerIntegrationTest.java` |
| 15 | `corsPreflightAllowsFrontendOriginForRecommendationEndpoints` | Validates Recommendation CORS preflight support for the frontend origin. | Recommendation Service | Spring Boot Test / MockMvc | `services/recommendation-service/src/test/java/com/benchmark/recommendation/controller/RecommendationControllerIntegrationTest.java` |
| 16 | `UserSongInteractionRepositoryTest` | Validates recommendation persistence mapping and repository behavior with a JPA test slice. | Recommendation Service | Spring Data JPA Test | `services/recommendation-service/src/test/java/com/benchmark/recommendation/repository/UserSongInteractionRepositoryTest.java` |
| 17 | `PlaybackEventKafkaPersistenceIntegrationTest` | Validates Recommendation Kafka ingestion persists playback interactions. | Recommendation Service | Spring Boot Test / Embedded Kafka | `services/recommendation-service/src/test/java/com/benchmark/recommendation/messaging/PlaybackEventKafkaPersistenceIntegrationTest.java` |
| 18 | `PlaybackEventKafkaRobustnessIntegrationTest` | Validates Recommendation Kafka consumer skips malformed/incompatible records and consumes later valid records. | Recommendation Service | Spring Boot Test / Embedded Kafka | `services/recommendation-service/src/test/java/com/benchmark/recommendation/messaging/PlaybackEventKafkaRobustnessIntegrationTest.java` |
| 19 | `PlaylistUpdateKafkaIntegrationTest` | Validates Notification Kafka consumption of playlist update events through the Spring application context. | Notification Service | Spring Boot Test / Embedded Kafka | `services/notification-service/src/test/java/com/benchmark/notification/messaging/PlaylistUpdateKafkaIntegrationTest.java` |
| 20 | `OpenSearchInfrastructureIT` | Validates Search index creation, bulk indexing, and search results against the real Compose OpenSearch container. | Search Service | JUnit 5 / Docker Compose OpenSearch | `services/search-service/src/test/java/com/benchmark/search/opensearch/OpenSearchInfrastructureIT.java` |
| 21 | `AnalyticsClickHouseInfrastructureIT` | Validates Analytics schema creation, event persistence, history reads, and global chart aggregation against the real Compose ClickHouse container. | Analytics Service | JUnit 5 / Docker Compose ClickHouse | `services/analytics-service/src/test/java/com/benchmark/analytics/persistence/AnalyticsClickHouseInfrastructureIT.java` |
| 22 | `RecommendationCacheInfrastructureIT` | Validates Recommendation cache put/get/evict behavior and malformed JSON cleanup against the real Compose Redis container. | Recommendation Service | JUnit 5 / Docker Compose Redis | `services/recommendation-service/src/test/java/com/benchmark/recommendation/service/RecommendationCacheInfrastructureIT.java` |
| 23 | `PlaybackEventPublisherInfrastructureIT` | Validates Streaming publisher delivery, Kafka key, and JSON payload round-trip through the real Compose Kafka broker. | Streaming Service | JUnit 5 / Docker Compose Kafka | `services/streaming-service/src/test/java/com/benchmark/streaming/messaging/PlaybackEventPublisherInfrastructureIT.java` |
| 24 | `auth.spec.ts` | Validates frontend health route in a browser. | Frontend | Playwright | `frontend/e2e/auth.spec.ts` |
| 25 | `navigation.spec.ts` | Validates frontend login route rendering in a browser. | Frontend | Playwright | `frontend/e2e/navigation.spec.ts` |
| 26 | `playback.spec.ts` | Validates protected shell/player-bar browser behavior. | Frontend | Playwright | `frontend/e2e/playback.spec.ts` |
| 27 | `playlists.spec.ts` | Validates playlist route protection in a browser. | Frontend | Playwright | `frontend/e2e/playlists.spec.ts` |
| 28 | `search.spec.ts` | Validates search route protection in a browser. | Frontend | Playwright | `frontend/e2e/search.spec.ts` |
