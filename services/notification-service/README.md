# Notification Service

Consumes playlist update events from Kafka and persists in-app notifications to MongoDB. Exposes a JWT-protected endpoint for retrieving a user's notifications.

## Stack

| Component | Technology |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3.4 |
| Persistence | MongoDB 7.0 |
| Messaging | Apache Kafka (consumer) |
| Auth | RS256 JWT (shared public key) |

## Endpoint

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/notifications` | JWT required | Returns the authenticated user's notifications, newest first |
| `GET` | `/actuator/health` | public | Health check |
| `GET` | `/actuator/prometheus` | public | Prometheus metrics |

## Events Consumed

Topic: `playlist-events` (configurable via `KAFKA_TOPIC_PLAYLIST_EVENTS`)

All six playlist event types produce a stored notification: `PLAYLIST_CREATED`, `PLAYLIST_UPDATED`, `PLAYLIST_DELETED`, `TRACK_ADDED`, `TRACK_REMOVED`, `TRACKS_REORDERED`.

## Running Tests

```bash
docker run --rm \
  -v "$(pwd)":/app \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app \
  maven:3.9-eclipse-temurin-21-alpine \
  mvn test
```

## Building the Image

```bash
docker build -t notification-service .
```

## Environment Variables

See `.env.example` for all configurable variables. The `JWT_PUBLIC_KEY_PATH` must point to the RSA public key written by Auth Service (mounted via the `jwt-keys` Docker volume).
