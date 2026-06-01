# Notification Service

Internal in-app notification service for the benchmark application.

## Runtime

The service is a Java 21 Spring Boot application. It consumes playlist update events from Kafka and stores resulting in-app notifications in MongoDB.

Minimum exposed HTTP interfaces:

- `GET /actuator/health`
- `GET /actuator/prometheus`

No public client-facing notification inbox API is implemented in this phase.

## Event Contract

The service consumes JSON events from `NOTIFICATION_PLAYLIST_EVENTS_TOPIC`.

Supported event type:

- `playlist.updated`

Expected fields:

- `eventId`
- `eventType`
- `actorUserId`
- `recipientUserIds`
- `playlistId`
- `playlistName`
- `occurredAt`
- `metadata`

Malformed events and unsupported event types are skipped. Duplicate `eventId` values do not create duplicate notifications.

## Local Commands

```bash
docker compose build notification-service
docker compose up -d kafka notification-db notification-service
curl -s http://localhost:8088/actuator/health
```

## Configuration

See `.env.example` for service-specific environment variables.
