# benchmark-application

Backend microservices, Docker Compose deployment, observability, and scalability benchmark assets for a music streaming benchmark application.

## Branch Scope

This branch contains the backend of the eight microservices, the shared Docker Compose deployment, the Docker Compose scalability/load-testing support and the cloud cost-optimization implementation work.

The application is backend-only in this branch: there is no frontend UI deliverable.

## What The App Does

The system models a music streaming platform using independent microservices with service-owned persistence, JWT-based authentication, internal Kafka events, and Docker-only deployment. It is designed for backend validation and Docker Compose benchmarking toward the workload described in `SCALABILITY.md`.

Implemented services:

| Service | Purpose | Host port |
| --- | --- | ---: |
| Auth Service | Registers users, logs users in, and issues RS256 JWT access tokens. | 8081 |
| Catalog Service | Loads the song dataset and serves paginated song metadata. | 8082 |
| Streaming Service | Simulates stream descriptors/segments and publishes playback events. | 8083 |
| Playlist Service | Manages playlists, liked songs, playlist tracks, and track ordering. | 8084 |
| Search Service | Indexes catalog data into OpenSearch and exposes protected search. | 8085 |
| Analytics Service | Consumes playback events, stores history in ClickHouse, and exposes history/charts. | 8086 |
| Recommendation Service | Consumes playback events, maintains recommendation state/cache, and returns recommendations. | 8087 |
| Notification Service | Consumes playlist events and stores internal in-app notification documents. | 8088 |

The Nginx gateway exposes benchmark traffic on port `8080` and routes requests to backend services by path.

## Source Of Truth

Use these documents for system shape and validation:

| File | Role |
| --- | --- |
| `ARCHITECTURE.md` | Service boundaries and architecture. |
| `REQUIREMENTS.md` | Required, optional, and out-of-scope behavior. |
| `TECH-STACK.md` | Implementation technologies. |
| `SCALABILITY.md` | Docker Compose scaling plan, target workload, and benchmark guidance. |
| `PROGRESS.md` | Checklist and phase completion tracking. |
| `DESIGN-DECISIONS.md` | Decision log, rationale, assumptions, and validation notes. |
| `REFACTORED-PROMPTS.md` | Extra Session 11 prompt for backend cost-efficiency improvement. |
| `COST-AWARE-DECISIONS.md` | Decision log for the cost-efficiency improvement session. |
| `TESTS.md` | Test inventory, coverage, and validation evidence. |
| `BUGS.md` | Bugs found during development and their fixes. |

## Repository Layout

```text
benchmark-application/
  docker-compose.yml
  docker-compose.cost-smoke.yml
  docker-compose.scale-baseline.yml
  docker-compose.scale-100k.yml
  docker-compose.scale-1m.yml
  .env.example
  .env.cost-smoke.example
  config/
    grafana/
    jwt/
    nginx/
    prometheus/
  infrastructure/
    clickhouse/
    mongodb/
    opensearch/
    postgres/
  load-generator/
    k6/
  services/
    analytics-service/
    auth-service/
    catalog-service/
    notification-service/
    playlist-service/
    recommendation-service/
    search-service/
    streaming-service/
```

## Runtime Components

`docker-compose.yml` defines the baseline local runtime:

* Eight Spring Boot backend services.
* Nginx gateway for benchmark traffic routing.
* Kafka plus topic initialization for internal events.
* PostgreSQL databases for Auth, Catalog, Playlist, and Recommendation.
* OpenSearch for Search.
* ClickHouse for Analytics.
* Redis for Recommendation caching.
* MongoDB for Notification persistence.
* Prometheus, Grafana, gateway metrics, Kafka metrics, Redis metrics, and container metrics.
* k6 load-generator service and workload scripts.
* Named Docker network: `benchmark-network`.

Cost controls included in this branch:

* `docker-compose.cost-smoke.yml` for lower-footprint backend smoke runs.
* Bounded Prometheus and Kafka retention defaults.
* Redis cache persistence disabled by default because recommendation cache entries can be rebuilt.
* k6 cost evidence summaries written under the `k6-results` Docker volume.

## Service Interfaces

Public backend endpoints:

| Service | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login` |
| Catalog | `GET /catalog/songs`, `GET /catalog/songs/{id}` |
| Streaming | `GET /stream/{songId}`, `GET /stream/{songId}/segments/{segmentIndex}`, `POST /stream/{songId}/ended`, `POST /stream/{songId}/skipped` |
| Playlist | `GET /playlists`, `POST /playlists`, `GET /playlists/{id}`, `PATCH /playlists/{id}`, `DELETE /playlists/{id}`, `POST /playlists/{id}/tracks`, `DELETE /playlists/{id}/tracks/{songId}`, `PATCH /playlists/{id}/tracks/reorder` |
| Search | `GET /search` |
| Analytics | `GET /analytics/me/history`, `GET /analytics/charts/global` |
| Recommendation | `GET /recommend/daily-mix`, `GET /recommend/similar/{songId}` |

All public application endpoints except Auth register/login require `Authorization: Bearer <jwt>`.

Operational endpoints:

* `GET /actuator/health`
* `GET /actuator/prometheus`

Notification Service is internal-only for this version and does not expose a public client API.

## Messaging

Kafka topics:

| Topic | Producers | Consumers |
| --- | --- | --- |
| `playback-events` | Streaming Service | Analytics Service, Recommendation Service |
| `playlist-events` | Playlist-related event flow | Notification Service |

Playback-event identity uses the Auth JWT subject as the canonical user id so user history and recommendations stay consistent across services.

## Configuration

Copy the example environment file before local customization:

```bash
cp .env.example .env
```

Important configuration areas:

* Host ports and container resource defaults.
* JWT key paths and issuer settings.
* Database, Redis, Kafka, OpenSearch, ClickHouse, and MongoDB credentials/settings.
* Service-specific limits, page sizes, Kafka tuning, and JVM memory settings.
* k6 workload rates and benchmark duration settings.

JWT keys for local Docker Compose are stored under `config/jwt/`.

## Run The Baseline Environment

Docker Engine or Docker Desktop with Docker Compose is required.

Build and start the full baseline stack:

```bash
docker compose up -d --build
```

Check running containers:

```bash
docker compose ps
```

Useful local URLs:

| Component | URL |
| --- | --- |
| Gateway health | http://localhost:8080/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| OpenSearch | http://localhost:9200 |

Default Grafana credentials from `.env.example` are `admin` / `admin`.

## Validate Configuration

Validate base and scale Compose files:

```bash
docker compose config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config --quiet
```

Validate gateway, Prometheus, and k6 scripts:

```bash
docker compose run --rm --no-deps gateway nginx -t
docker compose run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose run --rm --no-deps k6 inspect /scripts/smoke.js
docker compose run --rm --no-deps k6 inspect /scripts/mixed-user-journey.js
```

## Run Tests

Build all backend services. Each Java service build runs Maven verification:

```bash
docker compose build auth-service catalog-service streaming-service playlist-service search-service analytics-service recommendation-service notification-service
```

Detailed test inventory, coverage numbers, infrastructure smoke tests, and final validation evidence are in `TESTS.md`.

## Run Benchmark Profiles

All benchmark traffic should enter through the gateway instead of direct service ports. Scaled profiles remove direct application host ports so service replicas can run behind Docker Compose service discovery.

Lower-footprint backend smoke profile. This profile keeps the backend runnable through the gateway, removes direct service/infra host ports, and moves Grafana/exporters/cAdvisor/k6 behind optional Compose profiles:

```bash
docker compose --env-file .env.cost-smoke.example -f docker-compose.yml -f docker-compose.cost-smoke.yml up -d --build
```

Add full observability to the cost-smoke profile when you need Prometheus/Grafana/exporters:

```bash
docker compose --env-file .env.cost-smoke.example -f docker-compose.yml -f docker-compose.cost-smoke.yml --profile observability up -d
```

Baseline smoke profile:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.scale-baseline.yml run --rm k6 run /scripts/smoke.js
```

100k-user benchmark shape:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml up -d --build --scale streaming-service=6 --scale catalog-service=3 --scale search-service=3 --scale recommendation-service=3 --scale analytics-service=2 --scale auth-service=4 --scale playlist-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=50 -e K6_AUTH_PREALLOCATED_VUS=100 -e K6_AUTH_MAX_VUS=250 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_CATALOG_SEARCH_PREALLOCATED_VUS=300 -e K6_CATALOG_SEARCH_MAX_VUS=700 -e K6_STREAMING_SESSION_RATE=2000 -e K6_STREAMING_PREALLOCATED_VUS=1500 -e K6_STREAMING_MAX_VUS=3000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 -e K6_PLAYLIST_PREALLOCATED_VUS=100 -e K6_PLAYLIST_MAX_VUS=250 k6 run /scripts/mixed-user-journey.js
```

Host-calibration profile. Start below the full target and increase only while `dropped_iterations` stays near zero:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml run --rm -e K6_RATE_SCALE=0.25 -e BENCHMARK_DURATION=5m -e K6_AUTH_LOGIN_RATE=50 -e K6_CATALOG_SEARCH_ITER_RATE=400 -e K6_STREAMING_SESSION_RATE=2000 -e K6_PLAYLIST_MUTATION_ITER_RATE=20 k6 run /scripts/mixed-user-journey.js
```

1M target-shape profile. This is resource-heavy and may not fit on a single developer machine:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml up -d --build --scale auth-service=4 --scale catalog-service=8 --scale streaming-service=20 --scale playlist-service=4 --scale search-service=8 --scale analytics-service=8 --scale recommendation-service=8 --scale notification-service=2
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml run --rm -e BENCHMARK_DURATION=10m -e K6_AUTH_LOGIN_RATE=500 -e K6_CATALOG_SEARCH_ITER_RATE=2000 -e K6_STREAMING_SESSION_RATE=20000 -e K6_PLAYLIST_MUTATION_ITER_RATE=100 k6 run /scripts/mixed-user-journey.js
```

See `SCALABILITY.md` for the target workload table, scaling rationale, bottleneck analysis, recommended scaling order, and interpretation guidance.

The k6 scripts write cost evidence summaries to `/results/*-cost-summary.json` inside the `k6-results` Docker volume. The summary includes selected profile, target workload variables, k6 rates, dropped iterations, request counts, latency metrics, and failure rates.

## Stop The Environment

Stop containers without deleting volumes:

```bash
docker compose down
```

Stop containers and delete local persisted data:

```bash
docker compose down -v
```

Use `down -v` only when you intentionally want to delete local database, cache, search, and analytics data.

## Out Of Scope

This branch does not include:

* Frontend UI, frontend runtime containers, browser tests, or frontend metrics.
* Kubernetes, Helm, Nomad, Swarm, Terraform, or cloud deployment code.
* Real audio object storage. Streaming is simulated with generated segment payloads.
