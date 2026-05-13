# Catalog Service

Spring Boot Catalog Service for song metadata browsing, single-song lookup, and automatic startup ingestion into a dedicated PostgreSQL database.

## Stack

* Java 21
* Spring Boot 3.x
* Maven
* PostgreSQL
* Flyway
* Spring Security resource server with RS256 JWT verification
* Actuator and Prometheus metrics

## Protected API

### `GET /catalog/songs`

Returns a paginated list of songs.

Query parameters:

* `page` - zero-based page number, default `0`
* `size` - page size, default from `CATALOG_DEFAULT_PAGE_SIZE`

### `GET /catalog/songs/{id}`

Returns detailed metadata for a single song, or `404` when the song is unknown.

## Dataset

The service reads a CSV from `CATALOG_DATASET_PATH` during startup. The generated container includes a small runnable seed file at `/app/data/catalog.csv`; replace or mount that path with the required Kaggle Spotify Music Analytics dataset CSV for full benchmark data.

## Configuration

See `.env.example` for service variables. Catalog application endpoints require an `Authorization: Bearer <jwt>` header signed by Auth Service and verifiable with `JWT_PUBLIC_KEY_PATH`.

## Validation

From the repository root:

```powershell
docker compose build catalog-service
docker compose up -d --build catalog-db catalog-service
```

Then call `http://localhost:8082/catalog/songs` and `http://localhost:8082/catalog/songs/{id}` with a valid bearer token.
