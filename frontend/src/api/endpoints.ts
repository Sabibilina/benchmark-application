const env = import.meta.env;

export const endpoints = {
  auth: env.VITE_AUTH_API_BASE_URL ?? "http://localhost:8081",
  catalog: env.VITE_CATALOG_API_BASE_URL ?? "http://localhost:8082",
  streaming: env.VITE_STREAMING_API_BASE_URL ?? "http://localhost:8083",
  playlist: env.VITE_PLAYLIST_API_BASE_URL ?? "http://localhost:8084",
  search: env.VITE_SEARCH_API_BASE_URL ?? "http://localhost:8085",
  analytics: env.VITE_ANALYTICS_API_BASE_URL ?? "http://localhost:8086",
  recommendation: env.VITE_RECOMMENDATION_API_BASE_URL ?? "http://localhost:8087",
  notification: env.VITE_NOTIFICATION_API_BASE_URL ?? ""
};

export const retryConfig = {
  attempts: Number(env.VITE_API_RETRY_ATTEMPTS ?? 3),
  baseDelayMs: Number(env.VITE_API_RETRY_BASE_DELAY_MS ?? 250)
};
