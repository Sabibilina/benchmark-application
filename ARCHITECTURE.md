# System Architecture

## 1. System Overview

The application is a cloud-native music streaming system designed for benchmarking. It consists of a fixed set of microservices, each with clearly defined responsibilities, along with the shared architectural principles that govern how the system is built and operated. 
This architecture is intended to remain stable over time and should not vary based on runtime behavior, deployment conditions, or implementation details.

## 2. Purpose of this document

`ARCHITECTURE.md` is a shared, high-level description of the system’s static structure. It explains:

- what the system is,
- how it is organized,
- what each service is responsible for,
- and what architectural rules govern the design.

It is not a runtime log, implementation guide, or evolving design note. It is a stable reference that describes the system shape independent of code or deployments.

## 3. Microservices Specifications
The system is composed of eight independent microservices. Each service owns its own responsibilities and, when it stores state, its own dedicated persistence layer. The architecture is designed for comparison, observability, and experimental research.

### 1. Auth Service

* Purpose: handles user registration, login, and JWT issuance for authenticated access across the system.  
* Required endpoints:  
  * `POST /auth/register`  
  * `POST /auth/login`  
* Required behavior:  
  * Creates user accounts and authenticates users.  
  * Issues JWT access tokens for protected endpoints.  
  * Signs JWTs so that the other services can validate them locally without making a per-request validation call to Auth Service.  
* Security requirements:  
  * JWT verification must use a shared verification configuration.  
  * JWTs must use asymmetric signing so other services verify them using a public key.  
* Persistence:  
  * Must persist authentication-related state in its own dedicated persistence layer.  
* Allowed implementation:  
  * Stack must be selected from the Approved Service-Stack Options section.

### 2. Catalog Service

* Purpose: manages the song catalog and exposes song metadata for browsing and retrieval.  
* Required endpoints:  
  * `GET /catalog/songs`  
  * `GET /catalog/songs/:id`  
* Required behavior:  
  * Supports paginated browsing of songs.  
  * Returns detailed metadata for a single song.  
  * Automatically ingests the song dataset at startup without manual import steps.  
* Dataset requirements:  
  * Song records must contain the metadata fields required by the application and recommendation/search flows.  
* Persistence:  
  * Must store catalog data in its own dedicated persistence layer.  
* Optional behavior:  
  * `GET /catalog/artists/:id/top-tracks` may be implemented as a should-have feature.

### 3. Streaming Service

* Purpose: simulates song streaming behavior and emits playback-related interaction events.  
* Required endpoint:  
  * `GET /stream/:songId`  
* Required behavior:  
  * Returns a simulated HLS manifest or equivalent stream descriptor.  
  * Does not use real audio files.  
  * Generates configurable dummy segment payloads to simulate network load.  
  * Emits at least play.started, play.ended, and play.skipped interaction events with user, song, and timestamp data.  
* Security:  
  * Protected streaming access must require a valid JWT.  
* Persistence:  
  * If the service persists state, it must use its own dedicated persistence layer.

### 4. Playlist Service

* Purpose: manages user playlists and playlist track operations.  
* Required endpoints:  
  * `GET /playlists`  
  * `POST /playlists` 
  * `GET /playlists/:id`  
  * `PATCH /playlists/:id`  
  * `DELETE /playlists/:id`  
  * `POST /playlists/:id/tracks`  
  * `DELETE /playlists/:id/tracks/:songId`  
  * `PATCH /playlists/:id/tracks/reorder`  
* Required behavior:  
  * Supports playlist CRUD operations.  
  * Supports adding, removing, and reordering tracks in playlists.  
  * Implements a per-user special playlist named Liked Songs.  
* Persistence:  
  * Must store playlist and liked-song data in its own dedicated persistence layer.  
* Optional behavior:  
  * Mood-based smart playlists, version history with undo, collaborative editing, and queue management may be implemented as optional features.

### 5. Search Service

* Purpose: provides filtered text search over the song catalog.  
* Required endpoint:  
  * `GET /search?q=\&genre=\&bpm\_min=\&bpm\_max=\&year=`  
* Required behavior:  
  * Supports text search over songs.  
  * Supports filtering by genre, BPM, and year.  
* Persistence and indexing:  
  * If the service stores search indexes or cached search data, it must use its own dedicated persistence layer.  
* Optional behavior:  
  * Expanded search across artists, albums, and playlists may be implemented.  
  * Autocomplete suggestions may be implemented.  
  * Elasticsearch or another search backend may be used, but it is not mandatory.

### 6. Analytics Service

* Purpose: stores listening history and aggregates playback data into chart-oriented analytics.  
* Required endpoint:  
  * `GET /analytics/me/history`  
* Required behavior:  
  * Persists user listen history across sessions.  
  * Aggregates playback events into chart data.  
  * Computes at least global play-count-based rankings from ingested and/or emitted playback data.  
* Observability:  
  * Must expose metrics suitable for Prometheus scraping.  
* Persistence:  
  * Must store analytics data in its own dedicated persistence layer.  
* Optional behavior:  
  * `GET /analytics/charts/global` may be implemented as a should-have endpoint.  
  * Personal charts and listening statistics may be implemented as an optional extension.

### 7. Recommendation Service

* Purpose: generates personalised and song-based recommendations from observed listening behavior.  
* Required endpoints:  
  * `GET /recommend/daily-mix`  
  * `GET /recommend/similar/:songId`  
* Required behavior:  
  * Consumes playback-related interaction data.  
  * Returns non-empty recommendation responses for valid requests.  
* Persistence:  
  * If the service persists recommendation data, models, or caches, it must use its own dedicated persistence layer.  
* Implementation note:  
  * Recommendation quality may be simple in the first version as long as the endpoints and behavior are functional.

### 8. Notification Service

* Purpose: stores internal in-app notifications generated from application events.  
* Exposure:  
  * Internal service only; no public client-facing API is required in the minimum version.  
* Required behavior:  
  * Consumes internal events.  
  * Stores in-app notifications retrievable from its own storage.  
  * Must consume at least playlist update events or new release events and persist resulting in-app notifications.  
* Out of scope:  
  * Email and push notifications must not be implemented.  
* Persistence:  
  * Must use its own dedicated persistence layer.

## 4. Technology Stack

To preserve comparability while still allowing a polyglot architecture, each service must choose its technology stack from the following approved options. The choice must be documented.

### 4.1. Options

#### Auth Service

* Node.js \+ Express  
* Go \+ Gin

#### Catalog Service

* Python \+ FastAPI  
* Java \+ Spring Boot

#### Streaming Service

* Go \+ Gin  
* Node.js \+ Express

#### Playlist Service

* Node.js \+ Express  
* Python \+ FastAPI

#### Search Service

* Python \+ FastAPI  
* Node.js \+ Express

#### Analytics Service

* Python \+ FastAPI  
* Go \+ Gin

#### Recommendation Service

* Python \+ FastAPI preferred  
* Python \+ Flask acceptable if kept lightweight and clearly documented

#### Notification Service

* Node.js \+ Express  
* Python \+ FastAPI  
* Go \+ Gin

### 4.2. Shared persistence and infrastructure guidance

* The system may use polyglot persistence. Each service may choose the persistence technology that best fits its data model, query patterns, and consistency requirements, provided that service data isolation is preserved.  
* Each service that persists state must use its own dedicated persistence layer. Services must not directly access another service’s persistence layer.  
* Acceptable persistence categories include relational, document, key-value/cache, search-oriented, graph, and object storage where appropriate to the service responsibilities.  
* Search may use PostgreSQL full-text search, Elasticsearch, or another documented search-oriented backend that satisfies the requirements.  
* Event-driven communication may use Kafka or another documented message broker that satisfies the requirements.  
* Monitoring should use Prometheus, with Grafana recommended for the admin dashboard.  
* Object storage for simulated streaming payloads may use MinIO or an equivalent documented local object store if needed by the implementation.

### 4.3. Selection constraints

* Each service must use exactly one approved application stack.  
* The final system must include at least three distinct language/framework stacks across the 8 services.  
* Persistence technology may vary across services, but the database-per-service pattern and service data isolation must be preserved.  
* Stack choices and persistence choices must be documented in the final README or implementation notes.  
* If an approved alternative is selected instead of the preferred one, the choice must be briefly justified in the generated documentation.

## 5. Requirements

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

## 6. Frontend Architecture

### Technology & Architecture

The frontend should be a single-page application (SPA) with client-side routing. **React** with TypeScript is the recommended stack, using a state management library (Zustand or Redux Toolkit) for global state such as the current user session, playback state, and queue.

### Authentication

The app must support registration and login flows backed by the Auth Service. On login, the issued JWT must be stored in memory (not localStorage) and attached to all subsequent API requests via an `Authorization: Bearer` header. A silent refresh mechanism should handle token expiry gracefully, redirecting to login only when the refresh fails.

### Core Views

**1\. Home / Discovery** — Personalized landing page showing Daily Mix cards, "Because you listened to…" rails, and trending tracks from the Analytics Service global charts.

**2\. Search** — A full-text search bar with real-time autocomplete suggestions. Results should be filterable by genre, BPM range, and release year. Results should display songs, with optional expanded sections for artists, albums, and playlists.

**3\. Catalog Browse** — Paginated song grid/list with sort controls (title, artist, BPM). Clicking an artist name navigates to an artist detail page showing their top tracks.

**4\. Now Playing / Player Bar** — A persistent bottom bar showing the current track's title, artist, album art placeholder, playback controls (play/pause, skip, previous), a progress scrubber, and volume control. This bar must remain visible across all views.

**5\. Playlists** — A sidebar list of the user's playlists (including the special "Liked Songs" playlist). A playlist detail view allows track reordering (drag-and-drop), removal, and adding tracks from search or catalog. Playlist creation and deletion must be supported.

**6\. Listening History** — A chronological log of the user's play events fetched from the Analytics Service, grouped by date.

**7\. Notifications** — An inbox panel (accessible from a bell icon in the nav) listing in-app notifications from the Notification Service, such as playlist updates from collaborators.

### Playback Integration

The player must call the Streaming Service (`GET /stream/:songId`) to initiate a stream session. Since the service returns a simulated HLS manifest, the frontend should treat playback as a state machine: `idle → loading → playing → paused → ended/skipped`. State transitions must emit the appropriate events (the backend handles Kafka publishing). Skip and completion actions must be explicitly triggered.

### API Communication

All HTTP calls to backend services must go through a centralized API client that handles JWT injection, retry with exponential backoff (mirroring the backend requirement), and error normalization. A React Query or SWR library is recommended for server state caching and background refetching.

### Observability

The frontend should expose a `/health` route (or equivalent static response) and track key client-side metrics: page load time, API error rates, and playback failure counts. These can be reported to the Analytics Service or logged for Prometheus scraping via a lightweight metrics endpoint.

