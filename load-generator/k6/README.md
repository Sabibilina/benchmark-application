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

`mixed-user-journey.js` creates a bounded login user pool during setup using `K6_AUTH_USER_POOL_SIZE`, then reuses those users for the auth-login scenario. This keeps the mixed profile aligned with the scalability target of login pressure rather than measuring continuous registration writes. Both workload scripts set stable k6 `name` tags for variable routes such as stream IDs and playlist IDs so URL cardinality does not dominate local k6 memory usage.
