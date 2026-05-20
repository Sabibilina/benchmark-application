# Music Streaming Frontend

React 18 + TypeScript SPA built with Vite, connecting to the 8-service Docker Compose backend.

## Prerequisites

- Node.js 20+
- All backend services running via `docker compose up`

## Setup

```bash
cd frontend
npm install
```

Copy the env file (optional — the Vite dev proxy handles routing without it):

```bash
cp .env.example .env
```

## Development

```bash
npm run dev
```

Opens at http://localhost:3000. The Vite dev proxy forwards all `/api/*` calls to the correct backend service — no CORS issues.

## Production build

```bash
npm run build        # outputs to dist/
npm run preview      # serves dist/ locally for smoke-testing
```

The `Dockerfile` in this directory builds the final image (Nginx serving the `dist/` folder).

## Route map

| Path              | View             | Auth required |
|-------------------|------------------|---------------|
| /                 | Home             | Yes           |
| /search           | Search           | Yes           |
| /catalog          | Catalog          | Yes           |
| /playlists        | Playlists list   | Yes           |
| /playlists/:id    | Playlist detail  | Yes           |
| /history          | Listening history| Yes           |
| /health           | Health check     | Yes           |
| /login            | Login            | No            |
| /register         | Register         | No            |

## Environment variables

| Variable                 | Default                  | Description               |
|--------------------------|--------------------------|---------------------------|
| VITE_AUTH_URL            | http://localhost:8081    | Auth service base URL     |
| VITE_CATALOG_URL         | http://localhost:8082    | Catalog service base URL  |
| VITE_STREAMING_URL       | http://localhost:8083    | Streaming service base URL|
| VITE_PLAYLIST_URL        | http://localhost:8084    | Playlist service base URL |
| VITE_SEARCH_URL          | http://localhost:8085    | Search service base URL   |
| VITE_ANALYTICS_URL       | http://localhost:8086    | Analytics service base URL|
| VITE_RECOMMENDATION_URL  | http://localhost:8087    | Recommendation service URL|
| VITE_NOTIFICATION_URL    | http://localhost:8088    | Notification service URL  |

## Client-side metrics

Exposed at `window.__metrics` for scraping or forwarding:

```ts
{
  pageLoadMs: number        // Navigation timing
  apiErrors: Record<string, number>   // Error count keyed by service base URL
  playbackFailures: number  // Count of failed /stream calls
}
```
