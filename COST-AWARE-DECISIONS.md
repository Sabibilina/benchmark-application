# Cost-Aware Decisions

This document records decisions, assumptions, trade-offs, evidence, affected files or services, validation findings, and unresolved issues from the cloud cost-efficiency improvement session defined in `REFACTORED-PROMPTS.md`.

Use this structure where applicable:

| Field | Content |
| --- | --- |
| What changed | The specific planned or implemented modification. |
| Where | Files, services, configs, tests, docs, or runtime components affected. |
| Why | The reason for the change. |
| Cost driver targeted | The resource, operational cost, or waste pattern addressed. |
| Evidence | Repository evidence, workload assumption, metric, test result, or source document. |
| Expected cost impact | The expected reduction or cost-control effect. |
| Risk or trade-off | Possible behavior, performance, reliability, or observability downside. |
| Validation | Checks run, evidence collected, or validation still needed. |
| Status | Planned, implemented, validated, deferred, rejected, or unresolved. |

## Session 11 - Cloud Cost-Efficiency Improvement

### Step 1 - Plan

#### Assumptions

- The architecture remains backend-only and keeps the eight required backend services: Auth, Catalog, Streaming, Playlist, Search, Analytics, Recommendation, and Notification.
- Required behavior from `ARCHITECTURE.md` and `REQUIREMENTS.md` must be preserved; this phase is not a feature-removal exercise.
- `SCALABILITY.md` is the source of truth for workload assumptions: 1,000,000 registered users, about 100,000 DAU, about 20,000 peak concurrent users, about 10 songs per session, about 40,000 playback events/s, about 500 auth logins/s, about 4,000 catalog/search requests/s, and about 200 playlist mutations/s.
- Docker Compose remains the benchmark runtime. Kubernetes, Helm, Terraform, managed services, and other orchestration or cloud-provisioning systems remain out of scope for this repository.
- Cost-efficiency changes should be measurable through existing Compose profiles, k6 workload scripts, Prometheus/Grafana metrics, and backend tests.

#### Main Cost Drivers

- **Streaming and Kafka path:** `SCALABILITY.md` identifies Streaming as the highest-volume path, and the 1M profile scales Streaming to 20 replicas while targeting about 40,000 playback events/s.
- **Search/OpenSearch:** Search shares the 4,000 catalog/search requests/s target and depends on a comparatively memory-heavy OpenSearch container.
- **Analytics/ClickHouse:** Analytics consumes the playback-event stream and stores history/charts in ClickHouse, which is CPU, memory, and disk sensitive under high insert volume.
- **Recommendation/PostgreSQL/Redis:** Recommendation combines API reads, Kafka consumers, durable state, and Redis cache memory.
- **Always-on support components:** Prometheus, Grafana, exporters, cAdvisor, k6, and the gateway are valuable for benchmarks but add baseline CPU/memory cost when running in non-benchmark environments.
- **Direct host exposure:** Base Compose publishes service and infrastructure ports for debugging, while scaled profiles remove application ports. Keeping every port open is useful locally but not always cost- or operations-efficient for benchmark/cloud-like runs.

#### Prioritized Planned Changes

| Priority | Planned change | Expected impact | Risk |
| --- | --- | --- | --- |
| P1 | Add a cost-focused Compose/run profile strategy that separates always-needed backend runtime from benchmark-only helpers while keeping the documented benchmark path intact. | Reduces idle CPU/memory for non-benchmark runs and makes benchmark cost attributable. | Medium: must not violate `REQUIREMENTS.md` M-19 or make monitoring/load generation unavailable. |
| P1 | Add benchmark cost evidence capture to docs/scripts so each run records active profile, replica counts, resource limits, workload rates, and host notes. | Makes cost comparisons repeatable and analyzable after runs. | Low: documentation/script-output focused. |
| P1 | Review 1M and 100k scale-profile allocations against observed bottleneck order and workload assumptions; plan conservative reductions or staged profiles where components are over-provisioned before evidence exists. | Avoids paying for target-scale resources before bottleneck evidence justifies them. | Medium: under-sizing can invalidate performance tests if not staged. |
| P2 | Reduce idle observability/exporter overhead outside benchmark runs while preserving full observability for scalability validation. | Lowers baseline runtime cost for development and smoke tests. | Medium: less visibility if users run reduced profiles accidentally during benchmark validation. |
| P2 | Add or document retention/storage-growth controls for stateful benchmark data where current configuration leaves growth ambiguous. | Controls long-running storage cost for Kafka, Prometheus, ClickHouse, MongoDB, and persistent volumes. | Medium: overly aggressive retention can remove evidence needed for analysis. |
| P2 | Revisit cache and response reuse opportunities that serve repeated read-heavy paths under the documented 100,000 DAU workload. | Reduces repeated database/search/analytics work for popular catalog, recommendation, and chart reads. | Medium: stale data and cache cardinality must be bounded. |
| P3 | Document a cost-validation workflow that compares baseline, 100k, and 1M profiles using existing k6 and Prometheus metrics before and after changes. | Turns cost-efficiency claims into measurable evidence. | Low: mostly documentation, but depends on Docker availability for live evidence. |

#### Planned Change Entries

##### Planned Change 1 - Cost-Focused Compose Profile Strategy

| Field | Content |
| --- | --- |
| What changed | Plan to introduce or document a clearer separation between minimal backend runtime, observability, load-generation, and full benchmark profiles. |
| Where | `docker-compose.yml`, scale override files, `README.md`, `SCALABILITY.md`, `.env.example`, and validation commands if implemented. |
| Why | The base stack currently includes backend services plus Prometheus, Grafana, exporters, cAdvisor, k6, gateway, and all stateful infrastructure. Some of these are essential for benchmarks but are idle cost for non-benchmark runs. |
| Cost driver targeted | Idle CPU and memory from always-on observability, load-generation, gateway/exporter, and support containers. |
| Evidence | `docker-compose.yml` defines Prometheus, Grafana, nginx-exporter, redis-exporter, kafka-exporter, cAdvisor, k6, and gateway in the base runtime; `REQUIREMENTS.md` M-19 and M-24 require them for the compose deliverable and monitoring. |
| Expected cost impact | Lower non-benchmark runtime footprint while preserving full benchmark capability. |
| Risk or trade-off | If profiles are too aggressive, users may run benchmarks without required monitoring or load-generation services. The documented default/benchmark commands must stay unambiguous. |
| Validation | Compose config checks for base, baseline, 100k, and 1M profiles; `docker compose config --services`; smoke run for the documented benchmark profile when Docker is available. |
| Status | Planned. |

##### Planned Change 2 - Benchmark Cost Evidence Capture

| Field | Content |
| --- | --- |
| What changed | Plan to make benchmark runs record enough context for cost analysis: selected profile, replicas, resource limits, k6 rates, workload scale, host notes, and validation result. |
| Where | `README.md`, `SCALABILITY.md`, `TESTS.md`, load-generator documentation, and possibly k6 summary output if implementation chooses code changes. |
| Why | Cost-efficiency experimentation needs comparable evidence, not only successful/failed load tests. Without run metadata, later analysis cannot attribute cost changes to configuration changes. |
| Cost driver targeted | Analysis waste and unrepeatable benchmark results; over-provisioning caused by unclear evidence. |
| Evidence | `SCALABILITY.md` states every benchmark run should record replica counts, CPU/memory limits, dataset size, k6 profile, and host hardware; `README.md` already documents profile commands and target rates. |
| Expected cost impact | Indirect but important: enables evidence-based resource reduction and prevents premature scaling. |
| Risk or trade-off | Adds documentation/process overhead. |
| Validation | Review generated docs/output for the required metadata fields; run k6 inspect or a smoke run if scripts change. |
| Status | Planned. |

##### Planned Change 3 - Stage Target-Scale Resources Before Full 1M Runs

| Field | Content |
| --- | --- |
| What changed | Plan to review the 100k and 1M scale override defaults and introduce or document staged cost profiles so full target resources are used only after lower stages show the relevant bottleneck. |
| Where | `docker-compose.scale-100k.yml`, `docker-compose.scale-1m.yml`, `SCALABILITY.md`, `.env.example`, and README benchmark commands. |
| Why | The 1M profile allocates high replica counts and large limits, such as 20 Streaming replicas, 8 Catalog/Search/Analytics/Recommendation replicas, larger Kafka/OpenSearch/ClickHouse/Redis allocations, and high k6 resources. These are valid target-shape resources but expensive if used before bottlenecks are proven. |
| Cost driver targeted | Over-provisioned compute and memory in target-scale benchmark profiles. |
| Evidence | `SCALABILITY.md` recommends staged load phases and says not to jump to the full mixed 1M-user scenario before isolating bottlenecks; scale override files encode large target resources. |
| Expected cost impact | Lower cost during calibration and fewer expensive failed full-target runs. |
| Risk or trade-off | Too-small staged profiles may hide target-only bottlenecks. The plan must keep the full 1M target path available. |
| Validation | Compose config checks for each profile; compare k6 dropped iterations, p95/p99 latency, Kafka lag, and container saturation across stages. |
| Status | Planned. |

##### Planned Change 4 - Observability Cost Controls

| Field | Content |
| --- | --- |
| What changed | Plan to reduce or make optional non-essential observability/exporter overhead outside full benchmark validation, while preserving Prometheus/Grafana evidence for benchmark runs. |
| Where | `docker-compose.yml`, `config/prometheus/prometheus.yml`, `README.md`, `SCALABILITY.md`, and `.env.example`. |
| Why | Prometheus, Grafana, cAdvisor, gateway metrics, Kafka exporter, and Redis exporter are useful but consume resources even when not analyzing a benchmark. |
| Cost driver targeted | Idle observability CPU/memory and metric storage growth. |
| Evidence | `docker-compose.yml` defines multiple observability services with CPU/memory limits; `SCALABILITY.md` requires metrics for scaling decisions. |
| Expected cost impact | Reduced baseline resource footprint for development/smoke runs. |
| Risk or trade-off | Reduced profiles may miss issues if used for benchmark validation. Documentation must make full-observability runs the required benchmark mode. |
| Validation | Compose config checks, Prometheus config validation, Grafana dashboard JSON validation, and verification that full benchmark profile still includes required metrics. |
| Status | Planned. |

##### Planned Change 5 - Stateful Retention And Storage-Growth Review

| Field | Content |
| --- | --- |
| What changed | Plan to review and add/document bounded retention or cleanup controls for stateful components where long benchmark runs can grow storage. |
| Where | Kafka topic initialization, Prometheus command/config, ClickHouse schema/config, MongoDB notification storage, Redis config, README/SCALABILITY guidance. |
| Why | Long-running benchmarks can accumulate Kafka logs, Prometheus time series, ClickHouse analytics rows, MongoDB notifications, and persistent volume data. Storage growth is a direct cloud cost driver. |
| Cost driver targeted | Persistent disk/storage growth and retained benchmark data. |
| Evidence | `docker-compose.yml` defines named volumes for Kafka, Prometheus, ClickHouse, MongoDB, OpenSearch, Redis, and PostgreSQL; `SCALABILITY.md` calls out Kafka, ClickHouse, Redis, MongoDB, and Prometheus metrics as benchmark concerns. |
| Expected cost impact | More predictable storage usage and lower risk of costly/unbounded volumes. |
| Risk or trade-off | Retention windows that are too short can remove data needed for validation or analysis. |
| Validation | Compose config checks; service-specific tests if schema/config behavior changes; documentation review for retention rationale. |
| Status | Planned. |

##### Planned Change 6 - Read-Path Reuse And Cache Review

| Field | Content |
| --- | --- |
| What changed | Plan to evaluate bounded reuse/caching for repeated read-heavy paths such as catalog pages, global charts, and recommendation responses, without caching writes or user-specific high-cardinality data blindly. |
| Where | Gateway config, Catalog Service, Analytics Service, Recommendation Service, Redis config, service tests, and `SCALABILITY.md` if implemented. |
| Why | The workload includes about 4,000 catalog/search requests/s and frequent recommendation/history/chart reads. Repeated reads can drive database/search/analytics cost if every request recomputes or refetches the same data. |
| Cost driver targeted | Repeated database, OpenSearch, ClickHouse, and service CPU work on hot read paths. |
| Evidence | `SCALABILITY.md` identifies cache candidates for daily mix, similar songs, global charts, catalog first pages/popular filters, and measured hot searches; `docker-compose.yml` already includes Redis and Nginx gateway cache configuration. |
| Expected cost impact | Lower backend CPU and database/search load for hot repeated reads. |
| Risk or trade-off | Stale responses, cache fragmentation, and extra Redis memory use if cache keys are too broad or unbounded. |
| Validation | Focused unit/integration tests for cache TTL/invalidations if code changes; k6 read-heavy phase; Redis hit/miss and memory metrics; endpoint correctness checks. |
| Status | Planned. |

##### Planned Change 7 - Direct Host Port And Debug Surface Review

| Field | Content |
| --- | --- |
| What changed | Plan to review whether direct application and infrastructure host ports should remain enabled by default or be limited to debug/development profiles, while keeping gateway-based benchmark traffic intact. |
| Where | `docker-compose.yml`, scale override files, `.env.example`, README run instructions. |
| Why | Direct host ports are convenient for local debugging, but scaled benchmark profiles already remove application ports and route through the gateway. Reducing default exposure can simplify cloud-like runs and avoid unnecessary public/debug surfaces. |
| Cost driver targeted | Operational overhead and misconfigured benchmark paths rather than raw CPU cost. |
| Evidence | Base Compose publishes backend service and infrastructure ports; scale profiles reset application ports; `SCALABILITY.md` says benchmark traffic should enter through the reverse proxy. |
| Expected cost impact | Indirect: fewer accidental direct-service benchmark runs and cleaner profile separation. |
| Risk or trade-off | Developers may lose convenient local access unless debug instructions remain clear. |
| Validation | Compose config inspection to confirm benchmark profiles route through gateway; README command review; smoke test via gateway. |
| Status | Planned. |

#### Validation Plan For Step 2/3

- Static Compose validation: `docker compose config --quiet`, plus baseline, 100k, and 1M override config checks.
- Service list inspection: `docker compose config --services` to confirm all eight backend services and required infrastructure remain available in the documented benchmark path.
- Focused backend tests for any service behavior touched by implementation, especially cache behavior, retention-related behavior, pagination limits, or metrics.
- k6 script inspection and smoke run when Docker is available.
- Metrics evidence from Prometheus/Grafana for benchmark runs: request rates, p95/p99 latency, error rates, Kafka lag, Redis hit/miss and memory, DB connection pools, container CPU/memory, OpenSearch/ClickHouse pressure.
- Documentation review to confirm each cost-saving claim names the workload assumption, expected impact, trade-off, and validation evidence.
