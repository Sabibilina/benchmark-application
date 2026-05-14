# Analytics Service

Spring Boot service that stores playback history and computes chart-oriented analytics from Kafka playback events.

## Runtime Interfaces

Protected endpoints:

* `GET /analytics/me/history`
* `GET /analytics/charts/global`

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

## Persistence and Messaging

Analytics consumes `PlaybackEvent` JSON messages from Kafka topic `ANALYTICS_PLAYBACK_EVENTS_TOPIC` and stores them in its own ClickHouse table. The expected event fields are `eventId`, `type`, `userId`, `songId`, and `timestamp`. The consumer ignores producer type headers and deserializes valid JSON into the local Analytics `PlaybackEvent` type so older Streaming type headers do not block ingestion. Malformed records are skipped by the Kafka error handler.

Global charts count `play.started` events by song id. Listen history returns stored `play.started`, `play.ended`, and `play.skipped` events for the authenticated JWT subject. The canonical user identity is the Auth-issued UUID in JWT `sub`; manual smoke data must use that same UUID in both the JWT subject and the stored playback event `userId`.

## Local Commands

From the repository root:

```powershell
docker compose build analytics-service
docker compose up -d --build kafka analytics-db analytics-service
```

Run tests through the Docker build or directly from this directory:

```powershell
mvn test
```
