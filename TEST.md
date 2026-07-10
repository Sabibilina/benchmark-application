# Test Coverage Report

## Coverage Summary

| Metric | Value |
|---|---|
| Total Services | 8 |
| Services with Unit Tests | 8 / 8 |
| Services with Integration Tests | 8 / 8 |
| Total Backend Test Files | 18 (excl. support/config classes) |
| Total Unit Test Methods | 89 |
| Total Integration Test Methods | 99 |
| Total Context Tests | 8 |
| Total Backend Test Methods | 196 |
| E2E Test Suites | 9 |
| Total E2E Test Cases | 101 |
| Overall E2E Pass Rate | 101 / 101 (100%) |
| JaCoCo Coverage Plugin | Configured in all 8 service `pom.xml` files — reports generated 2026-05-20 |
| Test Framework | JUnit 5 + Mockito + Testcontainers + Spring Boot Test |

> **Generating coverage reports:** Run `mvn verify` inside any service directory. JaCoCo HTML reports will appear at `services/<service-name>/target/site/jacoco/index.html`. The CSV at `target/site/jacoco/jacoco.csv` gives per-class line, branch, and method coverage counts.

---

## Backend Coverage

### Per-Service Test Method Breakdown

| Service | Unit Test File(s) | Unit Tests | Integration Test File | Integration Tests | Context Test | Total |
|---|---|---|---|---|---|---|
| auth-service | `AuthServiceTest` (6), `JwtConfigTest` (4) | 10 | `AuthControllerIT` | 10 | 1 | **21** |
| catalog-service | `CatalogServiceTest` (7), `DataSeederTest` (6) | 13 | `CatalogControllerIT` | 13 | 1 | **27** |
| streaming-service | `StreamingServiceTest` | 8 | `StreamingControllerIT` | 15 | 1 | **24** |
| playlist-service | `PlaylistServiceTest` | 21 | `PlaylistControllerIT` | 22 | 1 | **44** |
| search-service | `SearchQueryBuilderTest` | 10 | `SearchControllerIT` | 12 | 1 | **23** (fixed F-009) |
| analytics-service | `AnalyticsServiceTest` | 9 | `AnalyticsControllerIT` | 12 | 1 | **22** |
| recommendation-service | `RecommendationServiceTest` | 6 | `RecommendationControllerIT` | 9 | 1 | **16** |
| notification-service | `NotificationServiceTest` | 12 | `NotificationControllerIT` | 6 | 1 | **19** |

---

### Coverage Assessment Table

| Service | Test Files | Test Cases | Line Coverage | Branch Coverage | Function/Method Coverage | Coverage Assessment |
|---|---|---|---|---|---|---|
| auth-service | 5 | 21 | **86%** (95/110) | **90%** (9/10) | **74%** (35/47) | **Good** — service layer fully mocked (register/login/JWT paths), controller tested against live PostgreSQL container; config and entity classes untested |
| catalog-service | 6 | 27 | **74%** (181/244) | **46%** (12/26) | **98%** (81/83) | **Good** — seeder logic + service queries covered by unit tests; controller endpoints verified with Testcontainers PostgreSQL; pagination and filter paths tested; low branch coverage reflects uncovered CSV-parse edge paths in DataSeeder |
| streaming-service | 4 | 24 | **96%** (104/108) | **75%** (6/8) | **97%** (29/30) | **High** — manifest building, segment generation, and all three event types (started/ended/skipped) covered in unit tests; HLS and JWT paths verified in integration tests |
| playlist-service | 5 | 44 | **96%** (239/250) | **88%** (30/34) | **94%** (85/90) | **High** — 21 unit tests exercise all service paths including Liked Songs enforcement, reorder, and exception branches; 22 integration tests cover all 8 required endpoints |
| search-service | 5 | 23 | **66%** (169/258) | **43%** (29/68) | **89%** (66/74) | **Good** — query builder logic fully unit-tested across filter combinations; controller integration tests verify OpenSearch interaction; lower coverage reflects uncovered OpenSearch exception-handling paths and index-management code. Note: OpenSearch container startup fix applied (F-009); re-run `mvn test` to confirm pass. |
| analytics-service | 5 | 22 | **94%** (117/124) | **80%** (8/10) | **93%** (40/43) | **Good** — Kafka consumer, history persistence, and aggregation logic tested; ClickHouse integration verified via dedicated test container |
| recommendation-service | 6 | 16 | **72%** (195/271) | **52%** (30/58) | **87%** (62/71) | **Moderate** — daily mix and similar-song logic covered; Redis caching paths tested; lower coverage reflects uncovered Kafka deserialization error paths and fallback recommendation branches |
| notification-service | 5 | 19 | **86%** (116/135) | **77%** (20/26) | **92%** (44/48) | **Moderate** — Kafka consumer and MongoDB persistence tested; benign shutdown exception documented (see FINDINGS.md) |

---

### E2E Test Results (Last Executed Run — 2026-05-20)

Tests were executed against the full Docker Compose stack (`mvn verify` in `e2e-tests/`). All 101 test cases passed. Total build time: 45.251 s.

| E2E Suite | Test Cases | Failures | Errors | Skipped | Duration |
|---|---|---|---|---|---|
| `AuthFlowIT` | 10 | 0 | 0 | 0 | 1.662 s |
| `CatalogFlowIT` | 16 | 0 | 0 | 0 | 1.769 s |
| `StreamingFlowIT` | 8 | 0 | 0 | 0 | 0.472 s |
| `PlaylistFlowIT` | 26 | 0 | 0 | 0 | 2.614 s |
| `ChartsFlowIT` | 5 | 0 | 0 | 0 | 4.070 s |
| `HistoryFlowIT` | 6 | 0 | 0 | 0 | 4.493 s |
| `RecommendationFlowIT` | 7 | 0 | 0 | 0 | 1.860 s |
| `NotificationFlowIT` | 8 | 0 | 0 | 0 | 6.494 s |
| `FullUserJourneyIT` | 15 | 0 | 0 | 0 | 10.700 s |
| **TOTAL** | **101** | **0** | **0** | **0** | **~34.1 s** |

`FullUserJourneyIT` covers the complete cross-service path: register → login → browse catalog → search → create playlist → add track → reorder → stream → complete stream → check history → get daily mix → get similar songs → receive notification → remove track → delete playlist.

---

## Remaining Backend Risks

| Area | Recommendation |
|---|---|
| **Coverage measurement** | JaCoCo reports generated 2026-05-20 for all 8 services. HTML reports at `services/<name>/target/site/jacoco/index.html`. Coverage table populated with real numbers; re-run `mvn verify` to refresh after code changes. |
| **Auth service confirmed test run** | Phase 1 validation deferred (no Docker/Maven at the time). Run `cd services/auth-service && mvn verify` to produce a confirmed pass record (see FINDINGS.md F-008). |
| **Inter-service resilience** | No retry or circuit-breaker logic is implemented for inter-service HTTP calls. Requirements M-22 and M-23 in `REQUIREMENTS.md` remain open. |
| **Frontend tests** | All Phase 9 test acceptance criteria are unchecked. No frontend test files exist. Auth flow, playback state machine, search filtering, and playlist reorder need automated coverage. |
| **Monitoring validation** | Prometheus scrape targets, Grafana dashboard panels, and CPU/memory limits in `docker-compose.yml` have not been end-to-end validated (Phase 10). |
| **Load generator** | No K6 script or workload definition exists yet. Phase 10 requires coverage of: registration, login, catalog browsing, search, streaming, playlist operations, and history queries. |
| **Uncommitted docker-compose.yml changes** | `git status` shows `docker-compose.yml` is locally modified. The change has not been reviewed or committed. |

---

## Unit Tests

| No. | Name | Description | Service Tested | Runner / Framework | File |
|---|---|---|---|---|---|
| 1 | `register_success_returnsTokenAndSavesUser` | Successful registration returns a JWT token and persists the user | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 2 | `register_duplicateUsername_throwsConflict` | Registration with a duplicate username throws a conflict error | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 3 | `register_duplicateEmail_throwsConflict` | Registration with a duplicate email throws a conflict error | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 4 | `login_success_returnsToken` | Successful login returns a JWT token | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 5 | `login_wrongPassword_throwsInvalidCredentials` | Login with wrong password throws invalid credentials | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 6 | `login_unknownUser_throwsInvalidCredentials` | Login with unknown user throws invalid credentials | auth-service | JUnit 5 + Mockito | `AuthServiceTest.java` |
| 7 | `init_generatesKeyPairAndWritesPemFiles` | JWT initialisation generates RSA key pair and writes PEM files | auth-service | JUnit 5 + Mockito | `JwtConfigTest.java` |
| 8 | `init_loadsExistingKeyPairAndProducesSamePublicKey` | JWT initialisation loads existing key pair and produces the same public key | auth-service | JUnit 5 + Mockito | `JwtConfigTest.java` |
| 9 | `token_roundtrip_claimsAreCorrect` | JWT token roundtrip preserves correct claims | auth-service | JUnit 5 + Mockito | `JwtConfigTest.java` |
| 10 | `expired_token_isRejected` | Expired JWT token is rejected | auth-service | JUnit 5 + Mockito | `JwtConfigTest.java` |
| 11 | `findAll_returnsPagedResponse` | Finding all songs returns a paged response with correct metadata | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 12 | `findAll_emptyPage_returnsEmptyContent` | Finding all songs returns empty content when no songs exist | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 13 | `findAll_sizeExceedsMax_isCappedAt100` | Page size exceeding the maximum is capped at 100 | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 14 | `findAll_invalidSortField_fallsBackToId` | Invalid sort field falls back to id | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 15 | `findAll_descDirection_usedInPageable` | Descending sort direction is correctly applied | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 16 | `findById_existingSong_returnsMappedDto` | Finding existing song by ID returns mapped DTO | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 17 | `findById_missingSong_throwsSongNotFoundException` | Finding a missing song throws SongNotFoundException | catalog-service | JUnit 5 + Mockito | `CatalogServiceTest.java` |
| 18 | `seed_whenDisabled_nothingIsExecuted` | Data seeding does nothing when disabled | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 19 | `seed_whenTableAlreadyHasData_skipsInsert` | Data seeding skips insert when table already has data | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 20 | `computeArtistId_sameInput_returnsSameHash` | Artist ID computation returns consistent hash for the same input | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 21 | `computeArtistId_isCaseInsensitive` | Artist ID computation is case-insensitive | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 22 | `computeArtistId_returns16HexChars` | Artist ID computation returns 16 hexadecimal characters | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 23 | `computeArtistId_differentArtists_returnDifferentIds` | Different artists produce different artist IDs | catalog-service | JUnit 5 + Mockito | `DataSeederTest.java` |
| 24 | `buildManifest_startsWithExtM3U` | M3U8 manifest starts with EXTM3U header | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 25 | `buildManifest_containsConfiguredSegmentCount` | M3U8 manifest contains configured number of segments | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 26 | `buildManifest_segmentUrlsContainSongId` | M3U8 manifest segment URLs contain the song ID | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 27 | `buildManifest_endsWithEndList` | M3U8 manifest ends with EXT-X-ENDLIST tag | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 28 | `generateSegmentPayload_returnsConfiguredByteLength` | Segment payload generation returns configured byte length | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 29 | `handleStreamStart_publishesPlayStartedEvent` | Stream start publishes a play started event to Kafka | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 30 | `handleStreamComplete_publishesPlayEndedEvent` | Stream completion publishes a play ended event to Kafka | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 31 | `handleStreamSkip_publishesPlaySkippedEvent` | Stream skip publishes a play skipped event to Kafka | streaming-service | JUnit 5 + Mockito | `StreamingServiceTest.java` |
| 32 | `getPlaylists_createsLikedSongsWhenAbsent` | Getting playlists auto-creates the Liked Songs playlist when absent | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 33 | `getPlaylists_doesNotDuplicateLikedSongs` | Getting playlists does not duplicate the Liked Songs playlist | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 34 | `createPlaylist_happyPath` | Creating a playlist with a valid name succeeds | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 35 | `createPlaylist_rejectsDuplicateName` | Creating a playlist with a duplicate name is rejected | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 36 | `createPlaylist_rejectsLikedSongsName` | Creating a playlist with the reserved "Liked Songs" name is rejected | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 37 | `createPlaylist_rejectsLikedSongsNameCaseInsensitive` | "Liked Songs" name rejection is case-insensitive | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 38 | `getPlaylist_returnsOwnedPlaylist` | Getting a playlist returns the playlist owned by the requesting user | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 39 | `getPlaylist_throwsWhenNotFound` | Getting a non-existent playlist throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 40 | `getPlaylist_throwsWhenOwnedByOtherUser` | Getting a playlist owned by another user throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 41 | `updatePlaylist_updatesName` | Updating a playlist changes its name | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 42 | `updatePlaylist_rejectsLikedSongsNameOnNormalPlaylist` | Updating a playlist rejects the reserved "Liked Songs" name | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 43 | `updatePlaylist_rejectsRenameOfLikedSongsPlaylist` | Renaming the Liked Songs playlist is rejected | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 44 | `deletePlaylist_happyPath` | Deleting a playlist succeeds | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 45 | `deletePlaylist_throwsForLikedSongs` | Deleting the Liked Songs playlist throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 46 | `deletePlaylist_throwsWhenNotFound` | Deleting a non-existent playlist throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 47 | `addTrack_appendsAtNextPosition` | Adding a track to a playlist appends it at the next position | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 48 | `addTrack_throwsOnDuplicate` | Adding a duplicate track to a playlist throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 49 | `removeTrack_happyPath` | Removing a track from a playlist succeeds | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 50 | `removeTrack_throwsWhenTrackNotInPlaylist` | Removing a non-existent track throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 51 | `reorderTracks_updatesPositions` | Reordering tracks updates their positions correctly | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 52 | `reorderTracks_throwsOnMismatch` | Reordering tracks with mismatched IDs throws an exception | playlist-service | JUnit 5 + Mockito | `PlaylistServiceTest.java` |
| 53 | `build_noParams_usesMatchAll` | Search query with no parameters uses match-all | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 54 | `build_withTextQuery_addsMustMultiMatch` | Search query with text adds a multi-match clause | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 55 | `build_withBlankQuery_usesMatchAll` | Search query with a blank text string uses match-all | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 56 | `build_withGenre_addsTermFilter` | Search query with genre parameter adds a term filter | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 57 | `build_withBpmRange_addsRangeFilter` | Search query with a BPM range adds a range filter | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 58 | `build_withBpmMinOnly_addsOpenUpperRange` | Search query with BPM minimum only adds an open upper range | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 59 | `build_withBpmMaxOnly_addsOpenLowerRange` | Search query with BPM maximum only adds an open lower range | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 60 | `build_withYear_addsTermFilter` | Search query with year parameter adds a term filter | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 61 | `build_allFilters_buildsCombinedQuery` | Search query with all filters builds a correctly combined query | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 62 | `build_setsMaxResultsAsSize` | Search query sets max results as the size field | search-service | JUnit 5 + Mockito | `SearchQueryBuilderTest.java` |
| 63 | `getHistory_delegatesToRepositoryWithConfiguredLimit` | Getting history delegates to the repository with the configured limit | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 64 | `getHistory_returnsEmptyList_whenNoEvents` | Getting history returns an empty list when no events exist | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 65 | `getHistory_returnsEntriesFromRepository` | Getting history returns entries from the repository | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 66 | `getGlobalCharts_delegatesToRepositoryWithLimit50` | Getting global charts delegates with a limit of 50 | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 67 | `getGlobalCharts_returnsEmptyList_whenNoData` | Getting global charts returns empty list when no data exists | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 68 | `getGlobalCharts_returnsRankedEntriesFromRepository` | Getting global charts returns ranked entries from the repository | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 69 | `recordEvent_insertsToRepository_whenValidEvent` | Recording a valid event inserts it to the repository | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 70 | `recordEvent_skipsInsert_whenUserIdIsNull` | Recording an event skips insert when user ID is null | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 71 | `recordEvent_skipsInsert_whenSongIdIsNull` | Recording an event skips insert when song ID is null | analytics-service | JUnit 5 + Mockito | `AnalyticsServiceTest.java` |
| 72 | `dailyMix_returnsNonEmptyList_whenUserHasPlayHistory` | Daily mix returns a non-empty list when the user has play history | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 73 | `dailyMix_returnsNonEmptyList_whenUserHasNoHistory` | Daily mix returns a non-empty list even when the user has no history | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 74 | `dailyMix_returnsCachedResult_whenCacheHit` | Daily mix returns the cached result on a cache hit | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 75 | `similar_returnsNonEmptyList_whenSongFound` | Similar songs returns a non-empty list when the seed song is found | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 76 | `similar_returnsNonEmptyList_whenSongNotFound` | Similar songs returns a non-empty fallback list when seed song is not found | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 77 | `similar_returnsCachedResult_whenCacheHit` | Similar songs returns cached result on a cache hit | recommendation-service | JUnit 5 + Mockito | `RecommendationServiceTest.java` |
| 78 | `createNotification_savesWithCorrectUserId` | Creating a notification saves it with the correct user ID | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 79 | `createNotification_savesWithCorrectType` | Creating a notification saves it with the correct event type | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 80 | `createNotification_setsReadFalse` | Creating a notification sets the read flag to false | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 81 | `createNotification_setsReferenceId` | Creating a notification sets the reference ID | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 82 | `createNotification_nullTimestamp_usesNow` | Creating a notification with a null timestamp defaults to current time | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 83 | `createNotification_allEventTypes_producesNonBlankTitleAndMessage` | All event types produce a non-blank title and message — `@ParameterizedTest` with 6 values (`PLAYLIST_CREATED`, `PLAYLIST_UPDATED`, `PLAYLIST_DELETED`, `TRACK_ADDED`, `TRACK_REMOVED`, `TRACKS_REORDERED`); counts as 6 test executions | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |
| 84 | `getNotificationsForUser_delegatesToRepository` | Getting notifications delegates to the repository | notification-service | JUnit 5 + Mockito | `NotificationServiceTest.java` |

---

## Integration Tests

| No. | Name | Description | Service Tested | Runner / Framework | File |
|---|---|---|---|---|---|
| 1 | `register_validRequest_returns201WithToken` | POST /auth/register with valid request returns 201 and a token | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 2 | `register_tokenIsRS256WithCorrectClaims` | Registered token is RS256-signed with correct claims | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 3 | `register_duplicateUsername_returns409` | POST /auth/register with duplicate username returns 409 | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 4 | `register_duplicateEmail_returns409` | POST /auth/register with duplicate email returns 409 | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 5 | `register_invalidRequest_returns400` | POST /auth/register with invalid body returns 400 | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 6 | `login_validCredentials_returns200WithToken` | POST /auth/login with valid credentials returns 200 and a token | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 7 | `login_tokenContainsCorrectClaims` | Login token contains claims matching registration | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 8 | `login_wrongPassword_returns401` | POST /auth/login with wrong password returns 401 | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 9 | `login_unknownUser_returns401` | POST /auth/login with unknown user returns 401 | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 10 | `publicKeyFile_existsAfterStartup` | JWT public key file exists after service startup | auth-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `AuthControllerIT.java` |
| 11 | `getSongs_noToken_returns401` | GET /catalog/songs without token returns 401 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 12 | `getSongs_invalidToken_returns401` | GET /catalog/songs with invalid token returns 401 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 13 | `getSongs_expiredToken_returns401` | GET /catalog/songs with expired token returns 401 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 14 | `getSongById_noToken_returns401` | GET /catalog/songs/:id without token returns 401 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 15 | `getSongs_validToken_returns200WithPage` | GET /catalog/songs with valid token returns 200 and a paged response | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 16 | `getSongs_paginationPage0Size5_returns5Items` | GET /catalog/songs page=0&size=5 returns 5 items | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 17 | `getSongs_paginationPage1Size5_returnsDifferentItems` | GET /catalog/songs page 1 returns items different from page 0 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 18 | `getSongs_sizeLargerThanMax_cappedAt100` | GET /catalog/songs with size > 100 is capped at 100 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 19 | `getSongById_existingId_returns200WithMetadata` | GET /catalog/songs/:id with existing ID returns 200 with metadata | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 20 | `getSongById_unknownId_returns404` | GET /catalog/songs/:id with unknown ID returns 404 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 21 | `getSongById_nonNumericId_returns400` | GET /catalog/songs/:id with non-numeric ID returns 400 | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 22 | `getArtistTopTracks_validArtistId_returnsUpTo10Songs` | GET /catalog/artists/:id/top-tracks returns up to 10 songs for a known artist | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 23 | `getArtistTopTracks_unknownArtistId_returnsEmptyList` | GET /catalog/artists/:id/top-tracks returns empty list for unknown artist | catalog-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) | `CatalogControllerIT.java` |
| 24 | `getStream_withoutToken_returns401` | GET /stream/:songId without token returns 401 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 25 | `getStream_withInvalidToken_returns401` | GET /stream/:songId with invalid token returns 401 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 26 | `getSegment_withoutToken_returns401` | GET /stream/:songId/segment/:index without token returns 401 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 27 | `completeStream_withoutToken_returns401` | POST /stream/:songId/complete without token returns 401 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 28 | `skipStream_withoutToken_returns401` | POST /stream/:songId/skip without token returns 401 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 29 | `getStream_withValidToken_returns200` | GET /stream/:songId with valid token returns 200 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 30 | `getStream_withValidToken_contentTypeIsM3U8` | GET /stream/:songId response content-type is application/x-mpegURL | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 31 | `getStream_withValidToken_bodyContainsM3U8Structure` | GET /stream/:songId body contains valid M3U8 structure | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 32 | `getStream_withValidToken_publishesPlayStartedEvent` | GET /stream/:songId publishes a play started event to Kafka | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 33 | `getSegment_withValidToken_returns200` | GET /stream/:songId/segment/:index with valid token returns 200 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 34 | `getSegment_withValidToken_returnsCorrectByteLength` | GET /stream/:songId/segment/:index returns the configured byte length | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 35 | `completeStream_withValidToken_returns204` | POST /stream/:songId/complete with valid token returns 204 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 36 | `completeStream_withValidToken_publishesPlayEndedEvent` | POST /stream/:songId/complete publishes a play ended event to Kafka | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 37 | `skipStream_withValidToken_returns204` | POST /stream/:songId/skip with valid token returns 204 | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 38 | `skipStream_withValidToken_publishesPlaySkippedEvent` | POST /stream/:songId/skip publishes a play skipped event to Kafka | streaming-service | JUnit 5 + Spring Boot Test + EmbeddedKafka | `StreamingControllerIT.java` |
| 39 | `allEndpoints_return401WithoutToken` | All playlist endpoints return 401 without a token | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 40 | `allEndpoints_return401WithInvalidToken` | All playlist endpoints return 401 with an invalid token | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 41 | `getPlaylists_autoCreatesLikedSongs` | GET /playlists auto-creates the Liked Songs playlist for a new user | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 42 | `getPlaylists_returnsOnlyOwnPlaylists` | GET /playlists returns only the authenticated user's playlists | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 43 | `createPlaylist_returns201` | POST /playlists returns 201 with the created playlist | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 44 | `createPlaylist_rejectsLikedSongsName` | POST /playlists rejects the reserved "Liked Songs" name | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 45 | `createPlaylist_rejectsDuplicateName` | POST /playlists rejects a duplicate playlist name | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 46 | `getPlaylist_returns200ForOwner` | GET /playlists/:id returns 200 for the playlist owner | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 47 | `getPlaylist_returns404ForOtherUser` | GET /playlists/:id returns 404 for another user's playlist | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 48 | `getPlaylist_returns404ForUnknownId` | GET /playlists/:id returns 404 for unknown playlist ID | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 49 | `updatePlaylist_updatesName` | PATCH /playlists/:id updates the playlist name | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 50 | `updatePlaylist_rejectsLikedSongsRename` | PATCH /playlists/:id rejects renaming the Liked Songs playlist | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 51 | `deletePlaylist_returns204` | DELETE /playlists/:id returns 204 | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 52 | `deletePlaylist_returns400ForLikedSongs` | DELETE /playlists/:id returns 400 for the Liked Songs playlist | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 53 | `addTrack_returns201AndTrackPresent` | POST /playlists/:id/tracks returns 201 and adds the track | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 54 | `addTrack_returns409OnDuplicate` | POST /playlists/:id/tracks returns 409 for a duplicate track | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 55 | `addTrack_returns404ForOtherUsersPlaylist` | POST /playlists/:id/tracks returns 404 for another user's playlist | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 56 | `removeTrack_returns204` | DELETE /playlists/:id/tracks/:songId returns 204 | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 57 | `removeTrack_returns404ForMissingSong` | DELETE /playlists/:id/tracks/:songId returns 404 for a missing track | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 58 | `reorderTracks_updatesPositions` | PATCH /playlists/:id/tracks/reorder updates track positions | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 59 | `reorderTracks_returns400OnMismatch` | PATCH /playlists/:id/tracks/reorder returns 400 on song ID mismatch | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 60 | `addTrack_publishesKafkaEvent` | POST /playlists/:id/tracks publishes a Kafka event | playlist-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) + EmbeddedKafka | `PlaylistControllerIT.java` |
| 61 | `search_withoutToken_returns401` | GET /search without token returns 401 | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 62 | `search_withInvalidToken_returns401` | GET /search with invalid token returns 401 | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 63 | `search_noParams_returns200WithResults` | GET /search with no parameters returns 200 with results | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 64 | `search_withTextQuery_returnsMatchingSong` | GET /search with a text query returns matching songs | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 65 | `search_withTextQuery_doesNotReturnUnrelatedSong` | GET /search text query does not return unrelated songs | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 66 | `search_byGenre_returnsOnlyMatchingGenre` | GET /search genre filter returns only songs matching the genre | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 67 | `search_byGenre_excludesOtherGenres` | GET /search genre filter excludes songs with other genres | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 68 | `search_byBpmRange_returnsOnlySongsInRange` | GET /search BPM range filter returns only songs in the specified range | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 69 | `search_byBpmMinOnly_returnsOnlySongsAboveMin` | GET /search BPM minimum filter returns only songs above the minimum | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 70 | `search_byYear_returnsOnlyMatchingYear` | GET /search year filter returns only songs from the specified year | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 71 | `search_combinedFilters_returnsCorrectSubset` | GET /search combined filters return the correct subset of songs | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 72 | `search_responseContainsExpectedFields` | GET /search response body contains all expected fields | search-service | JUnit 5 + Spring Boot Test + Testcontainers (OpenSearch) | `SearchControllerIT.java` |
| 73 | `getHistory_returns401_withoutJWT` | GET /analytics/me/history without JWT returns 401 | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 74 | `getHistory_returns401_withInvalidJWT` | GET /analytics/me/history with invalid JWT returns 401 | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 75 | `getGlobalCharts_returns401_withoutJWT` | GET /analytics/charts/global without JWT returns 401 | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 76 | `getGlobalCharts_returns401_withInvalidJWT` | GET /analytics/charts/global with invalid JWT returns 401 | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 77 | `getHistory_returns200_emptyArray_forNewUser` | GET /analytics/me/history returns 200 with empty array for a new user | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 78 | `getGlobalCharts_returns200_withValidJWT` | GET /analytics/charts/global returns 200 with a valid JWT | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 79 | `getHistory_returnsEntry_afterKafkaPlaybackEvent` | GET /analytics/me/history returns an entry after a Kafka playback event | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 80 | `getHistory_allEventTypesRecorded` | GET /analytics/me/history records all playback event types | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 81 | `getHistory_isolatesEventsByUser` | GET /analytics/me/history isolates events per user | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 82 | `getGlobalCharts_returnsHigherRankForMorePlayedSong` | GET /analytics/charts/global ranks more-played songs higher | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 83 | `getGlobalCharts_doesNotCountSkippedEventsAsPlays` | GET /analytics/charts/global does not count skipped events | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 84 | `getGlobalCharts_responseHasExpectedFields` | GET /analytics/charts/global response contains expected fields | analytics-service | JUnit 5 + Spring Boot Test + Testcontainers (ClickHouse) + EmbeddedKafka | `AnalyticsControllerIT.java` |
| 85 | `dailyMix_returns401_withoutJwt` | GET /recommend/daily-mix without JWT returns 401 | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 86 | `similar_returns401_withoutJwt` | GET /recommend/similar/:id without JWT returns 401 | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 87 | `dailyMix_returns200_withNoHistory` | GET /recommend/daily-mix returns 200 when the user has no play history | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 88 | `dailyMix_returns200_withHistory` | GET /recommend/daily-mix returns 200 when the user has play history | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 89 | `similar_returns200_existingSong` | GET /recommend/similar/:id returns 200 for an existing song | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 90 | `similar_returns200_unknownSong_fallbackNonEmpty` | GET /recommend/similar/:id returns 200 non-empty fallback for unknown song | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 91 | `similar_returns400_forNonNumericSongId` | GET /recommend/similar/:id returns 400 for non-numeric ID | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 92 | `kafkaConsumer_persistsPlayStartedEvent` | Kafka consumer persists play started events to PostgreSQL | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 93 | `kafkaConsumer_ignoresNonStartedEvents` | Kafka consumer ignores non-started playback event types | recommendation-service | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL + Redis) + EmbeddedKafka | `RecommendationControllerIT.java` |
| 94 | `getNotifications_noJwt_returns401` | GET /notifications without JWT returns 401 | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |
| 95 | `getNotifications_validJwt_returnsEmpty_whenNoNotifications` | GET /notifications with valid JWT returns empty list when no notifications exist | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |
| 96 | `kafkaConsumer_playlistCreated_storesNotification` | Kafka consumer creates and stores a notification for PLAYLIST_CREATED event | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |
| 97 | `kafkaConsumer_trackAdded_storesNotification` | Kafka consumer creates and stores a notification for TRACK_ADDED event | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |
| 98 | `kafkaConsumer_notificationsFiltered_byUserId` | Notifications are stored and retrieved in isolation per user ID | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |
| 99 | `getNotifications_returnsNewestFirst` | GET /notifications returns notifications in newest-first order | notification-service | JUnit 5 + Spring Boot Test + Testcontainers (MongoDB) + EmbeddedKafka | `NotificationControllerIT.java` |

---

## Backend Validation Commands

Run these from the repository root. Each service requires Docker (for Testcontainers) and Java 21 + Maven.

```bash
# Run tests for a single service (replace <service-name> as needed)
cd services/<service-name> && mvn verify

# Run all backend services sequentially
for svc in auth-service catalog-service streaming-service playlist-service \
           search-service analytics-service recommendation-service notification-service; do
  echo "=== Testing $svc ==="
  (cd services/$svc && mvn verify)
done

# Run only unit tests (no Testcontainers required)
cd services/<service-name> && mvn test -Dtest="**/unit/**"

# Run only integration tests
cd services/<service-name> && mvn verify -Dit.test="**/integration/**" -Dsurefire.failIfNoSpecifiedTests=false

# Generate JaCoCo coverage report (plugin now configured in all pom.xml files)
cd services/<service-name> && mvn verify
# HTML report: services/<service-name>/target/site/jacoco/index.html
# CSV  report: services/<service-name>/target/site/jacoco/jacoco.csv
```

---

## Infrastructure Confidence Commands

These commands validate that the Docker Compose stack starts correctly and all services are healthy before running E2E tests.

```bash
# Start full stack (build all images first)
docker-compose up --build -d

# Check all container statuses
docker-compose ps

# Health-check all 8 services via Spring Boot Actuator
for port in 8081 8082 8083 8084 8085 8086 8087 8088; do
  echo -n "Port $port: "; curl -s http://localhost:$port/actuator/health | jq -r '.status' 2>/dev/null || echo "UNREACHABLE"
done

# Verify named Docker network exists and all services are on it
docker network inspect music-net --format '{{range .Containers}}{{.Name}} {{end}}'

# Verify Prometheus is scraping metrics
curl -s http://localhost:9090/-/ready
curl -s "http://localhost:9090/api/v1/targets" | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# Verify Grafana is reachable
curl -s http://localhost:3001/api/health | jq '.database'

# Spot-check Prometheus metrics from a service
curl -s http://localhost:8081/actuator/prometheus | grep 'http_server_requests'

# Verify Kafka is reachable and key topic exists
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic playback-events

# Run E2E tests against the running stack
cd e2e-tests && mvn verify

# Run load generator (separate Docker Compose profile)
docker-compose --profile load-test up load-generator

# Tear down stack and volumes when done
docker-compose down -v
```

---

## Test Infrastructure Notes

- **Testcontainers:** Integration tests for each service spin up the required database or broker container automatically. No external test infrastructure is needed beyond a running Docker daemon.
- **JWT in tests:** Each service's integration test suite includes a `JwtTestHelper` (or `TestKeys`) class that generates an in-memory RSA key pair to sign tokens for protected endpoint tests. These keys are independent from the production shared key.
- **Kafka in integration tests:** Services that consume Kafka events use `@EmbeddedKafka` (Spring Kafka test support) with static initialization to ensure the broker is started before `@DynamicPropertySource` resolves.
- **Kafka topic in production:** The authoritative topic name for playback events is `playback-events` (aligns all producers and consumers).
- **E2E test isolation:** `FullUserJourneyIT` and individual flow tests use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` to control execution sequence, since some tests depend on state set up by earlier steps.
