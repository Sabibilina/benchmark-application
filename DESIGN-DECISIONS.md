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


