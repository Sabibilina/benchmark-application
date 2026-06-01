## Prompt 1 — Plan

Use `ARCHITECTURE.md` as the source of truth for system shape and service topology. Use `SCALABILITY.md` for capacity targets. Use the source code and integration tests of all 8 microservices as the authoritative reference for actual API contracts, response field names, and Kafka event shapes — these take precedence over `ARCHITECTURE.md` if they conflict. If any unresolvable conflict arises, stop and report it rather than guessing.

The integration tests are located at:

- auth-service: `services/auth-service/src/test/java/com/musicstreaming/auth/integration/AuthControllerIT.java`
- catalog-service: `services/catalog-service/src/test/java/com/musicstreaming/catalog/integration/CatalogControllerIT.java`
- streaming-service: `services/streaming-service/src/test/java/com/musicstreaming/streaming/integration/StreamingControllerIT.java`
- search-service: `services/search-service/src/test/java/com/musicstreaming/search/integration/SearchControllerIT.java`
- analytics-service: `services/analytics-service/src/test/java/com/musicstreaming/analytics/integration/AnalyticsControllerIT.java`
- recommendation-service: `services/recommendation-service/src/test/java/com/musicstreaming/recommendation/integration/RecommendationControllerIT.java`
- playlist-service: `services/playlist-service/src/test/java/com/musicstreaming/playlist/integration/PlaylistControllerIT.java`
- notification-service: `services/notification-service/src/test/java/com/musicstreaming/notification/integration/NotificationControllerIT.java`

Read these files before planning. The load test must exercise the real API contracts, not assumed ones.

---

**Context**

The load generator must stress the system against the per-service RPS targets established in the scaling plan:

- Playback events: ~40 000 eps (peak) — streaming-service → Kafka → analytics-service, recommendation-service
- Catalog + search: ~4 000 rps (peak) — catalog-service, search-service
- Auth logins: ~500 rps (burst) — auth-service
- Playlist mutations: ~200 rps — playlist-service
- Recommendations: ~400 rps (~20 % of active users) — recommendation-service

The peak concurrent user count is 20 000 (20 % of 100 K DAU). All traffic routes through `nginx-lb`.

Known API facts from the integration tests (use these exactly — do not guess):

- `POST /auth/register` → 201; `POST /auth/login` → 200; response body field is `token` (not `access_token`)
- `GET /catalog/songs?page=&size=` → `PagedResponse` with fields `content, page, size, totalElements, totalPages, last`; song items contain both `id` and `trackId`; size capped at 100
- `GET /stream/{songId}` → HLS manifest (M3U8); `GET /stream/{songId}/segment/{index}` → binary; `POST /stream/{songId}/complete` → 204; `POST /stream/{songId}/skip` → 204
- `GET /search?q=&genre=&bpm_min=&bpm_max=&year=` → array of results; field is `bpm` (not `tempo`); result items carry `songId` and `trackId`
- `GET /analytics/me/history` → array of `{ songId, eventType, timestamp }`; `GET /analytics/charts/global` → array of `{ rank, songId, playCount }`
- `GET /recommend/daily-mix` → `{ songs: [...] }`; `GET /recommend/similar/{songId}` → `{ seedSongId, songs: [...] }`
- `GET /playlists` → array (auto-creates "Liked Songs" on first call); `POST /playlists` → 201, `id` is UUID; `POST /playlists/{id}/tracks` → 201, body `{ songId }`
- `GET /notifications` → array of `{ id, type, title, message, referenceId, read, createdAt }`, newest first
- Kafka topics: `playback-events` (streaming → analytics, recommendation); `playlist-events` (playlist → notification)
- Kafka event types: `play.started`, `play.ended`, `play.skipped`; `PLAYLIST_CREATED`, `TRACK_ADDED`

---

**Deliverables for this plan (no code yet)**

1. **Scenario architecture** — Design k6 scenarios that use `constant-arrival-rate` or `ramping-arrival-rate` executors (not VU-based) so each traffic lane targets the RPS figures above independently. Specify `rate`, `timeUnit`, `preAllocatedVUs`, and `maxVUs` for each.

2. **Test phases** — Define three phases all scenarios run through:
   - *Warm-up*: ramp from 0 to 20 % of target over 5 min (validates the system starts clean)
   - *Steady state*: hold at 100 % of target for 15 min (measures sustained throughput)
   - *Peak burst*: spike auth to 3× target for 2 min, hold everything else (validates auth-service headroom)

3. **File tree** — List every file to be created under `load-generator/`.

4. **Custom metrics** — List every `Counter`, `Rate`, `Trend`, and `Gauge` that must be collected, in the LOAD.md file, including:
   - Kafka consumer lag (via polling the analytics-service `/actuator/metrics` or a Kafka admin endpoint)
   - Per-service p50/p95/p99 latency trends
   - Streaming error rate separately from global `http_req_failed`

5. **SLO thresholds** — Define k6 `thresholds` that encode the pass/fail criteria derived from the capacity assumptions. At minimum:
   - Streaming manifest p99 < 2 s at steady state
   - Search p99 < 1 s at steady state
   - Auth p99 < 500 ms outside burst window
   - Global error rate < 1 %
   - Kafka consumer lag does not grow monotonically during steady state

6. **Seed data requirement** — Arrival-rate executors need a pre-existing user and song corpus (registration at test-start does not scale to 40 K eps). Specify what seed data is required, how it should be generated (a dedicated k6 `setup()` function or a separate seed script), and how many users/songs are needed to produce realistic IDs. Account for the fact that `GET /catalog/songs` is paginated and size is capped at 100.

7. **Dependencies** — List any k6 extensions (`xk6-*`) or Docker image changes required.

8. **Env vars** — List all environment variables the scripts consume. Mark which already exist in `load-generator/.env.example`.

9. **Validation steps** — Describe how to run a smoke pass (`K6_SCENARIO=smoke`) to confirm the scripts start without errors before running the full suite.

10. **Missing information** — List anything that cannot be determined from the source files rather than inventing behavior.

Record all planning decisions in `DECISIONS.md` (what, why, which document justified it, affected files). Check the relevant boxes in `PROGRESS.md`.

Do not generate code.

---

## Prompt 2 — Execute

Generate the complete, runnable load-test implementation exactly as approved in the plan. Use the integration tests and source code of all 8 services (paths listed in Prompt 1) as the authoritative API reference, and `SCALABILITY.md` for RPS targets.

**All files must be complete — no pseudocode, no `// TODO`, no placeholder functions.**

---

**Generate the following:**

1. **`load-generator/scripts/main.js`**
   - Separate k6 `export` functions for each traffic lane: `streamingFlow`, `catalogSearchFlow`, `authBurstFlow`, `playlistFlow`, `recommendFlow`.
   - Arrival-rate executor config for each scenario in the `options.scenarios` block; the `exec` field maps each scenario to the appropriate function.
   - A `setup()` function that seeds users and collects song IDs from the real catalog (`/catalog/songs` paginated responses, using `content[].id`), returning a shared data object passed to every VU. Must not rely on registering users inside each VU iteration.
   - All custom metrics from the plan (Counters, Rates, Trends, Gauges).
   - All SLO thresholds from the plan in the `options.thresholds` block.
   - Per-request `tags: { endpoint, service }` on every `http.*` call so Prometheus labels are meaningful.
   - Realistic think-time between requests (non-uniform distribution, not a fixed sleep).
   - Use the exact field names found in the integration tests: `token` from auth, `content[].id` from catalog, `bpm` from search, `songId` in playlist track body, etc.

2. **`load-generator/scripts/seed.js`** (if the plan calls for a separate seed script)
   - Registers N users and collects song IDs by paginating through `/catalog/songs`.
   - Writes `data/seed.json`; `main.js` `setup()` reads this file.
   - Designed to run once before the main test (`k6 run seed.js`).

3. **`load-generator/scripts/kafka-lag-check.js`** (if the plan requires Kafka lag polling)
   - Polls the analytics-service actuator or a Kafka admin endpoint for consumer group lag on `playback-events`.
   - Emits a k6 `Gauge` metric; threshold fails if lag exceeds the value from the plan.

4. **`load-generator/Dockerfile`** — create or update for the required base image or extensions.

5. **`load-generator/.env.example`** — add any new env vars identified in the plan.

6. **`load-generator/README.md`** — short (< 60 lines) runbook: how to run smoke, steady-state, and peak-burst tests; how to interpret results; what each scenario covers.

After generating, update `PROGRESS.md` and `DECISIONS.md` to record what was completed, every assumption made, and every decision taken during generation.

---

## Prompt 3 — Validate / Fix

Review the generated load-test implementation against the integration tests and source code of all 8 services (paths listed in Prompt 1) and the approved plan. Fix any gaps. Output only changed files.

---

**Validate the following:**

1. **Scenario coverage** — Confirm a scenario exists for each traffic lane (streaming, catalog/search, auth burst, playlist, recommend) plus a smoke scenario. Confirm each uses an arrival-rate executor, not a VU-based one.

2. **RPS targets** — Verify the `rate` + `timeUnit` values in each scenario match `SCALABILITY.md` (40 K eps streaming, 4 K rps catalog+search, 500 rps auth, 200 rps playlist). Flag any that are off by more than 10 %.

3. **API correctness** — Cross-check every endpoint path, HTTP method, request body shape, and response field name against the integration tests. Flag any mismatch — the load test must exercise the real API, not an assumed one. Key checks:
   - Auth: response field is `token`, not `access_token`
   - Catalog: pagination via `?page=&size=`, song IDs extracted from `content[].id`
   - Streaming: `POST /stream/{songId}/complete` → 204 (not 200)
   - Search: query param is `bpm_min`/`bpm_max`, result field is `bpm`
   - Playlist: track add body is `{ songId }`, response status 201
   - All services: 401 is expected for missing/invalid tokens; the test must not treat these as errors when testing auth failure paths

4. **Thresholds** — Confirm all five SLO thresholds from the plan are present and correctly scoped (tagged thresholds where per-service filtering is required). A missing or misconfigured threshold means the test cannot signal a regression.

5. **Seed / setup** — Confirm `setup()` (or `seed.js`) runs without auth errors, returns a non-empty user array and song-ID array, and that every VU flow reads from the shared data object rather than registering at test-start.

6. **Kafka lag metric** — Confirm the lag-check produces a k6 `Gauge` metric and that the threshold fails if lag grows unbounded during steady state.

7. **Tag completeness** — Grep every `http.get` / `http.post` / `http.put` / `http.del` call and confirm each carries `tags: { endpoint: '...', service: '...' }` so Prometheus scrape labels are meaningful.

8. **Smoke run** — Execute `k6 run --env K6_SCENARIO=smoke --env NGINX_LB_URL=http://localhost scripts/main.js` (or the Docker equivalent) against a running local stack. The run must complete with exit code 0, all checks green, and no threshold violations before the phase is considered valid.

9. **Lint / syntax** — Run `k6 inspect scripts/main.js` to catch import errors or undefined references without requiring a live cluster.

**Fix rules:**
- Fix missing or incorrect parts; output only the changed files.
- Do not modify files unrelated to the load test.
- State every assumption explicitly.
- Prefer full-file regeneration over a patch if the scenario structure or API contract is wrong.
- Do not mark the phase complete if the smoke run fails, any threshold is absent, or any API field name is wrong.

Before closing, update `PROGRESS.md` to mark all validated checklist items and update `DECISIONS.md` to record every fix, deviation, or unresolved issue found during validation, and `LOAD.md`.

---

## Prompt 4 — Test

Execute the full load-test suite against the running application stack, collect all metrics produced during the run, and write a structured report to LOAD-RESULTS.md.