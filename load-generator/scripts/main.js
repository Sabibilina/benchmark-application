/**
 * Music Streaming Benchmark — k6 load test
 *
 * All traffic routes through nginx-lb (NGINX_LB_URL) to measure realistic
 * system throughput including reverse-proxy overhead and rate limiting.
 *
 * Scenario selection via K6_SCENARIO environment variable:
 *   smoke          —   5 VUs, 2 min  — quick sanity check, baseline latencies
 *   catalog_stream —  50 VUs, 5 min  — auth + browse + stream; reveals first
 *                                       service bottleneck
 *   kafka_pipeline — 200 VUs, 10 min — adds playlist + playlist events;
 *                                       validates Kafka consumer pipeline
 *   ramp           — 0→500 VUs ramp  — finds the knee of the throughput curve
 *   stress         — 0→2000 VUs ramp — drives services past comfortable limits
 *   soak           — 100 VUs, 2 h    — catches memory leaks and Kafka lag
 *                                       accumulation over time
 */

import http from 'k6/http';
import { sleep, check, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Service entry points ───────────────────────────────────────────────────
// Primary: all requests through the nginx load balancer.
const LB = __ENV.NGINX_LB_URL || 'http://nginx-lb';

// ── Custom metrics ─────────────────────────────────────────────────────────
const streamingErrors  = new Counter('streaming_errors');
const kafkaEventsSent  = new Counter('kafka_events_sent');
const cacheHintHits    = new Counter('recommendation_requests');
const registrationFail = new Rate('registration_failures');
const streamLatency    = new Trend('stream_manifest_duration_ms');

// ── Scenario definitions ───────────────────────────────────────────────────
const SCENARIO_CONFIGS = {
  smoke: {
    executor: 'constant-vus',
    vus: 5,
    duration: '2m',
  },
  catalog_stream: {
    executor: 'constant-vus',
    vus: 50,
    duration: '5m',
  },
  kafka_pipeline: {
    executor: 'constant-vus',
    vus: 200,
    duration: '10m',
  },
  ramp: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '3m',  target: 100 },
      { duration: '5m',  target: 500 },
      { duration: '5m',  target: 500 },
      { duration: '2m',  target: 0   },
    ],
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '5m',  target: 500  },
      { duration: '10m', target: 2000 },
      { duration: '5m',  target: 2000 },
      { duration: '5m',  target: 0    },
    ],
  },
  soak: {
    executor: 'constant-vus',
    vus: 100,
    duration: '2h',
  },
};

const selectedScenario = __ENV.K6_SCENARIO || 'ramp';
const scenarioCfg = SCENARIO_CONFIGS[selectedScenario] || SCENARIO_CONFIGS.ramp;

export const options = {
  scenarios: {
    benchmark: {
      ...scenarioCfg,
      exec: 'musicStreamingFlow',
    },
  },
  thresholds: {
    // Overall error rate must stay below 5 %
    http_req_failed:                        ['rate<0.05'],
    // Auth must respond within 3 s at p99
    'http_req_duration{endpoint:login}':    ['p(99)<3000'],
    // Streaming manifest must respond within 5 s at p99
    'http_req_duration{endpoint:stream}':   ['p(99)<5000'],
    // Catalog browsing must respond within 2 s at p99
    'http_req_duration{endpoint:catalog}':  ['p(99)<2000'],
    // Search must respond within 3 s at p99
    'http_req_duration{endpoint:search}':   ['p(99)<3000'],
  },
};

// ── Per-VU state ───────────────────────────────────────────────────────────
// k6 preserves module-level variables across iterations within the same VU.
// We register once per VU and reuse the token for all subsequent iterations.
let vuToken    = null;
let vuUserId   = null;
let vuSongIds  = [];    // catalog song IDs discovered during first iteration
let vuPlaylistId = null;

// ── Helpers ────────────────────────────────────────────────────────────────

function makeAuthHeaders(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

function uniqueUser() {
  // Combine VU number + timestamp for uniqueness across runs
  const ts = Date.now();
  return {
    username: `bench_${__VU}_${ts}`,
    email:    `bench_${__VU}_${ts}@bench.local`,
    password: 'Bench1234!',
  };
}

function registerAndLogin() {
  const user = uniqueUser();

  const regRes = http.post(
    `${LB}/auth/register`,
    JSON.stringify(user),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'register' } },
  );
  const regOk = check(regRes, { 'register 201': (r) => r.status === 201 });
  registrationFail.add(!regOk);

  const loginRes = http.post(
    `${LB}/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } },
  );
  check(loginRes, { 'login 200': (r) => r.status === 200 });

  if (loginRes.status !== 200) return null;

  const body = JSON.parse(loginRes.body);
  // Field name may be token or access_token depending on service version
  const token = body.token || body.access_token;
  return { token, userId: body.userId || body.id || user.email };
}

function browseCatalog(token) {
  const page = Math.floor(Math.random() * 5); // pages 0-4
  const res = http.get(
    `${LB}/catalog/songs?page=${page}&size=20`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'catalog' } },
  );
  check(res, { 'catalog 200': (r) => r.status === 200 });

  if (res.status === 200) {
    try {
      const data = JSON.parse(res.body);
      const items = data.content || data || [];
      return items.slice(0, 5).map((s) => s.trackId || s.id || s.songId).filter(Boolean);
    } catch (_) {}
  }
  return [];
}

function searchSongs(token, query) {
  const res = http.get(
    `${LB}/search?q=${encodeURIComponent(query)}`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'search' } },
  );
  check(res, { 'search 200': (r) => r.status === 200 });

  if (res.status === 200) {
    try {
      const items = JSON.parse(res.body);
      return (Array.isArray(items) ? items : []).slice(0, 3)
        .map((s) => s.trackId || s.id).filter(Boolean);
    } catch (_) {}
  }
  return [];
}

function streamSong(token, songId) {
  const start = Date.now();
  const manifestRes = http.get(
    `${LB}/stream/${songId}`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'stream' } },
  );
  streamLatency.add(Date.now() - start);

  const ok = check(manifestRes, { 'stream manifest 200': (r) => r.status === 200 });
  if (!ok) { streamingErrors.add(1); return; }

  // Fetch first segment to generate realistic segment traffic
  const segRes = http.get(
    `${LB}/stream/${songId}/segment/0`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'stream_segment' } },
  );
  check(segRes, { 'stream segment 200': (r) => r.status === 200 });

  // Mark play as complete (triggers playback-events Kafka message)
  const completeRes = http.post(
    `${LB}/stream/${songId}/complete`,
    null,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'stream_complete' } },
  );
  if (completeRes.status === 200 || completeRes.status === 204) {
    kafkaEventsSent.add(1);
  }
}

function ensurePlaylist(token) {
  if (vuPlaylistId) return vuPlaylistId;

  const listRes = http.get(
    `${LB}/playlists`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'playlists_list' } },
  );

  if (listRes.status === 200) {
    try {
      const playlists = JSON.parse(listRes.body);
      const arr = Array.isArray(playlists) ? playlists : [];
      if (arr.length > 0) {
        vuPlaylistId = arr[0].id;
        return vuPlaylistId;
      }
    } catch (_) {}
  }

  // Create one if none exist
  const createRes = http.post(
    `${LB}/playlists`,
    JSON.stringify({ name: `bench_list_${__VU}` }),
    { headers: makeAuthHeaders(token), tags: { endpoint: 'playlist_create' } },
  );
  check(createRes, { 'playlist created': (r) => r.status === 201 || r.status === 200 });

  if (createRes.status === 200 || createRes.status === 201) {
    try {
      vuPlaylistId = JSON.parse(createRes.body).id;
    } catch (_) {}
  }
  return vuPlaylistId;
}

function addToPlaylist(token, playlistId, songId) {
  if (!playlistId || !songId) return;
  const res = http.post(
    `${LB}/playlists/${playlistId}/tracks`,
    JSON.stringify({ songId }),
    { headers: makeAuthHeaders(token), tags: { endpoint: 'playlist_add_track' } },
  );
  check(res, { 'track added': (r) => r.status === 200 || r.status === 201 });
}

function getRecommendations(token) {
  const res = http.get(
    `${LB}/recommend/daily-mix`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'recommend' } },
  );
  check(res, { 'recommend 200': (r) => r.status === 200 });
  cacheHintHits.add(1);
}

function getAnalyticsHistory(token) {
  const res = http.get(
    `${LB}/analytics/me/history`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'analytics' } },
  );
  check(res, { 'analytics 200': (r) => r.status === 200 });
}

function getNotifications(token) {
  const res = http.get(
    `${LB}/notifications`,
    { headers: makeAuthHeaders(token), tags: { endpoint: 'notifications' } },
  );
  check(res, { 'notifications 200': (r) => r.status === 200 });
}

// ── Main scenario ──────────────────────────────────────────────────────────

export function musicStreamingFlow() {
  // ── Step 1: auth (once per VU lifetime) ───────────────────────────────────
  if (!vuToken) {
    group('auth', () => {
      const creds = registerAndLogin();
      if (creds) {
        vuToken  = creds.token;
        vuUserId = creds.userId;
      }
    });
    if (!vuToken) {
      sleep(2);
      return; // skip iteration if auth failed
    }
  }

  // ── Step 2: browse catalog (and cache song IDs for streaming) ─────────────
  group('browse', () => {
    const ids = browseCatalog(vuToken);
    if (ids.length > 0 && vuSongIds.length < 20) {
      vuSongIds = [...new Set([...vuSongIds, ...ids])];
    }
  });

  // ── Step 3: search (varies query to exercise OpenSearch) ──────────────────
  const QUERIES = ['rock', 'pop', 'jazz', 'classical', 'electronic', 'hip hop'];
  group('search', () => {
    const q = QUERIES[__VU % QUERIES.length];
    const searchIds = searchSongs(vuToken, q);
    if (searchIds.length > 0 && vuSongIds.length < 20) {
      vuSongIds = [...new Set([...vuSongIds, ...searchIds])];
    }
  });

  // ── Step 4: stream a song ─────────────────────────────────────────────────
  if (vuSongIds.length > 0) {
    group('stream', () => {
      const songId = vuSongIds[Math.floor(Math.random() * vuSongIds.length)];
      streamSong(vuToken, songId);
    });
  }

  // ── Step 5: playlist operation (every 3rd iteration) ──────────────────────
  if (__ITER % 3 === 0) {
    group('playlists', () => {
      const pid = ensurePlaylist(vuToken);
      if (pid && vuSongIds.length > 0) {
        const songId = vuSongIds[Math.floor(Math.random() * vuSongIds.length)];
        addToPlaylist(vuToken, pid, songId);
      }
    });
  }

  // ── Step 6: recommendations (every 5th iteration to limit Redis load) ─────
  if (__ITER % 5 === 0) {
    group('recommend', () => {
      getRecommendations(vuToken);
    });
  }

  // ── Step 7: analytics history (every 10th iteration) ──────────────────────
  if (__ITER % 10 === 0) {
    group('analytics', () => {
      getAnalyticsHistory(vuToken);
    });
  }

  // ── Step 8: notifications (every 10th iteration) ──────────────────────────
  if (__ITER % 10 === 1) {
    group('notifications', () => {
      getNotifications(vuToken);
    });
  }

  // Think-time: 0.5–2 s random pause between iterations (reduces "thundering
  // herd" effects and makes the load pattern more realistic)
  sleep(0.5 + Math.random() * 1.5);
}
