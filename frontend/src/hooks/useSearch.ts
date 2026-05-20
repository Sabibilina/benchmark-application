import { useQuery } from '@tanstack/react-query'
import { searchApi, type SearchParams } from '../api/search'

export function useSearch(params: SearchParams, enabled = true) {
  return useQuery({
    queryKey: ['search', params],
    queryFn: () => searchApi.search(params).then((r) => r.data),
    enabled: enabled && !!(params.q || params.genre || params.bpm_min || params.bpm_max || params.year),
    placeholderData: (prev) => prev,
  })
}
