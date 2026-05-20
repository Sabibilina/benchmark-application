// ── Song ─────────────────────────────────────────────────────────────────────

export interface Song {
  id: number
  trackId: string
  title: string
  artist: string
  artistId?: string
  album?: string
  releaseDate?: string
  year?: number
  genre?: string
  durationMs?: number
  popularity?: number
  danceability?: number
  energy?: number
  musicalKey?: number
  loudness?: number
  mode?: number
  instrumentalness?: number
  /** BPM — named "tempo" in the catalog and recommendation services */
  tempo?: number
  streamCount?: number
  country?: string
  explicit?: boolean
  label?: string
}

export interface PagedSongs {
  content: Song[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

// ── Search ────────────────────────────────────────────────────────────────────

export interface SongSearchResult {
  songId: number
  trackId: string
  title: string
  artist: string
  album?: string
  genre?: string
  year?: number
  /** BPM — named "bpm" in the search service */
  bpm?: number
  popularity?: number
  durationMs?: number
  danceability?: number
  energy?: number
  explicit?: boolean
  country?: string
  label?: string
}

// ── Recommendations ───────────────────────────────────────────────────────────

export interface SongRecommendation {
  id: number
  title: string
  artist: string
  genre?: string
  tempo?: number
}

export interface DailyMixResponse {
  songs: SongRecommendation[]
}

export interface SimilarSongsResponse {
  seedSongId: number
  songs: SongRecommendation[]
}

// ── Playlists ─────────────────────────────────────────────────────────────────

export interface PlaylistTrack {
  songId: string
  position: number
  addedAt: string
}

export interface Playlist {
  id: string
  name: string
  likedSongs: boolean
  tracks: PlaylistTrack[]
  createdAt: string
  updatedAt: string
}

export interface PlaylistSummary {
  id: string
  name: string
  trackCount: number
  likedSongs: boolean
  createdAt: string
  updatedAt: string
}

// ── Analytics ─────────────────────────────────────────────────────────────────

export interface HistoryEntry {
  songId: string
  eventType: string
  timestamp: string
}

export interface ChartEntry {
  rank: number
  songId: string
  playCount: number
}

// ── Notifications ─────────────────────────────────────────────────────────────

export interface Notification {
  id: string
  type: string
  title: string
  message: string
  referenceId?: string
  read: boolean
  createdAt: string
}

// ── Auth ──────────────────────────────────────────────────────────────────────

export interface AuthResponse {
  token: string
}

export interface User {
  username: string
  email: string
}

// ── API error ─────────────────────────────────────────────────────────────────

export interface ApiError {
  status: number
  message: string
  detail?: unknown
}

// ── Metrics (window.__metrics) ────────────────────────────────────────────────

export interface ClientMetrics {
  pageLoadMs: number
  apiErrors: Record<string, number>
  playbackFailures: number
}

declare global {
  interface Window {
    __metrics: ClientMetrics
  }
}
