# Design Decisions

This file records planning decisions, generation decisions, review decisions, corrections, assumptions, validation narratives, and implementation rationale moved from PROGRESS.md.

### Phase 0 Plan - Shared Deployment Environment and Repository Skeleton

#### Assumptions

* This phase is planning only. No service business logic, source files, Dockerfiles, Compose files, or configuration files are generated in this step.
* The baseline repository should be prepared for all eight required backend microservices, the frontend, and the required shared infrastructure.
* Object storage is not included in the baseline because `TECH-STACK.md` states that object storage should not be part of the minimum stack unless a later implementation introduces a real need, and `REQUIREMENTS.md` states that real audio files are not required.
* The Streaming Service is planned as stateless in the baseline because `TECH-STACK.md` defines it as stateless by default.
* The load generator is included in the shared environment plan because `REQUIREMENTS.md` M-19 requires the load generator in `docker-compose.yml`, and M-21 requires it to simulate the main user flows.

#### Proposed Repository Structure

```text
benchmark-application/
  docker-compose.yml
  .env.example
  README.md
  config/
    prometheus/
      prometheus.yml
    grafana/
      provisioning/
      dashboards/
    kafka/
    jwt/
      public.pem
      private.pem.example
  infrastructure/
    postgres/
      auth/
      catalog/
      playlist/
      recommendation/
    clickhouse/
      analytics/
    mongodb/
      notification/
    opensearch/
      search/
  services/
    auth-service/
    catalog-service/
    streaming-service/
    playlist-service/
    search-service/
    analytics-service/
    recommendation-service/
    notification-service/
  frontend/
  load-generator/
    k6/
  docs/
```

#### docker-compose.yml Structure

The planned Compose file should define:

* Eight application services: `auth-service`, `catalog-service`, `streaming-service`, `playlist-service`, `search-service`, `analytics-service`, `recommendation-service`, and `notification-service`.
* Frontend service: `frontend`.
* Messaging infrastructure: Kafka, plus any Kafka runtime dependency required by the selected Kafka container image.
* Dedicated persistence components:
  * `auth-db` using PostgreSQL.
  * `catalog-db` using PostgreSQL.
  * `playlist-db` using PostgreSQL.
  * `search-opensearch` using OpenSearch.
  * `analytics-db` using ClickHouse.
  * `recommendation-db` using PostgreSQL.
  * `recommendation-redis` using Redis.
  * `notification-db` using MongoDB.
* Observability infrastructure: `prometheus` and `grafana`.
* Load generator: `k6`.
* Per-service environment variables loaded from root and/or service-specific env files.
* Configurable CPU and memory limits per application service and infrastructure container.
* Health checks where supported by the container image or service.
* Named volumes for persistent infrastructure state.

#### Named Docker Network

Use one shared named Docker network for all application services and infrastructure services:

```text
benchmark-network
```

This directly supports `REQUIREMENTS.md` M-18 and `TECH-STACK.md` Networking and Runtime.

#### Supporting Infrastructure Needed by the Current Baseline

The baseline infrastructure should include only infrastructure justified by the source documents:

* Kafka for playback, recommendation, analytics, and notification event flows.
* PostgreSQL databases for Auth, Catalog, Playlist, and Recommendation durable relational state.
* Redis for Recommendation low-latency cache state.
* OpenSearch for Search indexing and filtering.
* ClickHouse for Analytics event storage and aggregations.
* MongoDB for Notification document storage.
* Prometheus for metrics scraping.
* Grafana for observability dashboards.
* k6 for repeatable load-generation workloads.

No object storage is planned for this baseline.

#### Service Directory Layout

Each backend service directory should use a consistent Spring Boot Maven layout:

```text
services/<service-name>/
  pom.xml
  Dockerfile
  README.md
  .env.example
  src/
    main/
      java/
      resources/
    test/
      java/
```

Service-specific database initialization or migration files should live either under the service directory when owned by application startup, or under `infrastructure/<database>/<service>/` when mounted directly into infrastructure containers during local Compose startup. The final choice should be made during each service's generation phase based on the service's migration approach.

The frontend directory should use the React + TypeScript + Vite stack from `TECH-STACK.md`:

```text
frontend/
  package.json
  Dockerfile
  README.md
  .env.example
  src/
```

The load generator should contain k6 scripts and workload documentation:

```text
load-generator/k6/
  scripts/
  README.md
```

#### Config and Env File Strategy

* Root `.env.example` should document shared Compose-level defaults such as exposed host ports, container resource limits, Kafka bootstrap settings, and common observability settings.
* Each service should also have its own `.env.example` documenting service-specific variables such as ports, datasource URLs, Kafka topic names, JWT public key path, and external service base URLs.
* Actual `.env` files should not be committed.
* JWT verification configuration should be shared through mounted public-key configuration so protected services can validate tokens locally.
* Auth Service should be the only service planned to require the private JWT signing key.
* Prometheus scrape configuration should live under `config/prometheus/prometheus.yml`.
* Grafana provisioning and dashboards should live under `config/grafana/`.
* Compose service names should be used for container-to-container URLs on `benchmark-network`.

#### Validation Steps for This Phase

Because this step is plan-only, validation is limited to document and plan verification:

* Confirm that the planned Compose environment includes all eight required application services and required infrastructure from `REQUIREMENTS.md` M-19.
* Confirm that all services and infrastructure are planned to join one named Docker network, satisfying M-18.
* Confirm that configurable CPU and memory limits are included in the Compose plan, satisfying M-20.
* Confirm that each stateful service has a dedicated persistence layer, satisfying M-26.
* Confirm that object storage is not included because the current baseline does not require real audio files.
* Confirm that no service business logic is generated during this phase.

#### Planning Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Start with the shared deployment environment and repository skeleton before service generation. | The system should integrate services incrementally into a common runtime. | `PROGRESS.md` Generation Protocol; `REQUIREMENTS.md` M-19. | `docker-compose.yml`, root config, all service directories. |
| Use one named Docker network called `benchmark-network`. | All application and infrastructure containers need shared inter-service communication. | `REQUIREMENTS.md` M-18; `TECH-STACK.md` Networking and Runtime. | `docker-compose.yml`, all services, all infrastructure containers. |
| Plan eight backend service directories under `services/`. | The architecture defines exactly eight independent microservices. | `ARCHITECTURE.md` Microservices Specifications. | `services/auth-service`, `services/catalog-service`, `services/streaming-service`, `services/playlist-service`, `services/search-service`, `services/analytics-service`, `services/recommendation-service`, `services/notification-service`. |
| Use Java 21, Spring Boot 3.x, and Maven as the backend skeleton standard. | The backend stack must stay consistent across services. | `TECH-STACK.md` Shared Backend Standard. | All backend service directories. |
| Include Kafka in the baseline infrastructure. | Playback and application events must be published and consumed asynchronously. | `TECH-STACK.md` Messaging Infrastructure per Service; `REQUIREMENTS.md` M-06, M-14, M-16, M-17. | `docker-compose.yml`, Streaming, Playlist, Analytics, Recommendation, Notification. |
| Use separate persistence components per stateful service. | Shared application databases are not allowed. | `REQUIREMENTS.md` M-26; `TECH-STACK.md` Database and Persistence per Service. | Auth DB, Catalog DB, Playlist DB, Search OpenSearch, Analytics ClickHouse, Recommendation PostgreSQL and Redis, Notification MongoDB. |
| Keep Streaming Service stateless in the baseline. | It does not need mandatory persistence for the minimum version. | `TECH-STACK.md` Streaming Service persistence choice; `ARCHITECTURE.md` Streaming Service persistence note. | `services/streaming-service`, `docker-compose.yml`. |
| Exclude object storage from the baseline. | Real audio files are out of scope, and object storage should not be added without a real need. | `TECH-STACK.md` Object Storage; `REQUIREMENTS.md` W-03. | `docker-compose.yml`, infrastructure directories. |
| Include Prometheus and Grafana in the shared environment plan. | Metrics scraping is mandatory and dashboards are the chosen observability layer. | `REQUIREMENTS.md` M-24; `TECH-STACK.md` Observability; `REQUIREMENTS.md` S-03 for dashboard support. | `config/prometheus`, `config/grafana`, `docker-compose.yml`, all services. |
| Include k6 as the load-generator service. | Main user flows must be simulated by a load generator. | `REQUIREMENTS.md` M-21; `TECH-STACK.md` Load Generation. | `load-generator/k6`, `docker-compose.yml`. |
| Use root and service-specific `.env.example` files while keeping real `.env` files uncommitted. | Shared Compose settings and per-service settings need to be documented without committing secrets. | `PROGRESS.md` checklist expectations; `TECH-STACK.md` Runtime choices and JWT guidance. | Root `.env.example`, service `.env.example` files, frontend `.env.example`. |
| Share JWT verification through a mounted public-key configuration and restrict private signing key use to Auth Service. | Protected services must validate JWTs locally using asymmetric signing. | `ARCHITECTURE.md` Auth Service security requirements; `REQUIREMENTS.md` M-03 and M-25. | `config/jwt`, Auth Service, all protected backend services, frontend API integration. |

### Phase 0 Generation - Shared Deployment Environment and Repository Skeleton

#### Completed Artifacts

* Created the top-level deployment skeleton and service folders for all eight required backend services.
* Added `docker-compose.yml` with the eight application service placeholders, frontend placeholder, Kafka, dedicated persistence components, Redis, Prometheus, Grafana, and k6.
* Added one named Docker network, `benchmark-network`, and attached every Compose service to it.
* Added root `.env.example` and service-specific `.env.example` files to document expected configuration.
* Added shared config files for Prometheus, Grafana provisioning, JWT key mounting, and placeholder infrastructure directories.
* Added placeholder Dockerfiles for the eight backend services and frontend so the topology can be built before business logic is generated.
* Added empty Spring Boot `src/main/java`, `src/main/resources`, and `src/test/java` directory skeletons for each backend service.
* Updated `README.md` with Phase 0 startup, shutdown, topology, and limitation notes.

#### Validation Results

* `docker compose config --quiet` completed successfully, confirming the Compose file is syntactically valid.
* `docker compose config --services` confirmed all required service and infrastructure names are present.
* `docker compose config --volumes` confirmed named volumes are defined for persistent infrastructure state.
* `docker compose config --format json` confirmed all services reference `benchmark-network`.
* Initial `docker compose up -d --build` was blocked because the local Docker daemon was not reachable from the sandbox. Docker Desktop was started and the command was rerun with approval outside the sandbox.
* The first escalated Compose run exposed an invalid Kafka image tag (`bitnami/kafka:3.7`). The Compose file was corrected to use `apache/kafka:3.7.0`, which was verified with `docker manifest inspect`.
* OpenSearch initially restarted because OpenSearch 2.13 requires security bootstrap settings. The Compose file was corrected with `DISABLE_SECURITY_PLUGIN=true` and an initial local admin password value.
* `docker compose up -d --build` completed successfully after fixes.
* `docker compose ps` confirmed all Phase 0 containers are running, including all eight backend placeholders, frontend, Kafka, persistence services, Prometheus, Grafana, and k6.
* Docker emitted warnings about `C:\Users\thele\.docker\config.json` being inaccessible to the sandbox user during non-escalated static validation; static Compose validation still exited successfully.

#### Generation Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Use inert long-running placeholder containers for application services and frontend. | The shared topology must be runnable before service logic exists, while avoiding fake endpoint or business behavior. | User instruction not to implement service business logic; `PROGRESS.md` Phase 0 Generate step. | `services/*/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`. |
| Use `eclipse-temurin:21-jre-alpine` for backend placeholders. | The backend implementation stack is Java 21, so placeholders should align with the future runtime without adding Spring Boot code yet. | `TECH-STACK.md` Shared Backend Standard. | All backend placeholder Dockerfiles. |
| Use `node:20-alpine` for the frontend placeholder. | The frontend will later use React, TypeScript, and Vite, so a Node runtime placeholder fits the planned frontend toolchain. | `TECH-STACK.md` Frontend Stack. | `frontend/Dockerfile`. |
| Reserve Spring Boot source and test directory layouts with `.gitkeep` files. | Later service phases need a consistent Maven/Spring structure without adding source code in Phase 0. | `TECH-STACK.md` Shared Backend Standard; user instruction not to implement service business logic yet. | `services/*/src/main/java`, `services/*/src/main/resources`, `services/*/src/test/java`. |
| Define every required service in `docker-compose.yml` from the start. | The shared deployment environment must start all eight application services and required infrastructure. | `REQUIREMENTS.md` M-19. | `docker-compose.yml`. |
| Use `apache/kafka:3.7.0` for the Kafka container. | The planned `bitnami/kafka:3.7` tag was not available from Docker Hub during validation; `apache/kafka:3.7.0` is available and keeps Kafka aligned with the selected technology. | `TECH-STACK.md` Messaging choice: Apache Kafka. | `docker-compose.yml`, Kafka service. |
| Add dedicated Compose services and named volumes for each stateful service's persistence boundary. | The architecture forbids shared application databases across service boundaries. | `REQUIREMENTS.md` M-26; `TECH-STACK.md` Database and Persistence per Service. | `auth-db`, `catalog-db`, `playlist-db`, `search-opensearch`, `analytics-db`, `recommendation-db`, `recommendation-redis`, `notification-db`. |
| Add local OpenSearch security bootstrap settings while disabling the security plugin. | OpenSearch 2.13 requires an initial admin password at startup even for this local baseline; disabling the security plugin keeps Phase 0 simple for local Compose use. | `TECH-STACK.md` Search backend: OpenSearch; Phase 0 startup validation. | `docker-compose.yml`, `.env.example`, `search-opensearch`. |
| Configure Prometheus to scrape `/actuator/prometheus` for all backend services even though placeholders do not expose metrics yet. | Spring Boot Actuator metrics are the expected later endpoint; Phase 0 should reserve the monitoring shape without adding service code. | `REQUIREMENTS.md` M-24; `TECH-STACK.md` Observability. | `config/prometheus/prometheus.yml`, all backend services. |
| Add Grafana datasource and dashboard provisioning, but no dashboard panels yet. | Grafana is part of the selected stack; detailed dashboards depend on metrics emitted by later service implementations. | `TECH-STACK.md` Observability; `REQUIREMENTS.md` S-03. | `config/grafana/provisioning`, `config/grafana/dashboards`. |
| Include placeholder JWT key files rather than generated production keys. | Protected services need a mounted public-key path later, but Auth Service key generation belongs to the Auth phase. | `REQUIREMENTS.md` M-03, M-25; user instruction not to implement service logic yet. | `config/jwt/public.pem`, `config/jwt/private.pem.example`, `docker-compose.yml`. |
| Leave object storage absent. | The minimum system does not use real audio files, and the tech stack says not to add object storage without a real need. | `TECH-STACK.md` Object Storage; `REQUIREMENTS.md` W-03. | `docker-compose.yml`, infrastructure folders. |
| Keep k6 as a placeholder container with no workload script. | The load generator is required in Compose, but actual workload flows depend on generated service endpoints. | `REQUIREMENTS.md` M-19, M-21; user instruction not to implement business logic yet. | `docker-compose.yml`, `load-generator/k6`. |
| Rerun startup validation after fixing image and OpenSearch settings. | The phase should not stop at static validation when Compose runtime issues are fixable in the current environment. | `PROGRESS.md` Validation expectations. | `docker-compose.yml`, `.env.example`, `PROGRESS.md`. |

### Phase 0 Review - Architecture, Requirements, and Tech Stack Validation

#### Review Results

* `docker-compose.yml` reflects the required baseline architecture: eight backend service placeholders, frontend placeholder, Kafka, dedicated state stores, Prometheus, Grafana, and k6.
* The shared named Docker network `benchmark-network` is defined, and every Compose service is attached to it.
* Stateful service persistence remains isolated by service boundary:
  * Auth, Catalog, Playlist, and Recommendation use separate PostgreSQL services.
  * Search uses OpenSearch.
  * Analytics uses ClickHouse.
  * Recommendation also has its own Redis cache.
  * Notification uses MongoDB.
* The generated folder structure supports the module-by-module workflow through separate service directories, service-specific `.env.example` files, placeholder Dockerfiles, and empty Spring Boot source/test directory skeletons.
* No service business logic, API endpoints, JWT enforcement, dataset ingestion, messaging behavior, frontend routes, or k6 workload flows were added during Phase 0.
* No extra infrastructure beyond `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` was found. Object storage remains absent because it is not required for the minimum simulated streaming baseline.

#### Validation Commands

* `docker compose config --quiet` passed.
* `docker compose config --services` confirmed the expected 21 Compose services.
* A JSON Compose topology check confirmed `benchmark-network` is defined and no Compose service is missing that network.
* `Get-ChildItem services -Directory` confirmed all eight backend service directories are present.

#### Fixes Made During Review

| Fix | Reason | Affected files |
| --- | --- | --- |
| Reworded the README overview from already "implemented" microservices to a system "planned" as eight independent microservices. | Phase 0 contains deployment skeletons and placeholders only, so the documentation should not imply completed service implementations. | `README.md` |
| Replaced "polyglot technology stacks" with "purpose-specific infrastructure choices." | `TECH-STACK.md` standardizes all backend services on Java 21, Spring Boot 3.x, and Maven; the current variety is in infrastructure and persistence choices, not backend application stacks. | `README.md` |

#### Unresolved Issues

* No unresolved Phase 0 architecture, requirements, or technology-stack issues remain.
* Docker still emits a non-blocking warning about sandbox access to `C:\Users\thele\.docker\config.json` during non-escalated Docker commands. Compose validation and startup are unaffected.

---


### Step 1 Plan - Auth Service

#### Assumptions

* This step is planning only. No Auth Service source code, Maven files, migrations, tests, or Dockerfile changes are generated in this step.
* Auth Service is the first backend service to implement because later protected services depend on its JWT signing and shared verification setup.
* Only `POST /auth/register` and `POST /auth/login` are public. No additional public Auth endpoints are planned because `REQUIREMENTS.md` M-25 says only those two endpoints may be publicly accessible.
* User registration and login require credentials, but the exact request and response field names are not specified in the source documents. These must be defined during generation and documented without adding behavior beyond account creation, authentication, and JWT issuance.
* Roles are mentioned in `TECH-STACK.md` as "if needed"; they are not required by `ARCHITECTURE.md` or `REQUIREMENTS.md`, so roles are not planned for the minimum Auth implementation.
* Refresh tokens, password reset, email verification, OAuth2, social login, account deletion, and admin user management are not planned because they are not required by the source documents, and OAuth2/social login is explicitly out of scope in `REQUIREMENTS.md` W-02.

#### Chosen Stack

* Language: Java 21 LTS.
* Framework: Spring Boot 3.x.
* Build tool: Maven.
* Containerization: Docker.
* Persistence: PostgreSQL dedicated to Auth Service.
* Security: Spring Security with JWT support using asymmetric signing.
* Observability: Spring Boot Actuator and Prometheus metrics endpoint for later Prometheus scraping.
* Testing: JUnit 5, Spring Boot Test, Spring Security Test, and PostgreSQL-backed integration testing.

#### Planned File Tree

```text
services/auth-service/
  pom.xml
  Dockerfile
  README.md
  .env.example
  src/
    main/
      java/
        <base-package>/
          AuthServiceApplication.java
          config/
          controller/
          dto/
          entity/
          repository/
          security/
          service/
      resources/
        application.yml
        db/
          migration/
    test/
      java/
        <base-package>/
          controller/
          security/
          service/
```

The exact Java base package is not specified in the source documents and should be chosen during generation using a conventional project package name.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web` for HTTP endpoints.
* `spring-boot-starter-security` for authentication, password hashing, and endpoint security.
* Spring Security JWT/Nimbus support through `spring-boot-starter-oauth2-resource-server` and related JOSE support as needed.
* `spring-boot-starter-data-jpa` for PostgreSQL persistence.
* PostgreSQL JDBC driver.
* Flyway or Liquibase for database migrations. Preferred: Flyway, unless a later repo convention dictates otherwise.
* `spring-boot-starter-validation` for request validation.
* `spring-boot-starter-actuator` and Micrometer Prometheus registry for metrics.
* `spring-boot-starter-test` for unit and integration tests.
* `spring-security-test` for security test support.
* Testcontainers PostgreSQL for integration tests against real PostgreSQL behavior if local tooling supports it.

#### Endpoints and Exposed Interfaces

Required public HTTP endpoints:

* `POST /auth/register`
  * Creates a user account.
  * Persists authentication-related state in Auth Service PostgreSQL.
  * Should reject duplicate user identity values through database-backed uniqueness.
* `POST /auth/login`
  * Authenticates a registered user.
  * Returns a JWT access token for protected endpoints.
  * JWT must be signed asymmetrically.

Planned operational endpoint exposure through Spring Boot Actuator:

* Health and Prometheus metrics endpoints needed for container validation and Prometheus scraping.

No additional public client-facing Auth endpoints are planned for this phase.

#### Environment Variables

Planned Auth Service variables:

```text
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://auth-db:5432/auth
SPRING_DATASOURCE_USERNAME=benchmark
SPRING_DATASOURCE_PASSWORD=benchmark
JWT_PUBLIC_KEY_PATH=/run/config/jwt/public.pem
JWT_PRIVATE_KEY_PATH=/run/secrets/jwt/private.pem
JWT_ISSUER=<missing-from-documents>
JWT_ACCESS_TOKEN_TTL=<missing-from-documents>
```

Details missing from source documents:

* JWT issuer value.
* JWT access token lifetime.
* Exact JWT claims beyond enough user identity data for other services to validate and associate requests with a user.
* Exact request/response JSON field names.
* Password complexity policy.

These missing values should be selected conservatively during generation and documented in Auth Service README and `.env.example`, without adding extra user-facing features.

#### Persistence and Messaging Approach

Persistence:

* Auth Service must use its own dedicated PostgreSQL persistence layer.
* Planned minimum persisted data:
  * User identifier.
  * Login identity, likely email or username. Exact field is not specified.
  * Password hash, never plaintext password.
  * Created and updated timestamps if useful for auditability and testing.
* Database migrations should enforce unique login identity and required fields.

Messaging:

* No Auth Service messaging is required by `ARCHITECTURE.md`, `REQUIREMENTS.md`, or `TECH-STACK.md`.
* Auth Service should not publish or consume Kafka events in the minimum version.

JWT signing and verification:

* Auth Service signs JWTs with a private key.
* Other services verify locally using the shared public key mounted from the shared JWT config.
* Auth Service should expose no per-request token validation endpoint because M-03 requires local validation by protected services.

#### Validation Steps

After generation, Auth Service validation should include:

* Build the Maven project.
* Run unit tests.
* Run integration tests.
* Build the Auth Service Docker image.
* Start `auth-db` and `auth-service` through Docker Compose.
* Confirm the service connects to its dedicated PostgreSQL database.
* Confirm database migrations apply successfully.
* Confirm `POST /auth/register` creates a user account.
* Confirm `POST /auth/login` returns a JWT access token for valid credentials.
* Confirm login fails for invalid credentials.
* Confirm issued JWTs use asymmetric signing and can be verified with the shared public key.
* Confirm public/private key configuration works through the configured mounted paths.
* Confirm Actuator health and Prometheus metrics are reachable when the service is running.
* Confirm no extra public Auth endpoints were added.

#### Required Unit Tests

Minimum unit test coverage:

* Registration service creates a new user with hashed password data.
* Registration rejects duplicate login identity.
* Login succeeds for valid credentials.
* Login rejects invalid credentials.
* Password hashing/verification behavior does not store or compare plaintext passwords.
* JWT issuance creates a signed token containing the required identity data.
* JWT verification succeeds with the public key and fails for invalid or tampered tokens.

#### Required Integration Tests

Minimum integration test coverage:

* `POST /auth/register` returns the expected success response and persists a user in PostgreSQL.
* `POST /auth/register` rejects duplicate registrations.
* `POST /auth/login` returns a JWT access token for valid credentials.
* `POST /auth/login` rejects invalid credentials.
* The issued JWT is signed asymmetrically and can be verified with the configured public key.
* Auth Service uses its own PostgreSQL database and migrations in the test environment.
* Only `POST /auth/register` and `POST /auth/login` are publicly accessible in the Auth Service implementation.
* Actuator metrics endpoint is available for Prometheus scraping if enabled by the generated configuration.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Auth Service with Java 21, Spring Boot 3.x, and Maven. | All backend microservices must use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/auth-service/pom.xml`, Java source tree, Dockerfile. |
| Use PostgreSQL as the Auth Service database. | Authentication state is structured, consistency-sensitive, and must use a dedicated persistence layer. | `ARCHITECTURE.md` Auth Service Persistence; `REQUIREMENTS.md` M-26; `TECH-STACK.md` Auth Service database choice. | `auth-db`, `services/auth-service/src/main/resources/db/migration`, JPA entities/repositories. |
| Implement exactly `POST /auth/register` and `POST /auth/login` as public Auth endpoints. | These are the required Auth endpoints, and only these may be public. | `ARCHITECTURE.md` Auth Service Required endpoints; `REQUIREMENTS.md` M-01, M-02, M-25. | Auth controller, security configuration, integration tests. |
| Use asymmetric JWT signing with private-key signing in Auth Service and shared public-key verification for other services. | Protected services must validate tokens locally without per-request Auth calls. | `ARCHITECTURE.md` Auth Service Security requirements; `REQUIREMENTS.md` M-03. | Auth security/JWT classes, `config/jwt`, service `.env.example`, future protected services. |
| Do not add a token validation endpoint. | Per-request validation by Auth Service would conflict with local JWT validation requirement. | `REQUIREMENTS.md` M-03. | Auth controller and security design. |
| Do not add Kafka messaging to Auth Service. | No Auth messaging requirement exists in the source documents. | `ARCHITECTURE.md` Auth Service specification; `TECH-STACK.md` Messaging Infrastructure per Service. | `services/auth-service/pom.xml`, application config. |
| Exclude OAuth2/social login, refresh tokens, password reset, and email verification from the minimum Auth plan. | These behaviors are not required, and OAuth2/social login is explicitly out of scope. | `REQUIREMENTS.md` W-02; user instruction not to invent requirements. | Auth endpoints, DTOs, services, tests. |
| Include Actuator and Prometheus metrics support. | Each service must expose metrics suitable for Prometheus scraping. | `REQUIREMENTS.md` M-24. | `pom.xml`, `application.yml`, Docker/Compose validation. |
| Treat JWT issuer, token TTL, exact claims, request fields, and password policy as missing details to be decided during generation. | The source documents require JWT issuance and user authentication but do not specify these exact values. | `ARCHITECTURE.md` Auth Service behavior; `REQUIREMENTS.md` M-01 through M-03. | Auth DTOs, config properties, README, tests. |

### Step 2 and Step 3 - Auth Service Generation and Validation

#### Completed Artifacts

* Replaced the Phase 0 Auth placeholder container with a real Java 21 Spring Boot 3.3.5 Maven application.
* Implemented `POST /auth/register` and `POST /auth/login`.
* Added PostgreSQL persistence through Spring Data JPA and Flyway migration `V1__create_users.sql`.
* Implemented BCrypt password hashing and duplicate-user protection through a unique email identity.
* Implemented asymmetric RS256 JWT issuance using a private key mounted only into Auth Service and a shared public key mounted for verification.
* Added Actuator health and Prometheus metrics exposure.
* Added unit tests for registration, duplicate rejection, login, password hashing, JWT signing, and tampered-token rejection.
* Added endpoint and persistence integration tests using Spring Boot, MockMvc, Flyway, JPA, and H2 in PostgreSQL compatibility mode.
* Added `services/auth-service/README.md`, service `.env.example`, Maven `pom.xml`, Dockerfile, application config, source code, and tests.
* Updated root `.env.example`, `docker-compose.yml`, and `config/jwt` for the real Auth runtime.

#### Generation Decisions and Corrections Recorded

| Decision or correction | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Use `email` and `password` as the Auth request credential fields. | The source documents require account creation and login credentials but do not specify JSON field names. | `ARCHITECTURE.md` Auth behavior; `REQUIREMENTS.md` M-01 and M-02. | Auth DTOs, controller tests, README. |
| Normalize email to lowercase before persistence and lookup. | This avoids duplicate accounts differing only by email case while keeping the login identity simple. | `TECH-STACK.md` notes PostgreSQL uniqueness constraints are suitable for auth data. | `AuthService`, repository tests, migration unique constraint. |
| Enforce minimum password length of 8 characters. | A minimal validation rule is needed to prevent blank or trivially invalid password input while not adding password-reset or policy workflows. | `ARCHITECTURE.md` requires authentication; missing detail recorded in Auth plan. | `RegisterRequest`, validation tests. |
| Use Flyway migration instead of a separate `init.sql`. | Application-owned migrations are repeatable with the service and validate automatically on startup. | `TECH-STACK.md` Spring Boot + PostgreSQL stack; `PROGRESS.md` Auth plan allowed migration approach. | `src/main/resources/db/migration/V1__create_users.sql`. |
| Use `TIMESTAMP WITH TIME ZONE` instead of PostgreSQL alias `TIMESTAMPTZ`. | This remains valid for PostgreSQL and allows the integration test database to apply the same migration. | Validation failure during Docker build. | `V1__create_users.sql`. |
| Run Maven tests during Docker image build. | The local environment has no `mvn` or `java` on PATH, so Docker is the available repeatable build and test runner. | `TECH-STACK.md` Containerization: Docker; `REQUIREMENTS.md` testing requirements. | `services/auth-service/Dockerfile`. |
| Use H2 in PostgreSQL compatibility mode for endpoint/persistence integration tests. | The sandbox blocks bind-mounting the workspace into `docker run`, which prevents a straightforward Testcontainers run from an external Maven container; H2 keeps integration tests runnable during Docker build while runtime remains PostgreSQL. | `REQUIREMENTS.md` requires automated integration tests; runtime stack remains `TECH-STACK.md` PostgreSQL. | `AuthControllerIntegrationTest`, `pom.xml`, Docker build validation. |
| Allow unauthenticated `/actuator/health` and `/actuator/prometheus` as operational endpoints. | Prometheus scraping and health validation require operational access; no additional public application API endpoints were added. | `REQUIREMENTS.md` M-24 and M-25. | `SecurityConfig`, `application.yml`, Prometheus integration. |
| Generate a local development RSA keypair under `config/jwt`. | Auth Service needs a real private key to sign runnable JWTs, and other services need the public key for local verification. | `REQUIREMENTS.md` M-03; `ARCHITECTURE.md` Auth security requirements. | `config/jwt/private.pem`, `config/jwt/public.pem`, `docker-compose.yml`. |
| Keep `private.pem.example` as a replacement guide while mounting only `private.pem` into Auth Service. | The public/private split documents how later services should receive only verification configuration. | `REQUIREMENTS.md` M-03. | `config/jwt/private.pem.example`, `docker-compose.yml`, Auth README. |
| Remove obsolete Auth `.gitkeep` files after adding real source and tests. | The Auth service now has real implementation files and no longer needs placeholder keep-files in source directories. | User instruction: no placeholders for the generated service. | `services/auth-service/src/main/java`, `src/main/resources`, `src/test/java`. |

#### Validation Results

* `docker compose build auth-service` completed successfully and ran the Maven test suite during the image build.
* The build executed 10 tests covering unit and integration behavior.
* `docker compose up -d --build auth-db auth-service` started Auth Service and its dedicated PostgreSQL database.
* Auth startup logs confirmed PostgreSQL connection and successful Flyway migration application.
* `docker compose ps auth-db auth-service` confirmed `auth-db` is healthy and `auth-service` is running.
* Live smoke validation registered a new user through `POST /auth/register` and logged in through `POST /auth/login`.
* A live JWT returned by Auth Service was verified with the shared public key using RS256 signature verification.
* `/actuator/health` returned `UP`, and `/actuator/prometheus` returned status 200 with JVM metrics.

#### Unresolved Issues

* No unresolved Auth Service implementation issues remain for this phase.
* Non-escalated Docker commands still emit sandbox permission warnings for Docker Desktop access, so Docker validation commands were run with approval where required.

### Step 3 Review - Auth Service Validation Pass

#### Review Scope

* Reviewed the generated Auth Service against `ARCHITECTURE.md` Auth Service requirements, `REQUIREMENTS.md` M-01, M-02, M-03, M-24, M-25, M-26, and backend testing requirements, plus `TECH-STACK.md` Java 21, Spring Boot 3.x, Maven, Docker, and PostgreSQL choices.
* Checked the generated service implementation, Dockerfile, Compose wiring, service `.env.example`, application configuration, database migration, README, unit tests, and integration tests.
* Confirmed no conflicts were found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for this Auth review.

#### Validated Checklist Items

* Required endpoints exist: `POST /auth/register` and `POST /auth/login`.
* No extra public application endpoints were added to Auth Service.
* JWT issuance uses RS256 asymmetric signing and the shared public key verifies live tokens.
* Auth Service uses its own dedicated PostgreSQL persistence layer through `auth-db`.
* Docker Compose keeps Auth Service on the shared `benchmark-network` and mounts the private key only into Auth Service.
* Configuration is documented in `services/auth-service/.env.example` and wired through `application.yml` and `docker-compose.yml`.
* Unit tests and integration tests are present and run during `docker compose build auth-service`.
* Runtime health and Prometheus metrics are available for operational validation and scraping.

#### Validation Commands Run

* `docker compose config --quiet` passed.
* `docker compose build auth-service` passed and ran the Maven test suite in the image build.
* `docker compose up -d --build auth-db auth-service` passed.
* `docker compose ps auth-db auth-service` confirmed `auth-db` healthy and `auth-service` running.
* Live smoke validation passed for registration, login, RS256 JWT signature verification using `config/jwt/public.pem`, unauthenticated protected-path rejection, invalid-token protected-path rejection, `/actuator/health`, and `/actuator/prometheus`.

#### Review Decisions and Corrections Recorded

| Decision, correction, or finding | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| No Auth source, Docker, Compose, or config corrections were required in this review pass. | The generated implementation matched the approved Auth plan and required source documents. | `ARCHITECTURE.md` Auth Service; `REQUIREMENTS.md` M-01, M-02, M-03, M-24, M-25, M-26; `TECH-STACK.md` Auth stack. | No generated Auth implementation files changed during this review pass. |
| Treat `/actuator/health` and `/actuator/prometheus` as operational interfaces, not additional public application APIs. | Health checks and Prometheus scraping are needed for runtime validation and metrics collection while Auth still exposes only the two required application endpoints. | `REQUIREMENTS.md` M-24 and M-25. | `SecurityConfig`, `application.yml`, `config/prometheus/prometheus.yml`, Auth runtime validation. |
| Keep the H2 PostgreSQL-compatibility integration test approach for this phase and pair it with live PostgreSQL Compose validation. | Automated endpoint/persistence tests run repeatably during the Docker build, and the runtime path validates the real PostgreSQL database and Flyway migration. | `REQUIREMENTS.md` Backend testing and completion expectation; `TECH-STACK.md` Auth Service PostgreSQL choice. | `AuthControllerIntegrationTest`, `pom.xml`, Docker build validation, `auth-db`. |
| Note that an attempted local PowerShell RSA verification was discarded because the host crypto API lacked PEM import support. | The failed local verifier did not represent service behavior; verification was rerun successfully in a temporary Node container using the same public key. | `REQUIREMENTS.md` M-03 requires locally verifiable asymmetric JWTs. | Validation notes only; no code affected. |

#### Unresolved Issues After Review

* No unresolved Auth Service implementation or test issues remain for this phase.
* Docker commands that access Docker Desktop still require approved elevation in this workspace.


### Phase 2 Step 1 Plan - Catalog Service

#### Source Document Check

* No conflicts were found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for the Catalog Service plan.
* `ARCHITECTURE.md` defines Catalog Service responsibilities as song catalog management, paginated browsing, single-song metadata retrieval, automatic startup ingestion of the specified Kaggle dataset, and dedicated persistence.
* `REQUIREMENTS.md` M-07, M-08, M-09, M-24, M-25, and M-26 define the validation baseline for this service.
* `TECH-STACK.md` requires Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, PostgreSQL for Catalog persistence, and Prometheus-compatible metrics.

#### Assumptions

* This step is planning only. No Catalog Service source code, Dockerfile replacement, dependency manifest, migrations, seed code, tests, or Compose changes are generated in this step.
* Catalog Service endpoints are treated as protected endpoints because `REQUIREMENTS.md` M-25 says only `POST /auth/register` and `POST /auth/login` may be publicly accessible.
* `/actuator/health` and `/actuator/prometheus` are treated as operational interfaces, not public application APIs, following the same M-24/M-25 interpretation already recorded for Auth Service.
* Catalog Service will validate JWTs locally using the mounted shared public key; it will not call Auth Service per request.
* Catalog Service will not publish or consume Kafka events in this phase because the source documents do not assign messaging behavior to Catalog Service.
* The optional `GET /catalog/artists/:id/top-tracks` endpoint is not included in the current minimum phase plan unless a later instruction explicitly expands scope to the should-have Catalog feature.
* Dataset ingestion must be automatic during service container startup, but the exact local dataset file name and CSV schema are not specified in the source documents.

#### Missing Details to Resolve During Generation

* The exact Kaggle CSV filename, archive layout, and local path are not defined by the documents.
* The exact dataset column names and complete response schema are not defined by the documents.
* The required page-size defaults, maximum page size, sort options, and error response format are not defined by the Catalog-specific source documents.
* Whether startup ingestion should fail the service when the dataset file is absent is not explicitly stated. The plan assumes absence should fail startup validation rather than silently starting with an empty catalog, because M-09 requires automatic ingestion.

#### Chosen Stack

* Language: Java 21.
* Framework: Spring Boot 3.x.
* Build tool: Maven.
* HTTP/API: Spring Web MVC.
* Persistence: PostgreSQL through Spring Data JPA.
* Database migrations: Flyway.
* Security: Spring Security resource server with JWT verification from the shared public key.
* Observability: Spring Boot Actuator with Micrometer Prometheus registry.
* Containerization: Docker and existing root `docker-compose.yml`.

#### Planned File Tree

```text
services/catalog-service/
  pom.xml
  Dockerfile
  README.md
  .env.example
  src/
    main/
      java/
        com/benchmark/catalog/
          CatalogServiceApplication.java
          config/
            ClockConfig.java
            JwtProperties.java
            KeyConfig.java
            SecurityConfig.java
          controller/
            ApiError.java
            ApiExceptionHandler.java
            CatalogController.java
          dto/
            SongDetailResponse.java
            SongListItemResponse.java
            SongPageResponse.java
          entity/
            Song.java
          ingestion/
            CatalogCsvReader.java
            CatalogDatasetIngestionRunner.java
            CatalogDatasetProperties.java
          repository/
            SongRepository.java
          security/
            PemKeyLoader.java
          service/
            CatalogService.java
            SongNotFoundException.java
      resources/
        application.yml
        db/
          migration/
            V1__create_songs.sql
    test/
      java/
        com/benchmark/catalog/
          controller/
            CatalogControllerIntegrationTest.java
          ingestion/
            CatalogDatasetIngestionRunnerTest.java
          service/
            CatalogServiceTest.java
          support/
            TestDataFiles.java
            TestKeyFiles.java
```

If the real dataset requires additional parsing helpers during generation, those helpers should remain under `ingestion/` and be covered by tests.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web`
* `spring-boot-starter-data-jpa`
* `spring-boot-starter-validation`
* `spring-boot-starter-security`
* `spring-boot-starter-oauth2-resource-server`
* `spring-boot-starter-actuator`
* `micrometer-registry-prometheus`
* `flyway-core`
* `flyway-database-postgresql`
* `postgresql`
* A CSV parsing library such as Apache Commons CSV, unless generation can use a structured CSV parser already available through Spring or the JDK without fragile manual splitting.
* Test dependencies: `spring-boot-starter-test`, `spring-security-test`, H2 in PostgreSQL compatibility mode or another automated test database approach that can run inside the Docker/Maven validation path.

#### Endpoints and Exposed Interfaces

Application endpoints:

* `GET /catalog/songs`
  * Protected by JWT.
  * Returns a paginated list of songs.
  * Planned query parameters: `page`, `size`, and optional `sort`, if implemented conservatively for pagination support.
  * Response should include pagination metadata and song list items with the metadata needed for browsing and downstream flows where available from the dataset.

* `GET /catalog/songs/{id}`
  * Protected by JWT.
  * Returns detailed metadata for one song.
  * Returns `404` when the song id does not exist.

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

The optional `GET /catalog/artists/{id}/top-tracks` endpoint is excluded from this phase plan.

#### Environment Variables

Planned service variables:

* `SERVER_PORT` - defaults to `8080`.
* `SPRING_DATASOURCE_URL` - defaults to `jdbc:postgresql://catalog-db:5432/catalog`.
* `SPRING_DATASOURCE_USERNAME` - defaults to Compose PostgreSQL user.
* `SPRING_DATASOURCE_PASSWORD` - defaults to Compose PostgreSQL password.
* `JWT_PUBLIC_KEY_PATH` - path to the mounted shared public key.
* `CATALOG_DATASET_PATH` - path to the dataset file inside the container, currently planned as `/app/data/catalog.csv` unless generation resolves a concrete dataset filename.
* `CATALOG_INGESTION_ENABLED` - optional boolean defaulting to `true` for startup ingestion.
* `CATALOG_DEFAULT_PAGE_SIZE` - optional default page size if pagination defaults need configuration.
* `CATALOG_MAX_PAGE_SIZE` - optional maximum page size if pagination bounds need configuration.

The root Compose service already provides the database URL, credentials, server port, and JWT public key path. Generation may need to add a read-only dataset volume once the local dataset location is known.

#### Persistence and Ingestion Approach

* Catalog Service uses only its dedicated `catalog-db` PostgreSQL service and the `catalog-db-data` volume.
* Flyway owns schema creation for a `songs` table.
* Song records should include the dataset fields needed by catalog browsing, single-song details, Search filtering, and Recommendation flows. Because exact dataset columns are missing, generation must map the real CSV columns to these required concepts where present: song/title/name, artist, genre, BPM or tempo, year or release date, and additional available metadata.
* Startup ingestion should run from inside the Catalog Service container after migrations and before normal readiness is considered successful.
* Ingestion should be idempotent so restarting the service does not duplicate song rows.
* No shared database, cross-service table access, or Kafka integration is planned for this phase.

#### Validation Steps

* Confirm no source-document conflict before generation.
* Build Catalog Service with Docker so Maven tests run in the build path.
* Run `docker compose config --quiet`.
* Run `docker compose build catalog-service`.
* Run `docker compose up -d --build catalog-db catalog-service`.
* Confirm `catalog-db` is healthy and `catalog-service` is running.
* Confirm startup logs show successful PostgreSQL connection, Flyway migration, and automatic dataset ingestion.
* Use a JWT issued by Auth Service or a test-valid RS256 token signed by the local development private key to call protected Catalog endpoints.
* Verify unauthenticated requests to `GET /catalog/songs` and `GET /catalog/songs/{id}` return `401`.
* Verify authenticated `GET /catalog/songs` returns a paginated non-empty response after ingestion.
* Verify authenticated `GET /catalog/songs/{id}` returns detailed metadata for a seeded song.
* Verify `/actuator/health` returns `UP` and `/actuator/prometheus` exposes metrics.

#### Required Unit Tests

* Catalog service pagination returns requested page content and metadata.
* Catalog service bounds or rejects invalid pagination input according to the generated validation rules.
* Single-song lookup returns details when present.
* Single-song lookup raises a not-found error when absent.
* Dataset ingestion parses representative CSV rows and maps expected metadata fields.
* Dataset ingestion is idempotent and does not duplicate existing songs.

#### Required Integration Tests

* `GET /catalog/songs` requires JWT authentication.
* `GET /catalog/songs/{id}` requires JWT authentication.
* Authenticated `GET /catalog/songs` returns paginated song data from the persisted dataset.
* Authenticated `GET /catalog/songs/{id}` returns persisted detailed metadata.
* Missing song id returns `404`.
* Startup migration and ingestion persist data in the test database.
* `/actuator/health` is reachable as an operational endpoint.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Catalog Service with Java 21, Spring Boot 3.x, and Maven. | All backend services should use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/catalog-service/pom.xml`, Java source tree, Dockerfile. |
| Use PostgreSQL as the Catalog system of record. | Catalog data is structured song metadata and must use a dedicated persistence layer. | `ARCHITECTURE.md` Catalog persistence; `REQUIREMENTS.md` M-26; `TECH-STACK.md` Catalog database choice. | `catalog-db`, `catalog-db-data`, migrations, JPA entities/repositories. |
| Implement required endpoints `GET /catalog/songs` and `GET /catalog/songs/{id}` only for this phase. | These are the mandatory Catalog endpoints; artist top-tracks is should-have, not required for the minimum phase. | `ARCHITECTURE.md` Catalog required endpoints; `REQUIREMENTS.md` M-07, M-08, S-01. | `CatalogController`, DTOs, service tests, integration tests. |
| Protect Catalog application endpoints with JWT verification using the shared public key. | Only Auth registration and login may be public, and other services must validate JWTs locally. | `REQUIREMENTS.md` M-03 and M-25. | `SecurityConfig`, JWT config, integration tests, Compose key mount. |
| Do not add Kafka messaging to Catalog Service. | The source documents do not define Catalog event production or consumption. | `ARCHITECTURE.md` Catalog specification; `TECH-STACK.md` Messaging Infrastructure per Service. | `pom.xml`, `application.yml`, Catalog service code. |
| Use Flyway migrations and application-owned startup ingestion. | Schema and data loading should be repeatable during container startup without manual import steps. | `REQUIREMENTS.md` M-09; `TECH-STACK.md` Catalog PostgreSQL rationale. | `V1__create_songs.sql`, ingestion package, Docker/Compose dataset mount. |
| Make dataset ingestion idempotent. | Service restarts must not duplicate song rows in the dedicated database. | `REQUIREMENTS.md` M-09 and M-26. | Ingestion runner, repository constraints, integration tests. |
| Treat the exact dataset CSV schema and filename as missing details to resolve during generation. | The documents name the Kaggle dataset but do not provide local file layout or column names. | `ARCHITECTURE.md` Dataset requirements; user instruction to list missing details instead of inventing behavior. | Dataset mount, ingestion parser, `Song` entity, DTO mapping, tests. |
| Exclude optional artist top-tracks from the current plan. | It is a should-have feature, while this phase targets required Catalog behavior. | `ARCHITECTURE.md` optional Catalog behavior; `REQUIREMENTS.md` S-01. | No artist endpoint or artist-specific service code in this phase. |

### Phase 2 Step 2 Generation - Catalog Service

#### Completed Artifacts

* Replaced the Phase 0 Catalog placeholder container with a real Java 21 Spring Boot 3.3.5 Maven application.
* Implemented protected `GET /catalog/songs` with pagination metadata and protected `GET /catalog/songs/{id}` with detailed song metadata.
* Added local JWT verification through the shared public key so Catalog does not call Auth Service per request.
* Added PostgreSQL persistence through Spring Data JPA and Flyway migration `V1__create_songs.sql`.
* Added automatic startup CSV ingestion through `CatalogDatasetIngestionRunner`.
* Added idempotent ingestion that skips already persisted song IDs on restart.
* Added a small bundled CSV at `services/catalog-service/data/catalog.csv` so the service is runnable in this workspace while the exact Kaggle CSV file is absent.
* Added Actuator health and Prometheus metrics exposure.
* Added unit tests for pagination, single-song lookup, missing-song handling, CSV parsing, and idempotent ingestion.
* Added integration tests for protected endpoint access, paginated persisted data, single-song detail retrieval, missing-song `404`, migration, ingestion, and operational health.
* Added `services/catalog-service/README.md`, service `.env.example`, Maven `pom.xml`, Dockerfile, application config, source code, tests, and `.dockerignore`.
* Updated root `.env.example` and `docker-compose.yml` with Catalog ingestion and pagination configuration.

#### Generation Decisions and Corrections Recorded

| Decision or correction | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Use Spring Security resource-server JWT validation for all Catalog application endpoints. | Only Auth register/login may be public, and protected services must validate JWTs locally. | `REQUIREMENTS.md` M-03 and M-25. | `SecurityConfig`, `JwtProperties`, `KeyConfig`, `PemKeyLoader`, integration tests, `docker-compose.yml`. |
| Keep `/actuator/health` and `/actuator/prometheus` public as operational endpoints. | Prometheus scraping and runtime health checks are mandatory, while no extra public application API was added. | `REQUIREMENTS.md` M-24 and M-25. | `SecurityConfig`, `application.yml`, runtime validation. |
| Use a flexible CSV parser with column aliases for title, artist, album, genre, tempo/BPM, release date/year, popularity, and duration. | The source documents require the Kaggle dataset but do not specify exact local CSV column names. | `ARCHITECTURE.md` Catalog dataset requirements; `REQUIREMENTS.md` M-08 and M-09. | `CatalogCsvReader`, ingestion tests, DTO/entity mapping. |
| Store raw normalized CSV fields in a metadata map. | Catalog responses and downstream Search/Recommendation flows need the available dataset metadata, including fields not normalized into first-class columns yet. | `ARCHITECTURE.md` Catalog dataset requirements; `REQUIREMENTS.md` M-08. | `Song`, DTOs, `StringMapConverter`, migration. |
| Use `TEXT` plus a JPA converter for metadata instead of PostgreSQL-only `jsonb`. | This keeps metadata portable across PostgreSQL runtime and H2-based automated integration tests while retaining structured JSON content in the column. | `REQUIREMENTS.md` Backend testing requirements; `TECH-STACK.md` Catalog PostgreSQL runtime choice. | `StringMapConverter`, `Song`, `V1__create_songs.sql`, tests. |
| Bundle a small runnable CSV seed file because no Kaggle CSV/archive exists in the workspace. | The service must start and ingest automatically in this phase; the exact required full dataset file is absent and was already recorded as a missing detail. | `REQUIREMENTS.md` M-09; `ARCHITECTURE.md` Catalog dataset requirement; Phase 2 Step 1 missing-details note. | `services/catalog-service/data/catalog.csv`, Dockerfile, README, ingestion config. |
| Default pagination to page `0`, size `20`, and max size `100`. | The documents require consistent pagination but do not define numeric defaults or limits. | `REQUIREMENTS.md` M-07; Phase 2 Step 1 missing-details note. | `CatalogController`, `CatalogDatasetProperties`, `.env.example`, tests. |
| Resolve default page size in service code instead of an annotation placeholder. | This ensures environment-provided pagination defaults are applied through normal configuration properties. | `REQUIREMENTS.md` M-07. | `CatalogController`, `CatalogService`, `CatalogServiceTest`. |
| Exclude Catalog Kafka dependencies and event tests. | Catalog has no required event production or consumption behavior. | `ARCHITECTURE.md` Catalog Service; `TECH-STACK.md` Messaging Infrastructure per Service. | `pom.xml`, service implementation, tests. |
| Exclude optional artist top-tracks endpoint from generated code. | The current phase targets mandatory Catalog requirements only. | `REQUIREMENTS.md` S-01; `ARCHITECTURE.md` optional Catalog behavior. | No artist controller/service files generated. |

#### Validation Results

* `docker compose build catalog-service` initially failed because `JwtProperties` was not registered and a unit test mocked pagination metadata incorrectly.
* Fixed configuration-property registration and the pagination unit test, then reran the build.
* `docker compose build catalog-service` completed successfully and ran 10 Maven tests during the image build.
* `docker compose up -d --build catalog-db catalog-service` started Catalog Service and its dedicated PostgreSQL database.
* Catalog startup logs confirmed PostgreSQL connection, successful Flyway migration application, and automatic ingestion from `/app/data/catalog.csv`.
* `docker compose ps catalog-db catalog-service` confirmed `catalog-db` is healthy and `catalog-service` is running.
* Live smoke validation confirmed unauthenticated `GET /catalog/songs` returns `401`.
* Live smoke validation confirmed an RS256 token signed by the local development private key can access `GET /catalog/songs?page=0&size=2`.
* Live smoke validation confirmed `GET /catalog/songs/{id}` returns detailed metadata for a persisted song and a missing song returns `404`.
* Live smoke validation confirmed omitted `size` uses the configured default page size and explicit `size=2` returns two songs.
* `/actuator/health` returned `UP`, `/actuator/prometheus` returned status 200 with JVM metrics, and `docker compose config --quiet` passed.

#### Assumptions and Unresolved Dataset Issue

* The full Kaggle Spotify Music Analytics dataset file is not present in the workspace. The generated service is runnable with the bundled seed CSV and is designed to ingest a real CSV mounted or copied to `CATALOG_DATASET_PATH` when the dataset is available.
* No unresolved Catalog implementation or test failure remains for this generation phase.

### Phase 2 Step 3 Review - Catalog Service Validation Pass

#### Review Scope

* Reviewed Catalog Service against `ARCHITECTURE.md` Catalog Service requirements, `REQUIREMENTS.md` M-07, M-08, M-09, M-24, M-25, M-26, and backend testing requirements, plus `TECH-STACK.md` Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, and PostgreSQL choices.
* Revalidated after `services/catalog-service/data/catalog.csv` was replaced with the Kaggle CSV.
* Confirmed no conflicts were found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for this Catalog review.

#### Validated Checklist Items

* Required endpoints exist: `GET /catalog/songs` and `GET /catalog/songs/{id}`.
* Catalog application endpoints require JWT authentication and reject unauthenticated requests.
* Catalog validates JWTs locally with the shared public key configuration.
* Catalog uses its own dedicated PostgreSQL persistence layer through `catalog-db`.
* Startup ingestion reads the Kaggle CSV at `/app/data/catalog.csv` without manual import steps.
* Song records preserve required browsing/search/recommendation metadata including title, artist, album, genre, tempo/BPM, release date/year, popularity, duration, and raw metadata.
* Dockerfile, dependency manifest, configuration, README, unit tests, and integration tests are present.
* No Catalog Kafka behavior, artist top-tracks endpoint, or other optional behavior was added.

#### Fixes Made During Review

* Fixed startup ingestion for the 85,000-row Kaggle CSV by streaming CSV records instead of materializing the whole file in memory.
* Changed persistence to batch inserts and clear the JPA persistence context between batches.
* Added a repository query to find existing IDs per batch so ingestion remains idempotent without issuing one lookup per row.
* Added Hibernate JDBC batching configuration for Catalog inserts.
* Updated the ingestion unit test to cover the new batched persistence path.

#### Validation Commands Run

* Confirmed the replaced CSV has 85,000 data rows and the expected Kaggle-style headers.
* `docker compose build --no-cache catalog-service` passed and reran the Maven test suite.
* `docker compose up -d --build catalog-db catalog-service` passed after the ingestion fix.
* `docker compose ps catalog-db catalog-service` confirmed `catalog-db` healthy and `catalog-service` running.
* Startup logs confirmed PostgreSQL connection, Flyway validation, and ingestion completion from `/app/data/catalog.csv`: 85,000 rows read and 85,000 inserted.
* Live smoke validation confirmed unauthenticated `GET /catalog/songs` returns `401`.
* Live smoke validation confirmed an RS256 token signed by the local development private key can access `GET /catalog/songs?page=0&size=2`.
* Live smoke validation confirmed `GET /catalog/songs/{id}` returns detailed metadata and a missing song returns `404`.
* `/actuator/health` returned `UP`, `/actuator/prometheus` returned status 200, and `docker compose config --quiet` passed.

#### Review Decisions and Corrections Recorded

| Decision, correction, or finding | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Stream the Kaggle CSV during startup ingestion. | The real 85,000-row dataset exceeded the memory behavior of the generated all-rows-in-memory ingestion path. | `REQUIREMENTS.md` M-09; `TECH-STACK.md` Catalog needs reliable startup ingestion. | `CatalogCsvReader`, `CatalogDatasetIngestionRunner`. |
| Persist songs in 500-row batches and clear the JPA persistence context after each batch. | The service must ingest the full dataset within the configured Compose resource limits. | `REQUIREMENTS.md` M-20 and M-09. | `CatalogDatasetIngestionRunner`, `application.yml`. |
| Query existing IDs per batch instead of checking one row at a time. | This keeps ingestion idempotent while reducing database round trips for the real dataset. | `REQUIREMENTS.md` M-09; Phase 2 plan idempotency decision. | `SongRepository`, `CatalogDatasetIngestionRunner`, ingestion unit test. |
| Keep the persistent `catalog-db` volume rather than deleting local data during review. | Deleting the database volume would be destructive local state cleanup, not a source-code correction. | User instruction not to modify unrelated files or data; `REQUIREMENTS.md` M-26 dedicated persistence. | Runtime validation notes only. |

#### Assumptions and Unresolved Issues After Review

* Assumption: the replaced `services/catalog-service/data/catalog.csv` is the required Kaggle Spotify Music Analytics dataset supplied by the user.
* The existing local `catalog-db` volume still contains 4 rows from the earlier generated seed file, so the live endpoint reported 85,004 total rows after inserting 85,000 Kaggle rows. This is residual local database state, not an implementation or test failure. A fresh `catalog-db` volume would contain only the 85,000 ingested Kaggle rows.
* No unresolved Catalog implementation or test issues remain for this phase.

### Phase 3 Step 1 Plan - Playlist Service

#### Source Document Check

* No conflicts were found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for the Playlist Service plan.
* `ARCHITECTURE.md` defines Playlist Service responsibilities as user playlist CRUD, playlist track operations, track reordering, and a per-user special `Liked Songs` playlist.
* `REQUIREMENTS.md` M-10, M-11, M-24, M-25, M-26, and backend testing requirements define the validation baseline for this service.
* `TECH-STACK.md` requires Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, PostgreSQL for Playlist persistence, and Prometheus-compatible metrics.

#### Assumptions

* This step is planning only. No Playlist Service source code, Dockerfile replacement, dependency manifest, migrations, tests, or Compose changes are generated in this step.
* All Playlist application endpoints are protected endpoints because `REQUIREMENTS.md` M-25 says only `POST /auth/register` and `POST /auth/login` may be publicly accessible.
* `/actuator/health` and `/actuator/prometheus` are treated as operational interfaces, not public application APIs, following the existing M-24/M-25 interpretation used for Auth and Catalog.
* Playlist Service will validate JWTs locally using the mounted shared public key; it will not call Auth Service per request.
* Playlist ownership is derived from the JWT subject claim because the documents require authenticated per-user behavior but do not define another user identity source.
* Song IDs stored in playlists are references to Catalog song IDs; Playlist Service does not copy catalog metadata or call Catalog Service in this phase because the source documents do not require that integration for minimum playlist behavior.
* Kafka playlist update events are not included in the minimum plan. `TECH-STACK.md` says Playlist may publish playlist update events if that event source is used for notifications, while `REQUIREMENTS.md` does not require Playlist event production in M-10 or M-11.
* Optional mood-based smart playlists, queue management, version history, undo, and collaborative editing are excluded from this phase because they are could-have features.

#### Missing Details to Resolve During Generation

* Exact request and response JSON schemas are not defined by the source documents.
* Exact playlist name and description validation limits are not defined.
* Exact maximum playlist size, duplicate-track behavior, and track-position numbering conventions are not defined.
* Exact behavior when adding a song ID that does not exist in Catalog is not defined; the plan records the song ID as a reference and does not validate against Catalog in this phase.
* Exact error response format is not defined; generation should follow the local pattern already used by Auth and Catalog.
* Exact Liked Songs creation timing is not defined; the plan assumes it should be created lazily when a user first interacts with Playlist Service.

#### Chosen Stack

* Language: Java 21.
* Framework: Spring Boot 3.x.
* Build tool: Maven.
* HTTP/API: Spring Web MVC.
* Persistence: PostgreSQL through Spring Data JPA.
* Database migrations: Flyway.
* Security: Spring Security resource server with JWT verification from the shared public key.
* Observability: Spring Boot Actuator with Micrometer Prometheus registry.
* Containerization: Docker and existing root `docker-compose.yml`.

#### Planned File Tree

```text
services/playlist-service/
  pom.xml
  Dockerfile
  README.md
  .env.example
  src/
    main/
      java/
        com/benchmark/playlist/
          PlaylistServiceApplication.java
          config/
            ClockConfig.java
            JwtProperties.java
            KeyConfig.java
            SecurityConfig.java
          controller/
            ApiError.java
            ApiExceptionHandler.java
            PlaylistController.java
          dto/
            AddTrackRequest.java
            CreatePlaylistRequest.java
            PlaylistResponse.java
            PlaylistSummaryResponse.java
            PlaylistTrackResponse.java
            ReorderTracksRequest.java
            UpdatePlaylistRequest.java
          entity/
            Playlist.java
            PlaylistTrack.java
          repository/
            PlaylistRepository.java
            PlaylistTrackRepository.java
          security/
            AuthenticatedUser.java
            PemKeyLoader.java
            UserPrincipalResolver.java
          service/
            LikedSongsService.java
            PlaylistAccessDeniedException.java
            PlaylistNotFoundException.java
            PlaylistService.java
            PlaylistTrackNotFoundException.java
      resources/
        application.yml
        db/
          migration/
            V1__create_playlists.sql
    test/
      java/
        com/benchmark/playlist/
          controller/
            PlaylistControllerIntegrationTest.java
          service/
            LikedSongsServiceTest.java
            PlaylistServiceTest.java
          support/
            TestKeyFiles.java
```

If generation needs small mapper/helper classes to keep controllers thin, they should remain within the controller, dto, or service package boundaries above.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web`
* `spring-boot-starter-data-jpa`
* `spring-boot-starter-validation`
* `spring-boot-starter-security`
* `spring-boot-starter-oauth2-resource-server`
* `spring-boot-starter-actuator`
* `micrometer-registry-prometheus`
* `flyway-core`
* `flyway-database-postgresql`
* `postgresql`
* Test dependencies: `spring-boot-starter-test`, `spring-security-test`, H2 in PostgreSQL compatibility mode or another automated test database approach that can run inside the Docker/Maven validation path.

Kafka dependencies are not planned for this minimum Playlist phase because event publication is not required by M-10 or M-11.

#### Endpoints and Exposed Interfaces

Application endpoints, all protected by JWT:

* `GET /playlists`
  * Returns playlists owned by the authenticated user.
  * Ensures the user's special `Liked Songs` playlist is available.

* `POST /playlists`
  * Creates a regular playlist owned by the authenticated user.
  * Request fields planned from missing schema details: `name` and optional `description`.

* `GET /playlists/{id}`
  * Returns one playlist owned by the authenticated user, including ordered tracks.

* `PATCH /playlists/{id}`
  * Updates mutable playlist metadata for a playlist owned by the authenticated user.
  * Must not rename or mutate protected behavior of `Liked Songs` unless generation records a safe allowed subset.

* `DELETE /playlists/{id}`
  * Deletes a regular playlist owned by the authenticated user.
  * Must not delete the user's `Liked Songs` playlist.

* `POST /playlists/{id}/tracks`
  * Adds a song reference to a playlist owned by the authenticated user.
  * Request fields planned from missing schema details: `songId`.

* `DELETE /playlists/{id}/tracks/{songId}`
  * Removes a song reference from a playlist owned by the authenticated user.

* `PATCH /playlists/{id}/tracks/reorder`
  * Reorders tracks transactionally for a playlist owned by the authenticated user.
  * Request should contain an ordered list of song IDs or track IDs; exact schema is missing and must be recorded during generation.

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

No optional queue, collaboration, version-history, smart-playlist, or mood endpoint is included in this plan.

#### Environment Variables

Planned service variables:

* `SERVER_PORT` - defaults to `8080`.
* `SPRING_DATASOURCE_URL` - defaults to `jdbc:postgresql://playlist-db:5432/playlist`.
* `SPRING_DATASOURCE_USERNAME` - defaults to Compose PostgreSQL user.
* `SPRING_DATASOURCE_PASSWORD` - defaults to Compose PostgreSQL password.
* `JWT_PUBLIC_KEY_PATH` - path to the mounted shared public key.
* `PLAYLIST_DEFAULT_LIKED_SONGS_NAME` - optional default value `Liked Songs` if generation makes the special playlist name configurable.

The existing Compose skeleton already defines `KAFKA_BOOTSTRAP_SERVERS` for Playlist Service, but this phase does not plan to consume it unless generation scope is expanded to playlist update events.

#### Persistence and Messaging Approach

* Playlist Service uses only its dedicated `playlist-db` PostgreSQL service and `playlist-db-data` volume.
* Flyway owns schema creation.
* Planned tables:
  * `playlists`: playlist id, owner user id, name, description, special playlist type or liked-songs flag, created timestamp, updated timestamp.
  * `playlist_tracks`: track row id or composite identity, playlist id, song id reference, position, added timestamp.
* Playlist ownership is enforced by queries that include both playlist id and authenticated owner id.
* `Liked Songs` is represented as a special playlist row per user with uniqueness enforcing one liked-songs playlist per owner.
* Track reorder should run in one transaction and persist a stable contiguous order.
* No shared database access, Catalog database access, or Kafka event production/consumption is planned for this phase.

#### Validation Steps

* Confirm no source-document conflict before generation.
* Build Playlist Service with Docker so Maven tests run in the build path.
* Run `docker compose config --quiet`.
* Run `docker compose build playlist-service`.
* Run `docker compose up -d --build playlist-db playlist-service`.
* Confirm `playlist-db` is healthy and `playlist-service` is running.
* Confirm startup logs show successful PostgreSQL connection and Flyway migration.
* Use a JWT issued by Auth Service or a test-valid RS256 token signed by the local development private key to call protected Playlist endpoints.
* Verify unauthenticated requests to all Playlist application endpoints return `401`.
* Verify authenticated `GET /playlists` returns only the authenticated user's playlists and includes the special `Liked Songs` playlist.
* Verify authenticated playlist create, read, update, and delete behavior for regular playlists.
* Verify `Liked Songs` cannot be deleted through `DELETE /playlists/{id}`.
* Verify adding, removing, and transactionally reordering tracks works for a user-owned playlist.
* Verify another user's JWT cannot access or mutate a playlist they do not own.
* Verify `/actuator/health` returns `UP` and `/actuator/prometheus` exposes metrics.

#### Required Unit Tests

* Playlist creation stores the authenticated user as owner.
* Listing returns only playlists owned by the authenticated user.
* Liked Songs is created or available per user and is unique per owner.
* Regular playlist metadata update succeeds for the owner.
* Regular playlist deletion succeeds for the owner.
* Liked Songs deletion is rejected.
* Track addition appends with the correct order.
* Track removal removes the selected song reference and compacts or preserves order according to the generated rule.
* Track reorder validates membership and persists the requested order transactionally.
* Cross-user access is rejected in service logic.

#### Required Integration Tests

* Each required endpoint rejects missing/invalid JWT with `401`.
* `GET /playlists` returns only the authenticated user's playlists and includes `Liked Songs`.
* `POST /playlists` creates a regular playlist for the authenticated user.
* `GET /playlists/{id}` returns playlist details and ordered tracks.
* `PATCH /playlists/{id}` updates regular playlist metadata.
* `DELETE /playlists/{id}` deletes a regular playlist and rejects deletion of `Liked Songs`.
* `POST /playlists/{id}/tracks` adds a song reference.
* `DELETE /playlists/{id}/tracks/{songId}` removes a song reference.
* `PATCH /playlists/{id}/tracks/reorder` updates track order transactionally.
* Cross-user access to playlist read/update/delete/track operations returns a forbidden or not-found response according to the generated access strategy.
* Flyway migration and persistence behavior run in the automated test database.
* `/actuator/health` is reachable as an operational endpoint.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Playlist Service with Java 21, Spring Boot 3.x, and Maven. | All backend services should use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/playlist-service/pom.xml`, Java source tree, Dockerfile. |
| Use PostgreSQL as the Playlist system of record. | Playlist ownership, CRUD, liked songs, and ordered tracks require transactional relational persistence. | `ARCHITECTURE.md` Playlist persistence; `REQUIREMENTS.md` M-10, M-11, M-26; `TECH-STACK.md` Playlist database choice. | `playlist-db`, `playlist-db-data`, migrations, JPA entities/repositories. |
| Implement exactly the eight required Playlist endpoints for this phase. | These are the minimum endpoints required by the architecture and requirements. | `ARCHITECTURE.md` Playlist required endpoints; `REQUIREMENTS.md` M-10. | `PlaylistController`, DTOs, service tests, integration tests. |
| Protect all Playlist application endpoints with JWT verification using the shared public key. | Only Auth registration and login may be public, and protected services must validate JWTs locally. | `REQUIREMENTS.md` M-03 and M-25. | `SecurityConfig`, JWT config, integration tests, Compose key mount. |
| Use JWT subject as Playlist owner id. | The documents require per-user playlist behavior but do not define a separate user lookup contract. | `ARCHITECTURE.md` Playlist behavior; `REQUIREMENTS.md` M-10, M-11, M-25. | Security principal resolver, `Playlist` entity, repository queries, tests. |
| Represent `Liked Songs` as a special playlist row per user. | The requirements say liked songs must be a per-user special playlist named `Liked Songs`. | `ARCHITECTURE.md` Playlist behavior; `REQUIREMENTS.md` M-11. | `Playlist` entity, Flyway migration, `LikedSongsService`, tests. |
| Exclude optional smart playlists, queue management, version history, undo, and collaborative editing from this phase. | These are could-have features, while the current phase targets must-have Playlist behavior only. | `ARCHITECTURE.md` Playlist optional behavior; `REQUIREMENTS.md` C-01, C-02, C-07, C-08. | No optional endpoint/service code in this phase. |
| Do not add Kafka messaging to the minimum Playlist implementation. | Playlist event production is conditional in `TECH-STACK.md`, and no must-have Playlist requirement requires event publication. | `TECH-STACK.md` Playlist messaging note; `REQUIREMENTS.md` M-10 and M-11. | `pom.xml`, application config, service code, tests. |
| Store song IDs as references without Catalog validation in this phase. | The source documents require Playlist track operations but do not require Playlist-to-Catalog validation. | `ARCHITECTURE.md` Playlist behavior; `REQUIREMENTS.md` M-10. | `PlaylistTrack` entity, DTOs, service logic, tests. |

### Phase 3 Step 2 Generation - Playlist Service

#### Completed Artifacts

* Replaced the Phase 0 Playlist placeholder with a runnable Java 21 Spring Boot service.
* Added `pom.xml`, a multi-stage Dockerfile, `.env.example`, `application.yml`, and a service README.
* Added Flyway migration `V1__create_playlists.sql` for the dedicated Playlist PostgreSQL schema.
* Added JWT resource-server security using the shared mounted public key.
* Added the eight required Playlist endpoints:
  * `GET /playlists`
  * `POST /playlists`
  * `GET /playlists/{id}`
  * `PATCH /playlists/{id}`
  * `DELETE /playlists/{id}`
  * `POST /playlists/{id}/tracks`
  * `DELETE /playlists/{id}/tracks/{songId}`
  * `PATCH /playlists/{id}/tracks/reorder`
* Added unit tests for Liked Songs creation and core playlist behavior.
* Added integration tests for JWT protection, CRUD behavior, track add/remove/reorder behavior, Liked Songs protection, cross-user isolation, invalid token rejection, and the operational health endpoint.

#### Generation Validation Run

* `docker compose build playlist-service` passed and ran the Maven package/test path.
* `docker compose config --quiet` passed. Docker still emitted the previously observed sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose up -d --build playlist-db playlist-service` initially failed because old local Docker containers referenced a stale deleted network id.
* Only the stale `playlist-db`, `playlist-service`, and `kafka` containers were removed with `docker compose rm -f playlist-db playlist-service kafka`; no volumes were deleted.
* `docker compose up -d --build playlist-db playlist-service` then passed.
* `docker compose ps playlist-db playlist-service kafka` confirmed PostgreSQL healthy, Kafka running because it is still a Compose dependency, and Playlist Service running on host port `8084`.
* Playlist Service logs confirmed PostgreSQL connection, Flyway migration success, JPA initialization, and Tomcat startup.
* Live smoke validation confirmed:
  * unauthenticated `GET /playlists` returns `401`;
  * `/actuator/health` returns `200`;
  * `/actuator/prometheus` returns `200`;
  * an RS256 token signed by the local development private key can create a playlist;
  * tracks can be added and reordered;
  * `GET /playlists` lazily creates and returns the user's `Liked Songs` playlist.

#### Generation Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Implement Playlist Service as a real Spring Boot service using the same dependency and Docker build pattern as Auth and Catalog. | The service must be runnable and consistent with the backend standard already used in the repo. | `TECH-STACK.md` Shared Backend Standard; user instruction for complete runnable implementation. | `services/playlist-service/pom.xml`, `Dockerfile`, Java source tree. |
| Use `playlists` and `playlist_tracks` tables owned by `playlist-db`. | Playlist CRUD, ownership, Liked Songs, and ordered tracks require transactional dedicated persistence. | `ARCHITECTURE.md` Playlist persistence; `REQUIREMENTS.md` M-10, M-11, M-26; `TECH-STACK.md` Playlist database choice. | `V1__create_playlists.sql`, `Playlist`, `PlaylistTrack`, repositories. |
| Use UUID identifiers for playlist and playlist-track rows. | UUIDs avoid cross-service numeric id assumptions and are supported by PostgreSQL and Hibernate for service-owned entities. | `REQUIREMENTS.md` M-26 service data isolation; implementation detail not specified by source docs. | Entities, DTOs, controller path variables, tests. |
| Store each `songId` at most once per playlist and make repeated add operations idempotent. | Duplicate-track behavior was not specified; one row per song reference keeps remove-by-song-id unambiguous for the required `DELETE /tracks/{songId}` endpoint. | `REQUIREMENTS.md` M-10 required track management; missing detail recorded in Phase 3 plan. | `playlist_tracks` unique constraint, `PlaylistService.addTrack`, tests. |
| Use zero-based contiguous track positions. | The documents require ordered reordering but do not define numbering; zero-based ordering is simple for APIs and internal list behavior. | `REQUIREMENTS.md` M-10; missing detail recorded in Phase 3 plan. | `PlaylistTrack`, reorder logic, integration tests. |
| Require reorder requests to include exactly the playlist's current song IDs. | This makes reorder transactional and prevents accidental track loss or implicit track creation during a reorder call. | `ARCHITECTURE.md` Playlist track operations; `REQUIREMENTS.md` M-10. | `ReorderTracksRequest`, `PlaylistService.reorderTracks`, tests. |
| Reject renaming and deletion of `Liked Songs`. | The requirement fixes a per-user special playlist named `Liked Songs`; preserving that name protects required behavior. | `ARCHITECTURE.md` Playlist behavior; `REQUIREMENTS.md` M-11. | `PlaylistService.updatePlaylist`, `PlaylistService.deletePlaylist`, integration tests. |
| Create `Liked Songs` lazily on `GET /playlists`. | Creation timing was missing; lazy creation keeps the playlist automatically available when the user first interacts with Playlist Service. | `REQUIREMENTS.md` M-11; Phase 3 plan assumption. | `LikedSongsService`, `PlaylistService.listPlaylists`, tests. |
| Return `404` for cross-user playlist access. | The source documents require user isolation but do not define disclosure behavior; querying by id and owner avoids revealing whether another user's playlist exists. | `REQUIREMENTS.md` M-25 protected endpoints; M-10 per-user playlist behavior. | Repository queries, `PlaylistService`, integration tests. |
| Do not add Kafka producer code in this phase. | Playlist update event publication is conditional in `TECH-STACK.md`; M-10 and M-11 do not require it. | `TECH-STACK.md` Playlist messaging note; `REQUIREMENTS.md` M-10 and M-11. | `pom.xml`, application config, service code. |
| Omit a database-level partial unique index for Liked Songs and enforce uniqueness through service logic in this phase. | H2-based integration tests need to run reliably in the Docker/Maven build path, and the source documents require behavior, not a specific constraint shape. | Backend testing requirements; `REQUIREMENTS.md` M-11. | `V1__create_playlists.sql`, `LikedSongsService`, tests. |
| Remove only stale Playlist/Kafka containers during runtime validation, preserving volumes. | Docker had local containers pointing at a deleted network id; removing only affected containers fixed startup without deleting persisted service data. | Validation need for current phase; no source requirement changed. | Local Docker runtime state only. |

#### Assumptions Made During Generation

* JWT subject remains the Playlist owner id.
* Request/response JSON schemas use `name`, optional `description`, `songId`, and `songIds` because exact schemas were not specified.
* Playlist Service stores Catalog song IDs as references without validating them against Catalog in this phase.
* Liked Songs is created lazily when `GET /playlists` is called.
* Cross-user reads and mutations return `404`.
* Optional smart playlists, queue management, version history, undo, collaborative editing, and playlist update events remain out of scope for this phase.

### Phase 3 Step 3 Review - Playlist Service

#### Review Results

* Reviewed Playlist Service against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, and the approved Phase 3 plan.
* Confirmed no conflicts were found between the source documents for this Playlist phase.
* Confirmed all eight required Playlist endpoints are implemented.
* Confirmed Playlist application endpoints are protected by JWT and validate tokens locally with the shared RSA public key configuration.
* Confirmed Playlist uses Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, PostgreSQL, Flyway, Actuator, and Prometheus metrics as required by `TECH-STACK.md`.
* Confirmed Playlist state is stored only in the dedicated `playlist-db` PostgreSQL persistence layer.
* Confirmed no optional Playlist features were added: no smart playlists, queue management, version history, undo, collaborative editing, or Kafka playlist update producer.

#### Fixes Made During Review

| Fix | Reason | Affected files |
| --- | --- | --- |
| Removed `KAFKA_BOOTSTRAP_SERVERS` and the `kafka` dependency from the `playlist-service` Compose service. | The approved Playlist plan and `TECH-STACK.md` only require Playlist Kafka production if playlist update events are used; this phase does not implement those events, so Kafka should not be a runtime prerequisite for Playlist startup. | `docker-compose.yml` |

#### Validation Commands Run

* `docker compose config --quiet` passed. Docker emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose build --no-cache playlist-service` passed and executed the Maven package/test path.
* `docker compose up -d --build playlist-db playlist-service` passed after the Compose dependency correction.
* `docker compose ps playlist-db playlist-service` confirmed `playlist-db` healthy and `playlist-service` running.
* Playlist Service logs confirmed PostgreSQL connection, Flyway validation, schema version `1`, JPA initialization, Actuator endpoint exposure, and Tomcat startup.
* Live smoke validation confirmed unauthenticated `GET /playlists` returns `401`.
* Live smoke validation confirmed `/actuator/health` and `/actuator/prometheus` return `200`.
* Live smoke validation with an RS256 token signed by the local development private key confirmed playlist creation, Liked Songs availability, track addition, and track reorder behavior.

#### Review Decisions and Corrections Recorded

| Decision, correction, or finding | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Keep Playlist independent of Kafka for this phase. | Playlist update event production is conditional and was not part of the approved minimum Playlist implementation. | `TECH-STACK.md` Playlist messaging note; `REQUIREMENTS.md` M-10 and M-11. | `docker-compose.yml`, `playlist-service`. |
| Mark Phase 3 validation complete after no-cache build, runtime startup, and live endpoint smoke checks passed. | The implementation, tests, containerization, persistence, protected endpoint behavior, and metrics exposure all met the Playlist acceptance criteria. | `REQUIREMENTS.md` M-10, M-11, M-24, M-25, M-26; backend testing requirements. | `PROGRESS.md`, `DESIGN-DECISIONS.md`, `services/playlist-service`. |

#### Assumptions and Unresolved Issues After Review

* Assumption: JWT `sub` remains the user identity for Playlist ownership, as planned.
* Assumption: song IDs remain Catalog references and are not validated against Catalog in this phase.
* No unresolved Playlist implementation or test issues remain for this phase.

### Phase 4 Step 1 Plan - Streaming Service

#### Source Document Check

* No conflicts were found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for the Streaming Service plan.
* `ARCHITECTURE.md` defines Streaming Service responsibilities as protected simulated song streaming, dummy segment payload generation, and playback interaction event emission.
* `REQUIREMENTS.md` M-04, M-05, M-06, M-24, M-25, and backend testing requirements define the validation baseline for this service.
* `TECH-STACK.md` requires Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, Kafka producer messaging, stateless Streaming persistence by default, and Prometheus-compatible metrics.

#### Assumptions

* This step is planning only. No Streaming Service source code, Dockerfile replacement, dependency manifest, configuration, tests, or Compose changes are generated in this step.
* `GET /stream/{songId}` is protected because `REQUIREMENTS.md` M-25 says only Auth registration and login may be public, and `ARCHITECTURE.md` explicitly says protected streaming access must require a valid JWT.
* `/actuator/health` and `/actuator/prometheus` are treated as operational interfaces, not public application APIs, following the existing Auth, Catalog, and Playlist interpretation of M-24/M-25.
* Streaming Service will validate JWTs locally using the mounted shared public key; it will not call Auth Service per request.
* Playback event `user` data will be derived from the JWT subject claim because the documents require user data in emitted events but do not define another user identity source.
* Song IDs are accepted as path references and are not validated against Catalog in this phase because the source documents do not require Streaming-to-Catalog validation for the minimum service.
* The simulated stream descriptor will use an HLS-style manifest plus service-hosted dummy segment URLs because `ARCHITECTURE.md` allows a simulated HLS manifest or equivalent stream descriptor.
* Event emission for `play.started`, `play.ended`, and `play.skipped` will be represented by explicit protected endpoints or actions tied to a generated stream session, because a single `GET /stream/{songId}` request cannot prove whether playback later ended or was skipped.
* No real audio files, object storage, or Streaming database will be added in this phase.

#### Missing Details to Resolve During Generation

* Exact HLS manifest or stream descriptor response shape is not defined.
* Exact dummy segment URL shape and segment count are not defined.
* Exact dummy segment media type is not defined.
* Exact segment payload byte pattern is not defined; generation should use generated bytes, not real audio.
* Exact playback session id format is not defined.
* Exact Kafka topic name is not defined.
* Exact JSON schema for playback event payloads is not defined beyond user, song, and timestamp data.
* Exact way to trigger `play.ended` and `play.skipped` events is not defined.
* Exact error response format is not defined; generation should follow the local pattern already used by Catalog and Playlist.

#### Chosen Stack

* Language: Java 21.
* Framework: Spring Boot 3.x.
* Build tool: Maven.
* HTTP/API: Spring Web MVC.
* Security: Spring Security resource server with JWT verification from the shared public key.
* Messaging: Spring Kafka producer to Apache Kafka.
* Persistence: stateless by default; no Streaming database in this phase.
* Observability: Spring Boot Actuator with Micrometer Prometheus registry.
* Containerization: Docker and existing root `docker-compose.yml`.

#### Planned File Tree

```text
services/streaming-service/
  pom.xml
  Dockerfile
  README.md
  .env.example
  src/
    main/
      java/
        com/benchmark/streaming/
          StreamingServiceApplication.java
          config/
            ClockConfig.java
            JwtProperties.java
            KeyConfig.java
            KafkaConfig.java
            SecurityConfig.java
            StreamingProperties.java
          controller/
            ApiError.java
            ApiExceptionHandler.java
            StreamingController.java
          dto/
            PlaybackEventRequest.java
            SegmentResponse.java
            StreamDescriptorResponse.java
          messaging/
            PlaybackEvent.java
            PlaybackEventPublisher.java
            PlaybackEventType.java
          security/
            AuthenticatedUser.java
            PemKeyLoader.java
            UserPrincipalResolver.java
          service/
            DummySegmentService.java
            StreamDescriptorService.java
            StreamingService.java
      resources/
        application.yml
    test/
      java/
        com/benchmark/streaming/
          controller/
            StreamingControllerIntegrationTest.java
          messaging/
            PlaybackEventPublisherTest.java
          service/
            DummySegmentServiceTest.java
            StreamingServiceTest.java
          support/
            TestKeyFiles.java
```

If generation needs small mapper/helper classes to keep controllers thin, they should remain within the controller, dto, messaging, or service package boundaries above.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web`
* `spring-boot-starter-validation`
* `spring-boot-starter-security`
* `spring-boot-starter-oauth2-resource-server`
* `spring-boot-starter-actuator`
* `spring-kafka`
* `micrometer-registry-prometheus`
* Test dependencies: `spring-boot-starter-test`, `spring-security-test`, `spring-kafka-test`, and Mockito support from Spring Boot test.

No JPA, Flyway, PostgreSQL driver, Redis, or object-storage dependencies are planned for this minimum stateless Streaming phase.

#### Endpoints and Exposed Interfaces

Application endpoints, all protected by JWT:

* `GET /stream/{songId}`
  * Returns a simulated HLS manifest or equivalent stream descriptor for the requested song.
  * Emits a `play.started` event with authenticated user id, song id, and timestamp.
  * Does not return or require real audio files.

* `GET /stream/{songId}/segments/{segmentIndex}`
  * Returns generated binary dummy segment payloads of configurable size.
  * This endpoint is planned as supporting infrastructure for M-05 because the required stream descriptor needs segment payload URLs to simulate network load.

* `POST /stream/{songId}/ended`
  * Emits a `play.ended` event with authenticated user id, song id, and timestamp.
  * Planned because M-06 requires ended events and no exact trigger is defined.

* `POST /stream/{songId}/skipped`
  * Emits a `play.skipped` event with authenticated user id, song id, and timestamp.
  * Planned because M-06 requires skipped events and no exact trigger is defined.

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

No catalog lookup, real audio endpoint, object-storage endpoint, or persistent streaming-session API is included in this plan.

#### Environment Variables

Planned service variables:

* `SERVER_PORT` - defaults to `8080`.
* `JWT_PUBLIC_KEY_PATH` - path to the mounted shared public key.
* `KAFKA_BOOTSTRAP_SERVERS` - defaults to `kafka:9092`.
* `STREAMING_PLAYBACK_EVENTS_TOPIC` - planned topic for playback events, default `playback-events`.
* `STREAMING_SEGMENT_COUNT` - configurable generated segment count per stream descriptor.
* `STREAMING_SEGMENT_SIZE_BYTES` - configurable dummy payload size for segment responses.
* `STREAMING_BASE_PATH` - optional base path for generated segment/action URLs, default derived from request path if omitted.

#### Persistence and Messaging Approach

* Streaming Service is stateless in this phase and does not get its own database.
* The service validates JWTs locally and derives the event user id from JWT `sub`.
* The service publishes JSON playback events to Kafka.
* Required event types:
  * `play.started`
  * `play.ended`
  * `play.skipped`
* Planned event fields:
  * event id
  * event type
  * user id
  * song id
  * timestamp
* Kafka is required for this service by `TECH-STACK.md`; Compose already has a `kafka` service and Streaming Service placeholder wiring.
* No event consumption is planned for Streaming Service.
* No persistence migration is planned because no Streaming state is persisted.

#### Validation Steps

* Confirm no source-document conflict before generation.
* Build Streaming Service with Docker so Maven tests run in the build path.
* Run `docker compose config --quiet`.
* Run `docker compose build streaming-service`.
* Run `docker compose up -d --build kafka streaming-service`.
* Confirm `streaming-service` and `kafka` are running.
* Confirm startup logs show JWT public key loading, Kafka producer configuration, Actuator endpoint exposure, and Tomcat startup.
* Use a JWT issued by Auth Service or a test-valid RS256 token signed by the local development private key to call protected Streaming endpoints.
* Verify unauthenticated requests to Streaming application endpoints return `401`.
* Verify authenticated `GET /stream/{songId}` returns a simulated descriptor/manifest and emits `play.started`.
* Verify authenticated segment endpoint returns generated binary payload with the configured size.
* Verify authenticated ended and skipped trigger endpoints publish `play.ended` and `play.skipped`.
* Verify Kafka contains the expected playback events, or use an automated test producer/consumer strategy to verify event publication.
* Verify `/actuator/health` returns `UP` and `/actuator/prometheus` exposes metrics.

#### Required Unit Tests

* Stream descriptor generation includes the requested song id and segment URLs.
* Stream descriptor generation respects configured segment count.
* Dummy segment generation returns generated payloads of the configured size.
* Dummy segment generation rejects invalid segment indexes.
* Playback event construction includes event type, authenticated user id, song id, timestamp, and event id.
* `play.started`, `play.ended`, and `play.skipped` event paths call the event publisher with the correct event type.
* JWT subject is resolved as the event user id.

#### Required Integration Tests

* `GET /stream/{songId}` rejects missing/invalid JWT with `401`.
* Segment, ended, and skipped endpoints reject missing/invalid JWT with `401`.
* `GET /stream/{songId}` with JWT returns a simulated HLS manifest or equivalent descriptor.
* Segment endpoint returns generated binary payload of the configured size.
* `GET /stream/{songId}` publishes a `play.started` event.
* Ended trigger publishes a `play.ended` event.
* Skipped trigger publishes a `play.skipped` event.
* Published playback events include user id, song id, and timestamp.
* `/actuator/health` is reachable as an operational endpoint.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Streaming Service with Java 21, Spring Boot 3.x, and Maven. | All backend services should use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/streaming-service/pom.xml`, Java source tree, Dockerfile. |
| Keep Streaming Service stateless with no database. | The minimum Streaming responsibilities do not require persistent state. | `TECH-STACK.md` Streaming persistence choice; `ARCHITECTURE.md` Streaming persistence note. | `services/streaming-service`, `docker-compose.yml`. |
| Protect all Streaming application endpoints with JWT verification using the shared public key. | Protected streaming access must require JWT, and only Auth registration/login may be public. | `ARCHITECTURE.md` Streaming security; `REQUIREMENTS.md` M-03 and M-25. | `SecurityConfig`, JWT config, integration tests, Compose key mount. |
| Use JWT subject as playback event user id. | Events must contain user data, but the documents do not define another user identity lookup contract. | `REQUIREMENTS.md` M-06 and M-25. | `UserPrincipalResolver`, event DTOs, tests. |
| Publish playback events to Kafka as a producer. | Streaming is required to emit playback events consumed asynchronously by other services. | `REQUIREMENTS.md` M-06; `TECH-STACK.md` Streaming messaging choice. | `spring-kafka` dependency, `PlaybackEventPublisher`, Compose Kafka dependency, tests. |
| Use `playback-events` as the default Kafka topic name. | The documents require event publication but do not specify a topic; a configurable default is needed for runnable local validation. | `REQUIREMENTS.md` M-06; missing detail recorded above. | `application.yml`, `.env.example`, `PlaybackEventPublisher`, tests. |
| Return a simulated HLS-style manifest or descriptor from `GET /stream/{songId}`. | The architecture allows a simulated HLS manifest or equivalent stream descriptor and forbids real audio files. | `ARCHITECTURE.md` Streaming behavior; `REQUIREMENTS.md` M-04 and W-03. | `StreamingController`, descriptor DTO/service, tests. |
| Add generated segment endpoint as supporting Streaming interface. | M-05 requires configurable dummy segment payloads; segment URLs provide the network-load target referenced by the descriptor. | `REQUIREMENTS.md` M-05. | `StreamingController`, `DummySegmentService`, tests. |
| Add explicit ended/skipped trigger endpoints for this phase. | M-06 requires `play.ended` and `play.skipped`; the source documents do not define another trigger mechanism before the frontend/player is implemented. | `REQUIREMENTS.md` M-06; `ARCHITECTURE.md` Playback Integration. | `StreamingController`, `StreamingService`, event tests. |
| Exclude real audio files and object storage. | Real audio playback files are out of scope and object storage is not part of the minimum stack without a real need. | `REQUIREMENTS.md` W-03; `TECH-STACK.md` Object Storage. | No object storage config or audio file assets. |

### Phase 4 Step 2 Generation - Streaming Service

#### Completed Artifacts

* Replaced the Phase 0 Streaming placeholder with a runnable Java 21 Spring Boot service.
* Added `pom.xml`, a multi-stage Dockerfile, `.env.example`, `application.yml`, and a service README.
* Added local JWT validation through the shared public key configuration.
* Added Kafka playback event production using Spring Kafka and JSON serialization.
* Added an explicit Kafka topic declaration for the configured playback events topic.
* Added the required protected streaming endpoint:
  * `GET /stream/{songId}`
* Added supporting protected endpoints for the required dummy payload and playback event behavior:
  * `GET /stream/{songId}/segments/{segmentIndex}`
  * `POST /stream/{songId}/ended`
  * `POST /stream/{songId}/skipped`
* Added operational Actuator health and Prometheus metrics exposure.
* Added unit tests for dummy segment generation, stream descriptor/event behavior, and Kafka publisher topic/key behavior.
* Added integration tests for protected endpoint access, descriptor response shape, generated binary segment size, ended/skipped event responses, publisher calls, invalid segment handling, and health.

#### Generation Validation Run

* Initial `docker compose build streaming-service` failed because one MockMvc assertion used a Hamcrest array matcher where a literal byte array was required.
* The segment integration test was corrected to inspect the response byte array length directly.
* `docker compose build streaming-service` passed and ran the Maven package/test path.
* `docker compose config --quiet` passed. Docker still emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose up -d --build kafka streaming-service` passed.
* `docker compose ps kafka streaming-service` confirmed Kafka and Streaming Service are running.
* Streaming Service logs confirmed Actuator endpoint exposure, Kafka admin startup for topic declaration, and Tomcat startup.
* Live smoke validation confirmed:
  * unauthenticated `GET /stream/{songId}` returns `401`;
  * `/actuator/health` returns `200`;
  * `/actuator/prometheus` returns `200`;
  * authenticated `GET /stream/{songId}` returns a `simulated-hls-descriptor`;
  * generated segment payload size matches the configured default of `65536` bytes;
  * `POST /stream/{songId}/ended` returns a `play.ended` event;
  * `POST /stream/{songId}/skipped` returns a `play.skipped` event;
  * emitted event responses include the JWT subject as `userId`.

#### Generation Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Implement Streaming Service as a real Spring Boot service using the same Docker/Maven pattern as Auth, Catalog, and Playlist. | The service must be runnable and consistent with the backend standard already used in the repo. | `TECH-STACK.md` Shared Backend Standard; user instruction for complete runnable implementation. | `services/streaming-service/pom.xml`, `Dockerfile`, Java source tree. |
| Keep Streaming stateless and omit JPA, Flyway, and database dependencies. | The minimum Streaming service does not need mandatory persistence. | `TECH-STACK.md` Streaming persistence choice; `ARCHITECTURE.md` Streaming persistence note. | `pom.xml`, `application.yml`, `docker-compose.yml`. |
| Return a JSON stream descriptor instead of a literal HLS playlist file. | `ARCHITECTURE.md` allows a simulated HLS manifest or equivalent stream descriptor; JSON is straightforward for tests and frontend integration while still carrying segment URLs. | `ARCHITECTURE.md` Streaming required behavior; `REQUIREMENTS.md` M-04. | `StreamDescriptorResponse`, `StreamDescriptorService`, `StreamingController`, tests. |
| Add a generated segment endpoint with configurable payload size. | The service must generate configurable dummy segment payloads to simulate network load. | `REQUIREMENTS.md` M-05. | `DummySegmentService`, `StreamingController`, `.env.example`, `application.yml`, `docker-compose.yml`, tests. |
| Publish `play.started` when `GET /stream/{songId}` is called. | The stream request is the minimum concrete signal that playback has started. | `REQUIREMENTS.md` M-06; `ARCHITECTURE.md` Streaming behavior. | `StreamingService`, `PlaybackEventPublisher`, tests. |
| Add explicit `ended` and `skipped` trigger endpoints. | Required ended/skipped event types need deterministic triggers before the frontend/player exists. | `REQUIREMENTS.md` M-06; missing trigger detail recorded in the Phase 4 plan. | `StreamingController`, `StreamingService`, integration tests. |
| Use JWT subject as playback event `userId`. | Events require user data and the documents do not define another user identity lookup. | `REQUIREMENTS.md` M-06 and M-25. | `UserPrincipalResolver`, `StreamingService`, tests. |
| Use `playback-events` as the configurable default Kafka topic. | The documents require Kafka event publication but do not specify a topic name. | `REQUIREMENTS.md` M-06; `TECH-STACK.md` Streaming messaging choice. | `StreamingProperties`, `PlaybackEventPublisher`, `KafkaTopicConfig`, `.env.example`, `application.yml`, `docker-compose.yml`. |
| Add a `NewTopic` bean for the playback events topic. | Declaring the topic at startup avoids relying only on broker-side auto-create behavior for the required event channel. | `TECH-STACK.md` Kafka event backbone; `REQUIREMENTS.md` M-06. | `KafkaTopicConfig`. |
| Disable Kafka admin auto-create only in the MockMvc integration test. | The production service should declare its topic, but tests should remain broker-free and fast while publisher behavior is verified with mocks. | Backend testing requirements; no runtime requirement changed. | `StreamingControllerIntegrationTest`. |
| Keep real audio files and object storage absent. | Real audio files are explicitly out of scope, and object storage is not part of the minimum stack without a real need. | `REQUIREMENTS.md` W-03; `TECH-STACK.md` Object Storage. | No audio assets or object storage config. |

#### Assumptions Made During Generation

* JWT subject remains the event user id.
* Song IDs are accepted as opaque path references and are not validated against Catalog in this phase.
* A JSON descriptor qualifies as the equivalent stream descriptor allowed by the architecture.
* Generated segment bytes are deterministic dummy payloads, not audio.
* `play.ended` and `play.skipped` are triggered through explicit protected POST endpoints until the frontend/player phase defines a richer playback state machine.
* Streaming Service owns no database state in this phase.

### Phase 4 Step 3 Review - Streaming Service

#### Review Results

* Reviewed Streaming Service against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, and the approved Phase 4 plan.
* Confirmed no conflicts were found between the source documents for this Streaming phase.
* Confirmed `GET /stream/{songId}` is implemented as a protected endpoint and returns a simulated stream descriptor.
* Confirmed supporting protected endpoints exist for generated segment payloads and explicit ended/skipped playback event triggers.
* Confirmed Streaming validates JWTs locally with the shared RSA public key configuration.
* Confirmed Streaming is stateless and does not introduce a database, migration, object storage, or real audio files.
* Confirmed Streaming uses Kafka as a producer for `play.started`, `play.ended`, and `play.skipped` events.
* Confirmed the implementation follows Java 21, Spring Boot 3.x, Maven, Docker Compose, Actuator, Micrometer Prometheus, and Spring Kafka choices from `TECH-STACK.md`.

#### Fixes Made During Review

| Fix | Reason | Affected files |
| --- | --- | --- |
| Added an integration test asserting a malformed Bearer token is rejected with `401`. | The generated tests covered missing JWT, but the phase acceptance criteria also require invalid JWT behavior to be tested. | `services/streaming-service/src/test/java/com/benchmark/streaming/controller/StreamingControllerIntegrationTest.java` |

#### Validation Commands Run

* `docker compose build --no-cache streaming-service` passed and executed the Maven package/test path.
* `docker compose config --quiet` passed. Docker emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose up -d --build kafka streaming-service` passed.
* `docker compose ps kafka streaming-service` confirmed Kafka and Streaming Service are running.
* Streaming Service logs confirmed Kafka admin startup for the playback topic, Actuator endpoint exposure, and Tomcat startup.
* Live smoke validation confirmed:
  * unauthenticated `GET /stream/{songId}` returns `401`;
  * authenticated `GET /stream/{songId}` returns a `simulated-hls-descriptor`;
  * generated segment payload size is `65536` bytes with default config;
  * `POST /stream/{songId}/ended` returns `play.ended`;
  * `POST /stream/{songId}/skipped` returns `play.skipped`;
  * `/actuator/health` and `/actuator/prometheus` return `200`.
* Kafka topic validation confirmed the `playback-events` topic contains `play.started`, `play.ended`, and `play.skipped` records with user id, song id, and timestamp data.

#### Review Decisions and Corrections Recorded

| Decision, correction, or finding | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Keep the JSON descriptor as the simulated stream descriptor for this phase. | The architecture allows an equivalent stream descriptor, and the descriptor exposes generated segment URLs without real audio files. | `ARCHITECTURE.md` Streaming behavior; `REQUIREMENTS.md` M-04 and W-03. | `StreamDescriptorResponse`, `StreamDescriptorService`, `StreamingController`. |
| Keep explicit ended/skipped trigger endpoints. | The source documents require ended and skipped events, while no frontend playback state machine exists yet to trigger them indirectly. | `REQUIREMENTS.md` M-06; Phase 4 plan assumption. | `StreamingController`, `StreamingService`, tests. |
| Mark Phase 4 validation complete after tests, container startup, HTTP smoke checks, and Kafka topic inspection passed. | The implementation, tests, containerization, security, event production, generated payloads, and metrics exposure met the Streaming acceptance criteria. | `REQUIREMENTS.md` M-04, M-05, M-06, M-24, M-25; backend testing requirements. | `PROGRESS.md`, `DESIGN-DECISIONS.md`, `services/streaming-service`. |

#### Assumptions and Unresolved Issues After Review

* Assumption: JWT `sub` remains the event user id.
* Assumption: song IDs remain opaque references and are not validated against Catalog in this phase.
* Assumption: generated deterministic dummy bytes satisfy the configurable dummy payload requirement because the documents do not require true randomness.
* No unresolved Streaming implementation or test issues remain for this phase.

### Phase 5 Step 1 Plan - Search Service

#### Source Document Check

No conflict was found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for the Search Service planning step.

`ARCHITECTURE.md` allows Elasticsearch or another search backend as optional rather than mandatory. `TECH-STACK.md`, which is the source of truth for implementation technology choices, selects OpenSearch as the Search Service's dedicated search persistence/index layer. This is treated as the implementation technology choice for this phase, not as an additional product requirement.

#### Assumptions

* This step is planning only. No Search Service source code, Dockerfile, dependency manifest, Compose changes, or runtime configuration is generated in this step.
* `GET /search` is a protected application endpoint because `REQUIREMENTS.md` M-25 says all protected endpoints require JWT and only Auth register/login are public.
* `/actuator/health` and `/actuator/prometheus` remain operational endpoints for health and metrics exposure, consistent with the existing service pattern and `REQUIREMENTS.md` M-24.
* JWT validation will use the shared RSA public key mounted into the service, so Search can validate tokens locally without a per-request Auth Service call.
* OpenSearch stores a derived search index only. Catalog remains the song metadata system of record.
* The documents do not define a Search indexing API, catalog event stream, or Catalog-to-Search synchronization contract. For the minimum runnable phase, Search will populate its OpenSearch index from a read-only mounted copy of the same Kaggle catalog CSV used by Catalog Service startup ingestion.
* Optional Search features are not included in this phase: autocomplete, expanded artist/album/playlist result sections, and explicit fuzzy/faceted behavior beyond the required text search plus filters.
* Exact response schema, result sorting, pagination defaults, OpenSearch index name, analyzer details, and the exact CSV column-to-search-field mapping are not specified by the source documents and must be documented during implementation rather than presented as source requirements.

#### Chosen Stack

* Java 21.
* Spring Boot 3.x.
* Maven.
* Spring Web MVC for HTTP endpoints.
* Spring Security OAuth2 Resource Server for local JWT verification.
* OpenSearch as the dedicated search persistence/index layer.
* Spring Boot Actuator with Micrometer Prometheus for metrics.
* Docker and Docker Compose on the shared named network.

#### Planned File Tree

```text
services/search-service/
  Dockerfile
  README.md
  .env.example
  pom.xml
  src/
    main/
      java/
        com/
          benchmark/
            search/
              SearchServiceApplication.java
              config/
                JwtProperties.java
                OpenSearchConfig.java
                SearchProperties.java
                SecurityConfig.java
              controller/
                ApiError.java
                ApiExceptionHandler.java
                SearchController.java
              dto/
                SearchPageResponse.java
                SearchRequest.java
                SearchResultResponse.java
              indexing/
                CatalogCsvReader.java
                SearchDocument.java
                SearchIndexInitializer.java
              opensearch/
                OpenSearchIndexClient.java
                OpenSearchQueryBuilder.java
              security/
                PemKeyLoader.java
              service/
                SearchService.java
                SearchValidationException.java
      resources/
        application.yml
    test/
      java/
        com/
          benchmark/
            search/
              controller/
                SearchControllerIntegrationTest.java
              indexing/
                CatalogCsvReaderTest.java
                SearchIndexInitializerTest.java
              opensearch/
                OpenSearchQueryBuilderTest.java
              service/
                SearchServiceTest.java
              support/
                TestDataFiles.java
                TestKeyFiles.java
```

Small helper classes may be added inside the listed package boundaries if implementation requires them, but no unrelated service modules should be modified in the Search implementation step.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web`
* `spring-boot-starter-validation`
* `spring-boot-starter-security`
* `spring-boot-starter-oauth2-resource-server`
* `spring-boot-starter-actuator`
* `micrometer-registry-prometheus`
* OpenSearch Java client dependency compatible with the selected Spring Boot version
* CSV parsing dependency such as Apache Commons CSV for startup indexing from the catalog dataset
* Test dependencies: `spring-boot-starter-test`, `spring-security-test`, and Mockito support from Spring Boot test

No JPA, Flyway, PostgreSQL driver, Kafka producer, or Kafka consumer dependency is planned for this minimum Search phase.

#### Endpoints and Exposed Interfaces

Application endpoint, protected by JWT:

* `GET /search?q=&genre=&bpm_min=&bpm_max=&year=`
  * Performs text search over songs.
  * Applies optional genre filter.
  * Applies optional BPM lower and upper bounds.
  * Applies optional release year filter.
  * Supports combined filters in one request.

Implementation may include bounded pagination parameters such as `page` and `size` to avoid unbounded responses, but these are implementation controls rather than additional source-document requirements.

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

No autocomplete endpoint, expanded artist/album/playlist endpoint, or write/index-management API is planned for this phase.

#### Environment Variables

Planned service variables:

* `SERVER_PORT` - defaults to `8080` inside the container.
* `JWT_PUBLIC_KEY_PATH` - path to the mounted shared public key.
* `OPENSEARCH_URL` - container-network URL for OpenSearch.
* `SEARCH_INDEX_NAME` - OpenSearch index name for song search documents.
* `SEARCH_CATALOG_DATASET_PATH` - path to the mounted Kaggle catalog CSV used for index population.
* `SEARCH_INDEXING_ENABLED` - enables startup index creation and CSV population in local Compose.
* `SEARCH_DEFAULT_PAGE_SIZE` - default bounded result size if pagination is implemented.
* `SEARCH_MAX_PAGE_SIZE` - maximum allowed result size if pagination is implemented.

#### Persistence and Indexing Approach

* Search Service will use its own OpenSearch index as a dedicated persistence/index layer, satisfying `REQUIREMENTS.md` M-26 when search indexes are stored.
* The OpenSearch container already planned for the system is the only Search persistence dependency.
* Startup indexing will create or validate the song index and idempotently upsert documents from the mounted catalog CSV.
* Indexed document fields should support the required behavior: song title or track name text, artist text where available, album text where available, genre keyword, BPM/tempo numeric, and release year numeric.
* Search queries will combine text matching with structured filters for genre, BPM range, and year.
* Search Service will not share the Catalog PostgreSQL database and will not write to Catalog Service storage.
* No Kafka messaging is planned for this phase because the source documents do not define Search Service event production or consumption.

#### Validation Steps

* Confirm no source-document conflict before generation.
* Build Search Service with Docker so Maven tests run during the build path.
* Run `docker compose config --quiet`.
* Run `docker compose build search-service`.
* Run `docker compose up -d --build search-opensearch search-service`.
* Confirm `search-opensearch` and `search-service` are running on the shared named Docker network.
* Confirm startup logs show OpenSearch connectivity, index creation or validation, and catalog CSV indexing.
* Verify unauthenticated and invalid-token requests to `GET /search` return `401`.
* Verify an authenticated text search returns song results from the indexed catalog data.
* Verify authenticated genre, BPM range, year, and combined filter requests behave as required.
* Verify `/actuator/health` returns healthy status and `/actuator/prometheus` exposes metrics.
* Verify the Search README documents how search data is populated.

#### Required Unit Tests

* Query builder creates a text search query when `q` is provided.
* Query builder applies genre filtering.
* Query builder applies `bpm_min` and `bpm_max` range filtering.
* Query builder applies year filtering.
* Query builder combines text search, genre, BPM, and year filters.
* Request validation rejects invalid BPM ranges such as `bpm_min` greater than `bpm_max`.
* Request validation rejects negative BPM values and invalid pagination bounds if pagination is implemented.
* CSV reader maps the required catalog fields into search documents.
* Index initializer creates or validates the index and performs idempotent document upserts through the OpenSearch client abstraction.
* Search service maps OpenSearch hits into Search API response DTOs.

#### Required Integration Tests

* `GET /search` rejects missing JWT with `401`.
* `GET /search` rejects invalid JWT with `401`.
* `GET /search?q=...` with a valid JWT returns matching song results from test-indexed data.
* Genre filter returns only matching genre results.
* BPM range filter returns only results inside the requested range.
* Year filter returns only results from the requested year.
* Combined text, genre, BPM, and year filters work together.
* Startup indexing behavior is covered against a test OpenSearch client abstraction or controlled test index path.
* `/actuator/health` is reachable as an operational endpoint.

#### Missing Details Not Defined by the Source Documents

* Exact Search response JSON schema.
* Exact CSV column names to use for title, artist, album, genre, BPM, and year after the Kaggle dataset replacement.
* Exact OpenSearch index name and analyzer configuration.
* Exact default result ordering or relevance tie-breaker.
* Whether pagination is required by the Search endpoint.
* Whether Search should synchronize from Catalog via API, events, or direct dataset import after startup. The minimum plan uses documented startup CSV indexing because no synchronization contract is specified.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Search Service with Java 21, Spring Boot 3.x, and Maven. | All backend services must use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/search-service/pom.xml`, Java source tree, Dockerfile. |
| Use OpenSearch as the dedicated Search persistence/index layer. | The Search Service needs full-text matching and structured filters, and the stack document selects OpenSearch for this service. | `TECH-STACK.md` Search backend; `ARCHITECTURE.md` Search persistence and indexing. | `search-opensearch`, `services/search-service`, `docker-compose.yml`. |
| Protect `GET /search` with local JWT validation using the shared public key. | All protected endpoints require JWT and only Auth register/login may be public. | `REQUIREMENTS.md` M-25. | `SecurityConfig`, `PemKeyLoader`, `.env.example`, integration tests, Compose key mount. |
| Expose only the required `GET /search` application endpoint in this phase. | The source documents require song search and filters; autocomplete and expanded result types are optional. | `ARCHITECTURE.md` Search Service; `REQUIREMENTS.md` M-12, C-05, C-06. | `SearchController`, DTOs, tests. |
| Exclude autocomplete, expanded artist/album/playlist search, and explicit fuzzy/faceted endpoints from this phase. | These are could-have or optional features and are not required for the minimum Search phase. | `REQUIREMENTS.md` C-04, C-05, C-06; `ARCHITECTURE.md` optional Search behavior. | No optional Search endpoints or UI-specific APIs. |
| Populate the OpenSearch index from a read-only mounted catalog CSV at startup. | The documents require use of the Kaggle dataset but do not define a Search synchronization API or event contract. CSV startup indexing keeps the phase runnable without sharing the Catalog database. | `ARCHITECTURE.md` Catalog dataset requirements; `REQUIREMENTS.md` M-12 and M-26; missing synchronization detail recorded above. | `SearchIndexInitializer`, `CatalogCsvReader`, `application.yml`, `.env.example`, `docker-compose.yml`, README. |
| Treat the OpenSearch index as derived data, not the song metadata system of record. | Catalog owns song metadata persistence, while Search owns search-specific indexes. | `ARCHITECTURE.md` Catalog and Search service boundaries; `REQUIREMENTS.md` M-26. | Search index, Search README, no Catalog DB access. |
| Do not add Kafka messaging to Search in this phase. | The source documents do not define Search event production or consumption. | `ARCHITECTURE.md` Search Service; `TECH-STACK.md` Messaging Infrastructure per Service. | `pom.xml`, `application.yml`, no Search Kafka config. |
| Use Actuator and Micrometer Prometheus for Search metrics. | Each service must expose Prometheus-suitable metrics. | `REQUIREMENTS.md` M-24; `TECH-STACK.md` Observability. | `pom.xml`, `application.yml`, `/actuator/prometheus`, Prometheus scrape config if needed. |
| Document unresolved details rather than filling them in as requirements. | The user instructed that missing details must be listed instead of invented. | User instruction for this planning step. | `DESIGN-DECISIONS.md`, future Search implementation README. |

### Phase 5 Step 2 Generation - Search Service

#### Completed Artifacts

* Replaced the Search placeholder with a runnable Java 21 Spring Boot service.
* Added Search Service `pom.xml`, Dockerfile, `.env.example`, `application.yml`, and README.
* Added local JWT validation through the shared public key configuration.
* Added protected `GET /search` with text search, genre filtering, BPM range filtering, year filtering, and combined filter support.
* Added OpenSearch index creation and search execution through the OpenSearch REST client.
* Added startup indexing from the mounted Kaggle catalog CSV into a Search-owned OpenSearch index.
* Added bounded pagination controls as implementation safety settings.
* Added Actuator health and Prometheus metrics exposure.
* Updated `docker-compose.yml` so Search mounts the read-only catalog CSV and waits for a healthy OpenSearch service.
* Added unit tests for query construction, request validation, CSV mapping, startup indexing, and service response mapping.
* Added integration tests for JWT protection, required search endpoint behavior, combined filters, invalid request handling, and health.

#### Generation Validation Run

* `docker compose build search-service` initially failed because the OpenSearch REST client uses Apache HttpComponents 4 imports, while the first implementation used HttpComponents 5 imports. The imports and request entity type were corrected.
* `docker compose build search-service` then passed and executed the Maven package/test path.
* `docker compose config --quiet` passed. Docker emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose up -d --build search-opensearch search-service` initially failed because Docker had stale container metadata pointing at a deleted network. The stale Search containers were removed with `docker compose rm -f search-opensearch search-service`, then recreated successfully.
* The first runtime startup exposed that indexing the whole Kaggle CSV in a single bulk request could exhaust the Search container heap. Startup indexing was corrected to stream the CSV and send bounded OpenSearch bulk batches.
* `docker compose up -d --build search-opensearch search-service` passed after batching was added.
* `docker compose ps search-opensearch search-service` confirmed OpenSearch is healthy and Search Service is running.
* Search logs confirmed `85000` catalog documents were indexed into the `songs` OpenSearch index.
* Live smoke validation confirmed `/actuator/health` returns `UP`.
* Live smoke validation with an RS256 JWT signed by the local development private key confirmed `GET /search?q=love&size=2` returns indexed Search results.

#### Generation Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Implement Search Service as a Spring Boot Maven service using the same service pattern as Auth, Catalog, Playlist, and Streaming. | The backend stack must remain consistent across services. | `TECH-STACK.md` Shared Backend Standard. | `services/search-service/pom.xml`, Java source tree, Dockerfile. |
| Use the OpenSearch REST client for index and query operations. | OpenSearch is the selected Search persistence/index layer; the REST client keeps the implementation direct and compatible with the OpenSearch container. | `TECH-STACK.md` Search backend. | `OpenSearchConfig`, `OpenSearchIndexClient`, `pom.xml`. |
| Store only a Search-owned derived index in OpenSearch. | Catalog owns metadata persistence; Search owns search-specific indexed data and must not share the Catalog database. | `ARCHITECTURE.md` Catalog and Search boundaries; `REQUIREMENTS.md` M-26. | `SearchIndexInitializer`, `docker-compose.yml`, Search README. |
| Populate the Search index from the mounted Catalog Kaggle CSV at startup. | The source documents require the dataset but do not define Catalog-to-Search synchronization. Startup CSV indexing keeps the phase runnable without inventing an API or event flow. | Phase 5 Step 1 missing details; `ARCHITECTURE.md` Catalog dataset requirement; `REQUIREMENTS.md` M-12. | `CatalogCsvReader`, `SearchIndexInitializer`, `docker-compose.yml`, `.env.example`, README. |
| Stream CSV indexing in bounded batches with configurable `SEARCH_INDEX_BATCH_SIZE`. | Runtime validation showed a single large bulk request could exhaust the small benchmark container heap with the Kaggle CSV. Batching preserves the startup indexing approach while keeping resource use bounded. | `REQUIREMENTS.md` M-20 benchmark resource configurability; runtime validation finding. | `SearchIndexInitializer`, `SearchProperties`, `application.yml`, `.env.example`, `docker-compose.yml`. |
| Add an OpenSearch healthcheck and make Search depend on `service_healthy`. | Runtime validation showed Search can start before OpenSearch accepts connections. Waiting for health reduces restart churn during local Compose startup. | `PROGRESS.md` Validation Expectations; `TECH-STACK.md` Docker Compose runtime. | `docker-compose.yml`, `search-opensearch`, `search-service`. |
| Protect `GET /search` and permit only Actuator health/prometheus operational endpoints without JWT. | All protected endpoints require JWT; only Auth register/login are public. | `REQUIREMENTS.md` M-25; `REQUIREMENTS.md` M-24. | `SecurityConfig`, `SearchControllerIntegrationTest`. |
| Keep optional autocomplete, expanded artist/album/playlist search, and explicit fuzzy/faceted APIs out of this generation step. | These features are optional/could-have and were not part of the approved minimum Search plan. | `ARCHITECTURE.md` optional Search behavior; `REQUIREMENTS.md` C-04, C-05, C-06. | No optional endpoint files added. |
| Use bounded `page` and `size` parameters as implementation controls. | The required endpoint does not specify pagination, but bounded response size prevents unbounded search responses and is configurable. | Phase 5 Step 1 assumption and missing detail. | `SearchRequest`, `SearchPageResponse`, `SearchProperties`, tests. |
| Remove stale Search containers during validation after Docker reported a deleted network reference. | Compose could not recreate the Search containers until stale metadata was removed; no volumes or unrelated services were removed. | Runtime validation finding. | Docker runtime state for `search-opensearch` and `search-service`; no repository files. |

#### Assumptions Made During Generation

* The mounted Catalog CSV remains the minimum Search indexing source until a later phase defines a synchronization contract.
* The OpenSearch `songs` index is derived data and may be recreated from the catalog CSV.
* `title`, `artist`, `album`, `genre`, `bpm`, and `year` are sufficient indexed fields for the required song search and filters.
* Query result ordering follows OpenSearch relevance by default because the source documents do not specify sorting.
* The health and Prometheus Actuator endpoints are operational endpoints and remain public.

### Phase 5 Step 3 Review - Search Service

#### Review Results

* Reviewed Search Service against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, and the approved Phase 5 plan.
* Confirmed no conflicts were found between the source documents for this Search phase.
* Confirmed the required protected endpoint `GET /search?q=&genre=&bpm_min=&bpm_max=&year=` exists.
* Confirmed Search supports text search over songs and filters by genre, BPM range, year, and combined filter requests.
* Confirmed Search uses OpenSearch as its own dedicated search persistence/index layer and does not share the Catalog PostgreSQL database.
* Confirmed Search populates its derived OpenSearch index from the read-only mounted Kaggle catalog CSV, and the method is documented in the service README.
* Confirmed no optional autocomplete endpoint, expanded artist/album/playlist endpoint, email/push behavior, object storage, or Kafka messaging was added to Search.
* Confirmed the implementation follows Java 21, Spring Boot 3.x, Maven, Docker, Docker Compose, Actuator, Micrometer Prometheus, and OpenSearch choices from `TECH-STACK.md`.

#### Fixes Made During Review

No repository file fixes were required during this review step. The generated Search implementation, configuration, containerization, tests, and documentation matched the approved plan.

#### Validation Commands Run

* `docker compose build search-service` passed.
* `docker compose config --quiet` passed. Docker emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose build --no-cache search-service` passed and executed the Maven package/test path.
* `docker compose up -d --build search-opensearch search-service` passed.
* `docker compose ps search-opensearch search-service` confirmed OpenSearch is healthy and Search Service is running.
* Search Service logs confirmed startup indexing completed with `85000` catalog documents indexed into the `songs` OpenSearch index.
* Live smoke validation confirmed:
  * unauthenticated `GET /search?q=love` returns `401`;
  * malformed Bearer token access returns `401`;
  * `/actuator/health` returns `UP`;
  * authenticated `GET /search?q=love&size=2` returns indexed results;
  * authenticated BPM range and year filter requests return indexed results;
  * authenticated `GET /search?genre=Pop&size=2` returns `Pop` results;
  * authenticated combined `GET /search?genre=Pop&bpm_min=0&bpm_max=300&year=2020&size=2` returns `Pop` results from year `2020`.

#### Review Decisions and Corrections Recorded

| Decision, correction, or finding | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Mark Search validation complete after no-cache build tests, Compose config validation, container startup, OpenSearch indexing, and live JWT/filter smoke checks passed. | The implementation, tests, containerization, authentication, persistence/indexing, and required search filters met the Search acceptance criteria. | `REQUIREMENTS.md` M-12, M-24, M-25, M-26; backend testing requirements. | `PROGRESS.md`, `DESIGN-DECISIONS.md`, `services/search-service`. |
| Keep genre filtering as exact keyword matching using the indexed genre value. | `GET /search?genre=pop` returned no live results because the Kaggle CSV stores the sampled value as `Pop`; exact keyword matching is appropriate for a structured genre filter and the API works when the indexed value is used. | `REQUIREMENTS.md` M-12; `TECH-STACK.md` Search backend. | `OpenSearchQueryBuilder`, live validation notes. |
| Keep the Search index population method unchanged. | The read-only CSV startup import completed successfully and preserved the service-owned OpenSearch index boundary. | Phase 5 Step 1 and Step 2 decisions; `REQUIREMENTS.md` M-26. | `SearchIndexInitializer`, `CatalogCsvReader`, `docker-compose.yml`, Search README. |

#### Assumptions and Unresolved Issues After Review

* Assumption: Search continues to use the mounted Kaggle catalog CSV as the minimum indexing source until a later phase defines a Catalog-to-Search synchronization contract.
* Assumption: Genre filters use the dataset's exact genre spelling/casing.
* Assumption: OpenSearch relevance ordering is acceptable because the source documents do not define sorting.
* No unresolved Search implementation or test issues remain for this phase.

### Phase 6 Step 1 Plan - Analytics Service

#### Source Document Check

No conflict was found between `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` for the Analytics Service planning step.

`ARCHITECTURE.md` requires Analytics to store listening history, aggregate playback data into chart-oriented analytics, compute at least global play-count-based rankings, expose metrics, and use dedicated persistence. `REQUIREMENTS.md` makes `GET /analytics/me/history`, persistent listen history, playback-event aggregation, Prometheus metrics, JWT protection, and dedicated persistence mandatory. `TECH-STACK.md` selects Java 21, Spring Boot 3.x, Maven, Kafka consumer messaging, and ClickHouse as the dedicated analytics store.

#### Assumptions

* This step is planning only. No Analytics Service source code, Dockerfile, dependency manifest, Compose changes, or runtime configuration is generated in this step.
* `GET /analytics/me/history` is a protected endpoint because `REQUIREMENTS.md` M-25 says all protected endpoints require JWT and only Auth register/login are public.
* `/actuator/health` and `/actuator/prometheus` remain operational endpoints for health and metrics exposure, consistent with the existing service pattern and `REQUIREMENTS.md` M-24.
* JWT validation will use the shared RSA public key mounted into the service, so Analytics can validate tokens locally without a per-request Auth Service call.
* Analytics consumes the existing Streaming Service `PlaybackEvent` JSON shape: `eventId`, `type`, `userId`, `songId`, and `timestamp`.
* Analytics consumes from the existing `playback-events` Kafka topic unless overridden by configuration.
* Listen history is derived from consumed playback events and tied to the authenticated JWT subject for retrieval.
* Global play-count rankings are computed from stored playback events. Only `play.started` events count toward global play counts unless later requirements define another counting rule.
* The global chart endpoint `GET /analytics/charts/global` is included as a documented should-have endpoint from `REQUIREMENTS.md` S-02 and `ARCHITECTURE.md` optional Analytics behavior; it is not treated as an invented must-have.
* Personal charts and listening statistics beyond history are not included because `REQUIREMENTS.md` C-03 marks them could-have.
* No synchronous service-to-service HTTP call is planned for this minimum Analytics phase, so WebClient, retry, and circuit breaker are not required inside Analytics yet.

#### Missing Details Not Defined by the Source Documents

* Exact response JSON schema for listening history.
* Exact paging defaults and maximum page size for history.
* Whether listen history should include all playback event types or only started/ended/skipped subsets. The plan stores all consumed playback event types and exposes them chronologically.
* Exact global ranking counting rule. The plan counts `play.started` events as plays.
* Exact Kafka consumer group id.
* Exact ClickHouse table engine and partitioning strategy.
* Whether duplicate Kafka events should be ignored by `eventId` or allowed. The plan stores `eventId` and deduplicates at the ClickHouse table/query layer where practical.
* Whether `GET /analytics/charts/global` should be part of the minimum acceptance criteria or only a should-have endpoint. The plan includes it because it is explicitly documented as should-have.

#### Chosen Stack

* Java 21.
* Spring Boot 3.x.
* Maven.
* Spring Web MVC for HTTP endpoints.
* Spring Security OAuth2 Resource Server for local JWT verification.
* Spring Kafka as a Kafka consumer for playback events.
* ClickHouse as the dedicated analytics persistence layer.
* JDBC access to ClickHouse for schema creation, inserts, history queries, and aggregation queries.
* Spring Boot Actuator with Micrometer Prometheus for metrics.
* Docker and Docker Compose on the shared named network.

#### Planned File Tree

```text
services/analytics-service/
  Dockerfile
  README.md
  .env.example
  pom.xml
  src/
    main/
      java/
        com/
          benchmark/
            analytics/
              AnalyticsServiceApplication.java
              config/
                AnalyticsProperties.java
                ClickHouseConfig.java
                JwtProperties.java
                KafkaConsumerConfig.java
                KeyConfig.java
                SecurityConfig.java
              controller/
                AnalyticsController.java
                ApiError.java
                ApiExceptionHandler.java
              dto/
                GlobalChartItemResponse.java
                HistoryEventResponse.java
                HistoryPageResponse.java
              messaging/
                PlaybackEvent.java
                PlaybackEventConsumer.java
              persistence/
                AnalyticsEventRecord.java
                AnalyticsSchemaInitializer.java
                AnalyticsEventRepository.java
              security/
                AuthenticatedUser.java
                PemKeyLoader.java
                UserPrincipalResolver.java
              service/
                AnalyticsService.java
                AnalyticsValidationException.java
      resources/
        application.yml
    test/
      java/
        com/
          benchmark/
            analytics/
              controller/
                AnalyticsControllerIntegrationTest.java
              messaging/
                PlaybackEventConsumerTest.java
              persistence/
                AnalyticsSchemaInitializerTest.java
                AnalyticsEventRepositoryTest.java
              service/
                AnalyticsServiceTest.java
              support/
                TestKeyFiles.java
```

Small helper classes may be added inside the listed package boundaries if implementation requires them, but no unrelated service modules should be modified in the Analytics implementation step.

#### Dependencies

Planned Maven dependencies:

* `spring-boot-starter-web`
* `spring-boot-starter-validation`
* `spring-boot-starter-security`
* `spring-boot-starter-oauth2-resource-server`
* `spring-boot-starter-actuator`
* `spring-kafka`
* ClickHouse JDBC driver
* `spring-boot-starter-jdbc`
* `micrometer-registry-prometheus`
* Test dependencies: `spring-boot-starter-test`, `spring-security-test`, `spring-kafka-test`, and Mockito support from Spring Boot test

No JPA, Flyway, PostgreSQL driver, Redis, MongoDB, OpenSearch, object-storage, or WebClient/Resilience4j dependency is planned for this minimum Analytics phase.

#### Endpoints and Exposed Interfaces

Application endpoints, protected by JWT:

* `GET /analytics/me/history`
  * Returns chronological playback history for the authenticated JWT subject.
  * Reads from Analytics-owned ClickHouse storage.
  * Supports bounded pagination parameters such as `page` and `size` as implementation controls.

* `GET /analytics/charts/global`
  * Returns global play-count-based rankings computed from stored playback events.
  * Included because `REQUIREMENTS.md` S-02 defines this should-have endpoint and `ARCHITECTURE.md` lists it as optional Analytics behavior.
  * Supports a bounded `limit` parameter with a default of `50`.

Messaging interface:

* Kafka consumer on the configured playback events topic, default `playback-events`.
* Consumes Streaming Service playback events with fields `eventId`, `type`, `userId`, `songId`, and `timestamp`.

Operational endpoints:

* `/actuator/health`
* `/actuator/prometheus`

No personal charts endpoint, listening-statistics endpoint, frontend metrics ingestion endpoint, or synchronous downstream service call is planned for this phase.

#### Environment Variables

Planned service variables:

* `SERVER_PORT` - defaults to `8080` inside the container.
* `JWT_PUBLIC_KEY_PATH` - path to the mounted shared public key.
* `CLICKHOUSE_URL` - JDBC URL for Analytics ClickHouse.
* `CLICKHOUSE_USER` - ClickHouse username.
* `CLICKHOUSE_PASSWORD` - ClickHouse password.
* `KAFKA_BOOTSTRAP_SERVERS` - Kafka bootstrap servers, default `kafka:9092`.
* `ANALYTICS_PLAYBACK_EVENTS_TOPIC` - Kafka topic for playback events, default `playback-events`.
* `ANALYTICS_KAFKA_CONSUMER_GROUP_ID` - Analytics consumer group id.
* `ANALYTICS_DEFAULT_PAGE_SIZE` - default history page size.
* `ANALYTICS_MAX_PAGE_SIZE` - maximum history page size.
* `ANALYTICS_GLOBAL_CHART_LIMIT` - default global chart limit, default `50`.

#### Persistence and Messaging Approach

* Analytics Service will use its own ClickHouse database/table as the dedicated analytics persistence layer.
* ClickHouse stores one row per consumed playback event, including `eventId`, event type, user id, song id, and timestamp.
* Kafka consumption is asynchronous and decoupled from Streaming request latency.
* The consumer persists `play.started`, `play.ended`, and `play.skipped` events because those are the event types emitted by Streaming.
* `GET /analytics/me/history` queries ClickHouse by authenticated user id and returns events in reverse chronological order.
* Global play-count rankings are computed by aggregating stored `play.started` events by `songId`.
* Schema initialization can be handled by application startup with idempotent `CREATE TABLE IF NOT EXISTS` statements because no separate migration framework is specified for ClickHouse in the source documents.
* No Analytics data is stored in Kafka, PostgreSQL, Catalog DB, or another service's database.

#### Validation Steps

* Confirm no source-document conflict before generation.
* Build Analytics Service with Docker so Maven tests run during the build path.
* Run `docker compose config --quiet`.
* Run `docker compose build analytics-service`.
* Run `docker compose up -d --build kafka analytics-db analytics-service`.
* Confirm `kafka`, `analytics-db`, and `analytics-service` are running on the shared named Docker network.
* Confirm startup logs show JWT public key loading, Kafka consumer startup, ClickHouse schema initialization, Actuator endpoint exposure, and Tomcat startup.
* Use a valid RS256 JWT to call protected Analytics endpoints.
* Verify unauthenticated and invalid-token requests to `GET /analytics/me/history` return `401`.
* Produce or trigger playback events on the configured Kafka topic and verify Analytics consumes and persists them.
* Verify `GET /analytics/me/history` returns only events for the authenticated user.
* Verify persisted listen history remains available after Analytics Service restart when the ClickHouse volume persists.
* Verify global play-count rankings are computable through the repository/service and, if implemented, `GET /analytics/charts/global`.
* Verify `/actuator/health` returns healthy status and `/actuator/prometheus` exposes metrics.
* Verify the Analytics README documents Kafka topic, ClickHouse persistence, history retrieval, and validation commands.

#### Required Unit Tests

* Playback event mapping from Kafka payload to persistence record includes event id, type, user id, song id, and timestamp.
* Kafka consumer passes valid playback events to the repository/service for persistence.
* Kafka consumer ignores or rejects malformed events according to the implemented error-handling strategy.
* History request validation rejects invalid pagination values.
* Global chart limit validation rejects invalid limits.
* Analytics service returns only authenticated user's history records.
* Analytics service maps persisted records to history response DTOs in chronological order.
* Analytics service computes global play-count rankings from `play.started` records.
* Schema initializer emits idempotent ClickHouse table creation SQL.

#### Required Integration Tests

* `GET /analytics/me/history` rejects missing JWT with `401`.
* `GET /analytics/me/history` rejects invalid JWT with `401`.
* `GET /analytics/me/history` with a valid JWT returns only that user's persisted playback events.
* Integration persistence test verifies playback event rows can be inserted and queried from the Analytics repository.
* Kafka consumer integration test verifies a consumed playback event is persisted.
* Global chart integration test verifies play-count rankings are computed from persisted playback events.
* If `GET /analytics/charts/global` is implemented, it is protected by JWT and returns ranked song play counts.
* `/actuator/health` is reachable as an operational endpoint.

#### Planning Decisions Recorded

| Decision | Why | Justification | Expected affected files/services |
| --- | --- | --- | --- |
| Implement Analytics Service with Java 21, Spring Boot 3.x, and Maven. | All backend services must use the shared backend standard. | `TECH-STACK.md` Shared Backend Standard. | `services/analytics-service/pom.xml`, Java source tree, Dockerfile. |
| Use ClickHouse as the dedicated Analytics persistence layer. | Analytics stores event-heavy listen history and aggregation data, and ClickHouse is selected for this service. | `TECH-STACK.md` Analytics database choice; `REQUIREMENTS.md` M-13, M-14, M-26. | `analytics-db`, `AnalyticsEventRepository`, `AnalyticsSchemaInitializer`, `docker-compose.yml`. |
| Consume Streaming playback events from Kafka. | Analytics must aggregate playback events and `TECH-STACK.md` specifies Analytics as a Kafka consumer. | `REQUIREMENTS.md` M-14; `TECH-STACK.md` Analytics messaging choice. | `PlaybackEventConsumer`, Kafka config, tests, `docker-compose.yml`. |
| Use the existing `playback-events` topic and Streaming `PlaybackEvent` shape. | Streaming already emits required playback events on this topic; Analytics should consume the existing contract rather than invent a new one. | Existing Streaming implementation; `REQUIREMENTS.md` M-06 and M-14. | `PlaybackEvent`, `PlaybackEventConsumer`, `.env.example`, tests. |
| Protect Analytics application endpoints with local JWT validation using the shared public key. | All protected endpoints require JWT and only Auth register/login may be public. | `REQUIREMENTS.md` M-25. | `SecurityConfig`, `PemKeyLoader`, integration tests, Compose key mount. |
| Use JWT subject as the user id for `/analytics/me/history`. | Listen history must be tied to authenticated users, and the JWT subject is the established user identity across existing services. | `REQUIREMENTS.md` M-13 and M-25; prior service pattern. | `UserPrincipalResolver`, `AnalyticsController`, `AnalyticsService`, tests. |
| Include `GET /analytics/charts/global` as a should-have endpoint in the plan. | Global rankings are mandatory to compute, and the source documents explicitly define this endpoint as should-have/optional. | `REQUIREMENTS.md` M-14 and S-02; `ARCHITECTURE.md` optional Analytics behavior. | `AnalyticsController`, `AnalyticsService`, repository aggregation query, tests. |
| Count `play.started` events for global play-count rankings. | The documents require play-count rankings but do not define the exact event type to count; started events are the clearest signal of a play attempt from Streaming. | `REQUIREMENTS.md` M-14; missing detail recorded above. | Aggregation query, service tests, README. |
| Exclude personal charts and listening-statistics endpoints from this phase. | These are could-have extensions and not required for the minimum Analytics service. | `REQUIREMENTS.md` C-03. | No personal chart/statistics endpoints. |
| Initialize ClickHouse schema at application startup with idempotent SQL. | The source documents require dedicated persistence but do not require a specific ClickHouse migration tool. | `REQUIREMENTS.md` M-13 and M-26; `TECH-STACK.md` ClickHouse choice. | `AnalyticsSchemaInitializer`, tests, README. |
| Do not add synchronous WebClient/Resilience4j dependencies in this phase. | Analytics does not need a synchronous downstream service call for the planned minimum behavior. | `TECH-STACK.md` Cross-Cutting API rule applies to synchronous service-to-service HTTP traffic; no such traffic is planned. | `pom.xml`, no HTTP client config. |

### Phase 6 Step 2 Generation - Analytics Service

#### Completed Artifacts

* Replaced the Phase 0 Analytics placeholder with a runnable Java 21 Spring Boot service.
* Added Analytics Service `pom.xml`, Dockerfile, `.env.example`, `application.yml`, and README.
* Added local JWT validation through the shared public key configuration.
* Added protected `GET /analytics/me/history` for authenticated user playback history.
* Added protected `GET /analytics/charts/global` for global play-count rankings.
* Added Kafka playback event consumption for the existing Streaming `PlaybackEvent` shape.
* Added ClickHouse JDBC persistence with idempotent startup schema creation.
* Added Actuator health and Prometheus metrics exposure.
* Updated `docker-compose.yml` with Analytics env vars and a ClickHouse readiness check before Analytics startup.
* Added root `.env.example` Analytics configuration defaults.
* Added unit tests for service validation/mapping, global ranking mapping, Kafka consumer persistence behavior, schema SQL, and repository SQL.
* Added integration tests for protected endpoint access, history response behavior, global chart behavior, validation errors, and health.

#### Generation Validation Run

* Initial `docker compose build analytics-service` failed because `clickhouse-jdbc` needed its matching `clickhouse-client` dependency on the runtime/test classpath. The dependency was added to `pom.xml`.
* The next build failed only in the controller health test because the test environment intentionally did not provide a live ClickHouse instance while Spring Boot's DB health indicator was enabled. The MockMvc test was corrected to disable `management.health.db.enabled` only for that test context.
* `docker compose build analytics-service` then passed and executed the Maven package/test path.
* `docker compose config --quiet` passed. Docker emitted the known sandbox warning about `C:\Users\thele\.docker\config.json` access, but the command exited successfully.
* `docker compose up -d --build kafka analytics-db analytics-service` initially timed out because the ClickHouse healthcheck used unauthenticated `/ping` against a non-default ClickHouse user.
* The ClickHouse healthcheck was corrected to use an authenticated `SELECT 1` HTTP query against `127.0.0.1`.
* Runtime startup then exposed that ClickHouse JDBC expected Apache HttpClient 5 classes at runtime. `httpclient5` was added explicitly to `pom.xml`.
* `docker compose build analytics-service` passed again after the dependency fix.
* `docker compose up -d --build kafka analytics-db analytics-service` passed.
* `docker compose ps kafka analytics-db analytics-service` confirmed Kafka is running, ClickHouse is healthy, and Analytics Service is running.
* Analytics logs confirmed the Kafka consumer subscribed to the `playback-events` topic and the application started successfully.
* Live smoke validation confirmed unauthenticated and malformed Bearer token requests to `GET /analytics/me/history` return `401`.
* Live smoke validation inserted two `play.started` rows into ClickHouse through `clickhouse-client` and confirmed:
  * `/actuator/health` returns `UP`;
  * authenticated `GET /analytics/me/history?size=5` returns the persisted events when the ClickHouse `user_id` matches the Auth-issued UUID stored in the JWT `sub`;
  * authenticated `GET /analytics/charts/global?limit=5` returns `song-smoke-1` with a play count of `2`.

#### Generation Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Implement Analytics Service as a Spring Boot Maven service using the same service pattern as prior backend services. | The backend stack must remain consistent across services. | `TECH-STACK.md` Shared Backend Standard; user instruction for complete runnable implementation. | `services/analytics-service/pom.xml`, Java source tree, Dockerfile. |
| Use ClickHouse JDBC with Spring `JdbcTemplate` instead of JPA. | Analytics uses ClickHouse as a specialized columnar store, and JPA is not a good fit for this persistence model. | `TECH-STACK.md` Analytics database choice. | `ClickHouseConfig`, `AnalyticsEventRepository`, `pom.xml`. |
| Add `clickhouse-client` and `httpclient5` dependencies explicitly. | Runtime and test validation showed the ClickHouse JDBC driver requires these classes for stable operation in the executable jar. | Runtime validation finding; `TECH-STACK.md` ClickHouse choice. | `services/analytics-service/pom.xml`. |
| Initialize the `playback_events` ClickHouse table at application startup. | The source documents require dedicated persistence but do not mandate a migration tool for ClickHouse. | `REQUIREMENTS.md` M-13 and M-26. | `AnalyticsSchemaInitializer`, tests. |
| Store all supported playback event types but count only `play.started` for global play counts. | History should preserve started, ended, and skipped events, while play-count rankings need one consistent play signal. | `REQUIREMENTS.md` M-13 and M-14; Phase 6 Step 1 assumption. | `AnalyticsEventRepository`, `AnalyticsService`, tests, README. |
| Implement `GET /analytics/charts/global` during generation. | The requirement table defines this endpoint as should-have, and global rankings must be computable. | `REQUIREMENTS.md` M-14 and S-02; `ARCHITECTURE.md` optional Analytics behavior. | `AnalyticsController`, `AnalyticsService`, repository query, tests. |
| Protect both Analytics application endpoints with JWT and leave Actuator health/prometheus public. | All protected endpoints require JWT, and metrics/health are operational endpoints used by the monitoring stack. | `REQUIREMENTS.md` M-24 and M-25. | `SecurityConfig`, controller integration tests. |
| Use JWT subject as the `/analytics/me/history` user id. | Listen history must be tied to authenticated users, and this matches the existing service identity pattern. | `REQUIREMENTS.md` M-13 and M-25; existing Auth/Playlist/Streaming pattern. | `UserPrincipalResolver`, `AnalyticsController`, tests. |
| Add an authenticated ClickHouse SQL healthcheck in Compose. | Analytics should not start until ClickHouse can accept authenticated queries; unauthenticated `/ping` did not work with the configured non-default user. | `PROGRESS.md` validation expectations; runtime validation finding. | `docker-compose.yml`, `analytics-db`, `analytics-service`. |
| Disable DB health only in the MockMvc controller test context. | Controller integration tests mock persistence and should not require a live ClickHouse service, while production health should still reflect database availability. | Backend testing requirements; runtime behavior unchanged. | `AnalyticsControllerIntegrationTest`. |

#### Assumptions Made During Generation

* Analytics consumes the existing `playback-events` topic and Streaming playback event shape.
* ClickHouse `playback_events` rows are the Analytics source of truth for listen history and global rankings.
* `play.started` is the event type counted for global play-count rankings.
* The should-have global chart endpoint is included because it is explicitly documented, but personal charts/statistics remain out of scope.
* The controller integration tests use mocked persistence; live ClickHouse behavior was validated through Docker Compose smoke checks.

### Phase 6 Analytics Identity Fix

#### Fix Recorded

* Corrected Analytics identity handling so playback events and `/analytics/me/history` use the same canonical authenticated identity end-to-end.
* The canonical identity is the Auth-issued UUID stored in JWT `sub`; Analytics now rejects non-UUID JWT subjects for history requests and ignores playback events whose `userId` is not a UUID.
* Updated tests to use UUID-form user ids instead of placeholder names such as `analytics-smoke-user` or `user-1`.
* Verified history should include stored `play.started`, `play.ended`, and `play.skipped` events, while global charts continue counting only `play.started`.

#### Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Treat the Auth-issued UUID in JWT `sub` as the canonical Analytics user identity. | Streaming already emits playback event `userId` from JWT `sub`, and history reads by JWT `sub`; hard-coded smoke users caused stored rows and authenticated queries to diverge. | `REQUIREMENTS.md` M-13 and M-25; prior Streaming decision to emit JWT subject as playback event user id. | `UserPrincipalResolver`, `PlaybackEventConsumer`, Analytics tests, README. |
| Reject non-UUID JWT subjects and ignore non-UUID playback event user ids. | Auth tokens use UUID subjects, so accepting placeholder identities can create history rows that no real authenticated user can retrieve. | Auth Service JWT behavior; `REQUIREMENTS.md` M-13 and M-25. | `UserPrincipalResolver`, `PlaybackEventConsumer`, tests. |
| Keep listen history inclusive of stored `play.started`, `play.ended`, and `play.skipped` events. | The documents require persistent listen history but do not say history starts only after `play.ended`; the approved plan stored all supported playback events and reserved `play.started` counting for global charts. | `ARCHITECTURE.md` Analytics behavior; `REQUIREMENTS.md` M-13 and M-14; Phase 6 Step 1 plan. | `AnalyticsServiceTest`, README, no repository filter change. |
| Preserve the existing single-broker Kafka Compose configuration. | The requested fix was limited to Analytics identity/history behavior and explicitly said Kafka ingestion and the Docker Compose broker fix already work. | User instruction for current fix. | No Kafka Compose settings changed. |

#### Validation Recorded

* Ran Analytics unit and integration tests through the Analytics Maven test suite.
* Rebuilt the Analytics container with Docker Compose.
* Started `kafka`, `analytics-db`, and `analytics-service` with Docker Compose.
* Confirmed live smoke rows using the same UUID as the JWT `sub` are returned by `/analytics/me/history`; the smoke check returned two matching events with `play.ended` and `play.started`.
* Confirmed live global charts still count `play.started`; the smoke song appeared in the chart response with a positive play count.

### Phase 6 Analytics Kafka Deserialization Robustness Fix

#### Fix Recorded

* Updated the Analytics Kafka consumer so one malformed or incompatible record in `playback-events` cannot permanently block consumption of later valid records.
* Replaced direct Kafka key/value deserializers with Spring Kafka `ErrorHandlingDeserializer` delegates:
  * key delegate: `StringDeserializer`;
  * value delegate: `JsonDeserializer`.
* Configured Analytics to ignore producer type headers and deserialize valid JSON into `com.benchmark.analytics.messaging.PlaybackEvent`.
* Kept trusted packages scoped to `com.benchmark.analytics.messaging` rather than broadening trust to the Streaming service package.
* Added a Kafka listener `DefaultErrorHandler` that treats deserialization failures as non-retryable and skips those records, while preserving short retries for other listener failures.
* Added an embedded-Kafka regression test that publishes malformed bytes followed by valid playback-event JSON carrying the old Streaming type header, then verifies the valid record is consumed.

#### Decisions Recorded

| Decision | Why | Justification | Affected files/services |
| --- | --- | --- | --- |
| Use `ErrorHandlingDeserializer` for Analytics Kafka key and value deserialization. | Deserialization failures occur before the listener method runs; without the error-handling wrapper, one bad record can pin the consumer offset. | Live smoke failure logs; `TECH-STACK.md` Analytics Kafka consumer choice. | `application.yml`, Analytics Kafka consumer runtime. |
| Ignore producer type headers and use the Analytics `PlaybackEvent` default value type. | Older records can carry `com.benchmark.streaming.messaging.PlaybackEvent` type headers even though the JSON shape is compatible; Analytics should consume the contract shape without trusting Streaming implementation classes. | Existing Streaming playback event contract; `REQUIREMENTS.md` M-14. | `application.yml`, `PlaybackEventKafkaRobustnessIntegrationTest`. |
| Keep trusted packages limited to `com.benchmark.analytics.messaging`. | The bug can be fixed without broadening deserialization trust beyond the Analytics-owned message DTO. | Security-focused deserialization configuration; user instruction not to broaden trust more than necessary. | `application.yml`. |
| Add a `DefaultErrorHandler` that skips deserialization failures but retries other listener failures briefly. | Bad Kafka records should not block later valid events, but non-deserialization listener failures should not be silently dropped immediately. | Live smoke failure behavior; reliability requirement implied by Analytics event aggregation. | `KafkaConsumerConfig`. |
| Add an embedded-Kafka regression test for bad-record recovery. | Unit tests alone cannot prove consumer offset recovery after a deserialization failure. | User request for tests proving malformed/incompatible records do not permanently stop later valid records. | `PlaybackEventKafkaRobustnessIntegrationTest`. |

#### Validation Recorded

* `docker compose build analytics-service` passed, including the embedded-Kafka regression test.
* `docker compose up -d --build kafka analytics-db analytics-service` passed.
* `GET /actuator/health` returned `UP`.
* Analytics logs showed `value.deserializer = class org.springframework.kafka.support.serializer.ErrorHandlingDeserializer`.
* Analytics logs confirmed subscription to `playback-events`.
