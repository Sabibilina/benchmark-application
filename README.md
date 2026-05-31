# benchmark-application
Cloud-native music streaming benchmark application for cost-aware IaC research

## What this application is

This is a cloud-native music streaming system planned as a set of eight independent microservices. Each service owns specific responsibilities such as user authentication, song catalog management, streaming simulation, playlist operations, search, analytics, recommendations, and notifications. The architecture emphasizes service isolation, database-per-service patterns, and purpose-specific infrastructure choices to support cost-aware Infrastructure-as-Code research.

## Current status

This repository contains the Docker Compose runtime for the benchmark application, including implemented backend services, persistence components, messaging, observability, and load-generation support.

The source-of-truth documents are:

* `ARCHITECTURE.md` for system shape and service boundaries.
* `REQUIREMENTS.md` for mandatory, optional, and out-of-scope behavior.
* `TECH-STACK.md` for implementation technology choices.
* `SCALABILITY.md` for Docker Compose scalability and benchmark planning.
* `PROGRESS.md` for phase status.
* `DESIGN-DECISIONS.md` for decision records.

## Repository layout

```text
benchmark-application/
  docker-compose.yml
  docker-compose.scale-baseline.yml
  docker-compose.scale-100k.yml
  docker-compose.scale-1m.yml
  .env.example
  config/
    nginx/
    prometheus/
    grafana/
    jwt/
  infrastructure/
    postgres/
    clickhouse/
    mongodb/
    opensearch/
  services/
    auth-service/
    catalog-service/
    streaming-service/
    playlist-service/
    search-service/
    analytics-service/
    recommendation-service/
    notification-service/
  load-generator/
    k6/
```

## Shared deployment environment

`docker-compose.yml` defines the baseline shared runtime:

* Eight backend services.
* Nginx gateway for benchmark traffic routing.
* Kafka for internal events.
* Dedicated persistence components for stateful services.
* Redis for recommendation caching.
* Prometheus, Grafana, gateway metrics, Kafka metrics, Redis metrics, and container metrics for observability.
* k6 workload scripts.
* One named Docker network: `benchmark-network`.

Object storage is intentionally not included in this phase because the minimum system simulates streaming and does not require real audio files.

## Start the baseline environment

Docker Desktop or a compatible Docker Engine with Docker Compose is required.

1. Optional: copy `.env.example` to `.env` and adjust ports or resource limits.
2. Build and start the skeleton:

```powershell
docker compose up -d --build
```

3. Check container status:

```powershell
docker compose ps
```

4. Open the shared observability tools:

* Gateway: http://localhost:8080/health
* Prometheus: http://localhost:9090
* Grafana: http://localhost:3001

The default Grafana login from `.env.example` is `admin` / `admin`.

## Run benchmark-oriented profiles

Scaled profiles keep traffic entering through the gateway and remove direct app-service host ports so replicas can run behind Docker Compose service discovery.

Baseline scale profile:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js
```

Moderate 100k-user benchmark shape:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml up -d --build --scale streaming-service=6 --scale catalog-service=3 --scale search-service=3 --scale recommendation-service=3 --scale analytics-service=2 --scale auth-service=4 --scale playlist-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=50 -e K6_AUTH_PREALLOCATED_VUS=100 -e K6_AUTH_MAX_VUS=250 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_CATALOG_SEARCH_PREALLOCATED_VUS=300 -e K6_CATALOG_SEARCH_MAX_VUS=700 -e K6_STREAMING_SESSION_RATE=2000 -e K6_STREAMING_PREALLOCATED_VUS=1500 -e K6_STREAMING_MAX_VUS=3000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 -e K6_PLAYLIST_PREALLOCATED_VUS=100 -e K6_PLAYLIST_MAX_VUS=250 k6 run /scripts/mixed-user-journey.js
```

The 100k profile raises the gateway and k6 container resource limits above local defaults. If `dropped_iterations` remains high, the load generator or host is still saturated and the result should not be treated as achieved backend throughput.

To find the current host's sustainable ceiling, run the same 100k traffic mix with `K6_RATE_SCALE=0.25`, then try `0.50`, `0.75`, and `1.0` only while `dropped_iterations` remains zero and thresholds pass.

The 1M target profile is intentionally resource-heavy and models the workload in `SCALABILITY.md`: 1,000,000 registered users, 100,000 DAU, 20,000 peak concurrent users, about 40,000 playback events per second, about 500 auth logins per second, about 4,000 combined catalog/search requests per second, and about 200 playlist mutations per second. Review host capacity before starting it:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config
```

Target-shape command:

```powershell
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml up -d --build --scale auth-service=4 --scale catalog-service=8 --scale streaming-service=20 --scale playlist-service=4 --scale search-service=8 --scale analytics-service=8 --scale recommendation-service=8 --scale notification-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=500 -e K6_CATALOG_SEARCH_ITER_RATE=2000 -e K6_STREAMING_SESSION_RATE=20000 -e K6_PLAYLIST_MUTATION_ITER_RATE=100 k6 run /scripts/mixed-user-journey.js
```

See `SCALABILITY.md` for the scaling rationale, instance counts, observability thresholds, and load-test order.

## Stop the environment

```powershell
docker compose down
```

To remove named volumes created by the infrastructure containers:

```powershell
docker compose down -v
```

## Benchmarking notes

Use the gateway port for benchmark traffic instead of individual service ports. Direct service ports are kept for development and debugging, but scaled benchmark profiles route through Nginx so replicas can be added with `docker compose up --scale`.
