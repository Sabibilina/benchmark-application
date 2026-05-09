# MusicApp — Polyglot Microservices Benchmark Application

A benchmark application implementing a music streaming platform across 8 microservices.  
Generated iteratively following the architecture brief.

---

## Architecture overview

| Service | Stack | Port | Persistence |
|---|---|---|---|
| auth | Node.js + Express | 3001 | PostgreSQL |
| catalog | Python + FastAPI | 3002 | PostgreSQL |
| streaming | Go + Gin | 3003 | MinIO (object storage) |
| playlist | Node.js + Express | 3004 | PostgreSQL |
| search | Python + FastAPI | 3005 | PostgreSQL (full-text) |
| analytics | Python + FastAPI | 3006 | PostgreSQL |
| recommendation | Python + FastAPI | 3007 | PostgreSQL |
| notification | Node.js + Express | 3008 | PostgreSQL |
| frontend | React + TypeScript | 3000 | — |

**Stacks used:** Node.js/Express · Python/FastAPI · Go/Gin

**Message broker:** Kafka (KRaft mode, no ZooKeeper)  
**Object storage:** MinIO  
**Monitoring:** Prometheus (9090) + Grafana (3100)

---

## Prerequisites

- Docker Engine ≥ 24
- Docker Compose V2 (`docker compose`, not `docker-compose`)
- `openssl` (for JWT key generation)

---

## Quick start

### 1. Generate JWT keys

```bash
bash config/jwt/keygen.sh
```

Creates `config/jwt/private.pem` and `config/jwt/public.pem`.  
`private.pem` is gitignored and never committed.

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env if you want non-default passwords
```

The defaults in `.env.example` work out of the box for local development.

### 3. Start infrastructure only (Phase 1 validation)

Bring up persistence, broker, object storage, and monitoring without application services:

```bash
docker compose up \
  postgres-auth postgres-catalog postgres-playlist \
  postgres-search postgres-analytics postgres-recommendation \
  postgres-notification kafka minio prometheus grafana \
  -d
```

### 4. Start the full stack

```bash
docker compose up -d
```

### 5. Run the load generator

```bash
docker compose --profile load up load-generator
```

---

## Phase 1 validation checklist

Run these after step 3 to confirm the shared environment is healthy.

```bash
# Compose file is valid
docker compose config --quiet

# Named network exists and all containers are attached
docker network inspect musicapp_musicapp-net

# Each postgres instance is ready
docker compose exec postgres-auth             pg_isready -U postgres
docker compose exec postgres-catalog          pg_isready -U postgres
docker compose exec postgres-playlist         pg_isready -U postgres
docker compose exec postgres-search           pg_isready -U postgres
docker compose exec postgres-analytics        pg_isready -U postgres
docker compose exec postgres-recommendation   pg_isready -U postgres
docker compose exec postgres-notification     pg_isready -U postgres

# Kafka broker is up and topic list is reachable
docker compose exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Prometheus health
curl -sf http://localhost:9090/-/healthy

# Grafana health
curl -sf http://localhost:3100/api/health

# MinIO health
curl -sf http://localhost:9000/minio/health/live
```

All commands should exit 0 or return a healthy response.

---

## Stopping and cleaning up

```bash
# Stop containers, keep volumes
docker compose down

# Stop and delete all volumes (wipes all database state)
docker compose down -v
```

---

## Resource limits

CPU and memory limits are defined per container in `docker-compose.yml` using YAML anchors:

| Anchor | Applies to | CPU | Memory |
|---|---|---|---|
| `&svc-limits` | Application services | 0.50 | 512 MB |
| `&db-limits` | PostgreSQL instances | 0.25 | 256 MB |
| `&infra-limits` | Kafka, MinIO, Prometheus, Grafana | 0.25 | 256 MB |

Adjust these values in `docker-compose.yml` before running benchmarks.

---

## Configuration reference

| File | Purpose |
|---|---|
| `.env` | Root environment — docker compose variable expansion |
| `.env.example` | Committed template for `.env` |
| `config/jwt/keygen.sh` | Generates RS256 key pair |
| `config/prometheus/prometheus.yml` | Prometheus scrape configuration |
| `config/grafana/provisioning/` | Grafana auto-provisioning (datasource + dashboards) |
| `services/<name>/.env.example` | Per-service variable reference |

---

## Implementation sessions

| Session | Deliverable | Status |
|---|---|---|
| 1 | Shared deployment environment + skeleton | done |
| 2 | Auth Service | pending |
| 3 | Catalog Service + seed logic | pending |
| 4 | Playlist Service | pending |
| 5 | Streaming Service | pending |
| 6 | Search Service | pending |
| 7 | Analytics Service | pending |
| 8 | Recommendation Service | pending |
| 9 | Notification Service | pending |
| 10 | Monitoring dashboards + load generator + integration fixes | pending |

---

## Manual changes

Any manual edits made after AI generation are marked in source with:

```
// MANUAL CHANGE: <reason>
```
