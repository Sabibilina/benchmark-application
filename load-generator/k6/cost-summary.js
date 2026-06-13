const ENV_KEYS = [
  'COST_PROFILE',
  'BASE_URL',
  'BENCHMARK_DURATION',
  'K6_DURATION',
  'K6_RATE_SCALE',
  'K6_RUN_ID',
  'K6_USER_POOL_SIZE',
  'K6_AUTH_LOGIN_RATE',
  'K6_CATALOG_SEARCH_ITER_RATE',
  'K6_STREAMING_SESSION_RATE',
  'K6_PLAYLIST_MUTATION_ITER_RATE',
  'TARGET_REGISTERED_USERS',
  'TARGET_DAU',
  'TARGET_PEAK_CONCURRENT_USERS',
  'TARGET_PLAYBACK_EVENTS_PER_SECOND',
  'TARGET_AUTH_LOGINS_PER_SECOND',
  'TARGET_CATALOG_SEARCH_RPS',
  'TARGET_PLAYLIST_MUTATIONS_PER_SECOND',
];

function envSnapshot() {
  const values = {};
  for (const key of ENV_KEYS) {
    if (__ENV[key] !== undefined) {
      values[key] = __ENV[key];
    }
  }
  return values;
}

function metricSnapshot(metrics, name) {
  const metric = metrics[name];
  if (!metric) {
    return null;
  }
  const values = metric.values || {};
  return {
    count: values.count,
    rate: values.rate,
    avg: values.avg,
    min: values.min,
    med: values.med,
    max: values.max,
    p90: values['p(90)'],
    p95: values['p(95)'],
    p99: values['p(99)'],
  };
}

export function buildCostSummary(data, scriptName) {
  const summary = {
    script: scriptName,
    profile: __ENV.COST_PROFILE || 'local',
    generatedAt: new Date().toISOString(),
    environment: envSnapshot(),
    metrics: {
      checks: metricSnapshot(data.metrics, 'checks'),
      droppedIterations: metricSnapshot(data.metrics, 'dropped_iterations'),
      httpRequests: metricSnapshot(data.metrics, 'http_reqs'),
      httpRequestDuration: metricSnapshot(data.metrics, 'http_req_duration'),
      httpRequestFailed: metricSnapshot(data.metrics, 'http_req_failed'),
      iterations: metricSnapshot(data.metrics, 'iterations'),
      vusMax: metricSnapshot(data.metrics, 'vus_max'),
    },
    interpretation: {
      droppedIterations: 'Treat non-zero dropped iterations as load-generator or host saturation before claiming backend throughput.',
      costUse: 'Compare this metadata with Compose replica counts, CPU limits, memory limits, and host notes for cost-efficiency analysis.',
    },
  };

  const body = `${JSON.stringify(summary, null, 2)}\n`;
  const defaultPath = `/results/${scriptName.replace(/\.js$/, '')}-cost-summary.json`;
  const outputPath = __ENV.K6_COST_SUMMARY_PATH || defaultPath;
  return {
    stdout: `\nCost evidence summary written to ${outputPath}\n${body}`,
    [outputPath]: body,
  };
}
