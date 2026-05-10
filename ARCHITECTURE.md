# System Architecture

## 1. System Overview

The application is a cloud-native music streaming system designed for benchmarking. It consists of a fixed set of microservices, each with clearly defined responsibilities, along with the system-wide architectural principles that govern how the system is built and operated. 
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

### 3.1. Auth Service

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
  * Stack must follow the implementation technology choices defined in TECH-STACK.md.

### 3.2. Catalog Service

* Purpose: manages the song catalog and exposes song metadata for browsing and retrieval.  
* Required endpoints:  
  * `GET /catalog/songs`  
  * `GET /catalog/songs/:id`  
* Required behavior:  
  * Supports paginated browsing of songs.  
  * Returns detailed metadata for a single song.  
  * Automatically ingests the song dataset at startup without manual import steps.  
* Dataset requirements:
  * This dataset must be used: https://www.kaggle.com/datasets/rohiteng/spotify-music-analytics-dataset-20152025/data.
  * Song records must contain the metadata fields required by the application and recommendation/search flows.  
* Persistence:  
  * Must store catalog data in its own dedicated persistence layer.  
* Optional behavior:  
  * `GET /catalog/artists/:id/top-tracks` may be implemented as a should-have feature.

### 3.3. Streaming Service

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

### 3.4. Playlist Service

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

### 3.5. Search Service

* Purpose: provides filtered text search over the song catalog.  
* Required endpoint:  
  * `GET /search?q=&genre=&bpm_min=&bpm_max=&year=` 
* Required behavior:  
  * Supports text search over songs.  
  * Supports filtering by genre, BPM, and year.  
* Persistence and indexing:  
  * If the service stores search indexes or cached search data, it must use its own dedicated persistence layer.  
* Optional behavior:  
  * Expanded search across artists, albums, and playlists may be implemented.  
  * Autocomplete suggestions may be implemented.  
  * Elasticsearch or another search backend may be used, but it is not mandatory.

### 3.6. Analytics Service

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

### 3.7. Recommendation Service

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

### 3.8. Notification Service

* Purpose: stores internal in-app notifications generated from application events.  
* Exposure:
  * Internal service only; no public client-facing API is required in the minimum version.
  * If notification retrieval is implemented for the optional frontend inbox, it must be exposed through a protected read interface or another explicitly defined access layer.
* Required behavior:  
  * Consumes internal events.  
  * Stores in-app notifications retrievable from its own storage.  
  * Must consume at least playlist update events or new release events and persist resulting in-app notifications.  
* Out of scope:  
  * Email and push notifications must not be implemented.  
* Persistence:  
  * Must use its own dedicated persistence layer.

## 4. Benchmarking Context

The system is intended to function not only as a music streaming application, but also as a benchmarkable reference system for experimental evaluation. Its fixed service boundaries, dedicated persistence per service, and observable interactions make it suitable for controlled comparison across implementations, deployment setups, and resilience strategies.

The architecture is designed to support benchmarking of performance, scalability, fault tolerance, observability, and cloud cost awareness under representative workloads. For that reason, the system structure described in this document is expected to remain stable so that benchmark results can be compared meaningfully across different runs, configurations, and infrastructure choices.

## 5. Frontend Architecture

### 5.1. Technology & Architecture

The frontend should be a single-page application (SPA) with client-side routing. **React** with TypeScript is the recommended stack, using a state management library (Zustand or Redux Toolkit) for global state such as the current user session, playback state, and queue.

### 5.2. Authentication

The app must support registration and login flows backed by the Auth Service. On login, the issued JWT must be stored in memory (not localStorage) and attached to all subsequent API requests via an `Authorization: Bearer` header.

### 5.3. Core Views

* **Home/Discovery** — Personalized landing page showing Daily Mix cards, "Because you listened to…" rails, and, if implemented, trending tracks from the Analytics Service global charts.

* **Search** — A full-text search bar with optional real-time autocomplete suggestions. Results should be filterable by genre, BPM range, and release year. Results should display songs, with optional expanded sections for artists, albums, and playlists.

* **Catalog Browse** — Paginated song grid/list with sort controls (title, artist, BPM). Clicking an artist name may navigate to an artist detail page showing their top tracks.

* **Now Playing/Player Bar** — A persistent bottom bar showing the current track's title, artist, album art placeholder, playback controls (play/pause, skip, previous), a progress scrubber, and volume control. This bar must remain visible across all views.

* **Playlists** — A sidebar list of the user's playlists (including the special "Liked Songs" playlist). A playlist detail view allows track reordering (drag-and-drop), removal, and adding tracks from search or catalog. Playlist creation and deletion must be supported.

* **Listening History** — A chronological log of the user's play events fetched from the Analytics Service, grouped by date.

* **Notifications** — An optional inbox panel (accessible from a bell icon in the nav) listing in-app notifications from the Notification Service, such as playlist updates from collaborators.

### 5.4. Playback Integration

The player must call the Streaming Service (`GET /stream/:songId`) to initiate a stream session. Since the service returns a simulated HLS manifest, the frontend should treat playback as a state machine: `idle → loading → playing → paused → ended/skipped`. State transitions must emit the appropriate events (the backend handles event publishing). Skip and completion actions must be explicitly triggered.

### 5.5. API Communication

All HTTP calls to backend services must go through a centralized API client that handles JWT injection, retry with exponential backoff, and error normalization. This should follow the same resilience pattern used for backend service-to-service communication.

### 5.6. Observability

The frontend should expose a `/health` route (or equivalent static response) and track key client-side metrics: page load time, API error rates, and playback failure counts. These can be reported to the Analytics Service or logged for Prometheus scraping via a lightweight metrics endpoint.

## 6. System Verification Deliverable

In addition to implementing the required services and infrastructure, the final system must include evidence that the services operate correctly together as an integrated application.

This verification deliverable must demonstrate that the microservices can run together in the shared deployment environment, communicate through their required interfaces, enforce authentication correctly, and support the main end-to-end application flows.

Verification should include automated testing at both service and system level so that the integrated behavior of the platform can be checked repeatedly across implementations and benchmark runs.

