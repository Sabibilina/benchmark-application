import { playlistClient } from './client'
import type { Playlist, PlaylistSummary } from '../types'

export const playlistApi = {
  getAll() {
    return playlistClient.get<PlaylistSummary[]>('/playlists')
  },
  create(name: string, description?: string) {
    return playlistClient.post<Playlist>('/playlists', { name, description })
  },
  getOne(id: string) {
    return playlistClient.get<Playlist>(`/playlists/${id}`)
  },
  update(id: string, name?: string, description?: string) {
    return playlistClient.patch<Playlist>(`/playlists/${id}`, { name, description })
  },
  delete(id: string) {
    return playlistClient.delete(`/playlists/${id}`)
  },
  addTrack(playlistId: string, songId: string) {
    return playlistClient.post<Playlist>(`/playlists/${playlistId}/tracks`, { songId })
  },
  removeTrack(playlistId: string, songId: string) {
    return playlistClient.delete(`/playlists/${playlistId}/tracks/${songId}`)
  },
  reorder(playlistId: string, orderedSongIds: string[]) {
    return playlistClient.patch<Playlist>(`/playlists/${playlistId}/tracks/reorder`, {
      orderedSongIds,
    })
  },
}
