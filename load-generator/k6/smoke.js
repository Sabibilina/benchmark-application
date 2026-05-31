import http from 'k6/http';
import { check, sleep } from 'k6';

const durationP95Ms = Number(__ENV.K6_SMOKE_HTTP_REQ_DURATION_P95_MS || 2000);

export const options = {
  vus: Number(__ENV.K6_VUS || 5),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: [`p(95)<${durationP95Ms}`],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';

function jsonAuthHeaders(authHeaders) {
  return Object.assign({ 'Content-Type': 'application/json' }, authHeaders);
}

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const credentials = {
    email: `k6-${suffix}@example.com`,
    password: 'CorrectHorse123',
  };

  const register = http.post(`${baseUrl}/auth/register`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(register, { 'register accepted': (response) => response.status === 201 || response.status === 409 });

  const login = http.post(`${baseUrl}/auth/login`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(login, { 'login ok': (response) => response.status === 200 });

  const token = login.json('accessToken');
  const authHeaders = { Authorization: `Bearer ${token}` };

  check(http.get(`${baseUrl}/catalog/songs?page=0&size=10`, { headers: authHeaders }), {
    'catalog ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/search?q=love&size=10`, { headers: authHeaders }), {
    'search ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/recommend/daily-mix`, { headers: authHeaders }), {
    'recommend ok': (response) => response.status === 200,
  });

  const songId = __ENV.K6_SONG_ID || 'spotify:track:benchmark';
  const encodedSongId = encodeURIComponent(songId);

  check(http.get(`${baseUrl}/stream/${encodedSongId}`, { headers: authHeaders }), {
    'stream ok': (response) => response.status === 200,
  });
  check(http.post(`${baseUrl}/stream/${encodedSongId}/ended`, null, { headers: authHeaders }), {
    'stream ended ok': (response) => response.status === 202,
  });

  const playlist = http.post(
    `${baseUrl}/playlists`,
    JSON.stringify({ name: `k6 smoke ${suffix}` }),
    { headers: jsonAuthHeaders(authHeaders) },
  );
  check(playlist, { 'playlist create ok': (response) => response.status === 201 });

  const playlistId = playlist.json('id');
  if (playlistId) {
    check(http.post(
      `${baseUrl}/playlists/${playlistId}/tracks`,
      JSON.stringify({ songId }),
      { headers: jsonAuthHeaders(authHeaders) },
    ), {
      'playlist add track ok': (response) => response.status === 201 || response.status === 204,
    });
  }

  check(http.get(`${baseUrl}/analytics/me/history?page=0&size=10`, { headers: authHeaders }), {
    'history ok': (response) => response.status === 200,
  });

  sleep(1);
}
