import { streamingClient } from './client'

export const streamingApi = {
  /** Returns raw HLS manifest text */
  getStream(songId: string | number) {
    return streamingClient.get<string>(`/stream/${songId}`, {
      responseType: 'text',
    })
  },
  complete(songId: string | number) {
    return streamingClient.post(`/stream/${songId}/complete`)
  },
  skip(songId: string | number) {
    return streamingClient.post(`/stream/${songId}/skip`)
  },
}
