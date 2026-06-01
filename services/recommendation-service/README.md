# Recommendation Service

Spring Boot service that consumes playback events and returns simple personalized and song-based recommendations.

## Runtime Interfaces

Protected endpoints:

* `GET /recommend/daily-mix`
* `GET /recommend/similar/{songId}`

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

## Persistence, Cache, and Messaging

Recommendation stores durable interaction and song-affinity state in PostgreSQL. Redis is used as a recomputable response cache for daily mixes and similar-song lookups.

The service consumes playback event JSON from Kafka topic `RECOMMENDATION_PLAYBACK_EVENTS_TOPIC`. Valid events use fields `eventId`, `type`, `userId`, `songId`, and `timestamp`. `play.started` and `play.ended` are treated as positive signals for recommendation ranking; `play.skipped` is persisted but not used as a positive affinity signal.

Kafka producer type headers are ignored and valid JSON is deserialized into the local Recommendation `PlaybackEvent` type. Malformed records are skipped so one bad event cannot block later valid events.

## Local Commands

From the repository root:

```bash
docker compose build recommendation-service
docker compose up -d --build kafka recommendation-db recommendation-redis recommendation-service
```

Run tests through the Docker build or directly from this directory:

```bash
mvn test
```
