# Auth Service

Spring Boot Auth Service for registration, login, and asymmetric JWT issuance.

## Stack

* Java 21
* Spring Boot 3.x
* Maven
* PostgreSQL
* Flyway
* Spring Security with RS256 JWT signing
* Actuator and Prometheus metrics

## Public API

### `POST /auth/register`

Creates a user account and returns a JWT access token.

```json
{
  "email": "user@example.com",
  "password": "CorrectHorse123"
}
```

### `POST /auth/login`

Authenticates an existing user and returns a JWT access token.

```json
{
  "email": "user@example.com",
  "password": "CorrectHorse123"
}
```

Successful responses include:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "user": {
    "id": "<uuid>",
    "email": "user@example.com"
  }
}
```

## Configuration

See `.env.example` for required environment variables. Auth Service needs the private key at `JWT_PRIVATE_KEY_PATH`; other services should only receive the public key at `JWT_PUBLIC_KEY_PATH`.

## Validation

From this directory, run tests with Maven:

```powershell
mvn test
```

From the repository root, build and start Auth with Compose:

```powershell
docker compose up -d --build auth-db auth-service
```

Then exercise the endpoints through `http://localhost:8081/auth/register` and `http://localhost:8081/auth/login`.
