# k6 Load Generator

This directory contains Docker Compose runnable k6 workloads for benchmark runs.

Run all scripts through the Compose `k6` service so traffic stays on the shared Docker network and enters through the gateway:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=50 -e K6_AUTH_PREALLOCATED_VUS=100 -e K6_AUTH_MAX_VUS=250 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_CATALOG_SEARCH_PREALLOCATED_VUS=300 -e K6_CATALOG_SEARCH_MAX_VUS=700 -e K6_STREAMING_SESSION_RATE=2000 -e K6_STREAMING_PREALLOCATED_VUS=1500 -e K6_STREAMING_MAX_VUS=3000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 -e K6_PLAYLIST_PREALLOCATED_VUS=100 -e K6_PLAYLIST_MAX_VUS=250 k6 run /scripts/mixed-user-journey.js
```

The default target is `http://gateway:8080`. Override it with `BASE_URL` when needed:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm -e BASE_URL=http://gateway:8080 k6 run /scripts/smoke.js
```

The smoke script is a low-volume correctness check. Its default latency threshold is `p(95)<2000ms` so a cold Docker Compose run can still pass when all functional checks succeed. Override it when you want a stricter local gate:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm -e K6_SMOKE_HTTP_REQ_DURATION_P95_MS=1000 k6 run /scripts/smoke.js
```

Available scripts:

| Script | Purpose |
| --- | --- |
| `smoke.js` | Low-volume registration, login, catalog, search, streaming, playlist, history, and recommendation smoke test through the gateway. |
| `mixed-user-journey.js` | Scenario-based mixed workload covering auth login, catalog/search reads, streaming playback events, and playlist mutations. |

For mixed benchmark runs, `dropped_iterations` must stay near zero before treating the measured backend throughput as valid. If k6 logs `Insufficient VUs`, increase the matching `K6_*_PREALLOCATED_VUS` and `K6_*_MAX_VUS` values, especially `K6_STREAMING_PREALLOCATED_VUS` and `K6_STREAMING_MAX_VUS` for playback-heavy profiles. The script creates a fresh benchmark user pool per run through `K6_RUN_ID`, groups dynamic URLs with stable k6 `name` tags, and omits the raw `url` system tag so playlist ids and song ids do not create high-cardinality metric series.

Use `K6_RATE_SCALE` to find the host's sustainable ceiling before running the full 100k or 1M shape. For example, `K6_RATE_SCALE=0.25` runs the same traffic mix at 25% of the documented rates. Increase gradually only while `dropped_iterations` remains zero and thresholds pass.

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e K6_RATE_SCALE=0.25 -e BENCHMARK_DURATION=5m -e K6_AUTH_LOGIN_RATE=50 -e K6_AUTH_PREALLOCATED_VUS=100 -e K6_AUTH_MAX_VUS=250 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_CATALOG_SEARCH_PREALLOCATED_VUS=300 -e K6_CATALOG_SEARCH_MAX_VUS=700 -e K6_STREAMING_SESSION_RATE=2000 -e K6_STREAMING_PREALLOCATED_VUS=1500 -e K6_STREAMING_MAX_VUS=3000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 -e K6_PLAYLIST_PREALLOCATED_VUS=100 -e K6_PLAYLIST_MAX_VUS=250 k6 run /scripts/mixed-user-journey.js
```

Target-shape rates for the 1M registered-user plan:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=500 -e K6_CATALOG_SEARCH_ITER_RATE=2000 -e K6_STREAMING_SESSION_RATE=20000 -e K6_PLAYLIST_MUTATION_ITER_RATE=100 k6 run /scripts/mixed-user-journey.js
```

Rate mapping:

| Variable | Target mapping |
| --- | --- |
| `K6_AUTH_LOGIN_RATE=500` | About 500 auth logins per second. |
| `K6_CATALOG_SEARCH_ITER_RATE=2000` | Each iteration sends one catalog and one search request, modeling about 4,000 combined catalog/search requests per second. |
| `K6_STREAMING_SESSION_RATE=20000` | Each iteration emits `play.started` plus `play.ended` or `play.skipped`, modeling about 40,000 playback events per second. |
| `K6_PLAYLIST_MUTATION_ITER_RATE=100` | Each iteration creates a playlist and adds one track, modeling about 200 playlist mutations per second. |
