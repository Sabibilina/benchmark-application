import { http, HttpResponse } from "msw";

export const handlers = [
  http.post("http://localhost:8081/auth/login", async () => HttpResponse.json({
    accessToken: "test-token",
    tokenType: "Bearer",
    user: { id: "user-1", email: "test@example.com" }
  })),
  http.post("http://localhost:8081/auth/register", async () => HttpResponse.json({
    accessToken: "test-token",
    tokenType: "Bearer",
    user: { id: "user-1", email: "test@example.com" }
  })),
  http.get("http://localhost:8085/search", ({ request }) => {
    const url = new URL(request.url);
    return HttpResponse.json({
      content: [{
        id: "song-1",
        title: url.searchParams.get("q") || "Song One",
        artist: "Artist",
        genre: url.searchParams.get("genre"),
        bpm: Number(url.searchParams.get("bpm_min") ?? 120),
        releaseYear: Number(url.searchParams.get("year") ?? 2020),
        score: 1
      }],
      totalElements: 1,
      page: 0,
      size: 12
    });
  }),
  http.get("http://localhost:8082/catalog/songs", () => HttpResponse.json({
    content: [{ id: "song-1", title: "Song One", artist: "Artist", genre: "Pop", releaseYear: 2020 }],
    page: 0,
    size: 12,
    totalElements: 1,
    totalPages: 1
  })),
  http.get("http://localhost:8083/stream/song-1", () => HttpResponse.json({
    songId: "song-1",
    descriptorType: "HLS",
    segmentCount: 2,
    segmentSizeBytes: 1024,
    issuedAt: "2026-01-01T00:00:00Z",
    segments: [],
    endedUrl: "/stream/song-1/ended",
    skippedUrl: "/stream/song-1/skipped"
  })),
  http.post("http://localhost:8083/stream/song-1/ended", () => new HttpResponse(null, { status: 204 })),
  http.post("http://localhost:8083/stream/song-1/skipped", () => new HttpResponse(null, { status: 204 })),
  http.get("http://localhost:8084/playlists", () => HttpResponse.json([
    { id: "liked", ownerUserId: "user-1", name: "Liked Songs", description: "", likedSongs: true, trackCount: 0, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }
  ])),
  http.get("http://localhost:8084/playlists/playlist-1", () => HttpResponse.json({
    id: "playlist-1",
    ownerUserId: "user-1",
    name: "Road Trip",
    description: "",
    likedSongs: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    tracks: [
      { id: "track-1", songId: "song-1", position: 0, addedAt: "2026-01-01T00:00:00Z" },
      { id: "track-2", songId: "song-2", position: 1, addedAt: "2026-01-01T00:00:00Z" }
    ]
  })),
  http.patch("http://localhost:8084/playlists/playlist-1/tracks/reorder", async ({ request }) => HttpResponse.json({
    id: "playlist-1",
    ownerUserId: "user-1",
    name: "Road Trip",
    description: "",
    likedSongs: false,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    tracks: (await request.json() as { songIds: string[] }).songIds.map((songId, position) => ({ id: `track-${position}`, songId, position, addedAt: "2026-01-01T00:00:00Z" }))
  })),
  http.get("http://localhost:8086/analytics/me/history", () => HttpResponse.json({
    content: [{ eventId: "event-1", type: "play.started", userId: "user-1", songId: "song-1", timestamp: "2026-01-01T00:00:00Z" }],
    totalElements: 1,
    page: 0,
    size: 50
  })),
  http.get("http://localhost:8086/analytics/charts/global", () => HttpResponse.json([{ songId: "song-1", playCount: 10, rank: 1 }])),
  http.get("http://localhost:8087/recommend/daily-mix", () => HttpResponse.json({ type: "daily-mix", recommendations: [{ songId: "song-1", rank: 1, reason: "Recent listens" }] }))
];
