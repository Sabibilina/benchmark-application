# Catalog Service

Manages the song catalog. Exposes paginated browsing and single-song retrieval. Automatically ingests 85,000 songs from the bundled Kaggle dataset on first startup.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/catalog/songs` | JWT | Paginated song list |
| `GET` | `/catalog/songs/{id}` | JWT | Single song by ID |
| `GET` | `/catalog/artists/{artistId}/top-tracks` | JWT | Top 10 tracks for an artist |
| `GET` | `/actuator/health` | Public | Health check |
| `GET` | `/actuator/prometheus` | Public | Prometheus metrics |

### Pagination parameters (`GET /catalog/songs`)

| Parameter | Default | Notes |
|-----------|---------|-------|
| `page` | `0` | 0-based |
| `size` | `20` | Max 100 (server-enforced) |
| `sort` | `id` | Allowed: `id`, `title`, `artist`, `year`, `popularity`, `tempo` |
| `direction` | `asc` | `asc` or `desc` |

### Artist ID

The `artistId` path parameter for `/catalog/artists/{artistId}/top-tracks` is computed as the first 16 hex characters of `SHA-256(lowercase(artistName))`. The `artistId` field is returned in every `SongResponse`.

## Running locally

```bash
# Start just the database and service
docker compose up catalog-db catalog-service

# Health check
curl http://localhost:8082/actuator/health

# List songs (requires JWT from auth-service)
TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"pass"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/catalog/songs
```

## Dataset seed

The dataset (`src/main/resources/data/songs.csv`, 85,000 rows) is bundled in the JAR and ingested on first startup. The seed is idempotent — it checks `SELECT COUNT(*) FROM songs` before inserting and skips if the table already has data.

**Note:** Seeding runs after the web server starts. There is a short window (~10 seconds) after the container becomes healthy where the songs table may still be empty.

## Running tests

```bash
mvn test          # unit tests only (no Docker required)
mvn verify        # unit + integration tests (Docker required for Testcontainers)
```

Integration tests use Testcontainers to spin up a real PostgreSQL instance automatically. No external infrastructure is needed.
