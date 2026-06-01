# Load Generator — Runbook

k6 arrival-rate load test for the music-streaming benchmark.

## Quick start

```bash
# 1. Start the full stack (all services + nginx-lb + kafka-exporter)
docker compose up -d

# 2. Smoke test (2 VUs, ~2 min, confirms scripts start without errors)
docker compose --profile load-test run --rm load-generator \
  k6 run -e K6_SCENARIO=smoke /scripts/main.js

# 3. Full benchmark
docker compose --profile load-test run --rm load-generator \
  k6 run -e K6_SCENARIOS=all \
         -e NGINX_LB_URL=http://nginx-lb \
         -e SEED_USER_COUNT=500 \
         /scripts/main.js
```

## Pre-seeding users (required for large runs)

For `SEED_USER_COUNT > 1000`, pre-register users once before running the main test:

```bash
docker compose --profile load-test run --rm load-generator \
  k6 run -e SEED_USER_COUNT=25000 \
         -e SEED_BATCH_SIZE=50 \
         -e NGINX_LB_URL=http://nginx-lb \
         /scripts/seed.js
# Writes: /scripts/data/seed.json inside the container
# Mount a host volume to persist it:
#   -v $(pwd)/load-generator/scripts/data:/scripts/data
```

Add `load-generator/scripts/data/seed.json` to `.gitignore` (contains test credentials).

## Scenarios

| Scenario | Endpoint(s) | Peak rate | Notes |
|---|---|---|---|
| `streaming_playback` | `GET /stream/{id}` → segment → complete/skip | 20 000 iter/s | Generates 40 K Kafka events/s |
| `catalog_search` | `GET /catalog/songs` + `GET /search` | 4 000 req/s | Alternates 50/50 |
| `auth_login` | `POST /auth/login` | 500 req/s (burst 1 500) | Burst in stage 3 |
| `playlist_mutations` | `GET/POST /playlists` + `POST /playlists/{id}/tracks` | 200 req/s | |
| `recommendations` | `GET /recommend/daily-mix` + `/similar/{id}` | 400 req/s | |

Run a subset: `-e K6_SCENARIOS=auth_login,catalog_search`

## Test phases

All scenarios run through three phases (total ~25 min):

1. **Warm-up** (5 min) — ramps from 0 to 20 % of peak
2. **Steady state** (15 min) — holds at 100 % of peak
3. **Auth burst** (2 min) — auth spikes to 3×, others hold at 100 %
4. **Ramp-down** (3 min)

Override durations: `-e K6_PHASE_WARMUP_DURATION=1m -e K6_PHASE_STEADY_DURATION=5m`

## SLO thresholds

The test fails (non-zero exit) if any threshold is violated:

| Metric | Threshold |
|---|---|
| `http_req_failed` (global) | `rate < 1 %` |
| `streaming_manifest_duration_ms` | `p(99) < 2 000 ms` |
| `search_query_duration_ms` | `p(99) < 1 000 ms` |
| `auth_login_duration_ms` | `p(99) < 500 ms` |
| `catalog_browse_duration_ms` | `p(99) < 1 000 ms` |
| `recommendation_daily_mix_duration_ms` | `p(99) < 500 ms` |
| `dropped_iterations` | `count < 500` |

**Note on auth p99**: The threshold aggregates over the full run including the burst window.
Steady-state compliance is verified by filtering `phase=steady` in Grafana.

## Kafka lag monitoring (standalone)

```bash
docker compose --profile load-test run --rm load-generator \
  k6 run -e KAFKA_EXPORTER_URL=http://kafka-exporter:9308 \
         -e LAG_THRESHOLD=10000 \
         -e CHECK_DURATION=5m \
         /scripts/kafka-lag-check.js
```

## Known limitations

1. **nginx-lb rate limits**: At default config, a single k6 IP is capped at 20 r/s for auth and 200 r/s for streaming. Raise limits in `infra/nginx-lb/nginx.conf` before running at full targets, or distribute k6 across multiple IPs.

2. **Distributed k6**: 20 000 iter/s (streaming) requires ≥ 4 k6 instances. Single container reliably produces ~3 000–5 000 iter/s.

3. **JWT TTL = 1 hour**: Tokens obtained in `setup()` are valid for the full 25-min test. If you reduce `jwt.expiration-ms` in auth-service, add token-refresh logic.

## Interpreting results

- **`dropped_iterations > 0`** — preAllocatedVUs too low; the arrival-rate executor ran out of free VUs. Increase `K6_STREAMING_RATE`-correlated VU limits or reduce target rate.
- **High `streaming_error_rate`** — check nginx-lb access log for 503s (rate-limit) or streaming-service logs for errors.
- **Rising `kafka_lag_analytics`** — analytics-service cannot consume fast enough; add replicas or increase batch flush frequency in `BatchEventBuffer`.
- **`auth_login_duration_ms p99 > 500ms`** — BCrypt threads saturated; scale auth-service replicas.
