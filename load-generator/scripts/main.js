import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = __ENV.NGINX_LB_URL || 'http://nginx-lb:80';
const scenario = __ENV.K6_SCENARIO || 'full';

const thresholds = {
  http_req_duration: ['p(95)<2000', 'p(99)<5000'],
  http_req_failed: ['rate<0.05'],
};

const scenarioConfigs = {
  smoke: {
    thresholds,
    scenarios: {
      smoke: {
        executor: 'constant-vus',
        vus: 5,
        duration: '2m',
      },
    },
  },
  streaming: {
    thresholds,
    scenarios: {
      streaming: {
        executor: 'constant-vus',
        vus: 50,
        duration: '5m',
      },
    },
  },
  full: {
    thresholds,
    scenarios: {
      full: {
        executor: 'constant-vus',
        vus: parseInt(__ENV.K6_VUS || '50'),
        duration: __ENV.K6_DURATION || '5m',
      },
    },
  },
  peak: {
    thresholds,
    scenarios: {
      peak: {
        executor: 'ramping-vus',
        stages: [
          { duration: '5m', target: 500 },
          { duration: '10m', target: 500 },
          { duration: '5m', target: 0 },
        ],
      },
    },
  },
};

export const options = scenarioConfigs[scenario];

// --- Auth helpers ---

function register(username, email, password) {
  return http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ username, email, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}

function login(username, password) {
  return http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}

function authHeaders(token) {
  return { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

// --- Flow helpers ---

function catalogFlow(token) {
  const catalogRes = http.get(`${BASE_URL}/catalog/songs?page=0&size=20`, authHeaders(token));
  check(catalogRes, { 'catalog songs 200': (r) => r.status === 200 });

  let songId = 'song-1';
  try {
    const body = JSON.parse(catalogRes.body);
    const songs = body.content || body.songs || body;
    if (Array.isArray(songs) && songs.length > 0) {
      songId = songs[Math.floor(Math.random() * songs.length)].id || songId;
    }
  } catch (_) {}

  const songRes = http.get(`${BASE_URL}/catalog/songs/${songId}`, authHeaders(token));
  check(songRes, { 'catalog song detail 200': (r) => r.status === 200 });

  return songId;
}

function searchStreamFlow(token, songId) {
  const searchRes = http.get(`${BASE_URL}/search?q=rock&page=0`, authHeaders(token));
  check(searchRes, { 'search 200': (r) => r.status === 200 });

  sleep(1);

  const streamRes = http.get(`${BASE_URL}/stream/${songId}`, authHeaders(token));
  check(streamRes, { 'stream 200': (r) => r.status === 200 });
}

function playlistFlow(token, songId) {
  const listRes = http.get(`${BASE_URL}/playlists`, authHeaders(token));
  check(listRes, { 'playlists 200': (r) => r.status === 200 });

  let playlistId = null;
  try {
    const playlists = JSON.parse(listRes.body);
    if (Array.isArray(playlists) && playlists.length > 0) {
      playlistId = playlists[0].id;
    }
  } catch (_) {}

  if (!playlistId) {
    const createRes = http.post(
      `${BASE_URL}/playlists`,
      JSON.stringify({ name: `Test Playlist ${__VU}` }),
      authHeaders(token)
    );
    check(createRes, { 'create playlist 200/201': (r) => r.status === 200 || r.status === 201 });
    try {
      playlistId = JSON.parse(createRes.body).id;
    } catch (_) {}
  }

  if (playlistId) {
    const addRes = http.post(
      `${BASE_URL}/playlists/${playlistId}/tracks`,
      JSON.stringify({ songId }),
      authHeaders(token)
    );
    check(addRes, { 'add track 200/201': (r) => r.status === 200 || r.status === 201 });
  }
}

function analyticsFlow(token) {
  const historyRes = http.get(`${BASE_URL}/analytics/me/history`, authHeaders(token));
  check(historyRes, { 'history 200': (r) => r.status === 200 });

  sleep(1);

  const recRes = http.get(`${BASE_URL}/recommend/daily-mix`, authHeaders(token));
  check(recRes, { 'recommendations 200': (r) => r.status === 200 });
}

// --- Default function ---

export default function () {
  const username = `vu-${__VU}-${__ITER}-${Date.now()}`;
  const email = `${username}@test.com`;
  const password = 'Password123!';

  // Registration
  const regRes = register(username, email, password);
  check(regRes, { 'register 200/409': (r) => r.status === 200 || r.status === 409 });

  let token = null;

  if (regRes.status === 200) {
    try {
      token = JSON.parse(regRes.body).token;
    } catch (_) {}
  }

  // If registration conflicted or token missing, fall back to login
  if (!token) {
    const loginRes = login(username, password);
    check(loginRes, { 'login 200': (r) => r.status === 200 });
    if (loginRes.status !== 200) return;
    try {
      token = JSON.parse(loginRes.body).token;
    } catch (_) {}
  }

  if (!token) return;

  sleep(1);

  // Catalog flow (all scenarios)
  const songId = catalogFlow(token);

  sleep(1);

  if (scenario === 'smoke') return;

  // Search + stream flow
  searchStreamFlow(token, songId);

  sleep(1);

  if (scenario === 'streaming') return;

  // Playlist ops (full + peak)
  playlistFlow(token, songId);

  sleep(1);

  // Analytics + recommendations (full + peak)
  analyticsFlow(token);

  sleep(1);
}
