# Frontend

React TypeScript SPA for the benchmark music application.

## Runtime

The app is built with Vite and served from the Docker image on port `5173`.

```powershell
npm install
npm run typecheck
npm test
npm run build
docker compose build frontend
docker compose up -d frontend
```

Open `http://localhost:5173`.

## Configuration

Copy `.env.example` if local overrides are needed. The frontend uses browser-reachable service URLs:

- `VITE_AUTH_API_BASE_URL`
- `VITE_CATALOG_API_BASE_URL`
- `VITE_PLAYLIST_API_BASE_URL`
- `VITE_STREAMING_API_BASE_URL`
- `VITE_SEARCH_API_BASE_URL`
- `VITE_ANALYTICS_API_BASE_URL`
- `VITE_RECOMMENDATION_API_BASE_URL`
- `VITE_NOTIFICATION_API_BASE_URL`
- `VITE_API_RETRY_ATTEMPTS`
- `VITE_API_RETRY_BASE_DELAY_MS`
- `VITE_PLAYBACK_PROGRESS_INTERVAL_MS`

JWT access tokens are kept in memory only and are not written to `localStorage`.

## Scope

Implemented views include authentication, home/discovery, search, catalog, playlists, listening history, persistent player bar, and `/health`.

The notification inbox remains non-fetching because the current Notification Service has no protected read interface.
