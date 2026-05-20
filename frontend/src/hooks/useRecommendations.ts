import { useQuery } from '@tanstack/react-query'
import { recommendApi } from '../api/recommendations'

export function useDailyMix() {
  return useQuery({
    queryKey: ['recommend', 'daily-mix'],
    queryFn: () => recommendApi.getDailyMix().then((r) => r.data),
  })
}

export function useSimilarSongs(songId: string | number | undefined) {
  return useQuery({
    queryKey: ['recommend', 'similar', songId],
    queryFn: () => recommendApi.getSimilar(songId!).then((r) => r.data),
    enabled: songId != null,
  })
}
