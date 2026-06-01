# benchmark-application

Backend microservices and Docker Compose deployment for a music streaming benchmark application.

## Branch Scope

This branch contains the backend implementation of the eight microservices plus the shared Docker Compose deployment environment. It does not include the scalability branch or cost-efficiency work.

The implemented backend services are:

| Service | Purpose | Host port |
| --- | --- | ---: |
| Auth Service | Registers users, logs users in, and issues JWT access tokens. | 8081 |
| Catalog Service | Stores and serves the song catalog loaded from `catalog.csv`. | 8082 |
| Streaming Service | Simulates stream descriptors and segments, and publishes playback events. | 8083 |
| Playlist Service | Manages user playlists, liked songs, tracks, and track ordering. | 8084 |
| Search Service | Indexes catalog data into OpenSearch and exposes protected song search. | 8085 |
| Analytics Service | Persists listening history and exposes user history plus global charts. | 8086 |
| Recommendation Service | Consumes playback events and returns daily-mix and similar-song recommendations. | 8087 |
| Notification Service | Internal service that consumes playlist update events and stores notifications in MongoDB. | 8088 |

## Source Of Truth

Use these documents for project decisions and validation:

* `ARCHITECTURE.md` defines system shape, service boundaries, and out-of-scope frontend behavior.
* `REQUIREMENTS.md` defines mandatory, optional, and will-not-have requirements.
* `TECH-STACK.md` defines implementation technology choices.
* `PROGRESS.md` tracks checklist completion status only.
* `DESIGN-DECISIONS.md` records decisions, rationale, assumptions, fixes, and validation notes.
* `TESTS.md` records test inventory, coverage evidence, and final validation evidence.
* `BUGS.md` records notable bugs and fixes discovered during development.

## Repository Layout

```text
benchmark-application/
  docker-compose.yml
  .env.example
  config/
    grafana/
    jwt/
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
* k6 directory reserved for load-generation assets

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

## Catalog Dataset

Catalog Service reads the CSV file at `services/catalog-service/data/catalog.csv` and ingests it automatically at startup when `CATALOG_INGESTION_ENABLED=true`.

Search Service uses the same CSV as indexing input through `SEARCH_CATALOG_DATASET_PATH`, but stores only its own derived OpenSearch index.

## Configuration

Copy `.env.example` to `.env` for local overrides:

```bash
cp .env.example .env
```

Important configuration groups:

* Host ports for all services and infrastructure
* CPU and memory defaults
* JWT key paths and issuer settings
* Catalog and Search dataset settings
* Kafka topic and consumer group settings
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

* Prometheus: http://localhost:9090
* Grafana: http://localhost:3001
* OpenSearch: http://localhost:9200
* Gateway is not part of this branch; use each service host port directly.

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
```

Validate running containers:

```bash
docker compose ps
```

Check observability endpoints:

```bash
curl -i http://localhost:9090/-/ready
curl -i http://localhost:3001/api/health
```

Service-level test and coverage details are documented in `TESTS.md`. Docker image builds run Maven verification for each Java service.

## Testing

Each backend service has unit and integration tests under `services/<service>/src/test`.

The main backend build command is:

```bash
docker compose build auth-service catalog-service streaming-service playlist-service search-service analytics-service recommendation-service notification-service
```

Infrastructure-facing test evidence is recorded in `TESTS.md`, including OpenSearch, ClickHouse, Redis, Kafka, and final live validation evidence where available.

## Out Of Scope

This branch does not include:

* Frontend UI, frontend runtime containers, browser tests, or frontend metrics.
* Scalability implementation profiles.
* Cost-efficiency implementation or cloud cost modeling.
* Kubernetes, Helm, Nomad, Terraform, or cloud deployment code.
