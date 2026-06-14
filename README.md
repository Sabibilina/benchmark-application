# benchmark-application

Backend microservices, Docker Compose deployment, observability, k6 load generation, and cost-aware scalability assets for a music streaming benchmark application.

## Branch Scope

This branch contains the backend implementation of the eight microservices plus the shared Docker Compose deployment environment and cost-aware scalability support. It does not include a frontend UI or an external cloud cost-efficiency implementation beyond Docker Compose-based tuning and benchmarking artifacts.

The implemented backend services are:

| Service | Purpose | Access path |
| --- | --- | --- |
| Auth Service | Registers users, logs users in, and issues JWT access tokens. | `/auth/**` through gateway |
| Catalog Service | Stores and serves the song catalog loaded from `catalog.csv`. | `/catalog/**` through gateway |
| Streaming Service | Simulates stream descriptors and segments, and publishes playback events. | `/stream/**` through gateway |
| Playlist Service | Manages user playlists, liked songs, tracks, and track ordering. | `/playlists/**` through gateway |
| Search Service | Indexes catalog data into OpenSearch and exposes protected song search. | `/search` through gateway |
| Analytics Service | Persists listening history and exposes user history plus global charts. | `/analytics/**` through gateway |
| Recommendation Service | Consumes playback events and returns daily-mix and similar-song recommendations. | `/recommend/**` through gateway |
| Notification Service | Internal service that consumes playlist update events and stores notifications in MongoDB. | Internal Kafka consumer |

## Source Of Truth

Use these documents for project decisions and validation:

* `ARCHITECTURE.md` defines system shape, service boundaries, and out-of-scope frontend behavior.
* `REQUIREMENTS.md` defines mandatory, optional, and will-not-have requirements.
* `TECH-STACK.md` defines implementation technology choices.
* `PROGRESS.md` tracks checklist completion status only.
* `DESIGN-DECISIONS.md` records decisions, rationale, assumptions, fixes, and validation notes.
* `TESTS.md` records test inventory, coverage evidence, and final validation evidence.
* `BUGS.md` records notable bugs and fixes discovered during development.
* `SCALABILITY.md` records the Docker Compose-only cost-aware scaling plan.
* `COST-AWARE-DECISIONS.md` records cost-aware implementation decisions and trade-offs.

## Repository Layout

```text
benchmark-application/
  docker-compose.yml
  .env.example
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

## Technology Stack

All backend services use Java 21, Spring Boot 3.x, and Maven. Services expose Actuator health and Prometheus endpoints.

Shared infrastructure:

* Docker Compose runtime with named network `benchmark-network`
* Kafka for internal events
* PostgreSQL for Auth, Catalog, Playlist, and Recommendation durable state
* OpenSearch for Search indexes
* ClickHouse for Analytics history and chart data
* Redis for Recommendation cache
* MongoDB for Notification storage
* Prometheus and Grafana for observability
* Nginx gateway for Docker Compose horizontal scaling
* k6 for load-generation assets and cost/performance summaries

## Service Interfaces

Public application endpoints:

| Service | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `POST /auth/login` |
| Catalog | `GET /catalog/songs`, `GET /catalog/songs/{id}` |
| Streaming | `GET /stream/{songId}`, `GET /stream/{songId}/segments/{segmentIndex}`, `POST /stream/{songId}/ended`, `POST /stream/{songId}/skipped` |
| Playlist | `GET /playlists`, `POST /playlists`, `GET /playlists/{id}`, `PATCH /playlists/{id}`, `DELETE /playlists/{id}`, `POST /playlists/{id}/tracks`, `DELETE /playlists/{id}/tracks/{songId}`, `PATCH /playlists/{id}/tracks/reorder` |
| Search | `GET /search` |
| Analytics | `GET /analytics/me/history`, `GET /analytics/charts/global` |
| Recommendation | `GET /recommend/daily-mix`, `GET /recommend/similar/{songId}` |

All public application endpoints except `POST /auth/register` and `POST /auth/login` require `Authorization: Bearer <jwt>`.

Operational endpoints:

* `GET /actuator/health`
* `GET /actuator/prometheus`

Notification Service is internal only. It does not expose a public client-facing API in this version.

## Persistence And Messaging

Each stateful service owns its own persistence layer:

| Service | Persistence |
| --- | --- |
| Auth | PostgreSQL database `auth` |
| Catalog | PostgreSQL database `catalog` |
| Playlist | PostgreSQL database `playlist` |
| Search | OpenSearch index |
| Analytics | ClickHouse database `analytics` |
| Recommendation | PostgreSQL database `recommendation` plus Redis cache |
| Notification | MongoDB database `notification` |

Kafka topics:

* `playback-events` for Streaming playback events consumed by Analytics and Recommendation.
* `playlist-events` for playlist update events consumed by Notification.

The `kafka-init` Compose service creates the required topics with configurable partition counts before benchmark traffic begins.

## Catalog Dataset

Catalog Service reads the CSV file at `services/catalog-service/data/catalog.csv` and ingests it automatically at startup when `CATALOG_INGESTION_ENABLED=true`.

Search Service uses the same CSV as indexing input through `SEARCH_CATALOG_DATASET_PATH`, but stores only its own derived OpenSearch index.

## Configuration

Copy `.env.example` to `.env` for local overrides:

```bash
cp .env.example .env
```

Important configuration groups:

* Gateway and infrastructure host ports
* CPU and memory defaults
* JWT key paths and issuer settings
* Catalog and Search dataset settings
* Kafka topic and consumer group settings
* Kafka topic partition, retention, and producer tuning
* Database connection pool caps
* Redis cache memory policy
* k6 workload rates and threshold settings
* k6 container user for writing result summaries to the `k6-results` volume
* Database credentials for local development containers

JWT keys are stored under `config/jwt/` for local Docker Compose use.

## Run Locally

Docker Desktop or a compatible Docker Engine with Docker Compose is required.

Build and start the full backend deployment:

```bash
docker compose up -d --build
```

Check service status:

```bash
docker compose ps
```

Useful URLs:

* Gateway: http://localhost:8080
* Prometheus: http://localhost:9090
* Grafana: http://localhost:3001
* OpenSearch: http://localhost:9200

Application service ports are internal to Docker Compose so replicas can be scaled without host-port conflicts. Send application traffic through the gateway.

Default Grafana credentials from `.env.example`:

* User: `admin`
* Password: `admin`

## Stop Locally

Stop containers without deleting volumes:

```bash
docker compose down
```

Stop containers and remove local database volumes:

```bash
docker compose down -v
```

Use `down -v` only when you intentionally want to delete local persisted data.

## Validation

Validate Compose configuration:

```bash
docker compose config --quiet
docker compose config --services
docker compose -f docker-compose.yml -f docker-compose.scale-smoke.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-100k.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.scale-1m.yml config --quiet
```

Validate running containers:

```bash
docker compose ps
```

Check observability endpoints:

```bash
curl -i http://localhost:8080/health
curl -i http://localhost:9090/-/ready
curl -i http://localhost:3001/api/health
```

Run a gateway-based k6 smoke test:

```bash
docker compose run --rm k6 run /scripts/smoke.js
```

Run the mixed workload with a scale profile:

```bash
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml --profile benchmark up -d --build
docker compose -f docker-compose.yml -f docker-compose.scale-calibration.yml --profile benchmark run --rm k6 run /scripts/mixed-user-journey.js
```

Service-level test and coverage details are documented in `TESTS.md`. Docker image builds run Maven verification for each Java service.

## Testing

Each backend service has unit and integration tests under `services/<service>/src/test`.

The main backend build command is:

```bash
docker compose build auth-service catalog-service streaming-service playlist-service search-service analytics-service recommendation-service notification-service
```

Infrastructure-facing test evidence is recorded in `TESTS.md`, including OpenSearch, ClickHouse, Redis, Kafka, and final live validation evidence where available.

## Scalability Profiles

Scale profiles keep benchmark cost explicit:

| File | Purpose |
| --- | --- |
| `docker-compose.scale-smoke.yml` | Low-cost correctness checks with small payloads and short retention. |
| `docker-compose.scale-calibration.yml` | Moderate bottleneck-discovery run before expensive profiles. |
| `docker-compose.scale-100k.yml` | Mid-scale workload using service-specific replica counts. |
| `docker-compose.scale-1m.yml` | Target-ratio profile for hosts with enough Docker CPU, memory, disk, and k6 capacity. |

For scaled profiles, seed Catalog and Search once with the default profile first, then use the scale profile where `CATALOG_INGESTION_ENABLED=false` and `SEARCH_INDEXING_ENABLED=false` avoid repeated startup work across replicas.

## Out Of Scope

This branch does not include:

* Frontend UI, frontend runtime containers, browser tests, or frontend metrics.
* Kubernetes, Helm, Nomad, Terraform, or cloud deployment code.
