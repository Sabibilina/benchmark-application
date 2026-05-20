import { catalogClient } from './client'
import type { PagedSongs, Song } from '../types'

export const catalogApi = {
  getSongs(page = 0, size = 20, sort?: string) {
    return catalogClient.get<PagedSongs>('/catalog/songs', {
      params: { page, size, ...(sort ? { sort } : {}) },
    })
  },
  getSong(id: string | number) {
    return catalogClient.get<Song>(`/catalog/songs/${id}`)
  },
  getArtistTopTracks(artistId: string) {
    return catalogClient.get<Song[]>(`/catalog/artists/${artistId}/top-tracks`)
  },
}
