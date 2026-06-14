export function buildCostSummary(data, scriptName) {
  const metrics = data.metrics || {};
  const value = (metricName, fieldName) => {
    if (!metrics[metricName] || !metrics[metricName].values) {
      return null;
    }
    return metrics[metricName].values[fieldName];
  };

  return {
    script: scriptName,
    profile: __ENV.COST_PROFILE || 'local',
    generatedAt: new Date().toISOString(),
    environment: {
      BASE_URL: __ENV.BASE_URL || 'http://gateway:8080',
      TARGET_REGISTERED_USERS: __ENV.TARGET_REGISTERED_USERS || '1000000',
      TARGET_DAU: __ENV.TARGET_DAU || '100000',
      TARGET_PEAK_CONCURRENT_USERS: __ENV.TARGET_PEAK_CONCURRENT_USERS || '20000',
      TARGET_PLAYBACK_EVENTS_PER_SECOND: __ENV.TARGET_PLAYBACK_EVENTS_PER_SECOND || '40000',
      TARGET_AUTH_LOGINS_PER_SECOND: __ENV.TARGET_AUTH_LOGINS_PER_SECOND || '500',
      TARGET_CATALOG_SEARCH_RPS: __ENV.TARGET_CATALOG_SEARCH_RPS || '4000',
      TARGET_PLAYLIST_MUTATIONS_PER_SECOND: __ENV.TARGET_PLAYLIST_MUTATIONS_PER_SECOND || '200',
    },
    metrics: {
      checksRate: value('checks', 'rate'),
      droppedIterations: value('dropped_iterations', 'count'),
      httpRequests: value('http_reqs', 'count'),
      httpRequestRate: value('http_reqs', 'rate'),
      httpRequestDurationP95: value('http_req_duration', 'p(95)'),
      httpRequestDurationP99: value('http_req_duration', 'p(99)'),
      httpRequestFailedRate: value('http_req_failed', 'rate'),
      iterations: value('iterations', 'count'),
      vusMax: value('vus_max', 'max'),
    },
    interpretation: {
      droppedIterations: 'Non-zero dropped iterations indicate load-generator or host saturation before backend capacity can be claimed.',
      costUse: 'Compare these results with Compose replica counts and resource limits for cost/performance analysis.',
    },
  };
}

export function summaryOutputs(data, scriptName, fileName) {
  const summary = JSON.stringify(buildCostSummary(data, scriptName), null, 2);
  const outputPath = __ENV.K6_COST_SUMMARY_PATH || `/results/${fileName}`;
  const outputs = {};
  outputs.stdout = `${summary}\n`;
  outputs[outputPath] = summary;
  return outputs;
}
