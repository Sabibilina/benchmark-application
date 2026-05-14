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

Analytics consumes `PlaybackEvent` JSON messages from Kafka topic `ANALYTICS_PLAYBACK_EVENTS_TOPIC` and stores them in its own ClickHouse table. The expected event fields are `eventId`, `type`, `userId`, `songId`, and `timestamp`.

Global charts count `play.started` events by song id. Listen history returns stored events for the authenticated JWT subject.

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
