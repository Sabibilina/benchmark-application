# k6 Load Generator

This directory contains Docker Compose runnable k6 workloads for benchmark runs.

Run all scripts through the Compose `k6` service so traffic stays on the shared Docker network and enters through the gateway:

```powershell
docker compose run --rm k6 run /scripts/smoke.js
docker compose run --rm -e K6_DURATION=10m -e K6_AUTH_LOGIN_RATE=50 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_STREAMING_SESSION_RATE=2000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 k6 run /scripts/mixed-user-journey.js
```

The default target is `http://gateway:8080`. Override it with `BASE_URL` when needed:

```powershell
docker compose run --rm -e BASE_URL=http://gateway:8080 k6 run /scripts/smoke.js
```

Available scripts:

| Script | Purpose |
| --- | --- |
| `smoke.js` | Low-volume registration, login, catalog, search, streaming, playlist, history, and recommendation smoke test through the gateway. |
| `mixed-user-journey.js` | Scenario-based mixed workload covering auth login, catalog/search reads, streaming playback events, and playlist mutations. |

Target-shape rates for the 1M registered-user plan:

```powershell
docker compose run --rm -e K6_DURATION=10m -e K6_AUTH_LOGIN_RATE=500 -e K6_CATALOG_SEARCH_ITER_RATE=2000 -e K6_STREAMING_SESSION_RATE=20000 -e K6_PLAYLIST_MUTATION_ITER_RATE=100 k6 run /scripts/mixed-user-journey.js
```

Rate mapping:

| Variable | Target mapping |
| --- | --- |
| `K6_AUTH_LOGIN_RATE=500` | About 500 auth logins per second. |
| `K6_CATALOG_SEARCH_ITER_RATE=2000` | Each iteration sends one catalog and one search request, modeling about 4,000 combined catalog/search requests per second. |
| `K6_STREAMING_SESSION_RATE=20000` | Each iteration emits `play.started` plus `play.ended` or `play.skipped`, modeling about 40,000 playback events per second. |
| `K6_PLAYLIST_MUTATION_ITER_RATE=100` | Each iteration creates a playlist and adds one track, modeling about 200 playlist mutations per second. |
