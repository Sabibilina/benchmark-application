import { toReorderPayload, moveTrack } from "../features/playlists/playlistDnD";
import type { PlaylistTrack } from "../types/playlist";

describe("playlist reorder", () => {
  const tracks: PlaylistTrack[] = [
    { id: "1", songId: "song-1", position: 0, addedAt: "2026-01-01T00:00:00Z" },
    { id: "2", songId: "song-2", position: 1, addedAt: "2026-01-01T00:00:00Z" }
  ];

  it("creates reorder payload in displayed order", () => {
    const moved = moveTrack(tracks, "song-2", "song-1");
    expect(toReorderPayload(moved)).toEqual(["song-2", "song-1"]);
  });
});
