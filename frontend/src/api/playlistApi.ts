import type { Playlist, PlaylistFormValues, PlaylistSummary } from "../types/playlist";
import { api } from "./client";

export async function fetchPlaylists(): Promise<PlaylistSummary[]> {
  const response = await api.playlist.get<PlaylistSummary[]>("/playlists");
  return response.data;
}

export async function fetchPlaylist(id: string): Promise<Playlist> {
  const response = await api.playlist.get<Playlist>(`/playlists/${id}`);
  return response.data;
}

export async function createPlaylist(values: PlaylistFormValues): Promise<Playlist> {
  const response = await api.playlist.post<Playlist>("/playlists", values);
  return response.data;
}

export async function reorderPlaylist(id: string, songIds: string[]): Promise<Playlist> {
  const response = await api.playlist.patch<Playlist>(`/playlists/${id}/tracks/reorder`, { songIds });
  return response.data;
}
