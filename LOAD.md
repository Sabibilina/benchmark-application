# Load Test Plan — Music Streaming Benchmark

> **Source of truth hierarchy**: integration tests > source code > ARCHITECTURE.md.
> All API shapes in this document are verified against the integration tests listed in the
> header of each section. No endpoint shape is assumed.

---

## Table of Contents

1. [Scenario Architecture](#1-scenario-architecture)
2. [Test Phases](#2-test-phases)
3. [File Tree](#3-file-tree)
4. [Custom Metrics](#4-custom-metrics)
5. [SLO Thresholds](#5-slo-thresholds)
6. [Seed Data Requirement](#6-seed-data-requirement)
7. [Dependencies](#7-dependencies)
8. [Environment Variables](#8-environment-variables)
9. [Validation Steps](#9-validation-steps)
10. [Missing Information and Blockers](#10-missing-information-and-blockers)

---

## 1. Scenario Architecture

Each traffic lane runs as an independent k6 scenario with a `ramping-arrival-rate` executor so
that the rate can be shaped across warm-up, steady-state, and burst phases independently.
Scenarios share a single main.js entry point but call separate `exec` functions populated from
a common SharedArray of pre-seeded user tokens and song IDs (see §6).

All traffic enters through `nginx-lb:80`. Scenario names match the `K6_SCENARIOS` env var
filter (comma-separated list or `all`).

### Scenario table

| Scenario ID | Target services | Peak rate | Executor |
|---|---|---|---|
| `streaming_playback` | streaming-service → Kafka → analytics + recommendation | 20 000 iter/s (= 40 K eps at 2 events/iter) | `ramping-arrival-rate` |
| `catalog_search` | catalog-service, search-service | 4 000 req/s | `ramping-arrival-rate` |
| `auth_login` | auth-service | 500 req/s (burst: 1 500 req/s) | `ramping-arrival-rate` |
| `playlist_mutations` | playlist-service | 200 req/s | `ramping-arrival-rate` |
| `recommendations` | recommendation-service | 400 req/s | `ramping-arrival-rate` |

### Executor parameters per scenario

> VU formula: `preAllocatedVUs = rate_at_peak × p50_latency_seconds × 2.0 (safety factor)`.
> `maxVUs` is 4× preAllocated to absorb latency spikes to p99.

#### `streaming_playback`

```
executor:           ramping-arrival-rate
startRate:          0
timeUnit:           1s
preAllocatedVUs:    2 000    # 20 000 × 0.05s p50 × 2.0
maxVUs:             8 000
stages:             see §2
exec:               streamingPlaybackFlow
```

Each iteration of `streamingPlaybackFlow` issues:
1. `GET /stream/{songId}` — emits `play.started` to Kafka (1 event)
2. `GET /stream/{songId}/segment/0` — binary payload, no Kafka event
3. `POST /stream/{songId}/complete` or `/skip` (80/20 split) — emits `play.ended`/`play.skipped` (1 event)

Total Kafka events per iteration = 2. At 20 000 iter/s → 40 000 eps.

> **Blocker — single-instance throughput**: A single k6 process reliably generates
> ≤ 5 000 iter/s for HTTP scenarios. Reaching 20 000 iter/s requires either distributed k6
> (≥ 4 instances behind a shared `--out` collector) or accepting a practical ceiling.
> See §10, item B-1.

> **Blocker — nginx-lb rate limit**: `/stream/` is rate-limited to 200 r/s per source IP
> (burst 1 000, nodelay). From a single k6 IP, requests beyond the burst are rejected 503.
> The nginx-lb `stream_rl` zone must be raised for load testing, or k6 must run from
> multiple IPs. See §10, item B-2.

#### `catalog_search`

```
executor:           ramping-arrival-rate
startRate:          0
timeUnit:           1s
preAllocatedVUs:    1 200    # 4 000 × 0.15s p50 × 2.0
maxVUs:             4 800
stages:             see §2
exec:               catalogSearchFlow
```

Each iteration alternates 60/40 between:
- `GET /catalog/songs?page={0..9}&size=50` (catalog-service)
- `GET /search?q={genre}&genre={genre}` (search-service)

> **Note**: The `/api/` prefix does NOT appear in nginx-lb routing. Paths are `/catalog/`,
> `/search`, `/stream/`, `/auth/`, `/playlists`, `/analytics/`, `/recommend/`, `/notifications`.
> Confirmed from `infra/nginx-lb/nginx.conf`.

> **Nginx rate limit**: `api_rl` zone is 500 r/s/IP (burst 1 000). At 4 000 req/s from a
> single IP the burst is exhausted in < 1 s. Raise to ≥ 5 000 r/s for load testing.
> See §10, item B-2.

#### `auth_login`

```
executor:           ramping-arrival-rate
startRate:          0
timeUnit:           1s
preAllocatedVUs:    300      # 500 × 0.3s p50 × 2.0
maxVUs:             1 800    # headroom for 3× burst (1 500 req/s)
stages:             see §2
exec:               authLoginFlow
```

Each iteration: `POST /auth/login` using credentials from the pre-seeded user corpus.
Tokens are NOT re-issued per iteration (stored in SharedArray from setup()).

> **Nginx rate limit**: `auth_rl` zone is 20 r/s/IP (burst 60). The 500 req/s steady-state
> target is 25× the per-IP rate limit. This is the most critical blocker.
> See §10, item B-2.

#### `playlist_mutations`

```
executor:           ramping-arrival-rate
startRate:          0
timeUnit:           1s
preAllocatedVUs:    60       # 200 × 0.15s p50 × 2.0
maxVUs:             240
stages:             see §2
exec:               playlistMutationFlow
```

Each iteration (varies by `__ITER % 3`):
- `POST /playlists/{id}/tracks` (body: `{ "songId": "..." }`) — produces TRACK_ADDED Kafka event
- `GET /playlists` — returns array including auto-created "Liked Songs"
- `GET /playlists/{id}` — returns playlist with `tracks` array

Playlist IDs are acquired once per VU in VU init from the SharedArray seed.

#### `recommendations`

```
executor:           ramping-arrival-rate
startRate:          0
timeUnit:           1s
preAllocatedVUs:    80       # 400 × 0.1s p50 × 2.0
maxVUs:             320
stages:             see §2
exec:               recommendationFlow
```

Each iteration alternates:
- `GET /recommend/daily-mix` → `{ songs: [...] }`
- `GET /recommend/similar/{numericSongId}` → `{ seedSongId, songs: [...] }` using numeric IDs from seed

> **Critical**: `/recommend/similar/{songId}` requires a **numeric (Long) ID** from the
> recommendation-service's own songs table. Non-numeric IDs return 400. Seed data must
> include numeric IDs valid for this service (see §6, point 3).

---

## 2. Test Phases

All scenarios run through three phases. Phases are encoded as `stages` in the
`ramping-arrival-rate` executor. The auth scenario adds a fourth stage for the burst.

### Phase durations

| Phase | Duration | Rate (auth) | Rate (all others) |
|---|---|---|---|
| Warm-up | 5 min | 0 → 100 req/s (20 % of 500) | 0 → 20 % of peak |
| Steady state | 15 min | 100 → 500 req/s | hold at 100 % |
| Peak burst | 2 min | 500 → 1 500 req/s (3×) | hold at 100 % |
| Ramp-down | 3 min | 1 500 → 0 | 100 % → 0 |

Total test duration: **25 minutes** per run.

### Stage definitions per scenario

#### `streaming_playback` and `catalog_search` (hold during auth burst)

```javascript
stages: [
  { duration: '5m',  target: Math.floor(peakRate * 0.20) }, // warm-up
  { duration: '15m', target: peakRate },                     // steady state
  { duration: '2m',  target: peakRate },                     // hold during auth burst
  { duration: '3m',  target: 0 },                            // ramp-down
]
```

#### `auth_login` (including burst)

```javascript
stages: [
  { duration: '5m',  target: 100  },  // warm-up to 20 %
  { duration: '15m', target: 500  },  // steady state
  { duration: '2m',  target: 1500 },  // 3× burst
  { duration: '3m',  target: 0    },  // ramp-down
]
```

#### `playlist_mutations` and `recommendations`

Same structure as streaming/catalog — hold at 100 % of their peak during the auth burst.

### Phase tagging

Each k6 VU tracks which phase it is in by checking elapsed time via `Date.now()` relative to
the test start (stored in a shared module-level variable set during `setup()`). Phase labels
`warmup`, `steady`, `burst`, `rampdown` are added as `tags: { phase }` on every HTTP call.
This enables threshold evaluation per phase (e.g., auth p99 < 500 ms tagged `phase:steady`
only, not `phase:burst`).

---

## 3. File Tree

All files to be created or modified under `load-generator/`:

```
load-generator/
├── Dockerfile                          # Update: bump to k6 ≥ 0.51.0; no new xk6 extensions required
├── .env.example                        # Update: add all new env vars listed in §8
│
├── scripts/
│   ├── main.js                         # REPLACE: arrival-rate orchestrator; imports scenarios; defines options + thresholds
│   ├── metrics.js                      # NEW: all custom Counter / Rate / Trend / Gauge declarations (imported by main.js)
│   ├── thresholds.js                   # NEW: SLO threshold map (imported by main.js)
│   │
│   ├── scenarios/
│   │   ├── auth.js                     # NEW: authLoginFlow(); POST /auth/login using seed tokens
│   │   ├── catalog.js                  # NEW: catalogSearchFlow(); GET /catalog/songs + GET /search alternation
│   │   ├── streaming.js                # NEW: streamingPlaybackFlow(); GET manifest + segment + POST complete/skip
│   │   ├── playlist.js                 # NEW: playlistMutationFlow(); GET playlists + POST tracks
│   │   └── recommendations.js          # NEW: recommendationFlow(); daily-mix + similar alternation
│   │
│   ├── lib/
│   │   ├── headers.js                  # NEW: makeAuthHeaders(token); constant Content-Type + Accept helpers
│   │   ├── tags.js                     # NEW: tag factories for per-phase and per-endpoint labelling
│   │   └── kafka-lag.js                # NEW: pollKafkaLag(exporterUrl); parse kafka-exporter /metrics text
│   │
│   └── setup/
│       ├── setup.js                    # NEW: setup() exported function; runs seed.js logic, returns SharedArray data
│       └── teardown.js                 # NEW: teardown() logs final Kafka lag snapshot and summary stats
│
└── seed/
    ├── seed-users.js                   # NEW: standalone k6 script; registers SEED_USER_COUNT users; writes seed-data.json
    ├── seed-data.json                  # GENERATED (not committed): { users: [{userId, email, password, token}], songIds: [...], numericSongIds: [...] }
    └── README.md                       # NEW: how to run the seed before the main test
```

> The `seed-data.json` file is generated by running `seed-users.js` once before the main
> test. It is excluded from version control (add to `.gitignore`). The `main.js` `setup()`
> function loads it via `open('../seed/seed-data.json')` and issues fresh login requests to
> obtain non-expired tokens. Seed-then-login approach avoids token expiry problems.

---

## 4. Custom Metrics

### Counters

| Name | What it counts | Increment condition |
|---|---|---|
| `streaming_events_sent` | Kafka events triggered via HTTP | +2 per completed streaming iteration (1 play.started + 1 play.ended/skipped) |
| `streaming_manifest_errors` | GET /stream/{id} non-200 responses | Each failure |
| `streaming_complete_errors` | POST /stream/{id}/complete or /skip non-204 | Each failure |
| `auth_login_errors` | POST /auth/login non-200 | Each failure |
| `auth_register_errors` | POST /auth/register non-201 (seed phase) | Each failure |
| `playlist_track_add_errors` | POST /playlists/{id}/tracks non-201 | Each failure |
| `kafka_lag_poll_errors` | Failed HTTP GET to kafka-exporter | Each failure |
| `dropped_iterations` | k6 internal dropped_iterations counter alias | VU pool exhausted |

### Rates

| Name | Formula | Purpose |
|---|---|---|
| `streaming_error_rate` | streaming errors / streaming requests | Separate from global `http_req_failed` |
| `auth_error_rate` | auth errors / auth requests | Isolates auth failures |
| `catalog_error_rate` | catalog errors / catalog requests | Isolates catalog failures |
| `search_error_rate` | search errors / search requests | Isolates search failures |

### Trends (per-service p50 / p95 / p99 latency)

| Name | Endpoint(s) measured |
|---|---|
| `streaming_manifest_duration_ms` | `GET /stream/{songId}` response time |
| `streaming_segment_duration_ms` | `GET /stream/{songId}/segment/{index}` response time |
| `streaming_complete_duration_ms` | `POST /stream/{songId}/complete` and `/skip` response time |
| `catalog_browse_duration_ms` | `GET /catalog/songs?page=&size=` response time |
| `search_query_duration_ms` | `GET /search?q=&genre=&bpm_min=&bpm_max=&year=` response time |
| `auth_login_duration_ms` | `POST /auth/login` response time |
| `auth_register_duration_ms` | `POST /auth/register` (seed phase only) response time |
| `playlist_list_duration_ms` | `GET /playlists` response time |
| `playlist_add_track_duration_ms` | `POST /playlists/{id}/tracks` response time |
| `recommendation_daily_mix_duration_ms` | `GET /recommend/daily-mix` response time |
| `recommendation_similar_duration_ms` | `GET /recommend/similar/{numericSongId}` response time |
| `analytics_history_duration_ms` | `GET /analytics/me/history` response time |
| `notification_list_duration_ms` | `GET /notifications` response time |

All Trends record raw milliseconds and k6 automatically computes p50/p95/p99 in the summary
output. Thresholds reference these Trend names directly (e.g.,
`'streaming_manifest_duration_ms': ['p(99)<2000']`).

### Gauges (Kafka consumer lag, polled during the test)

Polled every 30 seconds from `GET ${KAFKA_EXPORTER_URL}/metrics` (Prometheus text format)
inside a `group('kafka_lag_poll')` call in the `streaming_playback` scenario's `exec`
function.

| Gauge name | Kafka metric parsed | Consumer group |
|---|---|---|
| `kafka_lag_analytics` | `kafka_consumergroup_lag{consumergroup="analytics-service",topic="playback-events"}` | analytics-service |
| `kafka_lag_recommendation` | `kafka_consumergroup_lag{consumergroup="recommendation-service",topic="playback-events"}` | recommendation-service |
| `kafka_lag_notification` | `kafka_consumergroup_lag{consumergroup="notification-service",topic="playlist-events"}` | notification-service |

Parsing logic (in `lib/kafka-lag.js`): HTTP GET the Prometheus text endpoint, extract lines
matching the metric name and label set with a regex, parse the numeric value from the last
whitespace-separated token. Returns `NaN` on parse failure, which increments
`kafka_lag_poll_errors`.

> Kafka consumer lag does not grow monotonically validation: the `teardown()` function
> compares lag snapshots at the start and end of the steady-state window. If end lag >
> start lag + tolerance (default 5 000 messages), it logs a failing check. This cannot be
> expressed as a k6 `threshold` (which aggregates over the full run) but is captured as a
> `check()` result that appears in the summary JSON.

---

## 5. SLO Thresholds

Thresholds are expressed as k6 `thresholds` entries applied to named metrics. Failure of any
threshold marks the overall test run as failed (non-zero exit code).

```javascript
// load-generator/scripts/thresholds.js — authoritative list

export const THRESHOLDS = {

  // ── Global ─────────────────────────────────────────────────────────────────
  'http_req_failed':                 ['rate<0.01'],   // global error rate < 1 %

  // ── Streaming ──────────────────────────────────────────────────────────────
  // p99 < 2 s at steady state (phase:steady tag applied to metric)
  'streaming_manifest_duration_ms':  ['p(99)<2000'],
  'streaming_complete_duration_ms':  ['p(99)<3000'],  // Kafka send included
  'streaming_error_rate':            ['rate<0.01'],

  // ── Search ─────────────────────────────────────────────────────────────────
  'search_query_duration_ms':        ['p(50)<300', 'p(95)<700', 'p(99)<1000'],

  // ── Auth ───────────────────────────────────────────────────────────────────
  // p99 < 500 ms measured at steady state. The burst window will violate this;
  // the threshold is intentionally not tagged per-phase here because k6 thresholds
  // do not support tag filters. The burst violation is documented and expected.
  // Steady-state compliance is verified via the Grafana `auth_login_duration_ms`
  // panel filtered to phase=steady in the post-run dashboard.
  'auth_login_duration_ms':          ['p(99)<500', 'p(95)<300'],

  // ── Catalog ─────────────────────────────────────────────────────────────────
  'catalog_browse_duration_ms':      ['p(99)<1000'],

  // ── Recommendations ─────────────────────────────────────────────────────────
  'recommendation_daily_mix_duration_ms': ['p(99)<500'],
  'recommendation_similar_duration_ms':   ['p(99)<1000'],

  // ── Playlist ─────────────────────────────────────────────────────────────────
  'playlist_add_track_duration_ms':  ['p(99)<1500'],

  // ── Dropped iterations (VU starvation indicator) ──────────────────────────
  // k6 exposes this as a built-in metric; a high count means preAllocatedVUs is too low
  'dropped_iterations':              ['count<500'],

  // ── Kafka lag (verified in teardown, not a k6 threshold) ──────────────────
  // See teardown.js: end_lag(analytics) < start_lag(analytics) + 5000
  // Cannot be expressed here because Gauge values are not aggregated by k6
};
```

> **Auth burst window caveat**: `auth_login_duration_ms p(99) < 500 ms` will aggregate over
> all 25 min including the 2-min burst at 3× rate. The raw p99 will likely exceed 500 ms
> for the full run if BCrypt is the bottleneck. To evaluate the threshold strictly at
> steady state, filter the k6 output by `tags.phase=="steady"` in the JSON summary or
> Prometheus/Grafana. This is documented here rather than worked around with
> `abortOnFail:false` (which would mask real steady-state failures).

---

## 6. Seed Data Requirement

Arrival-rate executors create iterations at the configured rate regardless of VU lifecycle.
Registering users inside iteration loops at 20 000 iter/s would generate tens of millions of
`POST /auth/register` calls during the test — which is not the intent and would saturate
auth-service. All user credentials and song IDs must be pre-seeded.

### What seed data is required

| Data type | Quantity | Why |
|---|---|---|
| Pre-registered users | 25 000 | Slightly more than 20 K peak concurrent VUs; ensures each VU has a distinct identity |
| Valid JWT tokens | 25 000 | One per user; obtained via `POST /auth/login` in `setup()` using seeded credentials |
| Catalog `trackId` strings | 10 000 | Passed to `/stream/{songId}` (accepts any string) and `/search` scenario seeding |
| Catalog numeric `id` values | 10 000 | Needed for `/catalog/songs/{id}` direct lookups |
| Recommendation-service numeric song IDs | 500–1 000 | Needed for `/recommend/similar/{numericSongId}`; see note below |
| Pre-created playlist UUIDs | 25 000 (one per user) | Playlist mutation scenario needs existing playlist IDs; created in setup() |

### How seed data is generated

**Step 1 — Run `seed/seed-users.js` once before the full test:**

`seed-users.js` is a standalone k6 script run with `k6 run seed/seed-users.js`.
It uses a `ramping-vus` executor (e.g., 500 VUs, 3 min) to register `SEED_USER_COUNT`
(default 25 000) users against `POST /auth/register` in parallel.
It writes output to `seed/seed-data.json` in the format:

```json
{
  "users": [
    { "userId": "...", "username": "seed_0", "email": "seed_0@bench.local", "password": "Bench1234!" }
  ]
}
```

Note: user IDs are not returned by `POST /auth/register` directly (the response is `{ token }`
only, not `{ token, userId }`). The `sub` claim in the JWT is the user's UUID. The seed
script parses the JWT `sub` claim (base64-decode the payload, extract `sub`) to capture the
userId without a separate lookup endpoint.

**Step 2 — `setup()` in main.js:**

`setup()` runs once before all VU iterations begin. It:

1. Reads `seed/seed-data.json` via `open('../seed/seed-data.json')`.
2. Issues `POST /auth/login` in batches to obtain fresh tokens for all 25 000 users
   (necessary because seed-data.json may be days old; JWT expiry unknown — see §10, item B-3).
3. Paginates `GET /catalog/songs?page={0..99}&size=100` to collect up to 10 000 song
   records. Extracts both `trackId` (String) and `id` (Long numeric). Size cap of 100 is
   confirmed by `CatalogControllerIT.getSongs_sizeLargerThanMax_cappedAt100`. Total pages
   needed: 100 (for 10 000 songs).
4. Issues `GET /recommend/similar/1` as a probe. If 200, the recommendation-service has
   songs in its DB and IDs starting from 1 are valid. Otherwise falls back to only calling
   `daily-mix`.
5. Creates one playlist per user via `POST /playlists` for each of the 25 000 users.
   Stores the returned UUID playlist IDs.
6. Returns a `SharedArray`-compatible object:

```javascript
return {
  tokens:         [...],     // 25 000 JWT strings
  userIds:        [...],     // 25 000 userId strings
  trackIds:       [...],     // 10 000 trackId strings (for streaming)
  numericSongIds: [...],     // 10 000 numeric catalog IDs
  recommendIds:   [...],     // ≤ 1 000 numeric recommendation-service IDs
  playlistIds:    [...],     // 25 000 playlist UUIDs
};
```

Each scenario function receives this object as its parameter and accesses it via
`data.tokens[__VU % data.tokens.length]`.

### Setup duration estimate

| Step | Operations | Parallelism | Estimated time |
|---|---|---|---|
| Login 25 000 users | 25 000 × POST /auth/login | 500 VUs | ~50 s at 500 req/s |
| Catalog page fetch | 100 × GET /catalog/songs | 10 VUs | ~10 s |
| Playlist creation | 25 000 × POST /playlists | 500 VUs | ~50 s |
| **Total** | | | **~120 s** |

The k6 `setup()` function has no timeout limit by default; use `setupTimeout: '3m'` in
`options`.

---

## 7. Dependencies

### k6 version

Current Dockerfile uses `grafana/k6:0.51.0`. This version includes:
- `Counter`, `Rate`, `Trend`, `Gauge` (Gauge added in 0.44.0 ✓)
- `SharedArray` from `k6/data`
- `open()` for file reading
- Prometheus remote write output (`--out experimental-prometheus-rw`)

No upgrade required for base functionality. Keep at `0.51.0` unless a bug requires a newer
version.

### k6 extensions (xk6)

No `xk6` extensions are required:

- **Kafka lag polling**: done via `http.get(kafkaExporterUrl + '/metrics')` using standard
  k6 HTTP. No `xk6-kafka` needed.
- **CSV output**: `k6 run --out csv=results.csv` is built-in.
- **Prometheus output**: `k6 run --out experimental-prometheus-rw` is built-in at 0.51.0.

If distributed k6 is implemented (§10, item B-1), the orchestration layer (k6-operator or
`k6 cloud`) introduces its own dependencies outside this repository.

### Docker image changes

The `load-generator/Dockerfile` needs only one change: pass `--out` and other CLI flags via
the `CMD` override in `docker-compose.yml` rather than hardcoding in `ENTRYPOINT`. This
allows different output targets per run without rebuilding the image.

Recommended Dockerfile change:

```dockerfile
FROM grafana/k6:0.51.0
WORKDIR /scripts
COPY scripts/ .
COPY seed/   ../seed/
# ENTRYPOINT stays as k6; CMD provides defaults overridable at runtime
CMD ["run", "/scripts/main.js"]
```

### Infrastructure dependencies

The following services must be running before the load test starts:

- `nginx-lb` — all load test traffic enters here
- `auth-service` — user login during setup()
- `catalog-service` — song ID collection during setup()
- `kafka-exporter` — polled for consumer lag metrics
- All 8 application services — for scenario execution

---

## 8. Environment Variables

### Variables the scripts consume

| Variable | Default | Description | Already in .env.example? |
|---|---|---|---|
| `NGINX_LB_URL` | `http://nginx-lb` | Base URL for all load test traffic | No — **add** |
| `K6_SCENARIOS` | `all` | Comma-separated list of scenario IDs to run, or `all` | No — **add** |
| `K6_SEED_FILE` | `../seed/seed-data.json` | Path to pre-generated seed credentials | No — **add** |
| `SEED_USER_COUNT` | `25000` | Number of users to register in seed script | No — **add** |
| `K6_PHASE_WARMUP_DURATION` | `5m` | Duration of warm-up phase | No — **add** |
| `K6_PHASE_STEADY_DURATION` | `15m` | Duration of steady-state phase | No — **add** |
| `K6_PHASE_BURST_DURATION` | `2m` | Duration of auth burst phase | No — **add** |
| `K6_STREAMING_RATE` | `20000` | Peak streaming iterations per second | No — **add** |
| `K6_CATALOG_RATE` | `4000` | Peak catalog+search requests per second | No — **add** |
| `K6_AUTH_RATE` | `500` | Peak auth requests per second (burst = 3×) | No — **add** |
| `K6_PLAYLIST_RATE` | `200` | Peak playlist mutation requests per second | No — **add** |
| `K6_RECOMMEND_RATE` | `400` | Peak recommendation requests per second | No — **add** |
| `KAFKA_EXPORTER_URL` | `http://kafka-exporter:9308` | Prometheus endpoint for consumer lag polling | No — **add** |
| `K6_OUT` | (empty) | k6 output target, e.g. `experimental-prometheus-rw` | No — **add** |
| `K6_PROMETHEUS_RW_SERVER_URL` | (empty) | Remote write URL if `K6_OUT=experimental-prometheus-rw` | No — **add** |
| `K6_SCENARIO` | `ramp` | **Legacy**: single-scenario selector used by old main.js | Yes — keep for backward compat |
| `K6_VUS` | `10` | **Legacy**: VU count used by old main.js | Yes — keep |
| `K6_DURATION` | `60s` | **Legacy**: duration used by old main.js | Yes — keep |

> Variables prefixed `AUTH_SERVICE_URL`, `CATALOG_SERVICE_URL`, etc. in the current
> `.env.example` are superseded by `NGINX_LB_URL`. They should be retained with a
> comment noting they are for **direct-service debugging only** (bypassing nginx-lb rate
> limits and load distribution).

---

## 9. Validation Steps

### Pre-flight checks before running the full suite

**1. Confirm the stack is healthy:**

```bash
# All 8 services must be healthy before starting
docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep -v "healthy"
# Expected: no output (all services in healthy state)

# Verify nginx-lb routes traffic
curl -sf http://localhost/health
# Expected: {"status":"UP","service":"nginx-lb"}
```

**2. Run the seed script (one-time, before the first full test run):**

```bash
docker compose --profile load-test run --rm load-generator \
  k6 run -e SEED_USER_COUNT=25000 \
         -e NGINX_LB_URL=http://nginx-lb \
         /scripts/seed/seed-users.js

# Verify seed file was written
ls -lh load-generator/seed/seed-data.json
# Expected: file > 1 MB (25 000 user records)
```

**3. Smoke pass to confirm scripts start without errors:**

```bash
docker compose --profile load-test run --rm load-generator \
  k6 run -e K6_SCENARIOS=auth_login,catalog_search,streaming_playback \
         -e K6_STREAMING_RATE=5 \
         -e K6_CATALOG_RATE=5 \
         -e K6_AUTH_RATE=5 \
         -e K6_PLAYLIST_RATE=2 \
         -e K6_RECOMMEND_RATE=5 \
         -e K6_PHASE_WARMUP_DURATION=30s \
         -e K6_PHASE_STEADY_DURATION=1m \
         -e K6_PHASE_BURST_DURATION=30s \
         -e NGINX_LB_URL=http://nginx-lb \
         /scripts/main.js

# Alternatively with K6_SCENARIO=smoke (backward-compatible):
docker compose --profile load-test run --rm load-generator \
  k6 run -e K6_SCENARIO=smoke /scripts/main.js
```

### Expected smoke pass output

| Check | Pass criteria |
|---|---|
| `register 201` | 100 % pass during setup() |
| `login 200` | 100 % pass during setup() |
| `catalog 200` | 100 % pass |
| `stream manifest 200` | 100 % pass |
| `stream segment 200` | 100 % pass |
| `stream complete 204` | 100 % pass |
| `search 200` | 100 % pass |
| `recommend 200` | 100 % pass |
| `playlist 200/201` | 100 % pass |
| `notifications 200` | 100 % pass |
| `http_req_failed` | `rate < 0.01` |
| `dropped_iterations` | `count == 0` |
| k6 exit code | `0` |

A non-zero k6 exit code or any threshold violation during the smoke pass must be
investigated and fixed before running the full 25-minute suite.

### Full suite run command

```bash
docker compose --profile load-test run --rm load-generator \
  k6 run -e K6_SCENARIOS=all \
         -e NGINX_LB_URL=http://nginx-lb \
         -e KAFKA_EXPORTER_URL=http://kafka-exporter:9308 \
         /scripts/main.js
```

---

## 10. Missing Information and Blockers

### Blockers (must be resolved before code generation)

**B-1 — Distributed k6 required for streaming scenario at 20 K iter/s**

A single k6 process reliably generates ≤ 5 000 HTTP iterations per second for network-bound
scenarios. The 20 000 iter/s streaming target requires either:
- At minimum 4 k6 container instances running in parallel against the same nginx-lb
- Each instance uses `--out experimental-prometheus-rw` to push metrics to Prometheus
- Or: accept a practical cap of 5 000 iter/s (10 000 eps) per load-generator container and
  note this in the test report

**Decision needed**: Is a reduced streaming target (5 000 iter/s / 10 000 eps) acceptable for
the benchmark, or is distributed k6 infra required?

**B-2 — nginx-lb per-IP rate limits block target RPS from a single k6 IP**

From `infra/nginx-lb/nginx.conf`:
- `auth_rl`: 20 r/s per IP (burst 60 nodelay) — target is 500 r/s steady + 1 500 r/s burst
- `stream_rl`: 200 r/s per IP (burst 1 000 nodelay) — target is ≥ 3 000 r/s
- `api_rl`: 500 r/s per IP (burst 1 000 nodelay) — target is 4 000 r/s

From a single k6 container IP, auth requests at 500 r/s will trigger HTTP 503 after the 60
burst is exhausted (< 1 second). The same applies to streaming and api zones.

**Resolution options (pick one before code generation):**
1. Add a `load-test` nginx-lb profile with higher rate limits (`auth_rl: 2000r/s`, `stream_rl: 25000r/s`, `api_rl: 10000r/s`)
2. Run k6 from multiple IPs (distributed k6) to distribute per-IP traffic
3. Accept that rate-limited 503s are part of the test baseline (realistic user-facing behavior)

**B-3 — JWT token TTL is unknown**

The seed approach requires pre-obtained JWT tokens to remain valid for the entire test
duration (~25 min). If the auth-service default JWT TTL is less than 30 minutes, setup()
tokens will expire mid-test.

Source to check: `services/auth-service/src/main/resources/application.yml` — look for
`jwt.expiration` or equivalent property. If TTL < 30 min, either:
- Increase TTL via env var for load testing
- Implement token refresh logic in the scenario functions

### Open questions (cannot be determined from source files)

**Q-1 — Recommendation-service song IDs do not align with catalog IDs**

`GET /recommend/similar/{songId}` requires a numeric `id` from the recommendation-service's
own PostgreSQL `songs` table (confirmed by `RecommendationControllerIT`: it calls
`songRepo.save()` and uses `seed.getId()`). These IDs are auto-generated by that service's
own DB sequence.

It is unknown whether recommendation-service pre-seeds its songs table from the same Kaggle
CSV at startup (which would mean IDs are assigned sequentially and may align with catalog IDs)
or whether it only receives song metadata via Kafka events.

If IDs do not align, the seed setup() cannot reliably acquire valid IDs for `/recommend/similar`.
**Resolution**: check the recommendation-service's startup logic (DataSeeder or equivalent).
If no pre-seeding exists, call `GET /recommend/daily-mix`, extract a `songs[0].id` from the
response, and use that as the seed for `similar` calls.

**Q-2 — Search response includes `songId` field or not?**

The task brief states "result items carry songId and trackId". `SearchControllerIT`
`search_responseContainsExpectedFields` only asserts `trackId` (not `songId`). The `SongDocument`
model in the test fixture has a `songId` field (`d.setSongId(id)`) so it should appear in the
JSON response, but the field name in serialization is not confirmed.

The `catalogSearchFlow` scenario should extract IDs via `s.trackId || s.songId` (string trackId
preferred; numeric songId as fallback) to match the pattern used in the existing main.js.

**Q-3 — Auth login request body: `email` or `username`?**

`AuthControllerIT.login_validCredentials_returns200WithToken` calls
`login("alice@example.com", "password123")` which posts `{ email, password }`. The existing
main.js sends `{ email, password }`. This is consistent and correct. No ambiguity.

**Q-4 — `GET /analytics/me/history` user identity**

The analytics service uses the JWT `sub` claim to identify the user. The `sub` in the seed
tokens is the user's UUID (not username or email). The scenario must pass the token as
`Authorization: Bearer {token}` and does not need to embed the userId in the URL. Confirmed
by `AnalyticsControllerIT` which uses `JwtTestHelper.mintToken(userId)` and hits
`/analytics/me/history` without a userId path parameter.

**Q-5 — Kafka consumer group names**

The kafka-exporter metric labels use the consumer group IDs registered in Kafka. The actual
group IDs are set in each service's `application.yml` under
`spring.kafka.consumer.group-id`. These are assumed to be `analytics-service`,
`recommendation-service`, and `notification-service` (service names), but must be confirmed
from source before the Kafka lag polling regex in `lib/kafka-lag.js` is written.

---

## Appendix — API Contract Summary (from integration tests)

Used by scenario authors as authoritative reference. Do not rely on ARCHITECTURE.md if it
conflicts with these.

| Endpoint | Method | Auth? | Status | Response shape |
|---|---|---|---|---|
| `/auth/register` | POST | No | 201 | `{ token }` |
| `/auth/login` | POST | No | 200 | `{ token }` |
| `/catalog/songs` | GET | Bearer | 200 | `{ content, page, size, totalElements, totalPages, last }` |
| `/catalog/songs/{id}` | GET | Bearer | 200 | `{ id, trackId, title, artist, genre, tempo, year, ... }` |
| `/catalog/artists/{artistId}/top-tracks` | GET | Bearer | 200 | `[{ ... }]` |
| `/stream/{songId}` | GET | Bearer | 200 | M3U8 manifest (application/vnd.apple.mpegurl); emits play.started |
| `/stream/{songId}/segment/{index}` | GET | Bearer | 200 | binary |
| `/stream/{songId}/complete` | POST | Bearer | 204 | (empty); emits play.ended |
| `/stream/{songId}/skip` | POST | Bearer | 204 | (empty); emits play.skipped |
| `/search` | GET | Bearer | 200 | `[{ trackId, songId?, title, artist, genre, year, bpm, ... }]` |
| `/analytics/me/history` | GET | Bearer | 200 | `[{ songId, eventType, timestamp }]` |
| `/analytics/charts/global` | GET | Bearer | 200 | `[{ rank, songId, playCount }]` ordered by rank asc |
| `/recommend/daily-mix` | GET | Bearer | 200 | `{ songs: [...] }` |
| `/recommend/similar/{numericId}` | GET | Bearer | 200 | `{ seedSongId, songs: [...] }`; 400 for non-numeric |
| `/playlists` | GET | Bearer | 200 | `[{ id, name, likedSongs, ... }]`; auto-creates "Liked Songs" |
| `/playlists` | POST | Bearer | 201 | `{ id, name, likedSongs: false, ... }` |
| `/playlists/{id}` | GET | Bearer | 200 | `{ id, name, likedSongs, tracks: [{ songId, position }] }` |
| `/playlists/{id}` | PATCH | Bearer | 200 | updated playlist |
| `/playlists/{id}` | DELETE | Bearer | 204 | (empty) |
| `/playlists/{id}/tracks` | POST | Bearer | 201 | `{ tracks: [{ songId, position }] }` |
| `/playlists/{id}/tracks/{songId}` | DELETE | Bearer | 204 | (empty) |
| `/playlists/{id}/tracks/reorder` | PATCH | Bearer | 200 | `{ tracks: [{ songId, position }] }` |
| `/notifications` | GET | Bearer | 200 | `[{ id, type, title, message, referenceId, read, createdAt }]` newest first |

**Kafka event shapes:**

`playback-events` topic (produced by streaming-service):
```json
{ "type": "play.started|play.ended|play.skipped", "userId": "...", "songId": "...", "timestamp": "..." }
```

`playlist-events` topic (produced by playlist-service):
```json
{ "eventType": "PLAYLIST_CREATED|TRACK_ADDED", "playlistId": "...", "userId": "...", "playlistName": "...", "timestamp": "..." }
```

---

*Last updated: 2026-05-26*
