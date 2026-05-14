export interface Song {
  id: string;
  title: string;
  artist: string;
  album?: string | null;
  genre?: string | null;
  bpm?: number | null;
  releaseYear?: number | null;
  releaseDate?: string | null;
  popularity?: number | null;
  durationMs?: number | null;
  metadata?: Record<string, string>;
}

export interface SongPage {
  content: Song[];
  page: number;
  size: number;
  totalElements: number;
  totalPages?: number;
}
