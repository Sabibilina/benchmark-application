# Technology Stack Document

## Purpose

This document defines the implementation stack for each backend microservice and the shared infrastructure of the cloud-native music streaming benchmark system.

It translates the frozen system shape from `ARCHITECTURE.md` and `REQUIREMENTS.md` into concrete technology choices for implementation.

## Selection Principles

- Prefer widely used, production-proven technologies.
- Prefer stacks the team already knows well, so AI-generated code can be reviewed, corrected, and repaired quickly.
- Keep the backend stack as consistent as possible across services to simplify maintenance and benchmarking.
- Use the most suitable persistence model per service, but only where it provides a clear benefit.
- Choose infrastructure that supports observability, resilience, and repeatable benchmark runs.

## 1. Shared Backend Standard

### Primary Backend Stack

All backend microservices should use the same primary application stack:

- **Language:** Java 21 LTS
- **Framework:** Spring Boot 3.x
- **Build tool:** Maven
- **Containerization:** Docker
- **Local orchestration:** Docker Compose

### Why Java + Spring Boot

Java + Spring Boot is the preferred backend stack for all services for the following reasons:

- It is a very popular and well-supported enterprise stack with a large ecosystem.
- It is a strong fit for microservice architectures because it offers mature support for HTTP APIs, security, data access, observability, resilience, and messaging.
- The team is already familiar with Java and Spring Boot, which makes AI-generated code easier to inspect, manually verify, debug, and repair.
- The consistency of using one backend stack across services reduces operational overhead during benchmarking.
  
## 2. Shared Infrastructure

### Observability

- **Prometheus** should be used for metrics scraping and time-series collection.
- **Grafana** should be used as the observability dashboard layer.
- This combination is the most practical choice because it is standard, well documented, easy to run in Docker Compose, and directly aligned with the monitoring requirements of the system.

### Load Generation

- **k6** is recommended as the load generator.
- It is lightweight, scriptable, container-friendly, and well suited for repeatable benchmark scenarios.

### Networking and Runtime

- Use a single named Docker network shared by all application services and infrastructure services.
- Keep CPU and memory limits configurable per service in `docker-compose.yml` for benchmark experiments.

## 3. Messaging Infrastructure per Service

### Messaging choice: Apache Kafka

- **Apache Kafka** should be the shared event backbone for internal asynchronous communication.
- Kafka is required because the system emits and consumes internal playback and application events across multiple services.
- Kafka is a strong choice for benchmark systems because it is widely adopted, durable, scalable, and easy to observe under load.

### 3.1. Streaming Service
**Messaging choice: Kafka producer**
- Publish `play.started`, `play.ended`, and `play.skipped` events to Kafka.
- Kafka is the right fit because these events are consumed asynchronously by Analytics, Recommendation, and Notification-related flows.

### 3.2. Playlist Service
**Messaging choice: Kafka producer if playlist update events are used**
- Publish playlist update events when playlist mutations occur, when this event source is used for notifications.

### 3.3. Analytics Service
**Messaging choice: Kafka consumer**
- Consume playback events from Kafka and persist them into analytics tables.
- This keeps the write path decoupled from the Streaming Service and models a realistic event-driven architecture.

### 3.4. Recommendation Service
**Messaging choice: Kafka consumer**
- Consume playback events from Kafka to update recommendation inputs asynchronously.
- This allows recommendation logic to evolve without coupling it directly to streaming request latency.

### 3.5. Notification Service
**Messaging choice: Kafka consumer**
- Consume playlist update events and, if implemented, other internal application events such as new-release notifications.
- Persist the resulting notification documents in the Notification Service storage for backend retrieval in a later version.

## 4. Database and Persistence per Service

### 4.1. Auth Service

**Database choice: PostgreSQL**
- Use **PostgreSQL** as a relational database for users, password hashes, roles if needed, and authentication-related state.
- PostgreSQL is the best fit here because authentication data is strongly structured, relational, and consistency-sensitive.
- It supports transactions, uniqueness constraints, indexing, and straightforward manual inspection.
- It is also extremely popular, reliable, and easy to operate in Docker Compose.

### 4.2. Catalog Service

**Database choice: PostgreSQL**
- Use **PostgreSQL** as the catalog system of record.
- The catalog contains structured song metadata with predictable fields, so a relational database is the most practical choice.
- PostgreSQL supports pagination, indexing, sorting, joins if needed later, and easy bulk ingestion of dataset records.
- It is also easy to validate manually when checking seeded benchmark data.

**Why not a document or graph database**
- The required catalog workload is metadata browsing and lookup, not deeply connected graph traversal.
- A relational model keeps the system simpler and easier to benchmark consistently.

**Why this stack fits**
- The Catalog Service needs reliable startup ingestion, predictable reads, and straightforward API behavior.
- PostgreSQL provides all of that with low operational complexity.

### 4.3. Streaming Service

**Persistence choice: stateless by default**
- The Streaming Service does not need a mandatory database in the minimum version.
- Its primary responsibilities are validating JWTs, returning a simulated HLS manifest or stream descriptor, generating dummy segment payloads, and publishing playback events.
- Keeping it stateless is the best choice for simplicity, scalability, and benchmark repeatability.

### 4.4. Playlist Service

**Database choice: PostgreSQL**
- Use **PostgreSQL** for playlists, playlist ownership, liked songs, and ordered playlist tracks.
- This service needs transactional correctness because track order, ownership, create/update/delete operations, and the special `Liked Songs` behavior should remain consistent.
- PostgreSQL is a strong fit for ordered relational data and makes reordering logic and constraints straightforward.

### 4.5. Search Service

**Database / search engine choice: OpenSearch**
- Use **OpenSearch** as the dedicated search persistence/index layer.
- Search requires full-text matching plus structured filters for genre, BPM range, and year.
- A search engine is better suited than a plain relational database for tokenized text queries, ranking, optional autocomplete, and future fuzzy matching.
- OpenSearch is a strong fit because it is widely used for search workloads, Docker-friendly, and avoids overloading the catalog database with search-specific indexing concerns.

**Why not only PostgreSQL full-text search**
- PostgreSQL full-text search would be acceptable for a constrained version, but a dedicated search engine is the better long-term choice for this service boundary.
- It aligns well with the service's specific search responsibility.

**Why this stack fits**
- The Search Service exists to optimize search behavior, so choosing a dedicated search backend is justified.
- It also leaves room for the optional autocomplete and expanded search features.

### 4.6. Analytics Service

**Database choice: ClickHouse**
- Use **ClickHouse** as the dedicated analytics store.
- Analytics workloads are event-heavy, append-oriented, and aggregation-heavy, which is exactly where columnar databases perform well.
- ClickHouse is an excellent fit for listen history queries, global play-count rankings, chart generation, and high-volume event ingestion.
- It is especially appropriate in a benchmark system because it makes performance and aggregation behavior observable under load.

**Why this stack fits**
- Analytics is the clearest place to use a specialized analytical database.
- ClickHouse gives better aggregation performance than a general transactional database for this service's primary responsibilities.

### 4.7. Recommendation Service

**Database choices: PostgreSQL + Redis**
- Use **PostgreSQL** for durable recommendation state such as user-song affinity summaries, similarity snapshots, model metadata, or offline-generated recommendation artifacts.
- Use **Redis** for low-latency caches such as precomputed Daily Mix results, short-lived recommendation candidate sets, or hot lookups.
- This two-layer design balances durability and speed.

**Why this stack fits**
- Recommendation systems often need both durable state and fast cache access.
- PostgreSQL keeps the first version simple and understandable, while Redis improves responsiveness without forcing a heavy ML platform in the initial benchmark version.

### 4.8. Notification Service

**Database choice: MongoDB**
- Use **MongoDB** as the dedicated notification store.
- In-app notifications often have flexible payloads, event-derived metadata, timestamps, read/unread state, and varying structures depending on notification type.
- A document database is a good fit because it handles evolving payload shapes naturally without forcing rigid relational modeling for internal notification objects.
- MongoDB is also popular, mature, and easy to inspect manually for debugging.

**Why this stack fits**
- Notification data is document-like and event-derived.
- MongoDB keeps the persistence model simple and flexible for this internal service.

## 5. UI Scope

Frontend work is intentionally out of scope for this version. No client framework, client build tool, frontend UI, frontend runtime container, frontend tests, frontend health endpoint, or frontend metrics stack is required. The implementation scope remains backend microservices, infrastructure, observability, load generation, and Docker Compose-based scalability benchmarking.
## 6. Cross-Cutting Technical Decisions

### API and Service Communication

- All synchronous service-to-service HTTP traffic should use Spring `WebClient`.
- Apply retry with exponential backoff and a circuit breaker via Resilience4j.
- Use shared error formats and centralized HTTP client configuration where possible.

### Data Ownership

- Every service that persists state must have its own dedicated persistence layer.
- No shared application database should be used across service boundaries.

### Object Storage

- Do **not** make object storage part of the minimum stack unless a later implementation introduces a real need for it.
- The minimum streaming architecture does not require real audio files.

## 7. Technology Stack Summary

- **Backend standard:** Java 21 + Spring Boot + Maven
- **Auth DB:** PostgreSQL
- **Catalog DB:** PostgreSQL
- **Streaming:** Stateless; publishes playback events via Kafka
- **Playlist DB:** PostgreSQL
- **Search backend:** OpenSearch
- **Analytics DB:** ClickHouse
- **Recommendation persistence:** PostgreSQL + Redis
- **Notification DB:** MongoDB
- **Frontend:** Out of scope for this version
- **Messaging:** Apache Kafka
- **Observability:** Prometheus + Grafana
- **Load generator:** k6
- **Runtime:** Docker Compose + shared named Docker network
