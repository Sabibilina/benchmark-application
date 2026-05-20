import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { playlistApi } from '../api/playlists'

export function usePlaylists() {
  return useQuery({
    queryKey: ['playlists'],
    queryFn: () => playlistApi.getAll().then((r) => r.data),
  })
}

export function usePlaylist(id: string | undefined) {
  return useQuery({
    queryKey: ['playlists', id],
    queryFn: () => playlistApi.getOne(id!).then((r) => r.data),
    enabled: !!id,
  })
}

export function useCreatePlaylist() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ name, description }: { name: string; description?: string }) =>
      playlistApi.create(name, description).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['playlists'] }),
  })
}

export function useUpdatePlaylist() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name, description }: { id: string; name?: string; description?: string }) =>
      playlistApi.update(id, name, description).then((r) => r.data),
    onSuccess: (_, { id }) => {
      qc.invalidateQueries({ queryKey: ['playlists'] })
      qc.invalidateQueries({ queryKey: ['playlists', id] })
    },
  })
}

export function useDeletePlaylist() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => playlistApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['playlists'] }),
  })
}

export function useAddTrack() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ playlistId, songId }: { playlistId: string; songId: string }) =>
      playlistApi.addTrack(playlistId, songId).then((r) => r.data),
    onSuccess: (_, { playlistId }) => {
      qc.invalidateQueries({ queryKey: ['playlists'] })
      qc.invalidateQueries({ queryKey: ['playlists', playlistId] })
    },
  })
}

export function useRemoveTrack() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ playlistId, songId }: { playlistId: string; songId: string }) =>
      playlistApi.removeTrack(playlistId, songId),
    onSuccess: (_, { playlistId }) => {
      qc.invalidateQueries({ queryKey: ['playlists'] })
      qc.invalidateQueries({ queryKey: ['playlists', playlistId] })
    },
  })
}

export function useReorderTracks() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ playlistId, orderedSongIds }: { playlistId: string; orderedSongIds: string[] }) =>
      playlistApi.reorder(playlistId, orderedSongIds).then((r) => r.data),
    onSuccess: (_, { playlistId }) => {
      qc.invalidateQueries({ queryKey: ['playlists', playlistId] })
    },
  })
}
