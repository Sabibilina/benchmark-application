# k6 Load Generator

This directory contains Docker Compose runnable k6 workloads for benchmark runs.

Run all scripts through the Compose `k6` service so traffic stays on the shared Docker network and enters through the gateway:

```powershell
docker compose run --rm k6 run /scripts/smoke.js
docker compose run --rm -e K6_TARGET_RATE=50 -e K6_HOLD_DURATION=10m k6 run /scripts/mixed-user-journey.js
```

The default target is `http://gateway:8080`. Override it with `BASE_URL` when needed:

```powershell
docker compose run --rm -e BASE_URL=http://gateway:8080 k6 run /scripts/smoke.js
```

Available scripts:

| Script | Purpose |
| --- | --- |
| `smoke.js` | Low-volume registration, login, catalog, search, recommendation smoke test through the gateway. |
| `mixed-user-journey.js` | Ramping mixed workload covering auth, catalog, search, streaming, analytics, and recommendation. |
