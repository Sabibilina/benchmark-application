import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '../api/catalog'

export function useCatalogSongs(page: number, size: number, sort?: string) {
  return useQuery({
    queryKey: ['catalog', 'songs', { page, size, sort }],
    queryFn: () => catalogApi.getSongs(page, size, sort).then((r) => r.data),
  })
}

export function useSong(id: string | number | undefined) {
  return useQuery({
    queryKey: ['catalog', 'song', id],
    queryFn: () => catalogApi.getSong(id!).then((r) => r.data),
    enabled: id != null,
  })
}

export function useArtistTopTracks(artistId: string | undefined) {
  return useQuery({
    queryKey: ['catalog', 'artist', artistId, 'top-tracks'],
    queryFn: () => catalogApi.getArtistTopTracks(artistId!).then((r) => r.data),
    enabled: !!artistId,
  })
}
