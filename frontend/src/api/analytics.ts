import { analyticsClient } from './client'
import type { ChartEntry, HistoryEntry } from '../types'

export const analyticsApi = {
  getHistory() {
    return analyticsClient.get<HistoryEntry[]>('/analytics/me/history')
  },
  getGlobalCharts() {
    return analyticsClient.get<ChartEntry[]>('/analytics/charts/global')
  },
}
