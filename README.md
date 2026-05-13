# benchmark-application
Cloud-native music streaming benchmark application for cost-aware IaC research

## What this application is

This is a cloud-native music streaming system planned as a set of eight independent microservices. Each service owns specific responsibilities such as user authentication, song catalog management, streaming simulation, playlist operations, search, analytics, recommendations, and notifications. The architecture emphasizes service isolation, database-per-service patterns, and purpose-specific infrastructure choices to support cost-aware Infrastructure-as-Code research.

## Phase 0 status

This repository currently contains the shared deployment skeleton only. The service containers are placeholders so the Docker Compose topology can be started before business logic is generated service by service.

The source-of-truth documents are:

* `ARCHITECTURE.md` for system shape and service boundaries.
* `REQUIREMENTS.md` for mandatory, optional, and out-of-scope behavior.
* `TECH-STACK.md` for implementation technology choices.
* `PROGRESS.md` for phase status, validation notes, and decision records.

## Repository layout

```text
benchmark-application/
  docker-compose.yml
  .env.example
  config/
    prometheus/
    grafana/
    kafka/
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
  frontend/
  load-generator/
    k6/
```

## Shared deployment environment

`docker-compose.yml` defines the baseline shared runtime:

* Eight backend service placeholders.
* Frontend placeholder.
* Kafka for internal events.
* Dedicated persistence components for stateful services.
* Redis for recommendation caching.
* Prometheus and Grafana for observability.
* k6 placeholder for later workload scripts.
* One named Docker network: `benchmark-network`.

Object storage is intentionally not included in this phase because the minimum system simulates streaming and does not require real audio files.

## Start the Phase 0 environment

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

* Prometheus: http://localhost:9090
* Grafana: http://localhost:3001

The default Grafana login from `.env.example` is `admin` / `admin`.

## Stop the environment

```powershell
docker compose down
```

To remove named volumes created by the infrastructure containers:

```powershell
docker compose down -v
```

## Important Phase 0 limitations

The placeholders do not implement API endpoints, JWT enforcement, metrics endpoints, frontend routes, catalog ingestion, messaging behavior, or load-test flows. Those are generated and validated in later phases according to `PROGRESS.md`.

Prometheus is configured with the intended Spring Boot metrics target path, `/actuator/prometheus`, so application targets will remain unavailable until each service is implemented with metrics support.
