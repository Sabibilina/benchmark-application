import type { Song, SongPage } from "../types/catalog";
import { api } from "./client";

export async function fetchSongs(page = 0, size = 12): Promise<SongPage> {
  const response = await api.catalog.get<SongPage>("/catalog/songs", { params: { page, size } });
  return response.data;
}

export async function fetchSong(id: string): Promise<Song> {
  const response = await api.catalog.get<Song>(`/catalog/songs/${id}`);
  return response.data;
}
