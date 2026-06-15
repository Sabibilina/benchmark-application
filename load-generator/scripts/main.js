import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = __ENV.NGINX_LB_URL || 'http://nginx-lb:80';
const scenario = __ENV.K6_SCENARIO || 'full';

// Use APP_ prefix to avoid conflict with k6's built-in K6_VUS / K6_DURATION env vars,
// which override options.scenarios when set in the environment.
const rampTarget = parseInt(__ENV.APP_RAMP_TARGET || __ENV.K6_RAMP_TARGET || '1000');

const thresholds = {
  http_req_duration: ['p(95)<2000', 'p(99)<5000'],
  http_req_failed: ['rate<0.05'],
};

const scenarioConfigs = {
  // Phase B — smoke: verify basic connectivity and JWT flow (5 VUs, 2 min)
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
  // Phase 2 — streaming: auth + catalog + search + stream hot path (50 VUs, 5 min)
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
  // Phase 3 — full user journey: all 8 flows (configurable via APP_VUS / APP_DURATION)
  full: {
    thresholds,
    scenarios: {
      full: {
        executor: 'constant-vus',
        vus: parseInt(__ENV.APP_VUS || '50'),
        duration: __ENV.APP_DURATION || '5m',
      },
    },
  },
  // Phase 3 — burst: short 500-VU spike for regression testing
  burst: {
    thresholds,
    scenarios: {
      burst: {
        executor: 'ramping-vus',
        stages: [
          { duration: '5m', target: 500 },
          { duration: '5m', target: 500 },
          { duration: '5m', target: 0 },
        ],
      },
    },
  },
  // Phase 4 — ramp: sustained load ramp for scaling evidence.
  ramp: {
    thresholds,
    scenarios: {
      ramp: {
        executor: 'ramping-vus',
        stages: [
          { duration: '5m',  target: rampTarget },
          { duration: '10m', target: rampTarget },
          { duration: '5m',  target: 0 },
        ],
      },
    },
  },
  // Phase 6 — soak: 2000-VU constant load for 2 hours
  soak: {
    thresholds: {
      http_req_duration: ['p(95)<3000', 'p(99)<8000'],
      http_req_failed: ['rate<0.05'],
    },
    scenarios: {
      soak: {
        executor: 'constant-vus',
        vus: 2000,
        duration: '120m',
      },
    },
  },
};

export const options = scenarioConfigs[scenario] || scenarioConfigs['full'];

// ── Auth helpers ──────────────────────────────────────────────────────────────

function register(username, email, password) {
  return http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ username, email, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}

// Login requires email (not just username) per auth-service contract.
function login(email, password) {
  return http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
}

function authHeaders(token) {
  return { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

// ── Setup: pre-register users and collect song IDs ───────────────────────────
// Runs once before the load test. Returns shared data passed to every VU.

export function setup() {
  const cfg = options.scenarios || {};
  const firstScenario = Object.values(cfg)[0] || {};
  const numVUs = firstScenario.vus || 50;
  const numUsers = Math.max(numVUs, 20);

  console.log(`[setup] Registering ${numUsers} users...`);
  const users = [];
  for (let i = 0; i < numUsers; i++) {
    const username = `perf-${Date.now()}-${i}`;
    const email = `${username}@perf.test`;
    const password = 'Perf1234!';
    const regRes = register(username, email, password);
    let token = null;
    if (regRes.status === 200 || regRes.status === 201) {
      try { token = JSON.parse(regRes.body).token; } catch (_) {}
    }
    if (!token) {
      const loginRes = login(email, password);
      if (loginRes.status === 200) {
        try { token = JSON.parse(loginRes.body).token; } catch (_) {}
      }
    }
    if (token) users.push({ token, username, email });
    // Stay within the nginx auth rate limit (20 req/s) — one user per 60ms
    sleep(0.06);
  }
  console.log(`[setup] Ready with ${users.length} tokens`);

  // Collect song IDs for use in stream/catalog flows
  const songIds = ['1'];
  if (users.length > 0) {
    const catalogRes = http.get(
      `${BASE_URL}/catalog/songs?page=0&size=100`,
      authHeaders(users[0].token)
    );
    if (catalogRes.status === 200) {
      try {
        const body = JSON.parse(catalogRes.body);
        const songs = body.content || body.songs || body;
        if (Array.isArray(songs)) {
          songs.forEach(s => { if (s.id) songIds.push(String(s.id)); });
        }
      } catch (_) {}
    }
  }
  console.log(`[setup] Collected ${songIds.length} song IDs`);
  return { users, songIds };
}

// ── Flow helpers ──────────────────────────────────────────────────────────────

function catalogFlow(token, songIds) {
  const catalogRes = http.get(`${BASE_URL}/catalog/songs?page=0&size=20`, authHeaders(token));
  check(catalogRes, { 'catalog songs 200': (r) => r.status === 200 });

  const songId = songIds[Math.floor(Math.random() * songIds.length)];
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
      JSON.stringify({ name: `Perf Playlist ${__VU}` }),
      authHeaders(token)
    );
    check(createRes, { 'create playlist 200/201': (r) => r.status === 200 || r.status === 201 });
    try { playlistId = JSON.parse(createRes.body).id; } catch (_) {}
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

// ── Default function ──────────────────────────────────────────────────────────

export default function (data) {
  // Reuse pre-registered user from setup — no auth calls in the hot path
  const users = data.users;
  if (!users || users.length === 0) return;

  const user = users[__VU % users.length];
  const token = user.token;

  sleep(1);

  // Catalog flow (all scenarios)
  const songId = catalogFlow(token, data.songIds);

  sleep(1);

  if (scenario === 'smoke') return;

  // Search + stream flow
  searchStreamFlow(token, songId);

  sleep(1);

  if (scenario === 'streaming') return;

  // Playlist ops (full, burst, ramp, soak)
  playlistFlow(token, songId);

  sleep(1);

  // Analytics + recommendations (full, burst, ramp, soak)
  analyticsFlow(token);

  sleep(1);
}
