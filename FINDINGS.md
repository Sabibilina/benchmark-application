# Bug Findings Report

This document records all bugs, defects, and notable issues discovered during the iterative generation, validation, and review of the benchmark music streaming application. Issues are organised by service and labelled with the decision ID used in the development chat logs (`ClaudeChats/`).

---

## Summary

- **F-001** — `auth-service` — Test: Mockito strict stubs — *Low* — **Fixed** — `UnnecessaryStubbingException` from shared `@BeforeEach` JWT stubs consumed by only 2 of 6 tests
- **F-002** — `streaming-service` — Visibility: package-private constants — *Low* — **Fixed** — `EVENT_STARTED/ENDED/SKIPPED` constants inaccessible from test sub-package
- **F-003** — `notification-service` — Test infrastructure: teardown noise — *Info* — **Documented** — Benign `MongoSocketReadException` during Testcontainer shutdown
- **F-004** — `analytics-service` — Test infrastructure: teardown noise — *Info* — **Documented** — Benign ClickHouse monitor-thread exception during Testcontainer shutdown
- **F-005** — All Kafka consumers — Integration: deserializer coupling — *Medium* — **Fixed by design** — `JsonDeserializer` injects `__TypeId__` header that ties consumer to producer's class name
- **F-006** — `catalog-service`, `playlist-service`, `streaming-service` — Visibility: package-private constants — *Low* — **Fixed** — Recurring pattern: constants in main package unreachable from `*.unit` test sub-package
- **F-007** — All Kafka consumers — Test infrastructure: container ordering — *Medium* — **Fixed by design** — `@DynamicPropertySource` resolves before `@Container` starts; Kafka properties unavailable
- **F-008** — `auth-service` — Test environment — *Info* — **Pending** — Test suite execution deferred during Phase 1; requires Maven + Java 21 + Docker daemon

---

## Detailed Findings

### F-001 — Mockito Strict-Stubs Violation in `AuthServiceTest`

- **Service:** auth-service
- **File:** `services/auth-service/src/test/java/com/musicstreaming/auth/unit/AuthServiceTest.java`
- **Category:** Test — Mockito configuration
- **Severity:** Low (test failure only, not a production bug)
- **Decision Ref:** D-035 (ClaudeChats/1AuthService.txt)

**Description:**  
`MockitoExtension` enables `STRICT_STUBS` mode by default. The `@BeforeEach configureMocks()` method stubbed `jwtConfig.getPrivateKey()` and `jwtConfig.getExpirationMs()` for all six tests. However, four tests (`register_duplicateUsername`, `register_duplicateEmail`, `login_wrongPassword`, `login_unknownUser`) throw an exception before the JWT-building code path is reached, so those stubs are never consumed. `STRICT_STUBS` treats any unconsumed stub as an error and throws `UnnecessaryStubbingException`.

**Root Cause:** Shared `@BeforeEach` stubs set up for the happy-path tests are unused in the early-exit exception tests.

**Fix:** Changed both shared stubs in `@BeforeEach` from `when(...)` to `lenient().when(...)`. This allows the stubs to remain unused without triggering the strict-stub check while preserving strict-stub enforcement for all other stubs.

```java
// Before (caused UnnecessaryStubbingException in 4 tests)
when(jwtConfig.getPrivateKey()).thenReturn(testPrivateKey);
when(jwtConfig.getExpirationMs()).thenReturn(3_600_000L);

// After (fixed)
lenient().when(jwtConfig.getPrivateKey()).thenReturn(testPrivateKey);
lenient().when(jwtConfig.getExpirationMs()).thenReturn(3_600_000L);
```

---

### F-002 — Package-Private Constants Inaccessible from Test Sub-Package (Streaming Service)

- **Service:** streaming-service
- **File:** `services/streaming-service/src/main/java/com/musicstreaming/streaming/service/StreamingService.java`
- **Category:** Visibility / access control
- **Severity:** Low (compilation failure in tests only)
- **Decision Ref:** D-093 (ClaudeChats/4StreamingService.txt)

**Description:**  
`StreamingService` declared three event-type constants with package-private (default) visibility:

```java
static final String EVENT_STARTED = "play.started";
static final String EVENT_ENDED   = "play.ended";
static final String EVENT_SKIPPED = "play.skipped";
```

The unit test class `StreamingServiceTest` lives in `com.musicstreaming.streaming.unit`, which is a different package from `com.musicstreaming.streaming.service`. Java's package-private visibility does not extend across packages even within the same artifact, so the test class could not reference the constants.

**Root Cause:** Constants intended to be referenced externally were left with default package-private visibility.

**Fix:** Changed all three constants to `public static final`. This also allows future consumer services or test utilities to reference canonical event-type strings without hardcoding them.

**Pattern Note:** This same visibility issue was encountered in catalog-service (D-049) and playlist-service (D-073) and is documented as F-006 below.

---

### F-003 — Benign MongoDB Shutdown Exception in Notification Service Tests

- **Service:** notification-service
- **Category:** Test infrastructure — teardown noise
- **Severity:** Info (no test failures, cosmetic log noise only)
- **Decision Ref:** V-023 (ClaudeChats/8NotificationService.txt)

**Description:**  
After all notification-service tests complete and the `MongoTestContainer` is stopped, the MongoDB driver's background monitor thread attempts one final connection and encounters a closed socket. This produces a stack trace in the test log:

```
com.mongodb.MongoSocketReadException: Prematurely reached end of stream
```

The exception is thrown on a driver-internal monitor thread during container shutdown, not during any test method. All 13 notification-service test cases pass; the exception has no effect on results.

**Root Cause:** The MongoDB Java driver's connection monitor polls the server on a background thread. When the Testcontainer is forcibly stopped, the monitor's next read hits a closed socket before the driver has been notified to stop.

**Resolution:** Documented as expected teardown noise. No fix required; the same pattern is seen in all Testcontainer-based setups where the driver has an active monitor thread.

---

### F-004 — Benign ClickHouse Monitor Thread Exception in Analytics Service Tests

- **Service:** analytics-service
- **Category:** Test infrastructure — teardown noise
- **Severity:** Info (no test failures)
- **Decision Ref:** V-016 (ClaudeChats/6AnalyticsService.txt)

**Description:**  
Identical in nature to F-003. After analytics-service tests complete, the ClickHouse JDBC driver logs a connection exception as the `ClickHouseTestContainer` is stopped. All 22 analytics-service tests pass.

**Resolution:** Same as F-003 — expected teardown behaviour from JDBC/driver polling threads hitting a closed container socket.

---

### F-005 — Kafka `JsonDeserializer` `__TypeId__` Header Coupling

- **Services:** analytics-service, recommendation-service, notification-service (all Kafka consumers)
- **Category:** Integration — inter-service coupling
- **Severity:** Medium (would cause `ClassNotFoundException` at runtime if not resolved)
- **Decision Ref:** D-112, D-139, D-156 (ClaudeChats/6AnalyticsService.txt, 7RecommendationService.txt, 8NotificationService.txt)

**Description:**  
Spring Kafka's `JsonDeserializer` reads a `__TypeId__` header from the message to determine the target class for deserialization. When a producer serialises a `PlaybackEvent` from `com.musicstreaming.streaming.dto.PlaybackEvent`, the consumer (a different service with a different package structure) receives a header pointing to the producer's class, which does not exist in the consumer's classpath. This causes a `ClassNotFoundException` or silent deserialization failure at runtime.

**Root Cause:** `JsonDeserializer` couples the consumer to the exact fully-qualified class name of the producer's DTO, creating invisible cross-service class-path dependencies.

**Fix Applied Across All Consumers:** All Kafka consumer services use `StringDeserializer` to receive the raw JSON string, then deserialize it using `ObjectMapper` explicitly. This eliminates the `__TypeId__` header dependency entirely.

```java
// Consumer factory — correct approach used in all services
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
// In listener method:
PlaybackEvent event = objectMapper.readValue(payload, PlaybackEvent.class);
```

---

### F-006 — Recurring Package-Private Constant Visibility Pattern

- **Services:** catalog-service (D-049), playlist-service (D-073), streaming-service (D-093)
- **Category:** Visibility / access control — recurring pattern
- **Severity:** Low (compilation failure in tests, not a runtime bug)

**Description:**  
Across three services, constants defined in service classes with default (package-private) visibility were inaccessible from unit test classes placed in a `*.unit` sub-package. The pattern recurred because the generation process initially placed test classes in a sub-package rather than the same package as the class under test.

**Services and constants affected:**
- `catalog-service`: seed-status or category-type constants in `DataSeeder` / `CatalogService`
- `playlist-service`: track-position or event-type constants in `PlaylistService`
- `streaming-service`: `EVENT_STARTED`, `EVENT_ENDED`, `EVENT_SKIPPED` in `StreamingService`

**Fix:** Change `static final` constants to `public static final` in each affected class. This is the correct approach for constants intended to serve as part of a well-defined API surface — e.g., Kafka event type strings that multiple services may need to reference.

---

### F-007 — Testcontainers / `@DynamicPropertySource` Ordering Issue in Kafka Integration Tests

- **Services:** analytics-service, notification-service (any service with `@EmbeddedKafka` or Kafka `@Container`)
- **Category:** Test infrastructure — Spring/JUnit 5 lifecycle ordering
- **Severity:** Medium (tests fail to start if not addressed)
- **Decision Ref:** D-074, D-158 (ClaudeChats/6AnalyticsService.txt, 8NotificationService.txt)

**Description:**  
When using `@Container` (Testcontainers) together with `@DynamicPropertySource` in a JUnit 5 Spring Boot test, `@DynamicPropertySource` methods execute during the Spring context construction phase, which begins before the JUnit 5 `@Container` lifecycle has started the container. As a result, `@DynamicPropertySource` reads a null or `0` mapped port, and the Spring context is wired with an incorrect broker address.

**Root Cause:** JUnit 5 extension ordering: `@DynamicPropertySource` is a Spring extension hook that fires before the Testcontainers extension has had a chance to start `@Container`-annotated fields.

**Fix:** Use a `static {}` initializer block or `@BeforeAll` with `container.start()` to start the Kafka container (or `@EmbeddedKafka`) explicitly before the Spring context initialisation phase, so that port mappings are available when `@DynamicPropertySource` runs.

---

### F-008 — Auth Service Test Suite Execution Deferred (Environment Constraint)

- **Service:** auth-service
- **Category:** Test environment
- **Severity:** Info (process gap, not a code defect)
- **Decision Ref:** V-004 (ClaudeChats/1AuthService.txt)

**Description:**  
During Phase 1 validation, the test suite for auth-service could not be executed because the host environment lacked Maven, Java 21, and a running Docker daemon. The acceptance criterion "Test suite passes successfully" was left unchecked in `PROGRESS.md`.

All other services (Phases 2–8) had their test suites executed and passed (see `PROGRESS.md` for per-phase pass counts). Auth-service tests were subsequently considered passing based on structural review; however, a confirmed `mvn verify` run was not recorded for this service in the chat logs.

**Action Required:** Run `cd services/auth-service && mvn verify` to produce a verified pass record.

---

## Pending / Open Issues

The following items are not bugs in the current code but represent missing coverage or deferred work that may surface bugs later.

- **P-001** — Frontend tests: Phase 9 acceptance criteria for automated frontend tests (auth flow, playback state machine, search filtering, playlist reorder) are all unchecked in `PROGRESS.md`. No frontend test files exist yet.
- **P-002** — JaCoCo coverage: No `jacoco-maven-plugin` is configured. Actual line/branch/function coverage is unmeasured.
- **P-003** — Phase 10 Monitoring: Prometheus scrape targets, Grafana dashboard wiring, and CPU/memory limits in `docker-compose.yml` are not yet validated end-to-end.
- **P-004** — Phase 10 Load Generator: K6 load generator script and workload documentation are not yet present.
- **P-005** — Inter-service resilience: Retry with exponential backoff and circuit-breaker patterns for inter-service HTTP calls are not yet implemented (M-22, M-23 in `REQUIREMENTS.md`).
- **P-006** — `docker-compose.yml` has uncommitted local modifications (visible in `git status` at project start). The change has not been reviewed.

---

## Notes on Discovering Future Findings

This document will be updated as new issues are found. Sources to monitor:

- **E2E test failures:** Any new failure in `e2e-tests/target/surefire-reports/` should be logged here.
- **`mvn verify` output per service:** Any new `ERROR` or `FAIL` in surefire output should be logged here.
- **Code review of remaining phases:** Frontend (Phase 9) and integration (Phase 10) have not been reviewed yet.
- **`ClaudeChats/` log files:** The decision IDs referenced throughout this document correspond to entries in those files where additional context is available.
