import { searchClient } from './client'
import type { SongSearchResult } from '../types'

export interface SearchParams {
  q?: string
  genre?: string
  bpm_min?: number
  bpm_max?: number
  year?: number
}

export const searchApi = {
  search(params: SearchParams) {
    return searchClient.get<SongSearchResult[]>('/search', { params })
  },
}
