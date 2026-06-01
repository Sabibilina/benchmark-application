# Playlist Service

Playlist Service manages user-owned playlists, ordered playlist tracks, and each user's special `Liked Songs` playlist.

## Stack

- Java 21
- Spring Boot 3.3
- Maven
- PostgreSQL with Flyway migrations
- Spring Security JWT resource server
- Actuator and Prometheus metrics

## Endpoints

All application endpoints require `Authorization: Bearer <jwt>`.

- `GET /playlists`
- `POST /playlists`
- `GET /playlists/{id}`
- `PATCH /playlists/{id}`
- `DELETE /playlists/{id}`
- `POST /playlists/{id}/tracks`
- `DELETE /playlists/{id}/tracks/{songId}`
- `PATCH /playlists/{id}/tracks/reorder`

Operational endpoints:

- `GET /actuator/health`
- `GET /actuator/prometheus`

## Local Validation

Run the service tests:

```bash
docker compose build playlist-service
```

Run the service with its database:

```bash
docker compose up -d --build playlist-db playlist-service
```

The service listens on `http://localhost:8084` through the root Compose file.
