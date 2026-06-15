# benchmark-application

Cloud-native music streaming benchmark for cost-aware infrastructure research.

Eight Spring Boot microservices on Docker Compose. All traffic routes through an nginx load balancer. Prometheus + Grafana collect and visualise metrics. A k6 load generator reproduces six workload phases from smoke to 2000-VU soak.

---

## Architecture

| Service | Persistence | Responsibility |
|---|---|---|
| auth-service | PostgreSQL | JWT issuance (RSA-256), registration, login |
| catalog-service | PostgreSQL + OpenSearch | Song catalog, CSV seed, full-text indexing |
| streaming-service | Stateless | HLS manifest simulation, publishes `playback-events` to Kafka |
| playlist-service | PostgreSQL | Playlist CRUD, track reorder, publishes `playlist-events` to Kafka |
| search-service | OpenSearch | Full-text search, genre/BPM/year filters |
| analytics-service | ClickHouse | Consumes `playback-events`, batch insert, listening history, global charts |
| recommendation-service | PostgreSQL + Redis | Daily mix, similar songs, Redis cache with Resilience4j circuit breaker |
| notification-service | MongoDB | Consumes `playlist-events`, in-app notification inbox |

**Shared infrastructure:** nginx-lb (load balancer), Apache Kafka + Zookeeper, Prometheus v2.51.2, Grafana 10.4.2, PostgreSQL ×4, OpenSearch 2.13, ClickHouse 24.3, Redis 7.2, MongoDB 7.0.

All containers share the `music-net` Docker bridge network. Application services have no exposed host ports — all requests go through nginx-lb on port 80.

---

## Prerequisites

| Requirement | Minimum |
|---|---|
| Docker Engine | 24+ |
| Docker Compose | v2 (`docker compose version`) |
| Host RAM | 8 GB free (Profile A/B) |
| Free ports | 80, 2181, 9090, 3001, 5432–5435, 6379, 8123, 9000, 9200, 27017, 29092 |

---

## Quick start

### 1. Configure the environment

```bash
cp .env.example .env
# Edit .env to change credentials, ports, or resource limits
```

For a first run the defaults are sufficient.

### 2. Generate RSA keys (required once)

The eight services share a key pair for JWT signing and verification. Run this before the first `compose up`:

```bash
mkdir -p infra/keys
openssl genrsa -out infra/keys/private.pem 2048
openssl rsa -in infra/keys/private.pem -pubout -out infra/keys/public.pem
```

Keys are bind-mounted into every service container via the `jwt-keys` named volume, which is seeded by auth-service at startup.

### 3. Start the full stack

```bash
docker compose up -d
```

First run downloads images and compiles all eight services (Maven resolves ~400 MB of dependencies). Allow 10–15 minutes. Subsequent starts take under 2 minutes.

### 4. Wait for all services to be healthy

```bash
docker compose ps
```

All 8 application containers should show `healthy`. The `init-kafka` one-shot container will show `exited (0)` — that is expected.

### 5. Verify the stack

```bash
bash scripts/verify-integration.sh --keep-up
```

This runs a 7-step automated check: prerequisites → service health → JWT cross-service → Kafka event pipeline → k6 smoke run → Prometheus scrape targets → Grafana health. Exits 0 on full pass.

---

## Endpoints

All application endpoints are reached via nginx-lb on port 80. No direct service ports are exposed.

| Path prefix | Routed to | Auth required |
|---|---|---|
| `/auth/register`, `/auth/login` | auth-service | No |
| `/auth/**` | auth-service | Yes |
| `/catalog/**` | catalog-service | Yes |
| `/stream/**` | streaming-service | Yes |
| `/playlists/**` | playlist-service | Yes |
| `/search/**` | search-service | Yes |
| `/analytics/**` | analytics-service | Yes |
| `/recommendations/**` | recommendation-service | Yes |
| `/notifications` | notification-service | Yes |

| Monitoring endpoint | Description |
|---|---|
| `http://localhost:9090` | Prometheus UI |
| `http://localhost:3001` | Grafana (`admin` / `admin`) |
| `http://localhost:9200` | OpenSearch (direct) |
| `http://localhost:8123/ping` | ClickHouse HTTP ping |
| `http://localhost:29092` | Kafka external listener |

Grafana ships with two pre-built dashboards:
- **System Overview** — traffic, error rate, p99 latency, service health, Kafka lag, JVM heap
- **Scaling Decisions** — HikariCP saturation, Redis hit rate, OpenSearch heap, ClickHouse query latency

---

## Load generator

The k6 load generator is an opt-in compose profile. Six scenarios are available:

| Scenario | VUs | Duration | Purpose |
|---|---|---|---|
| `smoke` | 5 | 2 min | Quick stack health check |
| `streaming` | 50 | 5 min | Hot path only (auth + catalog + stream) |
| `full` | K6_VUS (default 50) | K6_DURATION (default 5m) | All 8 flows |
| `burst` | 0→500 | 15 min | Short spike |
| `ramp` | 0→K6_RAMP_TARGET (default 1000) | 20 min | Phase 4 sustained load |
| `soak` | 2000 constant | 120 min | Phase 6 memory/GC drift |

```bash
# Run a specific scenario (all traffic through nginx-lb)
K6_SCENARIO=smoke docker compose --profile load-test up --abort-on-container-exit load-generator

# Phase 5 (10 000 VUs) is isolated in a separate file — do not run without Profile D hardware
# k6 run load-generator/scripts/phase5-peak.js
```

Phase 5 requires ≥ 32 GB host RAM and Profile D replica counts. See `SCALABILITY.md §15`.

---

## Runtime profiles

Four resource profiles are defined in `SCALABILITY.md §2` and controlled by `.env` variables:

| Profile | Replicas | Host RAM | Purpose |
|---|---|---|---|
| A — Dev/CI | 1 each | ~8–9 GB | Default; local development and CI smoke tests |
| B — Smoke | 1 each + 5 VU k6 | ~9 GB | Quick regression gate |
| C — Calibration | 1 each + 50 VU k6, 10 min | ~10 GB | Baseline measurement |
| D — Benchmark | 2–10 each | ~20–22 GB | Full thesis load phases |

Switch profiles by editing the replica and resource limit variables in `.env`. Profile D values are documented as comments in `.env.example`.

---

## Running tests

### Unit and integration tests per service

```bash
cd services/<service-name>
mvn test
```

**Note:** Tests require Java 21+. On Java 22+ (including Java 26) the surefire argLine includes `--add-opens` flags and the `ByteBuddyMockMaker` mock maker is configured for services that mock Spring Data classes. If you run tests with a different JDK version, results may differ.

### System verification

```bash
bash scripts/verify-integration.sh         # starts, tests, tears down
bash scripts/verify-integration.sh --keep-up  # leaves the stack running
```

---

## Project structure

```
benchmark-application/
├── docker-compose.yml          # Full stack definition (30 services)
├── .env.example                # All tuneable variables with defaults
├── infra/
│   ├── nginx-lb/nginx.conf     # Reverse proxy + rate limiting
│   ├── prometheus/prometheus.yml
│   └── grafana/
│       ├── provisioning/       # Datasource auto-provisioning
│       └── dashboards/         # overview.json, scaling.json
├── load-generator/
│   ├── scripts/main.js         # k6 multi-scenario entrypoint
│   └── scripts/phase5-peak.js  # Phase 5 (10K VU) — isolated
├── scripts/
│   └── verify-integration.sh   # 7-step automated verification
└── services/
    ├── auth-service/
    ├── catalog-service/
    ├── streaming-service/
    ├── playlist-service/
    ├── search-service/
    ├── analytics-service/
    ├── recommendation-service/
    └── notification-service/
```

---

## Tearing down

```bash
# Stop containers and remove networks
docker compose down

# Also remove named volumes (destroys all persisted data)
docker compose down -v
```

---

## Implementation status

| Component | Status |
|---|---|
| Auth Service | Complete |
| Catalog Service | Complete |
| Streaming Service | Complete |
| Playlist Service | Complete |
| Search Service | Complete |
| Analytics Service | Complete |
| Recommendation Service | Complete |
| Notification Service | Complete |
| nginx load balancer | Complete |
| Prometheus + exporters | Complete |
| Grafana dashboards | Complete |
| k6 load generator (6 scenarios) | Complete |
| System verification script | Complete |
| Frontend | Not implemented |

See `PROGRESS.md` for the detailed phase-by-phase implementation log, `DESIGN-DECISIONS.md` for ordinary implementation decisions, and `COST-AWARE-DECISIONS.md` for all cloud cost trade-offs.
