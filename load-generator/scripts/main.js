import http from 'k6/http';
import { sleep, check } from 'k6';

// Service base URLs — injected from docker-compose environment
const AUTH_URL      = __ENV.AUTH_SERVICE_URL      || 'http://auth-service:8080';
const CATALOG_URL   = __ENV.CATALOG_SERVICE_URL   || 'http://catalog-service:8080';
const STREAMING_URL = __ENV.STREAMING_SERVICE_URL || 'http://streaming-service:8080';
const PLAYLIST_URL  = __ENV.PLAYLIST_SERVICE_URL  || 'http://playlist-service:8080';
const SEARCH_URL    = __ENV.SEARCH_SERVICE_URL    || 'http://search-service:8080';
const ANALYTICS_URL = __ENV.ANALYTICS_SERVICE_URL || 'http://analytics-service:8080';
const RECOMMEND_URL = __ENV.RECOMMENDATION_SERVICE_URL || 'http://recommendation-service:8080';

// k6 reads K6_VUS and K6_DURATION directly from the process environment and
// overrides these defaults, so the values here are fallbacks only.
export const options = {
  vus: 10,
  duration: '60s',
};

// Placeholder scenario — full implementation in Phase 10.
// Each virtual user runs through the main application flows:
//   register → login → browse catalog → search → stream → playlist ops → history
export default function () {
  // Health probe on all services (Phase 0 skeleton — flows added in Phase 10)
  const services = [
    { name: 'auth',           url: AUTH_URL },
    { name: 'catalog',        url: CATALOG_URL },
    { name: 'streaming',      url: STREAMING_URL },
    { name: 'playlist',       url: PLAYLIST_URL },
    { name: 'search',         url: SEARCH_URL },
    { name: 'analytics',      url: ANALYTICS_URL },
    { name: 'recommendation', url: RECOMMEND_URL },
  ];

  for (const svc of services) {
    const res = http.get(`${svc.url}/actuator/health`);
    check(res, {
      [`${svc.name} health 200`]: (r) => r.status === 200,
    });
  }

  sleep(1);
}
