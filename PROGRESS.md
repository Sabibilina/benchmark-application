# Progress Document

## Overview
This document defines the iterative generation, validation, and completion workflow for the system, ensuring that each phase, service, and supporting artifact is planned, implemented, checked against the frozen architecture, and verified through the checklist before the project is considered complete.

## Generation Protocol

`ARCHITECTURE.md` and `REQUIREMENTS.md` define the frozen system shape and validation baseline. `TECH-STACK.md` defines the required implementation technology choices.

The application must be generated iteratively and service-by-service rather than in a single pass. Each service should be completed, reviewed, and validated before moving to the next one so that architectural or integration problems can be detected early.

Generation should begin with the shared deployment environment, including the initial `docker-compose.yml` and supporting configuration, so that services can be integrated into an existing runtime environment as they are produced.

### Implementation procedure

1. Start with the shared deployment environment and base configuration.

2. Generate one service at a time, including its source code, configuration, and containerization artifacts.

3. After each service is generated, validate that it starts successfully and connects to the infrastructure it depends on.

4. Refine the generated output iteratively when errors, missing requirements, or integration issues are found.

5. Continue until all required services and deployment artifacts have been produced.

### Generation constraints

* The generated system must satisfy the frozen requirements table and the service specifications in the 'ARCHITECTURE.md' and 'REQUIREMENTS.md' documents.

* Each service must use the technology specified in the `TECH-STACK.md` document.

* All generated code must be runnable and complete; pseudocode and placeholder implementations are not acceptable.

### Validation expectations

* Each generated service should start cleanly in its container.

* Each service should connect successfully to its required dependencies.

* Protected endpoints should enforce JWT authentication according to the requirements.

* Integration behavior should be checked incrementally rather than postponed until the entire system has been generated.

* Unit tests and integration tests must be generated and executed incrementally for each service before moving to the next phase.

### Progress tracking and decision logging

* After each generation or validation/fix step, `PROGRESS.md` must be updated before moving to the next step or phase.

* The update must mark completed checklist items, note any incomplete or blocked items, and record whether the current phase is ready to continue.

* Every important implementation decision taken during planning, generation, or validation must be documented in `PROGRESS.md`.

* Each recorded decision should briefly state:
  * what was decided,
  * why it was decided,
  * which document or requirement justified it,
  * and which files or services were affected.

* If the generated output required a deviation, correction, or assumption, that change must also be recorded in `PROGRESS.md`.

* A phase must not be considered complete until both the implementation artifacts and the corresponding `PROGRESS.md` updates are finished.

## **More Detailed Checklist**

Services are built **one at a time** in the order below. Each service goes through three steps:
**Plan → Generate → Validate/Fix**. Do not move to the next service until the current one starts cleanly and passes its acceptance criteria.

## Phase 0 — Shared Deployment Environment

- [x] **Plan** — Propose repo structure, compose file layout, named Docker network, infrastructure, and env/config strategy
- [x] **Generate** — Top-level folder structure, `docker-compose.yml`, shared config files, placeholder Dockerfiles, and README
- [x] **Validate/Fix** — Confirm compose file reflects required architecture; named network defined; no invented requirements
- [x] Compose file starts without errors
- [x] Named Docker network is defined and all services reference it
- [x] Service directories are scaffolded
- [x] README covers startup instructions for this phase

---

## Phase 1 — Auth Service

### Steps
- [x] **Step 1 — Plan**: File tree, endpoints, JWT signing and verification approach, persistence approach, env vars, dependencies, validation steps
- [x] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, `init.sql`, unit tests, integration tests, README section
- [x] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [x] `POST /auth/register` is implemented
- [x] `POST /auth/login` is implemented
- [x] JWTs are signed so that other services can validate them locally
- [x] JWT verification uses a shared verification configuration
- [x] Asymmetric signing is used
- [x] The service uses its own dedicated persistence layer
- [x] Dockerfile builds and container starts without errors
- [x] `.env.example` documents the service configuration
- [x] No requirements from the brief are missing; no extra requirements added
- [x] Unit tests cover the core business logic of the service
- [x] Integration tests cover the required endpoints and persistence behavior where applicable
- [x] JWT issuance, signing, and verification behavior is tested, including valid and invalid token cases
- [ ] Test suite passes successfully — PENDING: requires Maven + Java 21 + Docker; run `mvn verify` inside `services/auth-service/` when available

---

## Phase 2 — Catalog Service

### Steps
- [ ] **Step 1 — Plan**: File tree, persistence and data model approach, dataset ingestion strategy, endpoints, pagination design, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, seed script, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /catalog/songs` is implemented with pagination
- [ ] `GET /catalog/songs/:id` returns song metadata
- [ ] The required dataset is ingested automatically at startup
- [ ] Stored song records include the metadata fields required by the application and recommendation/search flows
- [ ] The service uses its own dedicated persistence layer
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 3 — Playlist Service

### Steps
- [ ] **Step 1 — Plan**: File tree, persistence and data model approach, required endpoints, track reorder strategy, Liked Songs handling, JWT validation, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] All 8 required endpoints exist and return correct HTTP status codes
- [ ] All endpoints return `401` when no valid JWT is provided
- [ ] `POST /playlists` creates a playlist owned by the authenticated user
- [ ] `GET /playlists` returns only playlists belonging to the authenticated user
- [ ] `POST /playlists/:id/tracks` adds a track; `DELETE` removes it; `PATCH /reorder` updates track order transactionally
- [ ] "Liked Songs" playlist is automatically available per user and cannot be deleted via `DELETE /playlists/:id`
- [ ] Service uses its own dedicated persistence layer
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 4 — Streaming Service

### Steps
- [ ] **Step 1 — Plan**: File tree, endpoint design, dummy segment payload strategy, event emission strategy, JWT validation approach, persistence approach if needed, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, persistence/bootstrap logic if needed, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /stream/:songId` requires a valid JWT
- [ ] `GET /stream/:songId` returns a simulated HLS manifest or equivalent stream descriptor
- [ ] Segment payloads are configurable in size
- [ ] `play.started`, `play.ended`, and `play.skipped` events are emitted with user, song, and timestamp data
- [ ] JWT validation uses the Auth Service public key or equivalent shared verification setup
- [ ] If the service persists state, it uses its own dedicated persistence layer
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 5 — Search Service

### Steps
- [ ] **Step 1 — Plan**: File tree, endpoint design, search strategy, filter logic, persistence/indexing approach if needed, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration with indexes, data population script, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /search` supports text search
- [ ] Genre filtering works
- [ ] BPM range filtering works
- [ ] Year filtering works
- [ ] Combined filters work together
- [ ] If search indexes or cached search data are stored, the service uses its own dedicated persistence layer
- [ ] The method used to populate search data is documented
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 6 — Analytics Service

### Steps
- [ ] **Step 1 — Plan**: File tree, persistence approach, history endpoint design, aggregation approach, metrics exposure approach, JWT validation, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /analytics/me/history` is implemented
- [ ] Listen history persists across sessions
- [ ] Playback events are aggregated into chart data
- [ ] Global play-count-based rankings are computable
- [ ] Metrics suitable for Prometheus scraping are exposed
- [ ] The service uses its own dedicated persistence layer
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 7 — Recommendation Service

### Steps
- [ ] **Step 1 — Plan**: File tree, endpoint design, recommendation strategy, persistence/caching approach if needed, JWT validation, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, Maven build files, `.env.example`, schema/migration, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /recommend/daily-mix` returns a non-empty response for valid requests
- [ ] `GET /recommend/similar/:songId` returns a non-empty response for valid requests
- [ ] The service consumes playback-related interaction data
- [ ] If recommendation data, models, or caches are stored, the service uses its own dedicated persistence layer
- [ ] Recommendation quality is functional even if simple
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover the required endpoints and persistence behavior where applicable
- [ ] Protected endpoint behavior is tested for valid and invalid JWT access where applicable
- [ ] Test suite passes successfully

---

## Phase 8 — Notification Service

### Steps
- [ ] **Step 1 — Plan**: File tree, internal event handling design, notification persistence approach, internal exposure approach, env vars, dependencies, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, persistence/index setup if needed, unit tests, integration tests, README section
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] Internal events are consumed
- [ ] In-app notifications are persisted in the service’s own storage
- [ ] At least playlist update events or new release events result in stored notifications
- [ ] No email or push logic is implemented
- [ ] The service uses its own dedicated persistence layer
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Unit tests cover the core business logic of the service
- [ ] Integration tests cover internal event consumption and notification persistence behavior
- [ ] Internal-service behavior is tested according to the implemented exposure model
- [ ] Test suite passes successfully

---

## Phase 9 — Frontend (React + TypeScript SPA)

### Steps
- [ ] **Step 1 — Plan**: File tree for `frontend/`, routing strategy, global state shape (session, playback, queue), API client design (JWT injection, retry, error normalization), view inventory, env vars, build/containerization approach, validation steps
- [ ] **Step 2 — Generate**: All source files, Dockerfile, `package.json`, `.env.example`, build config, unit/component tests, integration tests, README section covering build, run, and backend configuration
- [ ] **Step 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Views to Implement
- [ ] **Home / Discovery** — Daily Mix cards and "Because you listened to…" rails; trending tracks from Analytics global charts if implemented
- [ ] **Search** — Full-text search bar; optional real-time autocomplete; results filterable by genre, BPM range, and release year; song results with optional artist/album/playlist sections
- [ ] **Catalog Browse** — Paginated song grid/list with sort controls (title, artist, BPM); artist detail/top tracks only if implemented
- [ ] **Now Playing / Player Bar** — Persistent bottom bar with track title, artist, album art placeholder, play/pause/skip/previous controls, progress scrubber, and volume control; visible across all views
- [ ] **Playlists** — Sidebar list of user's playlists including "Liked Songs"; playlist detail with drag-and-drop reordering, track removal, add-from-search/catalog; playlist creation and deletion
- [ ] **Listening History** — Chronological log of play events from Analytics Service, grouped by date
- [ ] **Notifications** — Optional inbox panel for in-app notifications if notification retrieval is exposed to the frontend in the implemented version

### Acceptance Criteria
- [ ] Registration and login flows work end-to-end against the Auth Service; JWT stored in memory (not `localStorage`)
- [ ] JWT is attached via `Authorization: Bearer` header on all protected API requests
- [ ] Required frontend views are present and navigable via client-side routing (no full-page reload between views)
- [ ] Now Playing bar persists across all route changes
- [ ] Player calls `GET /stream/:songId` to initiate a stream session; playback state machine transitions correctly (`idle → loading → playing → paused → ended/skipped`)
- [ ] Skip and completion actions explicitly trigger the appropriate state transitions and backend event payloads
- [ ] API client handles JWT injection, exponential backoff retry, and error normalization centrally
- [ ] Search returns results and filters (genre, BPM range, year) work in combination
- [ ] Playlist drag-and-drop reorder calls `PATCH /playlists/:id/tracks/reorder`
- [ ] "Liked Songs" playlist is visible but the delete control is hidden or disabled
- [ ] Notifications are retrievable and viewable from the frontend if the notification inbox is implemented in the current version
- [ ] Frontend exposes a `/health` route or static response for uptime checks
- [ ] Key client-side metrics tracked: page load time, API error rates, playback failure counts
- [ ] Dockerfile builds and container starts without errors; frontend is added to `docker-compose.yml`
- [ ] `.env.example` documents all `VITE_` / `REACT_APP_` variables (API base URLs, etc.)
- [ ] No requirements from the brief are missing; no extra requirements added
- [ ] Automated tests cover critical UI flows and route behavior
- [ ] Authentication flow is tested
- [ ] Playback state transitions are tested
- [ ] Search filtering behavior is tested
- [ ] Playlist reorder interaction is tested
- [ ] Test suite passes successfully

---

## Phase 10 — Monitoring, Load Generator & Integration

### Monitoring
- [ ] Prometheus configured to scrape all 8 services
- [ ] If implemented, Grafana dashboard is configured with panels for traffic, latency, error rate, and top tracks (S-03)
- [ ] All services expose metrics suitable for Prometheus scraping

### Load Generator
- [ ] Covers: registration, login, catalog browsing, search, streaming requests, playlist operations, and history queries (M-21)
- [ ] Load generator starts as a service in `docker-compose.yml`
- [ ] Workload definition is documented

### Integration Fixes
- [ ] Inter-service HTTP calls implement retry with exponential backoff (M-22)
- [ ] Inter-service HTTP calls implement circuit breaker or equivalent failure isolation (M-23)
- [ ] All 8 services communicate over the shared named Docker network (M-18)
- [ ] CPU and memory limits are configurable per service in `docker-compose.yml` (M-20)

### System Verification Deliverable
- [ ] Automated integration tests show that the services run correctly together in the shared deployment environment
- [ ] End-to-end tests cover the main application flows across service boundaries
- [ ] Cross-service authentication, persistence, and messaging behavior are validated in the integrated system
- [ ] Test evidence is documented and included in the final delivery

---

## Final Delivery Checklist

### Architecture
- [ ] Each application service that persists state uses its own dedicated persistence layer (M-26)
- [ ] All protected endpoints require JWT; only `/auth/register` and `/auth/login` are public (M-25)

### Artifacts
- [ ] `docker-compose.yml` starts all 8 application services and the required infrastructure
- [ ] Source code complete and runnable for all 8 services and the frontend (no pseudocode or placeholders)
- [ ] Dockerfiles present and building for all 8 services and the frontend
- [ ] `.env.example` present and complete for all 8 services and the frontend
- [ ] Database schemas / migrations present for all services with persistence
- [ ] Catalog CSV seed script included and runs automatically at startup
- [ ] Prometheus config file included
- [ ] Grafana dashboard config included if Grafana dashboards are implemented
- [ ] Load generator script and workload definition included

### Documentation
- [ ] Top-level README with setup, run, validation, and testing instructions

### Testing Deliverables
- [ ] All backend unit tests pass
- [ ] All backend integration tests pass
- [ ] Frontend automated tests pass
- [ ] Integrated system tests show that the services run correctly together
- [ ] End-to-end test results are documented

### Minimum Completion Criteria
- [ ] All 8 services start successfully in the local deployment environment
- [ ] Frontend starts and is reachable in the browser via `docker-compose up`
- [ ] All required endpoints are implemented and reachable
- [ ] Protected endpoints enforce JWT authentication
- [ ] Metrics are exposed and collected through the monitoring stack
- [ ] Load generator can execute the main application flows end-to-end
- [ ] Integrated system tests show that all services run correctly together in the shared deployment environment
- [ ] Cross-service authentication, persistence, and messaging behavior are verified end-to-end