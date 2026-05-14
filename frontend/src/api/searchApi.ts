import type { Song } from "../types/catalog";
import { api } from "./client";

export interface SearchFilters {
  q?: string;
  genre?: string;
  bpmMin?: number;
  bpmMax?: number;
  year?: number;
  page?: number;
  size?: number;
}

export interface SearchPage {
  content: Song[];
  totalElements: number;
  page: number;
  size: number;
}

export function toSearchParams(filters: SearchFilters): Record<string, string | number> {
  return {
    ...(filters.q ? { q: filters.q } : {}),
    ...(filters.genre ? { genre: filters.genre } : {}),
    ...(filters.bpmMin != null ? { bpm_min: filters.bpmMin } : {}),
    ...(filters.bpmMax != null ? { bpm_max: filters.bpmMax } : {}),
    ...(filters.year != null ? { year: filters.year } : {}),
    page: filters.page ?? 0,
    size: filters.size ?? 12
  };
}

export async function searchSongs(filters: SearchFilters): Promise<SearchPage> {
  const response = await api.search.get<SearchPage>("/search", { params: toSearchParams(filters) });
  return response.data;
}
