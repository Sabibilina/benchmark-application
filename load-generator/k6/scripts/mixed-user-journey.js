import http from 'k6/http';
import { check, sleep } from 'k6';
import { summaryOutputs } from './cost-summary.js';

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';
const authRate = Number(__ENV.K6_AUTH_LOGIN_RATE || 5);
const catalogRate = Number(__ENV.K6_CATALOG_SEARCH_RATE || 20);
const playlistRate = Number(__ENV.K6_PLAYLIST_MUTATION_RATE || 1);
const streamingRate = Number(__ENV.K6_STREAMING_SESSION_RATE || 50);
const duration = __ENV.K6_MIXED_DURATION || '2m';

export const options = {
  scenarios: {
    auth_logins: {
      executor: 'constant-arrival-rate',
      rate: authRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Number(__ENV.K6_AUTH_PREALLOCATED_VUS || 10),
      maxVUs: Number(__ENV.K6_AUTH_MAX_VUS || 50),
      exec: 'authLogin',
    },
    catalog_search: {
      executor: 'constant-arrival-rate',
      rate: catalogRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Number(__ENV.K6_CATALOG_PREALLOCATED_VUS || 25),
      maxVUs: Number(__ENV.K6_CATALOG_MAX_VUS || 125),
      exec: 'catalogSearch',
    },
    playlist_mutations: {
      executor: 'constant-arrival-rate',
      rate: playlistRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Number(__ENV.K6_PLAYLIST_PREALLOCATED_VUS || 10),
      maxVUs: Number(__ENV.K6_PLAYLIST_MAX_VUS || 50),
      exec: 'playlistMutation',
    },
    streaming_sessions: {
      executor: 'constant-arrival-rate',
      rate: streamingRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Number(__ENV.K6_STREAMING_PREALLOCATED_VUS || 55),
      maxVUs: Number(__ENV.K6_STREAMING_MAX_VUS || 275),
      exec: 'streamPlayback',
    },
  },
  thresholds: {
    dropped_iterations: [`count<${__ENV.K6_DROPPED_ITERATION_LIMIT || 1}`],
    http_req_failed: [`rate<${__ENV.K6_HTTP_FAIL_RATE || 0.05}`],
    http_req_duration: [
      `p(95)<${__ENV.K6_HTTP_REQ_DURATION_P95_MS || 1500}`,
      `p(99)<${__ENV.K6_HTTP_REQ_DURATION_P99_MS || 3000}`,
    ],
  },
};

export function setup() {
  const credentials = {
    email: `mixed-setup-${Date.now()}@example.com`,
    password: 'CorrectHorse123',
  };
  http.post(`${baseUrl}/auth/register`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  const login = http.post(`${baseUrl}/auth/login`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(login, { 'setup auth login ok': (response) => response.status === 200 });
  return { token: login.json('accessToken') };
}

function bearer(token) {
  return { Authorization: `Bearer ${token}` };
}

function jsonBearer(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

function uniqueCredentials(prefix) {
  return {
    email: `${prefix}-${__VU}-${__ITER}-${Date.now()}@example.com`,
    password: 'CorrectHorse123',
  };
}

export function authLogin() {
  const credentials = uniqueCredentials('auth');
  http.post(`${baseUrl}/auth/register`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  const login = http.post(`${baseUrl}/auth/login`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(login, { 'auth login ok': (response) => response.status === 200 });
}

export function catalogSearch(data) {
  const headers = bearer(data.token);
  check(http.get(`${baseUrl}/catalog/songs?page=0&size=20`, { headers }), {
    'catalog browse ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/search?q=love&size=20`, { headers }), {
    'search ok': (response) => response.status === 200,
  });
}

export function playlistMutation(data) {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const playlist = http.post(
    `${baseUrl}/playlists`,
    JSON.stringify({ name: `mixed ${suffix}` }),
    { headers: jsonBearer(data.token) },
  );
  check(playlist, { 'playlist create ok': (response) => response.status === 201 });
  const playlistId = playlist.json('id');
  if (!playlistId) {
    return;
  }
  check(http.post(
    `${baseUrl}/playlists/${playlistId}/tracks`,
    JSON.stringify({ songId: `spotify:track:mixed-${__ITER}` }),
    { headers: jsonBearer(data.token) },
  ), {
    'playlist add track ok': (response) => response.status === 201 || response.status === 204,
  });
}

export function streamPlayback(data) {
  const headers = bearer(data.token);
  const songId = encodeURIComponent(`spotify:track:benchmark-${__ITER % 100}`);
  check(http.get(`${baseUrl}/stream/${songId}`, { headers }), {
    'stream descriptor ok': (response) => response.status === 200,
  });
  check(http.post(`${baseUrl}/stream/${songId}/ended`, null, { headers }), {
    'stream terminal event ok': (response) => response.status === 202,
  });
}

export default function (data) {
  catalogSearch(data);
  streamPlayback(data);
  sleep(1);
}

export function handleSummary(data) {
  return summaryOutputs(data, 'mixed-user-journey.js', 'mixed-user-journey-cost-summary.json');
}
