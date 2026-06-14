# k6 Load Generator

This directory contains Docker Compose-run k6 scripts for backend-only benchmark validation.

Scripts are mounted into the `k6` container at `/scripts`:

| Script | Purpose |
| --- | --- |
| `smoke.js` | Short end-to-end correctness flow through the gateway. |
| `mixed-user-journey.js` | Scenario-based workload for auth, catalog/search, playlist mutations, and streaming events. |
| `cost-summary.js` | Shared summary helper that writes benchmark metadata and key metrics to `/results`. |

Run the smoke script:

```bash
docker compose run --rm k6 run /scripts/smoke.js
```

Run a calibration workload:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml --profile benchmark up -d --build
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml --profile benchmark run --rm k6 run /scripts/mixed-user-journey.js
```

The scripts send traffic to `BASE_URL`, which defaults to `http://gateway:8080` inside Docker Compose. Cost/performance summaries are written to the `k6-results` Docker volume unless `K6_COST_SUMMARY_PATH` overrides the path.
