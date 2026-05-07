## **Generation Protocol**

## The application must be generated iteratively and module-by-module rather than in a single pass. Each service should be completed, reviewed, and validated before moving to the next one so that architectural or integration problems can be detected early.

## Generation should begin with the shared deployment environment, including the initial docker-compose.yml and supporting configuration, so that services can be integrated into an existing runtime environment as they are produced.

## **Implementation procedure**

1. ## Start with the shared deployment environment and base configuration.

2. ## Generate one service at a time, including its source code, configuration, and containerization artifacts.

3. ## After each service is generated, validate that it starts successfully and connects to the infrastructure it depends on.

4. ## Refine the generated output iteratively when errors, missing requirements, or integration issues are found.

5. ## Continue until all required services and deployment artifacts have been produced.

## **Generation constraints**

* ## The generated system must satisfy the frozen requirements table and the service specifications.

* ## The implementation must follow a polyglot microservices architecture using at least three different language/framework stacks across the 8 services.

* ## Each service must use one of the approved implementation options listed in the Approved Service-Stack Options section.

* ## All generated code must be runnable and complete; pseudocode and placeholder implementations are not acceptable.

* ## Manual changes made after generation must be explicitly marked in code comments using MANUAL CHANGE: \<reason\>.

## **Validation expectations**

* ## Each generated service should start cleanly in its container.

* ## Each service should connect successfully to its required dependencies.

* ## Protected endpoints should enforce JWT authentication according to the requirements.

* ## Integration behavior should be checked incrementally rather than postponed until the entire system has been generated.

## **Delivery Checklist**

## The generated project must include all artifacts required to build, run, validate, and inspect the application locally. At minimum, the delivery must contain the complete source code, deployment configuration, service containerization artifacts, supporting configuration, and usage documentation needed to reproduce the system.

- [ ] docker-compose.yml for the full application and supporting infrastructure.

- [ ] Source code and containerization artifacts for the Auth Service.

- [ ] Source code, dataset-ingestion logic, and containerization artifacts for the Catalog Service.

- [ ] Source code and containerization artifacts for the Streaming Service.

- [ ] Source code and containerization artifacts for the Playlist Service.

- [ ] Source code and containerization artifacts for the Search Service.

- [ ] Source code and containerization artifacts for the Analytics Service.

- [ ] Source code and containerization artifacts for the Recommendation Service.

- [ ] Source code and containerization artifacts for the Notification Service.

- [ ] Configuration for metrics collection and monitoring support.

- [ ] Load-generator script and workload definition.

- [ ] README with setup, run, validation, and testing instructions.

## **Expected documentation**

* ## Description of the selected technology stack per service.

* ## Notes on any manual changes made after AI generation, marked in the codebase using MANUAL CHANGE: \<reason\>.

* ## Instructions for starting the system, validating the services, and running the load generator.

* ## Description and justification of the selected persistence technology per service.

## **Minimum completion criteria**

* ## All required services start successfully in the local deployment environment.

* ## Required endpoints are implemented and reachable.

* ## Protected endpoints enforce JWT authentication.

* ## Metrics are exposed and collectible through the monitoring setup.

* ## The load generator can execute the main application flows

1. ## **First prompt set**

## **Prompt 1**

Use the attached document as the single source of truth for this project. For this step, do not generate service business logic yet. Create an implementation plan only for the shared deployment environment and repository skeleton. Include:

* proposed repository structure,  
* docker-compose.yml structure,  
* named Docker network,  
* supporting infrastructure needed by the current baseline,  
* service directory layout,  
* config/env file strategy,  
* validation steps for this phase.

Do not generate code yet. State assumptions explicitly and do not invent requirements beyond the document.

## **Prompt 2**

Now generate the repository skeleton and shared deployment environment according to the approved plan and the attached source-of-truth document. Generate:

* top-level folder structure,  
* docker-compose.yml,  
* shared config files,  
* service folders,  
* placeholder Dockerfiles only where needed for the current phase,  
* README with startup instructions for this phase.

Do not implement full service business logic yet. All output must be runnable and consistent with the document.

## **Prompt 3**

Now review the generated repository skeleton and deployment environment against the attached document. Validate that:

* the compose file reflects the required architecture,  
* the shared network is defined,  
* services and infrastructure are organized correctly,  
* the setup supports the later module-by-module workflow,  
* no requirements were added that are not in the document.

Fix any issues and output only the changed files.

2. ## **Second Prompt Set**

   

**Give the LLM the same stable document every time as the source of truth, then work in iterative prompts like plan → generate → validate/fix**

* **Session 1:** repo skeleton \+ docker-compose.yml  
* **Session 2:** Auth Service  
* **Session 3:** Catalog Service \+ seed logic  
* **Session 4:** Playlist Service  
* **Session 5:** Streaming Service  
* **Session 6:** Search Service  
* **Session 7:** Analytics Service  
* **Session 8:** Recommendation Service  
* **Session 9:** Notification Service  
* **Session 10:** monitoring \+ load generator \+ integration fixes

## **Prompt pattern**

Use this pattern every time:

## **Prompt 1 — Plan**

“Use the attached generation brief as the source of truth. For this step, create an implementation plan only for the Auth Service. Include file tree, chosen stack, dependencies, endpoints, env vars, and validation steps. Do not generate code yet.”

## **Prompt 2 — Generate**

“Now generate the complete runnable implementation for the Auth Service exactly according to the approved plan and the generation brief. Include source code, Dockerfile, dependency manifest, config, and a short README. No pseudocode, no placeholders.”

## **Prompt 3 — Validate/Fix**

“Review the generated Auth Service against these acceptance criteria. Fix missing or incorrect parts and output only the changed files.”

* Use the attached generation brief as the only source of truth.  
* Do not invent requirements not present in the brief.  
* Do not modify unrelated files.  
* State assumptions explicitly.  
* Prefer regeneration over patching if the architecture is wrong.


## **Checklist**

Services are built **one at a time** in the order below. Each service goes through three prompts:
**Plan → Generate → Validate/Fix**. Do not move to the next service until the current one starts cleanly and passes its acceptance criteria.

---

## Phase 0 — Shared Deployment Environment

> Repo skeleton, `docker-compose.yml`, named network, and infrastructure scaffolding.

- [ ] **Plan** — Propose repo structure, compose file layout, named Docker network, infrastructure, and env/config strategy
- [ ] **Generate** — Top-level folder structure, `docker-compose.yml`, shared config files, placeholder Dockerfiles, and README
- [ ] **Validate/Fix** — Confirm compose file reflects required architecture; named network defined; no invented requirements
- [ ] Compose file starts without errors
- [ ] Named Docker network is defined and all services reference it
- [ ] Service directories are scaffolded
- [ ] README covers startup instructions for this phase

---

## Phase 1 — Auth Service

> Stack: **Node.js + Express** or **Go + Gin**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, DB schema (users, refresh_tokens), endpoints, JWT RS256 strategy, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, `init.sql`, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `POST /auth/register` accepts `{username, email, password}`, hashes password, persists user, returns `201`
- [ ] `POST /auth/login` accepts `{email, password}`, validates credentials, returns a signed RS256 JWT with at minimum `userId` and `email` claims
- [ ] JWT public key is accessible to other services (JWKS endpoint or mounted file) — method documented
- [ ] Service connects to its own dedicated PostgreSQL instance (not shared)
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` documents every variable the service reads
- [ ] No endpoint beyond `/auth/register` and `/auth/login` is publicly accessible without a JWT
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 2 — Catalog Service

> Stack: **Python + FastAPI** or **Java + Spring Boot**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, DB schema (songs table with all required columns), CSV ingestion strategy, endpoints, pagination design, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, seed script, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /catalog/songs` returns a paginated response (`page`, `limit`, `total`, `data` array)
- [ ] `GET /catalog/songs/:id` returns full song metadata including `id`, `name`, `artists`, `release_date`, `tempo`, `genre`, `explicit`, `popularity`, `duration_ms`
- [ ] CSV dataset is seeded automatically on container startup — no manual import step
- [ ] Service uses its own dedicated PostgreSQL database
- [ ] Fields required by Search Service (`genre`, `BPM/tempo`, `year`) are present and indexed
- [ ] Fields required by Recommendation Service (`tempo`, `energy`, `danceability`) are stored
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 3 — Playlist Service

> Stack: **Node.js + Express** or **Python + FastAPI**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, DB schema (`playlists`, `playlist_tracks` with order column, Liked Songs model), all 8 endpoints, track reorder strategy, Liked Songs protection logic, JWT validation, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] All 8 required endpoints exist and return correct HTTP status codes
- [ ] All endpoints return `401` when no valid JWT is provided
- [ ] `POST /playlists` creates a playlist owned by the authenticated user
- [ ] `GET /playlists` returns only playlists belonging to the authenticated user
- [ ] `POST /playlists/:id/tracks` adds a track; `DELETE` removes it; `PATCH /reorder` updates track order transactionally
- [ ] "Liked Songs" playlist is automatically available per user and cannot be deleted via `DELETE /playlists/:id`
- [ ] Service uses its own dedicated PostgreSQL instance
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 4 — Streaming Service

> Stack: **Go + Gin** or **Node.js + Express**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, endpoint design (simulated HLS manifest), dummy segment payload design, MinIO setup, Redis session state (optional), event emission strategy (stdout stub), JWT validation approach, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, MinIO init logic, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /stream/:songId` returns `401` when no JWT is provided
- [ ] `GET /stream/:songId` returns a simulated HLS manifest (m3u8-like or documented equivalent) for a valid JWT
- [ ] Segment payloads are configurable in size via environment variable
- [ ] `play.started`, `play.ended`, and `play.skipped` events are emitted to log/stdout with `userId`, `songId`, and `timestamp` fields
- [ ] MinIO bucket is initialized at startup without manual steps
- [ ] JWT validation uses RS256 and the Auth Service public key
- [ ] Service uses its own persistence layer (MinIO + optional Redis); no shared databases
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 5 — Search Service

> Stack: **Python + FastAPI** or **Node.js + Express**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, endpoint design, search backend strategy (PostgreSQL FTS with `tsvector`/`tsquery`), catalog data population approach, filter logic (genre, BPM range, year), index design, JWT validation, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration with indexes, data population script, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /search?q=test` returns song results matching the query text
- [ ] `GET /search?genre=pop` returns songs filtered by genre
- [ ] `GET /search?bpm_min=120&bpm_max=140` returns songs within the BPM range
- [ ] `GET /search?year=2020` returns songs released in that year
- [ ] Combined filters work together in a single request
- [ ] All search endpoints return `401` without a valid JWT
- [ ] Service has its own dedicated persistence layer — does not directly query the Catalog Service database
- [ ] Method used to populate the search index is documented in the README
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 6 — Analytics Service

> Stack: **Python + FastAPI** or **Go + Gin**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, DB choice (TimescaleDB or PostgreSQL), table/hypertable design, `GET /analytics/me/history` response shape, global charts approach, stub ingest endpoint contract, Prometheus metrics list, JWT validation, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, schema/migration, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `POST /analytics/events` (stub) accepts `{userId, songId, eventType, timestamp}` and persists the event
- [ ] `GET /analytics/me/history` returns the authenticated user's events in chronological order
- [ ] Listen history persists across container restarts (database-backed, not in-memory)
- [ ] Play-count aggregation is computable from stored events (query or materialized view exists)
- [ ] `GET /metrics` returns Prometheus-formatted metrics including at minimum event count and top track data
- [ ] All user-facing endpoints return `401` without a valid JWT
- [ ] Service uses its own dedicated database (TimescaleDB or PostgreSQL)
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 7 — Recommendation Service

> Stack: **Python + FastAPI** (preferred) or **Python + Flask**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, PostgreSQL schema (user–song interaction matrix), Redis key schema and TTL values, both endpoint designs, v1 algorithm description (genre/BPM heuristic), stub ingest endpoint, cache population strategy, JWT validation, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest (requirements.txt or pyproject.toml), `.env.example`, schema/migration, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `GET /recommend/daily-mix` returns a non-empty array of song objects for a valid authenticated user
- [ ] `GET /recommend/similar/:songId` returns a non-empty array of song objects for a valid `songId`
- [ ] Both endpoints return `401` without a valid JWT
- [ ] `POST /recommendation/events` persists interaction data to PostgreSQL
- [ ] Redis is used to cache recommendations with a documented TTL; cache is populated on first request or by a background task
- [ ] Responses are non-empty even for users with no interaction history (fallback logic exists and is documented)
- [ ] Service uses its own dedicated PostgreSQL and Redis instances (no shared databases)
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 8 — Notification Service

> Stack: **Node.js + Express**, **Python + FastAPI**, or **Go + Gin**

### Prompts
- [ ] **Prompt 1 — Plan**: File tree, stack choice + justification, MongoDB document schema, internal-only exposure rationale, stub ingest endpoint contract for `playlist_updated` and `new_release` events, notification creation logic, env vars, dependencies, validation steps
- [ ] **Prompt 2 — Generate**: All source files, Dockerfile, dependency manifest, `.env.example`, MongoDB index creation, README section
- [ ] **Prompt 3 — Validate/Fix**: Review against acceptance criteria; fix and return only changed files

### Acceptance Criteria
- [ ] `POST /notifications/internal` accepts a `playlist_updated` event payload and persists a notification document in MongoDB
- [ ] `POST /notifications/internal` accepts a `new_release` event payload and persists a notification document in MongoDB
- [ ] Stored notifications are retrievable per `userId` (via `GET /notifications` or equivalent internal endpoint)
- [ ] MongoDB document schema includes: `userId`, `type`, `payload`, `read` (boolean), `createdAt`
- [ ] A `userId` index exists in MongoDB for efficient per-user queries
- [ ] No email or push logic is present in any form
- [ ] Service uses its own dedicated MongoDB instance
- [ ] Dockerfile builds and container starts without errors
- [ ] `.env.example` is complete
- [ ] No requirements from the brief are missing; no extra requirements added

---

## Phase 9 — Monitoring, Load Generator & Integration

### Monitoring
- [ ] Prometheus configured to scrape all 8 services
- [ ] Grafana dashboard configured with panels for traffic, latency, error rate, and top tracks (S-03)
- [ ] All services expose a `/metrics` endpoint compatible with Prometheus scraping

### Load Generator
- [ ] Covers: registration, login, catalog browsing, search, streaming requests, playlist operations, and history queries (M-21)
- [ ] Load generator starts as a service in `docker-compose.yml`
- [ ] Workload definition is documented

### Integration Fixes
- [ ] Inter-service HTTP calls implement retry with exponential backoff (M-22)
- [ ] Inter-service HTTP calls implement circuit breaker or equivalent failure isolation (M-23)
- [ ] All 8 services communicate over the shared named Docker network (M-18)
- [ ] CPU and memory limits are configurable per service in `docker-compose.yml` (M-20)

---

## Final Delivery Checklist

### Architecture
- [ ] At least 3 distinct language/framework stacks used across 8 services (M-26)
- [ ] Database-per-service pattern enforced, no shared databases (M-27)
- [ ] All protected endpoints require JWT; only `/auth/register` and `/auth/login` are public (M-25)

### Artifacts
- [ ] `docker-compose.yml` starts all 8 services + full infrastructure in one command
- [ ] Source code complete and runnable for all 8 services (no pseudocode or placeholders)
- [ ] Dockerfiles present and building for all 8 services
- [ ] `.env.example` present and complete for all 8 services
- [ ] Database schemas / migrations present for all services with persistence
- [ ] Catalog CSV seed script included and runs automatically at startup
- [ ] MinIO bucket initialization runs automatically at startup
- [ ] Prometheus config file included
- [ ] Grafana dashboard config included
- [ ] Load generator script and workload definition included

### Documentation
- [ ] Top-level README with setup, run, validation, and testing instructions
- [ ] Stack selection documented per service with justification
- [ ] Persistence technology documented per service with justification
- [ ] All post-generation manual changes marked with `// MANUAL CHANGE: <reason>` or `# MANUAL CHANGE: <reason>`
- [ ] Stub ingest endpoints (Analytics, Recommendation, Notification) documented as Kafka placeholders

### Minimum Completion Criteria
- [ ] All 8 services start successfully in the local deployment environment
- [ ] All required endpoints are implemented and reachable
- [ ] Protected endpoints enforce JWT authentication
- [ ] Metrics are exposed and collected through the monitoring stack
- [ ] Load generator can execute the main application flows end-to-end


