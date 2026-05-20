import { useQuery } from '@tanstack/react-query'
import { analyticsApi } from '../api/analytics'

export function useHistory() {
  return useQuery({
    queryKey: ['analytics', 'history'],
    queryFn: () => analyticsApi.getHistory().then((r) => r.data),
  })
}

export function useGlobalCharts() {
  return useQuery({
    queryKey: ['analytics', 'charts', 'global'],
    queryFn: () => analyticsApi.getGlobalCharts().then((r) => r.data),
  })
}
