/**
 * kafka-lag-check.js — Standalone Kafka consumer-lag checker.
 *
 * Polls the kafka-exporter Prometheus endpoint every POLL_INTERVAL_S seconds
 * and emits Gauge metrics. Fails the run if any consumer group's lag exceeds
 * the configured threshold.
 *
 * Run independently before or during a load test to observe lag:
 *
 *   k6 run -e KAFKA_EXPORTER_URL=http://localhost:9308 \
 *          -e LAG_THRESHOLD=10000 \
 *          -e CHECK_DURATION=5m \
 *          load-generator/scripts/kafka-lag-check.js
 *
 * Consumer groups checked (names from each service's application.yml):
 *   analytics-service     → topic: playback-events
 *   recommendation-service → topic: playback-events
 *   notification-service  → topic: playlist-events
 */

import http  from 'k6/http';
import { sleep, check } from 'k6';
import { Gauge, Counter, Trend } from 'k6/metrics';

const KAFKA_EXP_URL   = __ENV.KAFKA_EXPORTER_URL || 'http://kafka-exporter:9308';
const LAG_THRESHOLD   = parseInt(__ENV.LAG_THRESHOLD   || '10000', 10);
const POLL_INTERVAL_S = parseInt(__ENV.POLL_INTERVAL_S || '10',    10);
const CHECK_DURATION  = __ENV.CHECK_DURATION || '2m';

// ── Metrics ────────────────────────────────────────────────────────────────────
const lagAnalytics      = new Gauge('kafka_lag_analytics');
const lagRecommendation = new Gauge('kafka_lag_recommendation');
const lagNotification   = new Gauge('kafka_lag_notification');
const pollErrors        = new Counter('kafka_lag_poll_errors');
const pollDuration      = new Trend('kafka_lag_poll_duration_ms', true);

export const options = {
  scenarios: {
    lag_poller: {
      executor: 'constant-vus',
      vus:      1,
      duration: CHECK_DURATION,
      exec:     'lagPollFlow',
    },
  },
  thresholds: {
    'kafka_lag_analytics':      [`value<${LAG_THRESHOLD}`],
    'kafka_lag_recommendation': [`value<${LAG_THRESHOLD}`],
    'kafka_lag_notification':   [`value<${LAG_THRESHOLD}`],
    'kafka_lag_poll_errors':    ['count<5'],
  },
};

export function lagPollFlow() {
  const t   = Date.now();
  const res = http.get(KAFKA_EXP_URL + '/metrics', {
    tags:    { endpoint: 'kafka_lag_poll', service: 'kafka_exporter' },
    timeout: '5s',
  });
  pollDuration.add(Date.now() - t);

  const ok = check(res, { 'kafka-exporter 200': r => r.status === 200 });
  if (!ok) {
    pollErrors.add(1);
    sleep(POLL_INTERVAL_S);
    return;
  }

  const body = res.body;

  const analytics = _parseGauge(body, 'kafka_consumergroup_lag', 'consumergroup="analytics-service"');
  const recommend = _parseGauge(body, 'kafka_consumergroup_lag', 'consumergroup="recommendation-service"');
  const notif     = _parseGauge(body, 'kafka_consumergroup_lag', 'consumergroup="notification-service"');

  lagAnalytics.add(analytics);
  lagRecommendation.add(recommend);
  lagNotification.add(notif);

  check({ analytics, recommend, notif }, {
    [`analytics lag < ${LAG_THRESHOLD}`]:      d => d.analytics < LAG_THRESHOLD,
    [`recommendation lag < ${LAG_THRESHOLD}`]: d => d.recommend < LAG_THRESHOLD,
    [`notification lag < ${LAG_THRESHOLD}`]:   d => d.notif     < LAG_THRESHOLD,
  });

  console.log(
    `[lag] analytics=${analytics} recommendation=${recommend} notification=${notif}`,
  );

  sleep(POLL_INTERVAL_S);
}

// ── Prometheus text-format parser ─────────────────────────────────────────────
// Finds the first line containing both metricName and labelSubstr;
// returns the numeric value from the last whitespace-delimited token.
function _parseGauge(body, metricName, labelSubstr) {
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
