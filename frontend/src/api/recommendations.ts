import { recommendClient } from './client'
import type { DailyMixResponse, SimilarSongsResponse } from '../types'

export const recommendApi = {
  getDailyMix() {
    return recommendClient.get<DailyMixResponse>('/recommend/daily-mix')
  },
  getSimilar(songId: string | number) {
    return recommendClient.get<SimilarSongsResponse>(`/recommend/similar/${songId}`)
  },
}
