# Auth Service

Handles user registration, login, and JWT issuance for the music-streaming system.

## Endpoints

| Method | Path             | Auth | Description                        |
|--------|------------------|------|------------------------------------|
| POST   | /auth/register   | none | Create account; returns JWT        |
| POST   | /auth/login      | none | Authenticate; returns JWT          |

Both endpoints return `{ "token": "<RS256 JWT>" }` on success.

### Register

```
POST /auth/register
Content-Type: application/json

{ "username": "alice", "email": "alice@example.com", "password": "secret123" }
```

Responses: `201 Created` with token · `400` validation error · `409` username or email taken

### Login

```
POST /auth/login
Content-Type: application/json

{ "username": "alice", "password": "secret123" }
```

Responses: `200 OK` with token · `400` missing fields · `401` invalid credentials

## JWT

Tokens are signed with **RS256** (2048-bit RSA). On first startup the service generates a key pair and writes `private.pem` and `public.pem` to the directory configured by `JWT_KEY_DIR`. All other services that need to validate tokens read `public.pem` from the same shared volume.

Claims: `sub` (user UUID), `username`, `iat`, `exp`.

## Configuration

| Environment variable       | Default                                      | Description               |
|----------------------------|----------------------------------------------|---------------------------|
| `SPRING_DATASOURCE_URL`    | `jdbc:postgresql://localhost:5432/authdb`    | PostgreSQL JDBC URL       |
| `SPRING_DATASOURCE_USERNAME` | `authuser`                                 | DB username               |
| `SPRING_DATASOURCE_PASSWORD` | `authpass`                                 | DB password               |
| `JWT_KEY_DIR`              | `/jwt-keys`                                  | RSA key pair directory    |
| `JWT_EXPIRATION_MS`        | `3600000`                                    | Token TTL in milliseconds |

See `.env.example` for local-run defaults.

## Database

PostgreSQL with Flyway-managed schema. Migration: `V1__create_users_table.sql`. JPA validates the schema on startup; it never modifies it.

## Running

### With Docker Compose (recommended)

```bash
docker compose up auth-db auth-service
```

The service starts on host port `8081` (container `8080`). Health check: `GET /actuator/health`.

### Locally

```bash
cd services/auth-service
# start a local postgres or point SPRING_DATASOURCE_URL at a running instance
export JWT_KEY_DIR=/tmp/auth-service-keys
mvn spring-boot:run
```

## Tests

```bash
mvn test                  # unit tests only (no Docker required)
mvn verify                # unit + integration tests (Docker required for Testcontainers)
```

Integration tests spin up a PostgreSQL container automatically via Testcontainers. No external database is needed.
