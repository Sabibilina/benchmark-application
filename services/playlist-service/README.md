# Playlist Service

Manages user playlists and playlist track operations. Emits playlist update events to Kafka for downstream consumers (Notification Service).

## Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.4 |
| Database | PostgreSQL (dedicated `playlist-db`) |
| Schema | Flyway (V1 playlists, V2 playlist_tracks) |
| Messaging | Apache Kafka producer (`playlist-events` topic) |
| JWT | JJWT 0.12.5, RS256 verification via shared public key |

## Endpoints

All endpoints require `Authorization: Bearer <jwt>`. Only `/actuator/**` is public.

| Method | Path | Description |
|---|---|---|
| `GET` | `/playlists` | List authenticated user's playlists (auto-creates Liked Songs) |
| `POST` | `/playlists` | Create a new playlist |
| `GET` | `/playlists/:id` | Get a single playlist with tracks |
| `PATCH` | `/playlists/:id` | Rename a playlist |
| `DELETE` | `/playlists/:id` | Delete a playlist (Liked Songs cannot be deleted) |
| `POST` | `/playlists/:id/tracks` | Add a track to a playlist |
| `DELETE` | `/playlists/:id/tracks/:songId` | Remove a track from a playlist |
| `PATCH` | `/playlists/:id/tracks/reorder` | Reorder all tracks in a playlist |

## Configuration

Copy `.env.example` and adjust as needed. Key variables:

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://playlist-db:5432/playlistdb` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `playlistuser` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `playlistpass` | DB password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker |
| `JWT_PUBLIC_KEY_PATH` | `/jwt-keys/public.pem` | RSA public key path |
| `PLAYLIST_KAFKA_TOPIC` | `playlist-events` | Kafka topic for events |

## Running via Docker Compose

```bash
docker compose up playlist-service playlist-db
```

Health check: `http://localhost:8084/actuator/health`

Metrics: `http://localhost:8084/actuator/prometheus`

## Running Tests

```bash
cd services/playlist-service
mvn test
```

Requires Docker (Testcontainers starts PostgreSQL and Kafka automatically).

## Special Behaviour

- **Liked Songs**: A permanent per-user playlist created lazily on the first `GET /playlists`. It cannot be deleted (`400`) or renamed (`400`). Creating a playlist named "Liked Songs" is also rejected (`400`).
- **Track deduplication**: Adding the same song twice to a playlist returns `409 Conflict`.
- **Reorder**: `PATCH /:id/tracks/reorder` expects the complete ordered list of song IDs currently in the playlist. Partial lists return `400`.
- **Ownership**: All operations validate that the playlist belongs to the authenticated user. Accessing another user's playlist returns `404` (not `403`) to prevent information leakage.
