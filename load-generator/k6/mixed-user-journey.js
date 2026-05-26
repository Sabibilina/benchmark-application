import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.K6_START_RATE || 5),
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.K6_PREALLOCATED_VUS || 50),
      maxVUs: Number(__ENV.K6_MAX_VUS || 250),
      stages: [
        { target: Number(__ENV.K6_TARGET_RATE || 25), duration: __ENV.K6_RAMP_DURATION || '2m' },
        { target: Number(__ENV.K6_TARGET_RATE || 25), duration: __ENV.K6_HOLD_DURATION || '5m' },
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';
const password = 'CorrectHorse123';

function authHeaders() {
  const email = `k6-user-${__VU}@example.com`;
  const payload = JSON.stringify({ email, password });
  http.post(`${baseUrl}/auth/register`, payload, { headers: { 'Content-Type': 'application/json' } });
  const login = http.post(`${baseUrl}/auth/login`, payload, { headers: { 'Content-Type': 'application/json' } });
  const token = login.json('accessToken');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export default function () {
  const headers = authHeaders();
  const songId = __ENV.K6_SONG_ID || 'spotify:track:benchmark';

  const catalog = http.get(`${baseUrl}/catalog/songs?page=0&size=20`, { headers });
  check(catalog, { 'catalog browse ok': (response) => response.status === 200 });

  const search = http.get(`${baseUrl}/search?q=love&genre=pop&size=20`, { headers });
  check(search, { 'search ok': (response) => response.status === 200 });

  const stream = http.get(`${baseUrl}/stream/${encodeURIComponent(songId)}`, { headers });
  check(stream, { 'stream descriptor ok': (response) => response.status === 200 });

  http.get(`${baseUrl}/analytics/me/history?page=0&size=10`, { headers });
  http.get(`${baseUrl}/analytics/charts/global?limit=10`, { headers });
  http.get(`${baseUrl}/recommend/daily-mix?limit=10`, { headers });

  sleep(Number(__ENV.K6_USER_SLEEP_SECONDS || 1));
}
