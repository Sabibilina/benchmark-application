export interface PlaylistTrack {
  id: string;
  songId: string;
  position: number;
  addedAt: string;
}

export interface PlaylistSummary {
  id: string;
  ownerUserId: string;
  name: string;
  description?: string | null;
  likedSongs: boolean;
  trackCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Playlist extends Omit<PlaylistSummary, "trackCount"> {
  tracks: PlaylistTrack[];
}

export interface PlaylistFormValues {
  name: string;
  description?: string;
}
