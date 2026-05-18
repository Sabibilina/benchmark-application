import { http, HttpResponse } from "msw";
import { fetchGlobalCharts, fetchHistory } from "../api/analyticsApi";
import { fetchSong, fetchSongs } from "../api/catalogApi";
import { createPlaylist, fetchPlaylist, fetchPlaylists, reorderPlaylist } from "../api/playlistApi";
import { fetchDailyMix, fetchSimilar } from "../api/recommendationApi";
import { server } from "../test/server";

describe("feature api modules", () => {
  it("passes catalog pagination and song id parameters", async () => {
    server.use(
      http.get("http://localhost:8082/catalog/songs/song-42", () => HttpResponse.json({
        id: "song-42",
        title: "Song Forty Two",
        artist: "Artist",
        genre: "jazz",
        releaseYear: 2021
      }))
    );

    const songs = await fetchSongs(2, 5);
    const song = await fetchSong("song-42");

    expect(songs.page).toBe(0);
    expect(songs.size).toBe(12);
    expect(song.id).toBe("song-42");
  });

  it("wraps playlist list, detail, create, and reorder calls", async () => {
    server.use(
      http.post("http://localhost:8084/playlists", async ({ request }) => {
        const body = await request.json() as { name: string; description?: string };
        return HttpResponse.json({
          id: "playlist-created",
          ownerUserId: "user-1",
          name: body.name,
          description: body.description,
          likedSongs: false,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          tracks: []
        });
      })
    );

    const playlists = await fetchPlaylists();
    const playlist = await fetchPlaylist("playlist-1");
    const created = await createPlaylist({ name: "Focus", description: "Deep work" });
    const reordered = await reorderPlaylist("playlist-1", ["song-2", "song-1"]);

    expect(playlists[0].likedSongs).toBe(true);
    expect(playlist.name).toBe("Road Trip");
    expect(created.id).toBe("playlist-created");
    expect(reordered.tracks.map((track) => track.songId)).toEqual(["song-2", "song-1"]);
  });

  it("wraps analytics and recommendation calls", async () => {
    server.use(
      http.get("http://localhost:8087/recommend/similar/song-1", () => HttpResponse.json({
        type: "similar",
        recommendations: [{ songId: "song-2", rank: 1, reason: "users also played" }]
      }))
    );

    const history = await fetchHistory();
    const charts = await fetchGlobalCharts();
    const dailyMix = await fetchDailyMix();
    const similar = await fetchSimilar("song-1");

    expect(history.content[0].type).toBe("play.started");
    expect(charts[0].rank).toBe(1);
    expect(dailyMix.type).toBe("daily-mix");
    expect(similar.recommendations[0].songId).toBe("song-2");
  });
});
