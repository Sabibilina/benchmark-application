import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import axiosRetry from 'axios-retry'
import type { ApiError } from '../types'

// ── Metrics bootstrap ─────────────────────────────────────────────────────────

if (!window.__metrics) {
  window.__metrics = {
    pageLoadMs: performance.now(),
    apiErrors: {},
    playbackFailures: 0,
  }
}

function recordApiError(baseURL: string) {
  const key = baseURL || 'unknown'
  window.__metrics.apiErrors[key] = (window.__metrics.apiErrors[key] ?? 0) + 1
}

// ── Token accessor (populated by authStore after login) ───────────────────────

let _getToken: (() => string | null) = () => null
let _onUnauthorized: (() => void) = () => {}

export function configureClientAuth(
  getToken: () => string | null,
  onUnauthorized: () => void,
) {
  _getToken = getToken
  _onUnauthorized = onUnauthorized
}

// ── Factory ───────────────────────────────────────────────────────────────────

function makeClient(baseURL: string): AxiosInstance {
  const client = axios.create({ baseURL, timeout: 15_000 })

  axiosRetry(client, {
    retries: 3,
    retryDelay: (count) => 300 * Math.pow(2, count - 1),
    retryCondition: (err) => {
      const status = err.response?.status ?? 0
      return axiosRetry.isNetworkError(err) || (status >= 500 && status < 600)
    },
  })

  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = _getToken()
    if (token) {
      config.headers = config.headers ?? {}
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(
    (res) => res,
    (err: AxiosError) => {
      const status = err.response?.status ?? 0

      if (status >= 400) {
        recordApiError(baseURL)
      }

      if (status === 401) {
        _onUnauthorized()
      }

      const normalized: ApiError = {
        status,
        message:
          (err.response?.data as Record<string, string>)?.message ??
          err.message ??
          'Unknown error',
        detail: err.response?.data,
      }

      return Promise.reject(normalized)
    },
  )

  return client
}

// ── One client per service ────────────────────────────────────────────────────

const resolve = (envKey: string, fallback: string) =>
  (import.meta.env[envKey] as string | undefined) ?? fallback

export const authClient         = makeClient(resolve('VITE_AUTH_URL',          '/api/auth'))
export const catalogClient      = makeClient(resolve('VITE_CATALOG_URL',       '/api/catalog'))
export const streamingClient    = makeClient(resolve('VITE_STREAMING_URL',     '/api/stream'))
export const playlistClient     = makeClient(resolve('VITE_PLAYLIST_URL',      '/api/playlists'))
export const searchClient       = makeClient(resolve('VITE_SEARCH_URL',        '/api/search'))
export const analyticsClient    = makeClient(resolve('VITE_ANALYTICS_URL',     '/api/analytics'))
export const recommendClient    = makeClient(resolve('VITE_RECOMMENDATION_URL', '/api/recommend'))
export const notificationClient = makeClient(resolve('VITE_NOTIFICATION_URL',  '/api/notifications'))
