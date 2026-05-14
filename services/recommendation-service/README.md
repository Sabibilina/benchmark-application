# Recommendation Service

Generates personalised daily-mix and song-based recommendations from observed listening behaviour.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/recommend/daily-mix` | JWT | Personalised mix based on user play history |
| GET | `/recommend/similar/{songId}` | JWT | Songs similar in genre and tempo to the given song |
| GET | `/actuator/health` | public | Liveness / readiness |
| GET | `/actuator/prometheus` | public | Prometheus metrics |

## Technology stack

Java 21 · Spring Boot 3.3.4 · PostgreSQL 16 (JPA + Flyway) · Redis 7.2 (recommendation cache) · Apache Kafka (consumer of `playback-events`) · JJWT 0.12.5 (RS256 JWT verification)

## Configuration

Copy `.env.example` to `.env` and adjust as needed. Key variables:

| Variable | Default | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5435/recommendationdb` | |
| `SPRING_DATA_REDIS_HOST` | `localhost` | |
| `JWT_PUBLIC_KEY_PATH` | `/jwt-keys/public.pem` | Written by auth-service at startup |
| `KAFKA_TOPIC_PLAYBACK_EVENTS` | `playback-events` | Must match streaming-service |
| `RECOMMENDATION_DAILY_MIX_SIZE` | `20` | Tune for benchmarking |
| `RECOMMENDATION_SIMILAR_SIZE` | `10` | Tune for benchmarking |
| `RECOMMENDATION_CACHE_TTL_SECONDS` | `3600` | Redis TTL in seconds |

## Running locally (via Docker Compose)

```bash
docker compose up recommendation-db redis kafka auth-service recommendation-service
```

## Running tests

```bash
# Inside this directory — requires Docker socket for Testcontainers
docker run --rm \
  -v "$(pwd)":/app \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app \
  maven:3.9-eclipse-temurin-21-alpine \
  mvn test
```

## Recommendation logic

**Daily Mix**: looks at `play.started` events in the last 30 days to determine the user's preferred genre. Samples songs from that genre, excluding already-played titles. Falls back to globally popular songs, then to random songs for cold-start users.

**Similar Songs**: finds songs with the same genre and BPM within ±20 of the target. Fills remaining slots with random same-genre songs. Falls back to random songs if the `songId` is not in the local catalog.

Both responses are cached in Redis with a configurable TTL. Cache misses degrade gracefully — the service always queries PostgreSQL as a fallback.
