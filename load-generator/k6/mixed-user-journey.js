import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';
const password = __ENV.K6_USER_PASSWORD || 'CorrectHorse123';
const duration = __ENV.K6_DURATION || '5m';

const userPoolSize = Number(__ENV.K6_USER_POOL_SIZE || 20);
const preAllocatedVUs = Number(__ENV.K6_PREALLOCATED_VUS || 100);
const maxVUs = Number(__ENV.K6_MAX_VUS || 500);

export const options = {
  scenarios: {
    auth_logins: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.K6_AUTH_LOGIN_RATE || 5),
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(10, Math.floor(preAllocatedVUs * 0.1)),
      maxVUs: Math.max(50, Math.floor(maxVUs * 0.1)),
      exec: 'authLogin',
    },
    catalog_search: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.K6_CATALOG_SEARCH_ITER_RATE || 20),
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(20, Math.floor(preAllocatedVUs * 0.25)),
      maxVUs: Math.max(100, Math.floor(maxVUs * 0.25)),
      exec: 'catalogSearch',
    },
    streaming_sessions: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.K6_STREAMING_SESSION_RATE || 50),
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(50, Math.floor(preAllocatedVUs * 0.55)),
      maxVUs: Math.max(250, Math.floor(maxVUs * 0.55)),
      exec: 'streamPlayback',
    },
    playlist_mutations: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.K6_PLAYLIST_MUTATION_ITER_RATE || 2),
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(10, Math.floor(preAllocatedVUs * 0.1)),
      maxVUs: Math.max(50, Math.floor(maxVUs * 0.1)),
      exec: 'playlistMutation',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
  },
};

function jsonHeaders(extra = {}) {
  return {
    headers: {
      'Content-Type': 'application/json',
      ...extra,
    },
  };
}

function login(email) {
  const payload = JSON.stringify({ email, password });
  const response = http.post(`${baseUrl}/auth/login`, payload, jsonHeaders());
  const token = response.json('accessToken');
  check(response, { 'auth login ok': (result) => result.status === 200 && Boolean(token) });
  return token;
}

function authFor(data) {
  const user = data.users[__VU % data.users.length];
  return {
    user,
    headers: {
      Authorization: `Bearer ${user.token}`,
    },
  };
}

function songFor(data) {
  return data.songIds[__ITER % data.songIds.length];
}

export function setup() {
  const users = [];

  for (let i = 0; i < userPoolSize; i += 1) {
    const email = `k6-user-${i}@example.com`;
    const payload = JSON.stringify({ email, password });
    http.post(`${baseUrl}/auth/register`, payload, jsonHeaders());
    const token = login(email);

    if (token) {
      users.push({ email, token });
    }
  }

  if (users.length === 0) {
    throw new Error('k6 setup could not authenticate any benchmark users');
  }

  return {
    users,
    songIds: (__ENV.K6_SONG_IDS || 'spotify:track:benchmark,spotify:track:benchmark-2,spotify:track:benchmark-3')
      .split(',')
      .map((songId) => songId.trim())
      .filter(Boolean),
  };
}

export function authLogin(data) {
  const user = data.users[__ITER % data.users.length];
  login(user.email);
}

export function catalogSearch(data) {
  const { headers } = authFor(data);

  const catalog = http.get(`${baseUrl}/catalog/songs?page=0&size=20`, { headers });
  check(catalog, { 'catalog browse ok': (response) => response.status === 200 });

  const search = http.get(`${baseUrl}/search?q=love&genre=pop&size=20`, { headers });
  check(search, { 'search ok': (response) => response.status === 200 });
}

export function streamPlayback(data) {
  const { headers } = authFor(data);
  const songId = songFor(data);
  const encodedSongId = encodeURIComponent(songId);

  const stream = http.get(`${baseUrl}/stream/${encodedSongId}`, { headers });
  check(stream, { 'stream descriptor ok': (response) => response.status === 200 });

  const terminalPath = __ITER % 5 === 0 ? 'skipped' : 'ended';
  const terminal = http.post(`${baseUrl}/stream/${encodedSongId}/${terminalPath}`, null, { headers });
  check(terminal, { 'stream terminal event ok': (response) => response.status === 202 || response.status === 204 });
}

export function playlistMutation(data) {
  const { headers } = authFor(data);
  const songId = songFor(data);
  const playlistName = `k6 playlist ${__VU}-${__ITER}`;

  const created = http.post(
    `${baseUrl}/playlists`,
    JSON.stringify({ name: playlistName }),
    jsonHeaders(headers),
  );
  check(created, { 'playlist create ok': (response) => response.status === 201 });

  const playlistId = created.json('id');
  if (!playlistId) {
    return;
  }

  const added = http.post(
    `${baseUrl}/playlists/${playlistId}/tracks`,
    JSON.stringify({ songId }),
    jsonHeaders(headers),
  );
  check(added, { 'playlist add track ok': (response) => response.status === 201 || response.status === 204 });
}
