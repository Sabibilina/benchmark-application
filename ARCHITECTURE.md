# System Architecture Overview

## What this system is

This benchmark application is cloud-native music streaming system. It represents a fixed set of microservices, their responsibilities, and the shared architectural principles that govern how the system is built and operated. The architecture is meant to remain stable over time and should not change based on runtime behavior or implementation details.

## Static architectural view

The system is composed of eight independent microservices. Each service owns its own responsibilities and, when it stores state, its own dedicated persistence layer. The architecture is designed for comparison, observability, and experimental research rather than for evolving feature shape.

### Core services

1. **Auth Service**
   - Provides user authentication, registration, and JWT token issuance.
   - Issues tokens that other services can validate locally using shared verification settings.

2. **Catalog Service**
   - Stores and serves song metadata.
   - Supports browsing and retrieving songs with catalog-specific metadata.

3. **Streaming Service**
   - Simulates streaming behavior and emits playback interaction events.
   - Provides a stream descriptor without using real audio files.

4. **Playlist Service**
   - Manages user playlists, track operations, and the special "Liked Songs" playlist.
   - Supports CRUD and playlist track reordering.

5. **Search Service**
   - Provides text search and filtering over the song catalog.
   - Supports search queries scoped by genre, BPM, and year.

6. **Analytics Service**
   - Stores listening history and aggregates playback events into analytics data.
   - Exposes history and metrics-oriented analytics.

7. **Recommendation Service**
   - Generates personalized and song-based recommendations.
   - Returns recommendation results such as daily mixes and similar songs.

8. **Notification Service**
   - Consumes internal application events and stores in-app notifications.
   - Focuses on internal notifications only, without email or push message delivery.

## Shared architectural principles

- **Service isolation**: Each service may choose its own implementation technology, but it must preserve service boundaries and data ownership.
- **Database-per-service**: Stateful services must use dedicated persistence layers and never directly access another service’s database.
- **Polyglot architecture**: The system should use at least three different language/framework stacks across the eight services.
- **Event-driven integration**: Services may communicate through events using a message broker when needed.
- **Observability**: Monitoring should be implemented with Prometheus, and Grafana is recommended for dashboards.
- **Stable contracts**: Service APIs are defined as fixed contracts, and the architecture focuses on those contracts rather than internal implementation details.

## What this document is for

`ARCHITECTURE.md` is a shared, high-level description of the system’s static structure. It explains:

- what the system is,
- how it is organized,
- what each service is responsible for,
- and what architectural rules govern the design.

It is not a runtime log, implementation guide, or evolving design note. It is a stable reference that describes the system shape independent of code or deployments.


| ID | Priority | Type | Requirement | Notes |
| ----- | ----- | ----- | ----- | ----- |
| M-01 | Must have | F | **Auth Service** must support user registration | **Endpoint**: POST /auth/register Creates a user account |
| M-02 | Must have | F | **Auth Service** must support user login and return a JWT access token | **Endpoint**: POST /auth/login Returned JWT is used for protected endpoints |
| M-03 | Must have | F | **Auth Service** must issue JWTs that are validated locally by the other services | Auth Service must sign JWTs, and protected services must validate them locally without making a per-request validation call to Auth Service. JWT verification must use a shared verification configuration, and JWTs must use asymmetric signing so services verify them using a public key |
| M-04 | Must have | F | **Streaming Service** must expose simulated audio streaming for a song | **Endpoint**: GET /stream/:songId Must return a simulated HLS manifest or equivalent stream descriptor and must not require real audio files |
| M-05 | Must have | F | **Streaming Service** must generate configurable dummy segment payloads for simulated streaming | Segment responses must return random or generated binary payloads of configurable size to simulate network load |
| M-06 | Must have | F | **Streaming Service** must publish playback events for song interactions | It must emit at least play.started, play.ended, and play.skipped events with user, song, and timestamp data |
| M-07 | Must have | F | **Catalog Service** must support paginated song catalog browsing | **Endpoint**: GET /catalog/songs Pagination must be implemented consistently |
| M-08 | Must have | F | **Catalog Service** must support retrieval of a single song and its metadata | **Endpoint**: GET /catalog/songs/:id Returned metadata must include the fields used by the application dataset |
| M-09 | Must have | F | **Catalog Service** must ingest the song dataset automatically at startup | The dataset must be seeded into the service database during container startup without manual import steps |
| M-10 | Must have | F | **Playlist Service** must support playlist CRUD and track management | Minimum **endpoints**:  GET /playlists POST /playlists GET /playlists/:id PATCH /playlists/:id DELETE /playlists/:id POST /playlists/:id/tracks  DELETE /playlists/:id/tracks/:songId PATCH /playlists/:id/tracks/reorder |
| M-11 | Must have | F | **Playlist Service** must implement liked songs as a per-user special playlist | A liked song must be stored in a dedicated playlist owned by the user named “Liked Songs”  |
| M-12 | Must have | F | **Search Service** must support text search over songs with filters by genre, BPM, and year | **Endpoint**: GET /search?q=\&genre=\&bpm\_min=\&bpm\_max=\&year= |
| M-13 | Must have | F | **Analytics Service** must persist listen history per user across sessions | **Endpoint**: GET /analytics/me/history Listen history must be tied to authenticated users and remain available after service restarts if the database persists |
| M-14 | Must have | F | **Analytics Service** must aggregate playback events into chart data | It must be able to compute at least global play-count-based rankings from ingested data and/or emitted playback events |
| M-15 | Must have | F | **Recommendation Service** must expose personalised and song-based recommendation endpoints | Minimum **endpoints**:  GET /recommend/daily-mix GET /recommend/similar/:songId  |
| M-16 | Must have | F | **Recommendation Service** must consume playback events and return non-empty recommendation responses for valid requests |  |
| M-17 | Must have | F | **Notification Service** must consume internal events and persist in-app notifications retrievable from its own storage | The service is internal only and must not implement email or push delivery. It must consume at least playlist update events or new release events and store resulting in-app notifications |
| M-18 | Must have | NF | All 8 application services must communicate over a shared named Docker network. | A single named Docker network must be configured for inter-service communication in Docker Compose |
| M-19 | Must have | NF | A single docker-compose.yml must start all 8 application services and the required infrastructure | Must include the application services, their required persistence components, the message broker, object storage if needed, the monitoring stack, and the load generator |
| M-20 | Must have | NF | CPU and memory limits must be configurable per service in docker-compose.yml | Resource limits must be defined per container or service and be easy to modify for benchmarking scenarios |
| M-21 | Must have | F | A load generator must simulate the main user flows of the application | The workload must cover registration, login, catalog browsing, search, streaming requests, playlist operations, and history queries |
| M-22 | Must have | NF | Inter-service HTTP calls must implement retry with exponential backoff | Retry behavior must be bounded and documented |
| M-23 | Must have | NF | Inter-service HTTP calls must implement a circuit breaker or equivalent failure-isolation mechanism | A slow or failing downstream service must not cascade-fail the rest of the system  |
| M-24  | Must have | NF | Each service must expose metrics suitable for Prometheus scraping, and Prometheus must be configured to scrape those metrics | Metrics must be available and collected for execution monitoring and load testing through the monitoring stack started by docker-compose.yml |
| M-25 | Must have | NF | All protected endpoints must require JWT authentication | Only POST /auth/register and POST /auth/login may be publicly accessible |
| M-26 | Must have | NF | The application must follow a polyglot microservices architecture | The 8 application services must use at least 3 different language/framework stacks. Each service must choose its implementation stack from the approved options defined in the Approved Service-Stack Options section |
| M-27 | Must have | NF | Each application service that persists state must use its own dedicated persistence layer | Shared application databases are not allowed; service data isolation must be preserved  |
| S-01 | Should have | F | **Catalog Service** should support artist pages and top tracks | **Endpoint**: GET /catalog/artists/:id/top-tracks  |
| S-02 | Should have | F | **Analytics Service** should expose a global top 50 chart endpoint | **Endpoint**: GET /analytics/charts/global |
| S-03 | Should have | NF | The monitoring stack should include an admin dashboard for observability | Grafana should expose dashboards for traffic, latency, error rate, and top tracks |
| C-01 | Could have | F | **Playlist Service** could support mood-based smart playlists generated from BPM and genre tags | Example moods: Chill, Focus, Workout, Party |
| C-02 | Could have | F | **Playlist Service** could support queue management features | Examples: shuffle, repeat, and manual queue ordering |
| C-03 | Could have | F | **Analytics Service** could support personal charts and listening statistics. | This extends global chart functionality with per-user analytics |
| C-04 | Could have | NF | **Search Service** could use Elasticsearch for fuzzy and faceted queries | Can fall back to full-text search if time is constrained |
| C-05 | Could have | F | **Search Service** could support expanded search across artists, albums, and playlists | Search results may be extended beyond songs to include artists, albums, and playlists |
| C-06 | Could have | F | **Search Service** could support autocomplete suggestions | Suggestions may be based on song, artist, album, or playlist names |
| C-07 | Could have | F | **Playlist Service** could support playlist version history and undo | Version history must be persisted if implemented |
| C-08 | Could have | F | **Playlist Service** could support collaborative playlist editing | Multiple users may be granted edit access to the same playlist |
| W-01 | Will not have | F | Email and push notifications will not be implemented | External SMTP or third-party notification delivery is out of scope |
| W-02 | Will not have | F | OAuth2 or social login will not be implemented | Authentication is limited to local registration and login |
| W-03 | Will not have | F | Real audio playback files will not be required | The system simulates streaming behavior and load using metadata and generated payloads only  |
|  |  |  |  |  |

## 

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

* ## docker-compose.yml for the full application and supporting infrastructure.

* ## Source code and containerization artifacts for the Auth Service.

* ## Source code, dataset-ingestion logic, and containerization artifacts for the Catalog Service.

* ## Source code and containerization artifacts for the Streaming Service.

* ## Source code and containerization artifacts for the Playlist Service.

* ## Source code and containerization artifacts for the Search Service.

* ## Source code and containerization artifacts for the Analytics Service.

* ## Source code and containerization artifacts for the Recommendation Service.

* ## Source code and containerization artifacts for the Notification Service.

* ## Configuration for metrics collection and monitoring support.

* ## Load-generator script and workload definition.

* ## README with setup, run, validation, and testing instructions.

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

### **Frontend Requirements**

#### **Technology & Architecture**

The frontend should be a single-page application (SPA) with client-side routing. **React** with TypeScript is the recommended stack, using a state management library (Zustand or Redux Toolkit) for global state such as the current user session, playback state, and queue.

#### **Authentication**

The app must support registration and login flows backed by the Auth Service. On login, the issued JWT must be stored in memory (not localStorage) and attached to all subsequent API requests via an `Authorization: Bearer` header. A silent refresh mechanism should handle token expiry gracefully, redirecting to login only when the refresh fails.

#### **Core Views**

**1\. Home / Discovery** — Personalized landing page showing Daily Mix cards, "Because you listened to…" rails, and trending tracks from the Analytics Service global charts.

**2\. Search** — A full-text search bar with real-time autocomplete suggestions. Results should be filterable by genre, BPM range, and release year. Results should display songs, with optional expanded sections for artists, albums, and playlists.

**3\. Catalog Browse** — Paginated song grid/list with sort controls (title, artist, BPM). Clicking an artist name navigates to an artist detail page showing their top tracks.

**4\. Now Playing / Player Bar** — A persistent bottom bar showing the current track's title, artist, album art placeholder, playback controls (play/pause, skip, previous), a progress scrubber, and volume control. This bar must remain visible across all views.

**5\. Playlists** — A sidebar list of the user's playlists (including the special "Liked Songs" playlist). A playlist detail view allows track reordering (drag-and-drop), removal, and adding tracks from search or catalog. Playlist creation and deletion must be supported.

**6\. Listening History** — A chronological log of the user's play events fetched from the Analytics Service, grouped by date.

**7\. Notifications** — An inbox panel (accessible from a bell icon in the nav) listing in-app notifications from the Notification Service, such as playlist updates from collaborators.

#### **Playback Integration**

The player must call the Streaming Service (`GET /stream/:songId`) to initiate a stream session. Since the service returns a simulated HLS manifest, the frontend should treat playback as a state machine: `idle → loading → playing → paused → ended/skipped`. State transitions must emit the appropriate events (the backend handles Kafka publishing). Skip and completion actions must be explicitly triggered.

#### **API Communication**

All HTTP calls to backend services must go through a centralized API client that handles JWT injection, retry with exponential backoff (mirroring the backend requirement), and error normalization. A React Query or SWR library is recommended for server state caching and background refetching.

#### **Observability**

The frontend should expose a `/health` route (or equivalent static response) and track key client-side metrics: page load time, API error rates, and playback failure counts. These can be reported to the Analytics Service or logged for Prometheus scraping via a lightweight metrics endpoint.

