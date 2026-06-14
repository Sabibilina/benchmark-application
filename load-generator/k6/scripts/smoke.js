import http from 'k6/http';
import { check, sleep } from 'k6';
import { summaryOutputs } from './cost-summary.js';

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';

export const options = {
  vus: Number(__ENV.K6_SMOKE_VUS || 5),
  duration: __ENV.K6_SMOKE_DURATION || '30s',
  thresholds: {
    http_req_failed: [`rate<${__ENV.K6_HTTP_FAIL_RATE || 0.05}`],
    http_req_duration: [`p(95)<${__ENV.K6_SMOKE_HTTP_REQ_DURATION_P95_MS || 2000}`],
  },
};

function jsonHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const credentials = {
    email: `smoke-${suffix}@example.com`,
    password: 'CorrectHorse123',
  };

  const register = http.post(`${baseUrl}/auth/register`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(register, { 'register accepted': (response) => response.status === 201 || response.status === 409 });

  const login = http.post(`${baseUrl}/auth/login`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(login, { 'login ok': (response) => response.status === 200 && response.json('accessToken') });

  const token = login.json('accessToken');
  if (!token) {
    return;
  }

  const authHeaders = { Authorization: `Bearer ${token}` };
  const songId = `spotify:track:benchmark-${__VU}`;
  const encodedSongId = encodeURIComponent(songId);

  check(http.get(`${baseUrl}/catalog/songs?page=0&size=10`, { headers: authHeaders }), {
    'catalog ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/search?q=love&size=10`, { headers: authHeaders }), {
    'search ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/recommend/daily-mix`, { headers: authHeaders }), {
    'recommend ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/stream/${encodedSongId}`, { headers: authHeaders }), {
    'stream ok': (response) => response.status === 200,
  });
  check(http.post(`${baseUrl}/stream/${encodedSongId}/ended`, null, { headers: authHeaders }), {
    'stream ended ok': (response) => response.status === 202,
  });

  const playlist = http.post(
    `${baseUrl}/playlists`,
    JSON.stringify({ name: `k6 smoke ${suffix}` }),
    { headers: jsonHeaders(token) },
  );
  check(playlist, { 'playlist create ok': (response) => response.status === 201 });

  const playlistId = playlist.json('id');
  if (playlistId) {
    check(http.post(
      `${baseUrl}/playlists/${playlistId}/tracks`,
      JSON.stringify({ songId }),
      { headers: jsonHeaders(token) },
    ), {
      'playlist add track ok': (response) => response.status === 201 || response.status === 204,
    });
  }

  check(http.get(`${baseUrl}/analytics/me/history?page=0&size=10`, { headers: authHeaders }), {
    'history ok': (response) => response.status === 200,
  });

  sleep(1);
}

export function handleSummary(data) {
  return summaryOutputs(data, 'smoke.js', 'smoke-cost-summary.json');
}
