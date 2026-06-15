/**
 * Phase 5 — Peak Load (10,000 VUs, 30 min)
 *
 * WARNING: This script requires:
 *   - Profile D deployment: all service replicas per SCALABILITY.md §5
 *   - ≥ 32 GB host RAM
 *   - 3-broker Kafka cluster (kafka, kafka2, kafka3)
 *   - 12-partition playback-events topic
 *   - nginx-lb active
 *   - Phase 4 (ramp scenario) must have completed without p99 > 2 s
 *
 * This is NOT included in main.js to prevent accidental invocation.
 * Run explicitly: docker exec load-generator k6 run /scripts/phase5-peak.js
 * Or:            K6_PHASE5_TARGET=10000 docker compose --profile load-test run \
 *                  -e K6_SCENARIO=phase5 load-generator
 *
 * Cost note: ~30 min × full Profile D RAM + CPU. Run once per thesis benchmark cycle.
 * Do not repeat without reviewing Phase 4 Grafana results.
 */

import http from 'k6/http';
import { sleep, check } from 'k6';

const BASE_URL = __ENV.NGINX_LB_URL || 'http://nginx-lb:80';
const peakTarget = parseInt(__ENV.K6_PHASE5_TARGET || '10000');

export const options = {
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<8000'],
    http_req_failed: ['rate<0.10'],
  },
  scenarios: {
    peak: {
      executor: 'ramping-vus',
      stages: [
        { duration: '10m', target: peakTarget },
        { duration: '10m', target: peakTarget },
        { duration: '10m', target: 0 },
      ],
    },
  },
};

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

export default function () {
  const username = `vu-p5-${__VU}-${__ITER}-${Date.now()}`;
  const email = `${username}@test.com`;
  const password = 'Password123!';

  const regRes = register(username, email, password);
  check(regRes, { 'register 200/409': (r) => r.status === 200 || r.status === 409 });

  let token = null;
  if (regRes.status === 200) {
    try { token = JSON.parse(regRes.body).token; } catch (_) {}
  }
  if (!token) {
    const loginRes = login(username, password);
    check(loginRes, { 'login 200': (r) => r.status === 200 });
    if (loginRes.status !== 200) return;
    try { token = JSON.parse(loginRes.body).token; } catch (_) {}
  }
  if (!token) return;

  sleep(1);

  const catalogRes = http.get(`${BASE_URL}/catalog/songs?page=0&size=20`, authHeaders(token));
  check(catalogRes, { 'catalog 200': (r) => r.status === 200 });

  let songId = '1';
  try {
    const body = JSON.parse(catalogRes.body);
    const songs = body.content || body.songs || body;
    if (Array.isArray(songs) && songs.length > 0) {
      songId = songs[Math.floor(Math.random() * songs.length)].id || songId;
    }
  } catch (_) {}

  sleep(1);

  const searchRes = http.get(`${BASE_URL}/search?q=rock&page=0`, authHeaders(token));
  check(searchRes, { 'search 200': (r) => r.status === 200 });

  const streamRes = http.get(`${BASE_URL}/stream/${songId}`, authHeaders(token));
  check(streamRes, { 'stream 200': (r) => r.status === 200 });

  sleep(1);

  const historyRes = http.get(`${BASE_URL}/analytics/me/history`, authHeaders(token));
  check(historyRes, { 'history 200': (r) => r.status === 200 });

  const recRes = http.get(`${BASE_URL}/recommend/daily-mix`, authHeaders(token));
  check(recRes, { 'recommendations 200': (r) => r.status === 200 });

  sleep(1);
}
