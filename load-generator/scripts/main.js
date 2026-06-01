/**
 * Music Streaming Benchmark — k6 arrival-rate load test
 *
 * Five independent traffic lanes target the RPS figures from SCALABILITY.md §1:
 *   streaming_playback — 20 000 iter/s → 40 K eps into Kafka
 *   catalog_search     —  4 000 req/s  (catalog + search alternating)
 *   auth_login         —    500 req/s  (burst: 3× = 1 500 req/s)
 *   playlist_mutations —    200 req/s
 *   recommendations    —    400 req/s
 *
 * All traffic routes through nginx-lb (NGINX_LB_URL).
 *
 * Usage:
 *   k6 run -e K6_SCENARIOS=all scripts/main.js
 *   k6 run -e K6_SCENARIO=smoke scripts/main.js  # backward-compat smoke
 *
 * Run seed.js first for large tests:
 *   k6 run -e SEED_USER_COUNT=25000 scripts/seed.js
 */

import http       from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import encoding from 'k6/encoding';

// ── Configuration ──────────────────────────────────────────────────────────────
const LB            = __ENV.NGINX_LB_URL       || 'http://nginx-lb';
const KAFKA_EXP_URL = __ENV.KAFKA_EXPORTER_URL || 'http://kafka-exporter:9308';

const STREAMING_RATE = parseInt(__ENV.K6_STREAMING_RATE  || '20000', 10);
const CATALOG_RATE   = parseInt(__ENV.K6_CATALOG_RATE    || '4000',  10);
const AUTH_RATE      = parseInt(__ENV.K6_AUTH_RATE       || '500',   10);
const PLAYLIST_RATE  = parseInt(__ENV.K6_PLAYLIST_RATE   || '200',   10);
const RECOMMEND_RATE = parseInt(__ENV.K6_RECOMMEND_RATE  || '400',   10);

const WARMUP_DUR    = __ENV.K6_PHASE_WARMUP_DURATION    || '5m';
const STEADY_DUR    = __ENV.K6_PHASE_STEADY_DURATION    || '15m';
const BURST_DUR     = __ENV.K6_PHASE_BURST_DURATION     || '2m';
const RAMPDOWN_DUR  = __ENV.K6_PHASE_RAMPDOWN_DURATION  || '3m';

const SEED_USER_COUNT = parseInt(__ENV.SEED_USER_COUNT || '200', 10);

const IS_SMOKE = (__ENV.K6_SCENARIO === 'smoke');

// ── Custom metrics ─────────────────────────────────────────────────────────────
const streamingEventsSent    = new Counter('streaming_events_sent');
const streamingManifestErr   = new Counter('streaming_manifest_errors');
const streamingCompleteErr   = new Counter('streaming_complete_errors');
const authLoginErrors        = new Counter('auth_login_errors');
const authRegisterErrors     = new Counter('auth_register_errors');
const playlistTrackAddErrors = new Counter('playlist_track_add_errors');
const kafkaLagPollErrors     = new Counter('kafka_lag_poll_errors');

const streamingErrorRate = new Rate('streaming_error_rate');
const authErrorRate      = new Rate('auth_error_rate');
const catalogErrorRate   = new Rate('catalog_error_rate');
const searchErrorRate    = new Rate('search_error_rate');

const streamingManifestDuration = new Trend('streaming_manifest_duration_ms', true);
const streamingSegmentDuration  = new Trend('streaming_segment_duration_ms',  true);
const streamingCompleteDuration = new Trend('streaming_complete_duration_ms', true);
const catalogBrowseDuration     = new Trend('catalog_browse_duration_ms',     true);
const searchQueryDuration       = new Trend('search_query_duration_ms',       true);
const authLoginDuration         = new Trend('auth_login_duration_ms',         true);
const authRegisterDuration      = new Trend('auth_register_duration_ms',      true);
const playlistListDuration      = new Trend('playlist_list_duration_ms',      true);
const playlistAddTrackDuration  = new Trend('playlist_add_track_duration_ms', true);
const recommendDailyMixDuration = new Trend('recommendation_daily_mix_duration_ms', true);
const recommendSimilarDuration  = new Trend('recommendation_similar_duration_ms',   true);
const analyticsHistoryDuration  = new Trend('analytics_history_duration_ms',  true);
const analyticsChartsDuration   = new Trend('analytics_charts_duration_ms',   true);
const notificationListDuration  = new Trend('notification_list_duration_ms',  true);

const kafkaLagAnalytics      = new Gauge('kafka_lag_analytics');
const kafkaLagRecommendation = new Gauge('kafka_lag_recommendation');
const kafkaLagNotification   = new Gauge('kafka_lag_notification');

// ── VU-local playlist ID cache (module state persists across iterations per VU) ──
let _vuPlaylistId = null;

// ── Seed data (written by seed.js, read at init time) ─────────────────────────
let _seedFile = { users: [] };
try {
  _seedFile = JSON.parse(open('./data/seed.json'));
} catch (_) {
  // seed.json absent — setup() will register users dynamically
}

// ── Scenario selection ─────────────────────────────────────────────────────────
const _selected = (__ENV.K6_SCENARIOS || 'all').split(',').map(s => s.trim());
const _allEnabled = _selected.includes('all');
function _enabled(name) { return IS_SMOKE || _allEnabled || _selected.includes(name); }

// ── Duration helpers ───────────────────────────────────────────────────────────
function _durationMs(d) {
  const v = parseInt(d, 10);
  if (d.endsWith('h')) return v * 3600000;
  if (d.endsWith('m')) return v * 60000;
  return v * 1000;
}
const _WARMUP_MS = _durationMs(WARMUP_DUR);
const _STEADY_MS = _durationMs(STEADY_DUR);
const _BURST_MS  = _durationMs(BURST_DUR);

function _getPhase(testStartMs) {
  const e = Date.now() - testStartMs;
  if (e < _WARMUP_MS)                          return 'warmup';
  if (e < _WARMUP_MS + _STEADY_MS)             return 'steady';
  if (e < _WARMUP_MS + _STEADY_MS + _BURST_MS) return 'burst';
  return 'rampdown';
}

// ── Arrival-rate stage builder ─────────────────────────────────────────────────
function _stages(peakRate) {
  if (IS_SMOKE) {
    return [
      { duration: '30s', target: Math.max(1, Math.floor(peakRate * 0.2)) },
      { duration: '1m',  target: peakRate },
      { duration: '30s', target: 0 },
    ];
  }
  return [
    { duration: WARMUP_DUR, target: Math.max(1, Math.floor(peakRate * 0.20)) },
    { duration: STEADY_DUR, target: peakRate },
    { duration: BURST_DUR,  target: peakRate },
    { duration: RAMPDOWN_DUR, target: 0 },
  ];
}

// Auth gets a 2× burst in stage 3
function _authStages(peakRate) {
  if (IS_SMOKE) {
    return [
      { duration: '30s', target: Math.max(1, Math.floor(peakRate * 0.2)) },
      { duration: '1m',  target: peakRate },
      { duration: '30s', target: 0 },
    ];
  }
  return [
    { duration: WARMUP_DUR, target: Math.max(1, Math.floor(peakRate * 0.20)) },
    { duration: STEADY_DUR, target: peakRate },
    { duration: BURST_DUR,  target: peakRate * 2 },
    { duration: RAMPDOWN_DUR, target: 0 },
  ];
}

// ── Scenario definitions ───────────────────────────────────────────────────────
const _scenarios = {};

if (_enabled('streaming_playback')) {
  const r = IS_SMOKE ? 2 : STREAMING_RATE;
  _scenarios.streaming_playback = {
    executor:        'ramping-arrival-rate',
    startRate:       0,
    timeUnit:        '1s',
    preAllocatedVUs: IS_SMOKE ? 10 : Math.min(2000, Math.ceil(r * 0.10)),
    maxVUs:          IS_SMOKE ? 20 : Math.min(2000, Math.ceil(r * 0.11)),
    stages:          _stages(r),
    exec:            'streamingPlaybackFlow',
  };
}

if (_enabled('catalog_search')) {
  const r = IS_SMOKE ? 2 : CATALOG_RATE;
  _scenarios.catalog_search = {
    executor:        'ramping-arrival-rate',
    startRate:       0,
    timeUnit:        '1s',
    preAllocatedVUs: IS_SMOKE ? 10 : Math.min(1200, Math.ceil(r * 0.15)),
    maxVUs:          IS_SMOKE ? 20 : Math.min(4800, Math.ceil(r * 0.20)),
    stages:          _stages(r),
    exec:            'catalogSearchFlow',
  };
}

if (_enabled('auth_login')) {
  const r = IS_SMOKE ? 2 : AUTH_RATE;
  _scenarios.auth_login = {
    executor:        'ramping-arrival-rate',
    startRate:       0,
    timeUnit:        '1s',
    preAllocatedVUs: IS_SMOKE ? 10 : Math.min(300, Math.ceil(r * 0.60)),
    maxVUs:          IS_SMOKE ? 20 : Math.min(1800, Math.ceil(r * 0.60)),
    stages:          _authStages(r),
    exec:            'authLoginFlow',
  };
}

if (_enabled('playlist_mutations')) {
  const r = IS_SMOKE ? 1 : PLAYLIST_RATE;
  _scenarios.playlist_mutations = {
    executor:        'ramping-arrival-rate',
    startRate:       0,
    timeUnit:        '1s',
    preAllocatedVUs: IS_SMOKE ? 5 : Math.min(60, Math.ceil(r * 0.15)),
    maxVUs:          IS_SMOKE ? 10 : Math.min(240, Math.ceil(r * 0.19)),
    stages:          _stages(r),
    exec:            'playlistMutationFlow',
  };
}

if (_enabled('recommendations')) {
  const r = IS_SMOKE ? 2 : RECOMMEND_RATE;
  _scenarios.recommendations = {
    executor:        'ramping-arrival-rate',
    startRate:       0,
    timeUnit:        '1s',
    preAllocatedVUs: IS_SMOKE ? 5 : Math.min(80, Math.ceil(r * 0.20)),
    maxVUs:          IS_SMOKE ? 10 : Math.min(320, Math.ceil(r * 0.30)),
    stages:          _stages(r),
    exec:            'recommendationFlow',
  };
}

// ── options ────────────────────────────────────────────────────────────────────
export const options = {
  scenarios: _scenarios,
  setupTimeout: '3m',
  thresholds: {
    // Global
    'http_req_failed':                         ['rate<0.01'],
    // Streaming
    'streaming_manifest_duration_ms':          ['p(99)<2000'],
    'streaming_complete_duration_ms':          ['p(99)<3000'],
    'streaming_error_rate':                    ['rate<0.01'],
    // Search
    'search_query_duration_ms':                ['p(50)<300', 'p(95)<700', 'p(99)<1000'],
    // Auth (p99<500ms applies to steady state; burst window violates this by design)
    'auth_login_duration_ms':                  ['p(99)<500', 'p(95)<300'],
    // Catalog
    'catalog_browse_duration_ms':              ['p(99)<1000'],
    // Recommendations
    'recommendation_daily_mix_duration_ms':    ['p(99)<500'],
    'recommendation_similar_duration_ms':      ['p(99)<1000'],
    // Playlist
    'playlist_add_track_duration_ms':          ['p(99)<1500'],
    // VU starvation guard
    'dropped_iterations':                      ['count<500'],
  },
};

// ── HTTP helpers ───────────────────────────────────────────────────────────────
function _h(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
}

function _hGet(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Accept': 'application/json',
  };
}

// HLS manifest — streaming service produces application/vnd.apple.mpegurl
function _hHls(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Accept': 'application/vnd.apple.mpegurl',
  };
}

// Binary segment — streaming service produces application/octet-stream
function _hOctet(token) {
  return {
    'Authorization': `Bearer ${token}`,
    'Accept': 'application/octet-stream',
  };
}

// Non-uniform think-time: power-law distribution (most pauses near minS)
function _jitter(minS, maxS) {
  return minS + (maxS - minS) * Math.pow(Math.random(), 2);
}

function _pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// ── JWT sub decoder ────────────────────────────────────────────────────────────
function _jwtSub(token) {
  try {
    const payload = token.split('.')[1];
    const decoded = encoding.b64decode(payload, 'rawurl', 's');
    return JSON.parse(decoded).sub || null;
  } catch (_) {
    return null;
  }
}

// ── setup() ────────────────────────────────────────────────────────────────────
export function setup() {
  const t0 = Date.now();

  // ── Step 1: Ensure user corpus ──────────────────────────────────────────────
  const users = [];  // [{email, password, token?, userId?}]

  if (_seedFile.users && _seedFile.users.length > 0) {
    console.log(`[setup] Loaded ${_seedFile.users.length} users from seed.json`);
    users.push(..._seedFile.users);
  }

  const target = IS_SMOKE ? 20 : SEED_USER_COUNT;
  const toReg  = Math.max(0, target - users.length);

  if (toReg > 0) {
    console.log(`[setup] Registering ${toReg} users in batches of 50...`);
    const BATCH = 50;
    for (let i = users.length; i < target; i += BATCH) {
      const batchReqs = [];
      for (let j = i; j < Math.min(i + BATCH, target); j++) {
        const email = `bench_${j}@bench.local`;
        batchReqs.push([
          'POST',
          `${LB}/auth/register`,
          JSON.stringify({
            username: `bench_${j}`,
            email,
            password: 'Bench1234!',
          }),
          { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'setup_register', service: 'auth' } },
        ]);
      }
      const responses = http.batch(batchReqs);
      for (let k = 0; k < responses.length; k++) {
        const res  = responses[k];
        const idx  = i + k;
        const email = `bench_${idx}@bench.local`;
        authRegisterDuration.add(res.timings.duration);
        if (res.status === 201) {
          const body = JSON.parse(res.body);
          const tok  = body.token;
          users.push({ email, password: 'Bench1234!', token: tok, userId: _jwtSub(tok) });
        } else if (res.status === 409) {
          // Already registered (re-run without seed.json) — record credential for login
          users.push({ email, password: 'Bench1234!' });
        } else {
          authRegisterErrors.add(1);
        }
      }
      sleep(0.1);
    }
    console.log(`[setup] Registered. Total users: ${users.length}`);
  }

  // ── Step 2: Fresh login for all users (JWT TTL = 1h; test = 25min) ──────────
  console.log(`[setup] Logging in ${users.length} users...`);
  const tokens  = [];
  const userIds = [];
  const loginCredentials = [];

  const LOGIN_BATCH = 100;
  for (let i = 0; i < users.length; i += LOGIN_BATCH) {
    const chunk = users.slice(i, i + LOGIN_BATCH);
    const reqs  = chunk.map(u => [
      'POST',
      `${LB}/auth/login`,
      JSON.stringify({ email: u.email, password: u.password }),
      { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'setup_login', service: 'auth' } },
    ]);
    const responses = http.batch(reqs);
    for (let k = 0; k < responses.length; k++) {
      const res = responses[k];
      authLoginDuration.add(res.timings.duration);
      if (res.status === 200) {
        const body = JSON.parse(res.body);
        const tok  = body.token;   // field name confirmed: AuthResponse.token()
        tokens.push(tok);
        const uid = users[i + k].userId || _jwtSub(tok);
        userIds.push(uid);
        loginCredentials.push({ email: users[i + k].email, password: users[i + k].password });
      } else {
        authLoginErrors.add(1);
        tokens.push(null);
        userIds.push(null);
        loginCredentials.push(null);
      }
    }
    sleep(0.05);
  }

  const validTokens = tokens.filter(Boolean);
  console.log(`[setup] Valid tokens: ${validTokens.length}`);

  // ── Step 3: Collect song IDs from catalog ───────────────────────────────────
  // CatalogControllerIT confirms: response has content[].id (Long) and content[].trackId
  // Size is capped at 100 (getSongs_sizeLargerThanMax_cappedAt100).
  const songIds  = [];   // numeric string IDs: used for /stream/ and /recommend/similar/
  const trackIds = [];   // trackId strings: alternative stream target

  if (validTokens.length > 0) {
    const probeToken = validTokens[0];
    const maxPages   = IS_SMOKE ? 3 : 100;

    for (let page = 0; page < maxPages; page++) {
      const res = http.get(
        `${LB}/catalog/songs?page=${page}&size=100`,
        { headers: _hGet(probeToken), tags: { endpoint: 'setup_catalog', service: 'catalog' } },
      );
      if (res.status !== 200) break;
      const body    = JSON.parse(res.body);
      const content = body.content || [];
      if (content.length === 0) break;
      for (const song of content) {
        if (song.id    != null) songIds.push(String(song.id));
        if (song.trackId)       trackIds.push(song.trackId);
      }
      if (body.last) break;
    }
    console.log(`[setup] Collected ${songIds.length} song IDs`);
  }

  // ── Step 4: Pre-create playlists (one per user) ────────────────────────────
  const playlistIds  = [];
  const CREATE_BATCH = 50;
  const plCount      = IS_SMOKE ? validTokens.length : Math.min(validTokens.length, target);

  for (let i = 0; i < plCount; i += CREATE_BATCH) {
    const chunk = validTokens.slice(i, i + CREATE_BATCH);
    const reqs  = chunk.map((tok, k) => [
      'POST',
      `${LB}/playlists`,
      JSON.stringify({ name: `bench_pl_${i + k}` }),
      { headers: _h(tok), tags: { endpoint: 'setup_create_playlist', service: 'playlist' } },
    ]);
    const responses = http.batch(reqs);
    for (const res of responses) {
      if (res.status === 201) {
        playlistIds.push(JSON.parse(res.body).id);
      } else if (res.status === 409) {
        // Already exists; fetch first non-liked playlist
        playlistIds.push(null);  // will be resolved per-VU on first call
      } else {
        playlistIds.push(null);
      }
    }
    sleep(0.05);
  }

  // Pad to match token array length
  while (playlistIds.length < validTokens.length) playlistIds.push(null);
  console.log(`[setup] Playlist IDs obtained: ${playlistIds.filter(Boolean).length}`);

  console.log(`[setup] Done in ${Math.floor((Date.now() - t0) / 1000)}s`);

  return {
    tokens:           validTokens,
    userIds:          userIds.filter(Boolean),
    loginCredentials: loginCredentials.filter(Boolean),
    songIds:          songIds.length  > 0 ? songIds  : ['1', '2', '3', '4', '5'],
    trackIds:         trackIds.length > 0 ? trackIds : ['track-1', 'track-2'],
    playlistIds,
    testStartMs:      Date.now(),
  };
}

// ── Scenario exec functions ────────────────────────────────────────────────────

export default function() {} // required by k6 0.51.0 even when all scenarios use exec

export function streamingPlaybackFlow(data) {
  if (data.tokens.length === 0) return;

  const token  = data.tokens[__VU % data.tokens.length];
  if (!token) return;

  const songId = _pick(data.songIds);
  const phase  = _getPhase(data.testStartMs);
  const tag    = { service: 'streaming', phase };

  // 1. Manifest → emits play.started to Kafka
  const t1  = Date.now();
  const mfR = http.get(
    `${LB}/stream/${songId}`,
    { headers: _hHls(token), tags: { endpoint: 'stream_manifest', service: tag.service, phase: tag.phase } },
  );
  streamingManifestDuration.add(Date.now() - t1);

  const mfOk = check(mfR, { 'stream manifest 200': r => r.status === 200 });
  streamingErrorRate.add(mfOk ? 0 : 1);
  if (!mfOk) {
    streamingManifestErr.add(1);
    return;
  }

  sleep(_jitter(0.03, 0.12));

  // 2. First HLS segment (binary)
  const t2  = Date.now();
  const sgR = http.get(
    `${LB}/stream/${songId}/segment/0`,
    { headers: _hOctet(token), tags: { endpoint: 'stream_segment', service: tag.service, phase: tag.phase } },
  );
  streamingSegmentDuration.add(Date.now() - t2);
  check(sgR, { 'stream segment 200': r => r.status === 200 });

  sleep(_jitter(0.05, 0.15));

  // 3. Complete (80%) or skip (20%) → emits play.ended or play.skipped
  const action = Math.random() < 0.80 ? 'complete' : 'skip';
  const t3  = Date.now();
  const cpR = http.post(
    `${LB}/stream/${songId}/${action}`,
    null,
    { headers: _h(token), tags: { endpoint: 'stream_' + action, service: tag.service, phase: tag.phase } },
  );
  streamingCompleteDuration.add(Date.now() - t3);
  const cpOk = check(cpR, { [`stream ${action} 204`]: r => r.status === 204 });
  if (!cpOk) {
    streamingCompleteErr.add(1);
  } else {
    streamingEventsSent.add(2);  // play.started + play.ended/skipped
  }

  // Kafka lag probe: VU 1 polls once per ~30 iterations to avoid overhead
  if (__VU === 1 && __ITER % 30 === 0) {
    _pollKafkaLag();
  }
}

export function catalogSearchFlow(data) {
  if (data.tokens.length === 0) return;

  const token = data.tokens[__VU % data.tokens.length];
  if (!token) return;

  const phase = _getPhase(data.testStartMs);
  const GENRES = ['Pop', 'Rock', 'Jazz', 'Classical', 'Electronic', 'Hip-Hop', 'R&B', 'Country'];

  // Alternate 50/50 between catalog browse and search
  if (__ITER % 2 === 0) {
    const page = Math.floor(Math.random() * 10);
    const t = Date.now();
    const res = http.get(
      `${LB}/catalog/songs?page=${page}&size=50`,
      { headers: _hGet(token), tags: { endpoint: 'catalog_browse', service: 'catalog', phase } },
    );
    catalogBrowseDuration.add(Date.now() - t);
    const ok = check(res, { 'catalog 200': r => r.status === 200 });
    catalogErrorRate.add(ok ? 0 : 1);
  } else {
    const genre  = GENRES[__VU % GENRES.length];
    const bpmMin = 60 + Math.floor(Math.random() * 80);
    const bpmMax = bpmMin + 40 + Math.floor(Math.random() * 60);
    const t = Date.now();
    const res = http.get(
      `${LB}/search?genre=${encodeURIComponent(genre)}&bpm_min=${bpmMin}&bpm_max=${bpmMax}`,
      { headers: _hGet(token), tags: { endpoint: 'search_query', service: 'search', phase } },
    );
    searchQueryDuration.add(Date.now() - t);
    const ok = check(res, { 'search 200': r => r.status === 200 });
    searchErrorRate.add(ok ? 0 : 1);
    // Periodically include a text query (exercises OpenSearch full-text path)
    if (__ITER % 5 === 0) {
      const TERMS = ['rock', 'pop', 'jazz', 'classical', 'electronic', 'hip hop', 'love', 'night'];
      const q = TERMS[__ITER % TERMS.length];
      const t2 = Date.now();
      const res2 = http.get(
        `${LB}/search?q=${encodeURIComponent(q)}`,
        { headers: _hGet(token), tags: { endpoint: 'search_text', service: 'search', phase } },
      );
      searchQueryDuration.add(Date.now() - t2);
      searchErrorRate.add(res2.status === 200 ? 0 : 1);
    }
  }

  sleep(_jitter(0.05, 0.20));
}

export function authLoginFlow(data) {
  if (data.loginCredentials.length === 0) return;

  const cred  = data.loginCredentials[__VU % data.loginCredentials.length];
  const phase = _getPhase(data.testStartMs);

  const t = Date.now();
  const res = http.post(
    `${LB}/auth/login`,
    JSON.stringify({ email: cred.email, password: cred.password }),
    { headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      tags: { endpoint: 'auth_login', service: 'auth', phase } },
  );
  authLoginDuration.add(Date.now() - t);
  const ok = check(res, { 'auth login 200': r => r.status === 200 });
  authErrorRate.add(ok ? 0 : 1);
  if (!ok) authLoginErrors.add(1);
}

export function playlistMutationFlow(data) {
  if (data.tokens.length === 0) return;

  const vuIdx     = __VU % data.tokens.length;
  const token     = data.tokens[vuIdx];
  if (!token) return;

  const phase      = _getPhase(data.testStartMs);
  const songId     = _pick(data.songIds);
  // data.playlistIds[vuIdx] may be null if setup() got 409 on playlist creation;
  // fall back to the VU-local cache populated on the first iteration of this VU.
  let   playlistId = data.playlistIds[vuIdx] || _vuPlaylistId || null;

  // If no pre-created playlist, fetch the first non-liked-songs playlist
  if (!playlistId) {
    const listRes = http.get(
      `${LB}/playlists`,
      { headers: _h(token), tags: { endpoint: 'playlist_list', service: 'playlist', phase } },
    );
    playlistListDuration.add(listRes.timings.duration);
    if (listRes.status === 200) {
      const arr = JSON.parse(listRes.body);
      if (Array.isArray(arr) && arr.length > 0) {
        // Prefer non-liked-songs playlist
        const pl = arr.find(p => !p.likedSongs) || arr[0];
        playlistId = pl.id;
        _vuPlaylistId = playlistId;  // cache in VU-local module state (persists across iterations)
      }
    }
    if (!playlistId) return;
  }

  const op = __ITER % 3;
  if (op === 0) {
    // List playlists
    const t = Date.now();
    const res = http.get(
      `${LB}/playlists`,
      { headers: _h(token), tags: { endpoint: 'playlist_list', service: 'playlist', phase } },
    );
    playlistListDuration.add(Date.now() - t);
    check(res, { 'playlists 200': r => r.status === 200 });
  } else if (op === 1) {
    // Add track — body must be { songId } (confirmed by PlaylistControllerIT addTrack_returns201)
    const t = Date.now();
    const res = http.post(
      `${LB}/playlists/${playlistId}/tracks`,
      JSON.stringify({ songId }),
      { headers: _h(token), tags: { endpoint: 'playlist_add_track', service: 'playlist', phase } },
    );
    playlistAddTrackDuration.add(Date.now() - t);
    // 201 = added; 409 = duplicate (expected at high rate — not an error)
    const ok = check(res, { 'track added 201|409': r => r.status === 201 || r.status === 409 });
    if (!ok) playlistTrackAddErrors.add(1);
  } else {
    // Get playlist detail
    const t = Date.now();
    const res = http.get(
      `${LB}/playlists/${playlistId}`,
      { headers: _h(token), tags: { endpoint: 'playlist_get', service: 'playlist', phase } },
    );
    playlistListDuration.add(Date.now() - t);
    check(res, { 'playlist get 200': r => r.status === 200 });
  }

  sleep(_jitter(0.02, 0.10));
}

export function recommendationFlow(data) {
  if (data.tokens.length === 0) return;

  const token  = data.tokens[__VU % data.tokens.length];
  if (!token) return;

  const phase = _getPhase(data.testStartMs);

  if (__ITER % 2 === 0) {
    // daily-mix: returns { songs: [...] }  (RecommendationControllerIT line 133)
    const t = Date.now();
    const res = http.get(
      `${LB}/recommend/daily-mix`,
      { headers: _hGet(token), tags: { endpoint: 'recommend_daily_mix', service: 'recommendation', phase } },
    );
    recommendDailyMixDuration.add(Date.now() - t);
    check(res, { 'daily-mix 200': r => r.status === 200 });
  } else {
    // similar/{numericId}: returns { seedSongId, songs: [...] }; 400 on non-numeric
    // songIds are catalog numeric IDs; recommendation-service seeds from same CSV
    const numId = _pick(data.songIds);  // numeric string e.g. "42"
    const t = Date.now();
    const res = http.get(
      `${LB}/recommend/similar/${numId}`,
      { headers: _hGet(token), tags: { endpoint: 'recommend_similar', service: 'recommendation', phase } },
    );
    recommendSimilarDuration.add(Date.now() - t);
    // 200 = success; 200 even for unknown song (fallback response per IT line 172)
    check(res, { 'similar 200': r => r.status === 200 });
  }
}

// ── Kafka lag polling (called from streamingPlaybackFlow on VU 1) ──────────────
function _pollKafkaLag() {
  const res = http.get(KAFKA_EXP_URL + '/metrics', {
    tags:    { endpoint: 'kafka_lag_poll', service: 'kafka_exporter' },
    timeout: '5s',
  });
  if (res.status !== 200) {
    kafkaLagPollErrors.add(1);
    return;
  }
  const analytics = _parsePromGauge(res.body, 'kafka_consumergroup_lag', 'consumergroup="analytics-service"');
  const recommend = _parsePromGauge(res.body, 'kafka_consumergroup_lag', 'consumergroup="recommendation-service"');
  const notif     = _parsePromGauge(res.body, 'kafka_consumergroup_lag', 'consumergroup="notification-service"');
  kafkaLagAnalytics.add(analytics);
  kafkaLagRecommendation.add(recommend);
  kafkaLagNotification.add(notif);
}

function _parsePromGauge(body, metricName, labelSubstr) {
  const lines = body.split('\n');
  for (const line of lines) {
    if (line.startsWith('#') || line.trim() === '') continue;
    if (line.includes(metricName) && line.includes(labelSubstr)) {
      const parts = line.trim().split(/\s+/);
      const val   = parseFloat(parts[parts.length - 1]);
      return isNaN(val) ? 0 : val;
    }
  }
  return 0;
}

// ── teardown() ─────────────────────────────────────────────────────────────────
export function teardown(data) {
  const elapsed = Math.floor((Date.now() - data.testStartMs) / 1000);
  console.log(`[teardown] Test finished. Elapsed: ${elapsed}s`);

  // Final Kafka lag snapshot — monotonicity check
  const res = http.get(KAFKA_EXP_URL + '/metrics', {
    tags: { endpoint: 'teardown_lag', service: 'kafka_exporter' },
    timeout: '5s',
  });
  if (res.status === 200) {
    const lagA = _parsePromGauge(res.body, 'kafka_consumergroup_lag', 'consumergroup="analytics-service"');
    const lagR = _parsePromGauge(res.body, 'kafka_consumergroup_lag', 'consumergroup="recommendation-service"');
    console.log(`[teardown] Final Kafka lag — analytics=${lagA}, recommendation=${lagR}`);
    check({ lagA, lagR }, {
      'analytics consumer lag < 50 000':     d => d.lagA < 50000,
      'recommendation consumer lag < 50 000': d => d.lagR < 50000,
    });
  }

  // Analytics and notification spot checks
  if (data.tokens.length > 0) {
    const tok = data.tokens[0];
    const hRes = http.get(`${LB}/analytics/me/history`,    { headers: _hGet(tok), tags: { endpoint: 'analytics_history', service: 'analytics' } });
    analyticsHistoryDuration.add(hRes.timings.duration);
    check(hRes, { 'analytics history 200': r => r.status === 200 });

    const cRes = http.get(`${LB}/analytics/charts/global`, { headers: _hGet(tok), tags: { endpoint: 'analytics_charts', service: 'analytics' } });
    analyticsChartsDuration.add(cRes.timings.duration);
    check(cRes, { 'analytics charts 200': r => r.status === 200 });

    const nRes = http.get(`${LB}/notifications`, { headers: _hGet(tok), tags: { endpoint: 'notification_list', service: 'notification' } });
    notificationListDuration.add(nRes.timings.duration);
    check(nRes, { 'notifications 200': r => r.status === 200 });
  }
}
