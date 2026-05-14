import type { PlaylistTrack } from "../../types/playlist";

export function moveTrack(tracks: PlaylistTrack[], activeSongId: string, overSongId: string): PlaylistTrack[] {
  const from = tracks.findIndex((track) => track.songId === activeSongId);
  const to = tracks.findIndex((track) => track.songId === overSongId);
  if (from < 0 || to < 0 || from === to) {
    return tracks;
  }
  const copy = [...tracks];
  const [item] = copy.splice(from, 1);
  copy.splice(to, 0, item);
  return copy.map((track, index) => ({ ...track, position: index }));
}

export function toReorderPayload(tracks: PlaylistTrack[]): string[] {
  return [...tracks].sort((a, b) => a.position - b.position).map((track) => track.songId);
}
