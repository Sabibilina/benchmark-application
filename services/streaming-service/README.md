# Streaming Service

Streaming Service returns simulated stream descriptors, generated dummy segment payloads, and playback interaction events for benchmarking.

## Stack

- Java 21
- Spring Boot 3.3
- Maven
- Spring Security JWT resource server
- Spring Kafka producer
- Actuator and Prometheus metrics

## Endpoints

All application endpoints require `Authorization: Bearer <jwt>`.

- `GET /stream/{songId}`
- `GET /stream/{songId}/segments/{segmentIndex}`
- `POST /stream/{songId}/ended`
- `POST /stream/{songId}/skipped`

Operational endpoints:

- `GET /actuator/health`
- `GET /actuator/prometheus`

## Local Validation

Run the service tests:

```bash
docker compose build streaming-service
```

Run the service with Kafka:

```bash
docker compose up -d --build kafka streaming-service
```

The service listens on `http://localhost:8083` through the root Compose file.
