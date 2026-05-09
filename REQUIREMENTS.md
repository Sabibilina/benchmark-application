# Requirements

This document defines the functional and non-functional requirements for the cloud-native music streaming benchmark system. It specifies the mandatory, recommended, optional, and out-of-scope capabilities that guide implementation and evaluation. The requirements are intended to provide a clear reference for what the system must support, what may be added as extensions, and which features are explicitly excluded.

## Requirements Table

| ID | Priority | Type | Requirement | Notes |
| ----- | ----- | ----- | ----- | ----- |
| M-01 | Must have | F | **Auth Service** must support user registration | **Endpoint**: `POST /auth/register` Creates a user account |
| M-02 | Must have | F | **Auth Service** must support user login and return a JWT access token | **Endpoint**: `POST /auth/login` Returned JWT is used for protected endpoints |
| M-03 | Must have | F | **Auth Service** must issue JWTs that are validated locally by the other services | Auth Service must sign JWTs, and protected services must validate them locally without making a per-request validation call to Auth Service. JWT verification must use a shared verification configuration, and JWTs must use asymmetric signing so services verify them using a public key |
| M-04 | Must have | F | **Streaming Service** must expose simulated audio streaming for a song | **Endpoint**: `GET /stream/:songId` Must return a simulated HLS manifest or equivalent stream descriptor and must not require real audio files |
| M-05 | Must have | F | **Streaming Service** must generate configurable dummy segment payloads for simulated streaming | Segment responses must return random or generated binary payloads of configurable size to simulate network load |
| M-06 | Must have | F | **Streaming Service** must publish playback events for song interactions | It must emit at least `play.started`, `play.ended`, and `play.skipped` events with user, song, and timestamp data |
| M-07 | Must have | F | **Catalog Service** must support paginated song catalog browsing | **Endpoint**: `GET /catalog/songs` Pagination must be implemented consistently |
| M-08 | Must have | F | **Catalog Service** must support retrieval of a single song and its metadata | **Endpoint**: `GET /catalog/songs/:id` Returned metadata must include the fields used by the application dataset |
| M-09 | Must have | F | **Catalog Service** must ingest the song dataset automatically at startup | The dataset must be seeded into the service database during container startup without manual import steps |
| M-10 | Must have | F | **Playlist Service** must support playlist CRUD and track management | Minimum **endpoints**: `GET /playlists`, `POST /playlists`, `GET /playlists/:id`, `PATCH /playlists/:id`, `DELETE /playlists/:id`, `POST /playlists/:id/tracks`, `DELETE /playlists/:id/tracks/:songId`, `PATCH /playlists/:id/tracks/reorder` |
| M-11 | Must have | F | **Playlist Service** must implement liked songs as a per-user special playlist | A liked song must be stored in a dedicated playlist owned by the user named `Liked Songs` |
| M-12 | Must have | F | **Search Service** must support text search over songs with filters by genre, BPM, and year | **Endpoint**: `GET /search?q=&genre=&bpm_min=&bpm_max=&year=` |
| M-13 | Must have | F | **Analytics Service** must persist listen history per user across sessions | **Endpoint**: `GET /analytics/me/history` Listen history must be tied to authenticated users and remain available after service restarts if the database persists |
| M-14 | Must have | F | **Analytics Service** must aggregate playback events into chart data | It must be able to compute at least global play-count-based rankings from ingested data and/or emitted playback events |
| M-15 | Must have | F | **Recommendation Service** must expose personalised and song-based recommendation endpoints | Minimum **endpoints**: `GET /recommend/daily-mix`, `GET /recommend/similar/:songId` |
| M-16 | Must have | F | **Recommendation Service** must consume playback events and return non-empty recommendation responses for valid requests | |
| M-17 | Must have | F | **Notification Service** must consume internal events and persist in-app notifications retrievable from its own storage | The service is internal only and must not implement email or push delivery. It must consume at least playlist update events or new release events and store resulting in-app notifications |
| M-18 | Must have | NF | All 8 application services must communicate over a shared named Docker network. | A single named Docker network must be configured for inter-service communication in Docker Compose |
| M-19 | Must have | NF | A single `docker-compose.yml` must start all 8 application services and the required infrastructure | Must include the application services, their required persistence components, the message broker, object storage if needed, the monitoring stack, and the load generator |
| M-20 | Must have | NF | CPU and memory limits must be configurable per service in `docker-compose.yml` | Resource limits must be defined per container or service and be easy to modify for benchmarking scenarios |
| M-21 | Must have | F | A load generator must simulate the main user flows of the application | The workload must cover registration, login, catalog browsing, search, streaming requests, playlist operations, and history queries |
| M-22 | Must have | NF | Inter-service HTTP calls must implement retry with exponential backoff | Retry behavior must be bounded and documented |
| M-23 | Must have | NF | Inter-service HTTP calls must implement a circuit breaker or equivalent failure-isolation mechanism | A slow or failing downstream service must not cascade-fail the rest of the system |
| M-24 | Must have | NF | Each service must expose metrics suitable for Prometheus scraping, and Prometheus must be configured to scrape those metrics | Metrics must be available and collected for execution monitoring and load testing through the monitoring stack started by `docker-compose.yml` |
| M-25 | Must have | NF | All protected endpoints must require JWT authentication | Only `POST /auth/register` and `POST /auth/login` may be publicly accessible |
| M-26 | Must have | NF | Each application service that persists state must use its own dedicated persistence layer | Shared application databases are not allowed; service data isolation must be preserved |
| S-01 | Should have | F | **Catalog Service** should support artist pages and top tracks | **Endpoint**: `GET /catalog/artists/:id/top-tracks` |
| S-02 | Should have | F | **Analytics Service** should expose a global top 50 chart endpoint | **Endpoint**: `GET /analytics/charts/global` |
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
| W-03 | Will not have | F | Real audio playback files will not be required | The system simulates streaming behavior and load using metadata and generated payloads only |