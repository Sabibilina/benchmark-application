# benchmark-application

Cloud-native music streaming benchmark application for cost-aware IaC research.

Eight independent microservices, a shared Kafka event bus, and a full Prometheus/Grafana
monitoring stack — all deployable with a single `docker compose up`.

---

## Architecture

| Service | Persistence | Description |
|---|---|---|
| auth-service | PostgreSQL | JWT-based registration and login |
| catalog-service | PostgreSQL | Paginated song catalog and artist top-tracks |
| streaming-service | Stateless | Simulated HLS manifest + segment delivery |
| playlist-service | PostgreSQL | Playlist CRUD, track ordering, Kafka producer |
| search-service | OpenSearch | Full-text song search |
| analytics-service | ClickHouse | Playback event ingestion and charts |
| recommendation-service | PostgreSQL + Redis | Daily mix and similar-songs recommendations |
| notification-service | MongoDB | Playlist-event-driven notification feed |
| frontend | — | React SPA served by nginx |

**Infrastructure:** Kafka + Zookeeper, Prometheus, Grafana, nginx load balancer.

All traffic enters through **nginx-lb on port 80**. Individual service ports are not exposed by
default; use `docker-compose.dev.yml` when you need direct host access (e2e tests).

---

## Prerequisites

- Docker Engine 24+ with Compose v2 — confirm with `docker compose version`
- At least 16 GB free RAM for the full scaled stack (8 services × up to 3 replicas + infrastructure)
- Ports free on host: `80`, `3000`, `3001`, `9090`, `9200`, `8123`, `29092`,
  `5432–5435`, `6379`, `27017`

---

## 1. Start the application

```bash
cp .env.example .env
docker compose up --build -d
```

First build downloads Maven dependencies and Docker base images — allow 10–15 minutes.
Subsequent starts take 2–3 minutes.

Check that everything is up:

```bash
docker compose ps
```

All services should show `healthy`. Services that consume Kafka (`streaming-service`,
`analytics-service`, `recommendation-service`, `notification-service`) wait for
`init-kafka` to finish creating topics before starting.

**Open in browser:**
- Frontend: http://localhost:3000
- Grafana (admin / admin): http://localhost:3001
- Prometheus: http://localhost:9090

---

## 2. Run per-service tests (unit + integration)

Each service has its own unit tests and Testcontainer-based integration tests.
**The full Docker Compose stack does not need to be running.** Docker daemon must be running.

Run all eight services in sequence:

```bash
cd /Users/Sabina/Desktop/Thesis/Iteration01/benchmark-application && \
for svc in auth-service catalog-service streaming-service playlist-service search-service analytics-service recommendation-service notification-service; do
  echo "====== $svc ======"
  (cd services/$svc && mvn verify -q) && echo "✓ $svc PASSED" || echo "✗ $svc FAILED"
done
```

Run a single service:

```bash
cd services/auth-service && mvn verify
```

Unit tests only (no Docker required):

```bash
cd services/auth-service && mvn test -Dtest="**/*Test"
```

> Testcontainers pulls database images on first run (PostgreSQL, ClickHouse, OpenSearch, etc.).
> This is slow once; subsequent runs use the cached images.

---

## 3. Run end-to-end tests

The e2e tests use REST Assured and call live services on `localhost:8081–8088`.
Because services do not expose host ports in the default compose file, use the
`docker-compose.dev.yml` overlay which adds them back.

**Terminal 1 — start the stack with ports exposed:**

```bash
cd /Users/Sabina/Desktop/Thesis/Iteration01/benchmark-application && \
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

Wait until all services are healthy:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
```

**Terminal 2 — run the e2e tests:**

```bash
cd /Users/Sabina/Desktop/Thesis/Iteration01/benchmark-application/e2e-tests && \
mvn test
```

Tear down when done:

```bash
cd /Users/Sabina/Desktop/Thesis/Iteration01/benchmark-application && \
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
```

---

## 4. Run load tests (k6)

The load generator runs inside Docker and routes all traffic through nginx-lb.
The full stack must be running (standard `docker compose up`, no dev overlay needed).

Default scenario (`ramp` — scales from 0 to 500 virtual users):

```bash
cd /Users/Sabina/Desktop/Thesis/Iteration01/benchmark-application && \
docker compose --profile load-test up --build
```

Choose a specific scenario:

```bash
# smoke — 5 VUs for 2 minutes (quick sanity check)
K6_SCENARIO=smoke docker compose --profile load-test up

# catalog_stream — 50 VUs for 5 minutes (auth + browse + stream)
K6_SCENARIO=catalog_stream docker compose --profile load-test up

# kafka_pipeline — 200 VUs for 10 minutes (full flow including playlists)
K6_SCENARIO=kafka_pipeline docker compose --profile load-test up

# stress — ramps to 2000 VUs (drives services past comfortable limits)
K6_SCENARIO=stress docker compose --profile load-test up

# soak — 100 VUs for 2 hours (catches memory leaks and Kafka lag)
K6_SCENARIO=soak docker compose --profile load-test up
```

Watch metrics in real time at http://localhost:3001 (Grafana → **Music Streaming — Scaling Dashboard**).

---

## 5. Scale individual services

Services with `container_name` removed support `--scale`. The load balancer picks up
new replicas within 5 seconds via Docker DNS re-resolution.

```bash
# Scale streaming to 6 replicas (highest traffic service)
docker compose up -d --scale streaming-service=6

# Scale catalog and search together
docker compose up -d --scale catalog-service=4 --scale search-service=4

# Check running replicas
docker compose ps streaming-service
```

> `auth-service` is intentionally excluded from scaling on a fresh volume.
> Start the stack once, wait for it to be healthy (RSA keys are generated),
> then scale: `docker compose up -d --scale auth-service=4`

---

## 6. Tear down

```bash
# Stop containers and remove network
docker compose down

# Also delete all persisted data (databases, Kafka logs, etc.)
docker compose down -v
```

---

## Key configuration

All tuneable values are in `.env` (copied from `.env.example`).

| Variable | Default | Effect |
|---|---|---|
| `STREAMING_SERVICE_REPLICAS` | 3 | Default replica count at `up` |
| `KAFKA_SCENARIO` | `ramp` | k6 load test scenario |
| `K6_VUS` | 50 | Virtual users for fixed-VU scenarios |
| `ANALYTICS_BATCH_SIZE` | 500 | ClickHouse insert batch size |
| `REDIS_MAXMEMORY` | `512mb` | Redis cache memory ceiling |
| `OPENSEARCH_JAVA_OPTS` | `-Xms1g -Xmx1g` | OpenSearch JVM heap |
| `PLAYBACK_EVENTS_PARTITIONS` | 12 | Kafka topic partition count (set before first boot) |
