/**
 * seed.js — Pre-register users and collect song IDs for the main load test.
 *
 * Writes load-generator/scripts/data/seed.json (via handleSummary).
 * Run ONCE before main.js, then re-run only when the data volume needs
 * to change or tokens look stale.
 *
 *   k6 run -e SEED_USER_COUNT=25000 \
 *          -e NGINX_LB_URL=http://localhost:80 \
 *          load-generator/scripts/seed.js
 *
 * Resulting seed.json shape:
 *   { "users": [{ "email", "password", "userId" }], "generatedAt": "ISO8601" }
 *
 * NOTE: Passwords are intentionally simple test-only credentials.
 * The file should not be committed (add to .gitignore).
 */

import http from 'k6/http';
import { sleep, check } from 'k6';
import encoding from 'k6/encoding';

const LB             = __ENV.NGINX_LB_URL    || 'http://nginx-lb';
const USER_COUNT     = parseInt(__ENV.SEED_USER_COUNT || '500', 10);
const BATCH_SIZE     = parseInt(__ENV.SEED_BATCH_SIZE || '50',  10);
const PASSWORD       = 'Bench1234!';

// Module-level accumulator — accessible from handleSummary()
// because setup() and handleSummary() share the main-thread context.
const _collectedUsers = [];

// ── options ────────────────────────────────────────────────────────────────────
export const options = {
  // Minimal VU scenario — all real work happens in setup()
  scenarios: {
    seed_runner: {
      executor:  'constant-vus',
      vus:       1,
      duration:  '1s',
      exec:      '_noop',
      startTime: '0s',
    },
  },
  // No thresholds — this script just registers users
  setupTimeout: '10m',
};

// ── setup() ───────────────────────────────────────────────────────────────────
export function setup() {
  console.log(`[seed] Registering ${USER_COUNT} users in batches of ${BATCH_SIZE}...`);
  const t0 = Date.now();
  let ok = 0;
  let fail = 0;

  for (let i = 0; i < USER_COUNT; i += BATCH_SIZE) {
    const batchEnd = Math.min(i + BATCH_SIZE, USER_COUNT);
    const reqs = [];

    for (let j = i; j < batchEnd; j++) {
      // Use a stable index so re-runs with the same count produce the same emails
      const email = `bench_${j}@bench.local`;
      reqs.push([
        'POST',
        `${LB}/auth/register`,
        JSON.stringify({ username: `bench_${j}`, email, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'seed_register' } },
      ]);
    }

    const responses = http.batch(reqs);

    for (let k = 0; k < responses.length; k++) {
      const res = responses[k];
      const idx = i + k;
      const email = `bench_${idx}@bench.local`;

      if (res.status === 201) {
        const body   = JSON.parse(res.body);
        const token  = body.token;
        const userId = _jwtSub(token);
        _collectedUsers.push({ email, password: PASSWORD, userId });
        ok++;
      } else if (res.status === 409) {
        // Already registered (re-run) — still record credential
        _collectedUsers.push({ email, password: PASSWORD, userId: null });
        ok++;
      } else {
        fail++;
        console.warn(`[seed] Register failed idx=${idx} status=${res.status}`);
      }
    }

    if ((i / BATCH_SIZE) % 10 === 0) {
      console.log(`[seed] Progress: ${Math.min(i + BATCH_SIZE, USER_COUNT)}/${USER_COUNT}`);
    }
    sleep(0.05);
  }

  const elapsed = Math.floor((Date.now() - t0) / 1000);
  console.log(`[seed] Done. OK=${ok} FAIL=${fail} elapsed=${elapsed}s`);
  return { ok, fail };
}

// ── Noop exec function ────────────────────────────────────────────────────────
export function _noop() {}   // eslint-disable-line
export default function() {} // required by k6 validator even when all scenarios use exec

// ── handleSummary — writes seed.json ─────────────────────────────────────────
export function handleSummary(data) {   // eslint-disable-line no-unused-vars
  const out = {
    users:       _collectedUsers,
    generatedAt: new Date().toISOString(),
    count:       _collectedUsers.length,
  };
  const json = JSON.stringify(out, null, 2);
  console.log(`[seed] Writing ${_collectedUsers.length} users to data/seed.json`);
  return {
    // Path relative to script working directory (/scripts in Docker)
    './data/seed.json': json,
    // Also print a brief summary to stdout
    stdout: `Seed complete: ${_collectedUsers.length} users written to data/seed.json\n`,
  };
}

// ── JWT sub decoder ───────────────────────────────────────────────────────────
function _jwtSub(token) {
  try {
    const payload = token.split('.')[1];
    const decoded = encoding.b64decode(payload, 'rawurl', 's');
    return JSON.parse(decoded).sub || null;
  } catch (_) {
    return null;
  }
}
