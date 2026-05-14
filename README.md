# benchmark-application

Cloud-native music streaming benchmark application for cost-aware IaC research.

## What this application is

A cloud-native music streaming system implemented as eight independent microservices. Services cover user authentication, song catalog management, simulated streaming, playlist operations, full-text search, analytics, recommendations, and notifications. The architecture emphasises database-per-service isolation, a shared Kafka event bus, and a Prometheus/Grafana monitoring stack — all deployable with a single `docker compose up`.

---

## Architecture overview

| Service | Technology | Persistence | Port |
|---|---|---|---|
| auth-service | Java 21 / Spring Boot 3 | PostgreSQL | 8081 |
| catalog-service | Java 21 / Spring Boot 3 | PostgreSQL | 8082 |
| streaming-service | Java 21 / Spring Boot 3 | Stateless | 8083 |
| playlist-service | Java 21 / Spring Boot 3 | PostgreSQL | 8084 |
| search-service | Java 21 / Spring Boot 3 | OpenSearch | 8085 |
| analytics-service | Java 21 / Spring Boot 3 | ClickHouse | 8086 |
| recommendation-service | Java 21 / Spring Boot 3 | PostgreSQL + Redis | 8087 |
| notification-service | Java 21 / Spring Boot 3 | MongoDB | 8088 |
| frontend | React + TypeScript (Vite) | — | 3000 |

**Shared infrastructure:** Apache Kafka (+ Zookeeper), Prometheus, Grafana.

All containers share the named Docker network `music-net`.

---

## Prerequisites

- Docker Engine 24+ with Docker Compose v2 (`docker compose version`)
- At least 8 GB of free RAM for the full stack
- Ports listed above plus 2181, 9090, 3001, 9200, 8123, 6379, 27017, 29092 available on the host

---

## Phase 0 — Shared deployment environment (current phase)

Phase 0 scaffolds the full repository and starts all infrastructure containers. Application service images are minimal Spring Boot stubs that respond to `/actuator/health`. Full service business logic is added in Phases 1–9.

### 1. Configure the environment

```bash
cp .env.example .env
# Edit .env if you need to change any port or credential
```

### 2. Start infrastructure only (fastest validation)

```bash
docker compose up -d \
  zookeeper kafka \
  auth-db catalog-db playlist-db recommendation-db \
  opensearch clickhouse redis mongodb \
  prometheus grafana
```

Wait for all containers to be healthy:

```bash
docker compose ps
```

### 3. Start the full skeleton (infrastructure + placeholder app services)

```bash
docker compose up -d
```

This builds the placeholder Spring Boot stubs from source (requires Maven to download dependencies — allow 5–10 minutes on first run) and starts all containers.

### 4. Run the load generator (opt-in)

```bash
docker compose --profile load-test up load-generator
```

---

## Service endpoints (Phase 0)

| Endpoint | Description |
|---|---|
| `http://localhost:808{1-8}/actuator/health` | Spring Boot health for each service |
| `http://localhost:808{1-8}/actuator/prometheus` | Prometheus metrics for each service |
| `http://localhost:9090` | Prometheus UI |
| `http://localhost:3001` | Grafana (admin / admin) |
| `http://localhost:3000` | Frontend placeholder |
| `localhost:29092` | Kafka (external / host access) |
| `http://localhost:9200` | OpenSearch |
| `http://localhost:8123/ping` | ClickHouse HTTP ping |

---

## Phase 0 validation checklist

Run these commands to confirm Phase 0 is working correctly.

```bash
# 1. Compose file parses without errors
docker compose config > /dev/null && echo "PASS: compose config valid"

# 2. Named network exists
docker network inspect music-net --format '{{.Name}}' | grep -q music-net && echo "PASS: music-net exists"

# 3. All infrastructure containers healthy
docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep -v "unhealthy"

# 4. Prometheus is up
curl -sf http://localhost:9090/-/healthy && echo "PASS: Prometheus healthy"

# 5. Grafana is up
curl -sf http://localhost:3001/api/health | grep -q ok && echo "PASS: Grafana healthy"

# 6. Kafka broker reachable from within the network
docker compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 && echo "PASS: Kafka broker API responding"

# 7. OpenSearch cluster status
curl -sf http://localhost:9200/_cluster/health | python3 -m json.tool | grep status

# 8. ClickHouse ping
curl -sf http://localhost:8123/ping && echo "PASS: ClickHouse responding"

# 9. Redis ping
docker compose exec redis redis-cli ping && echo "PASS: Redis pong"

# 10. MongoDB ping
docker compose exec mongodb mongosh --username mongouser --password mongopass \
  --authenticationDatabase admin --eval 'db.runCommand({ping:1})' --quiet
```

---

## Environment configuration

All tuneable values live in `.env` (copied from `.env.example`). Key variables:

| Variable | Default | Purpose |
|---|---|---|
| `*_CPU_LIMIT` / `*_MEMORY_LIMIT` | 1.0 / 512m | Per-service resource caps (M-20) |
| `JWT_EXPIRATION_MS` | 3600000 | JWT lifetime in ms |
| `STREAM_SEGMENT_SIZE_BYTES` | 65536 | Simulated HLS segment size |
| `K6_VUS` / `K6_DURATION` | 10 / 60s | Load generator concurrency and duration |
| `KAFKA_HOST_PORT` | 29092 | External Kafka port on the host |

---

## Tearing down

```bash
# Stop and remove containers, networks
docker compose down

# Also remove all named volumes (destroys persisted data)
docker compose down -v
```

---

## Phase 6 — Analytics Service

Consumes `playback-events` from Kafka and stores them in ClickHouse. Exposes two protected endpoints:

| Endpoint | Description |
|---|---|
| `GET /analytics/me/history` | Authenticated user's listening history (newest first, capped by `ANALYTICS_HISTORY_LIMIT`) |
| `GET /analytics/charts/global` | Global top-50 chart ranked by `play.started` event count |

### Running the tests

```bash
# From the project root — requires Docker socket mounted for ClickHouse Testcontainer
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD/services/analytics-service":/app \
  -w /app \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9-eclipse-temurin-21-alpine \
  mvn test
```

### Building the Docker image

```bash
cd services/analytics-service
docker build -t analytics-service .
```

### Configuration

| Variable | Default | Description |
|---|---|---|
| `CLICKHOUSE_HOST` | `clickhouse` | ClickHouse hostname |
| `CLICKHOUSE_HTTP_PORT` | `8123` | ClickHouse HTTP port |
| `CLICKHOUSE_DB` | `analyticsdb` | Target database |
| `CLICKHOUSE_USER` | `analyticsuser` | ClickHouse user |
| `CLICKHOUSE_PASSWORD` | `analyticspass` | ClickHouse password |
| `KAFKA_TOPIC_PLAYBACK_EVENTS` | `playback-events` | Topic consumed from Streaming Service |
| `KAFKA_CONSUMER_GROUP_ID` | `analytics-service` | Kafka consumer group |
| `ANALYTICS_HISTORY_LIMIT` | `100` | Max history entries per response |
| `JWT_PUBLIC_KEY_PATH` | `/jwt-keys/public.pem` | RSA public key for JWT verification |

---

## Implementation status

| Phase | Scope | Status |
|---|---|---|
| 0 | Shared deployment environment + skeleton | **Complete** |
| 1 | Auth Service | Pending |
| 2 | Catalog Service | Pending |
| 3 | Playlist Service | Pending |
| 4 | Streaming Service | Pending |
| 5 | Search Service | Pending |
| 6 | Analytics Service | **Complete** |
| 7 | Recommendation Service | Pending |
| 8 | Notification Service | Pending |
| 9 | Frontend | Pending |
| 10 | Monitoring, load generator, integration | Pending |
