# Cost-Aware Prompt Set

These prompts follow the same plan → generate → validate/fix pattern as the baseline, but add explicit cost-efficiency incentives at every stage. Every architectural and implementation decision must minimize long-term operational cost while meeting functional requirements. When a cheaper option is equally correct, prefer it and record why in DECISIONS.md.

---

## First Prompt Set

### Prompt 1

Use ARCHITECTURE.md as the source of truth for system shape, validation requirements, and implementation technology choices. If any conflicts arise, stop and report the conflict instead of guessing.

For this step, do not generate service business logic yet. Create an implementation plan only for the shared deployment environment and repository skeleton. Include:
- proposed repository structure,
- docker-compose.yml structure,
- named Docker network,
- supporting infrastructure needed by the current baseline,
- service directory layout,
- config/env file strategy,
- validation steps for this phase.

Apply the following cost-efficiency constraints throughout the plan:

- Every container must declare resource limits appropriate to its expected workload. Evaluate each service's role and size accordingly; justify over-allocation if you choose it.
- All multi-service infrastructure must include storage bounds in the plan. Unbounded growth is not acceptable; choose retention or eviction strategies that fit the data's useful lifetime.
- The shared nginx reverse proxy must be planned to reduce redundant data transfer for both API responses and static assets.
- Identify which services share a persistence technology and plan connection pool sizing to avoid holding more connections than necessary for the expected concurrency.
- Plan a shared caching layer. For each service that benefits from caching, identify what data to cache and how long it should remain valid before being invalidated.

Do not generate code yet. State assumptions explicitly and do not invent requirements beyond the document.

Record important planning decisions in DECISIONS.md, including what was decided, why it was decided, which document or requirement justified it, and which files or services are expected to be affected. For every infrastructure sizing decision, record the cost-efficiency rationale. Check the necessary boxes from PROGRESS.md to reflect the completed steps.

---

### Prompt 2

Now generate the repository skeleton and shared deployment environment according to the approved plan and the ARCHITECTURE.md document. Generate:
- top-level folder structure,
- docker-compose.yml,
- shared config files,
- service folders,
- placeholder Dockerfiles only where needed for the current phase,
- README with startup instructions for this phase.

Apply the following cost-efficiency requirements during generation:

- Every service Dockerfile must use a multi-stage build. The final stage must use a minimal base image suitable for a production runtime. Avoid shipping build-time tooling to production.
- Every container in docker-compose.yml must declare resource limits consistent with the approved plan.
- Kafka topics must be created with explicit retention configuration chosen to reflect how long the data remains useful. Do not leave retention at the broker default; justify your chosen values.
- Set a cost-bounded broker-level log retention default that reflects the workload.
- The nginx configuration must reduce unnecessary data transfer: enable response compression for applicable content types and set appropriate caching headers for static assets.
- Health check intervals must be long enough to avoid unnecessary container churn while still detecting failures promptly.

Do not implement full service business logic yet. All output must be runnable and consistent with the document.

After generating the repository skeleton and shared deployment environment, update PROGRESS.md and DECISIONS.md to reflect what was completed in this phase and document every important decision taken during generation, including the reason, affected files, and any cost-efficiency assumptions made.

---

### Prompt 3

Now review the generated repository skeleton and deployment environment against the ARCHITECTURE.md document. Validate that:
- the compose file reflects the required architecture,
- the shared network is defined,
- services and infrastructure are organized correctly,
- the setup supports the later module-by-module workflow,
- no requirements were added that are not in the document.

Also validate the following cost-efficiency properties:
- Every container has a resource limit defined and that limit is justified by the service's role.
- Every Kafka topic has explicit retention configuration that reflects the data's useful lifetime.
- Multi-stage Dockerfiles are present and the runtime stage avoids shipping unnecessary build-time tooling.
- nginx compression and cache headers are present, correct, and applied to the right content types.
- Health check intervals are not unnecessarily short.

Fix any issues and output only the changed files.

Before considering the phase complete, update PROGRESS.md and DECISIONS.md to mark validated checklist items, record fixes made, and document every important decision, correction, deviation, or unresolved issue found during validation.

---

## Second Prompt Set

Work in iterative prompts: plan → generate → validate/fix.

- Session 1: Auth Service
- Session 2: Catalog Service + seed logic
- Session 3: Playlist Service
- Session 4: Streaming Service
- Session 5: Search Service
- Session 6: Analytics Service
- Session 7: Recommendation Service
- Session 8: Notification Service
- Session 9: Frontend
- Session 10: Monitoring + load generator + integration fixes

---

### Prompt Pattern

Use this pattern for every session.

#### Prompt 1 — Plan

Use ARCHITECTURE.md as the source of truth for system shape, validation requirements, and implementation technology choices. If any conflicts arise, stop and report the conflict instead of guessing.

Create an implementation plan only for the current phase or service. Include:
- chosen stack,
- file tree,
- dependencies,
- endpoints or exposed interfaces,
- env vars,
- persistence or messaging approach where applicable,
- validation steps,
- required unit tests and integration tests for this phase.

Apply the following cost-efficiency requirements to the plan:

**Containerization**
- The Dockerfile must use a multi-stage build. The runtime stage must use a minimal production-suitable base image; justify your choice.
- JVM-based services must plan for container-aware heap sizing: choose heap bounds that fit within the container's memory limit without wasting headroom. Record the rationale for the values chosen.

**Database and connection management**
- Plan connection pool sizes that match the service's expected concurrency without holding idle connections unnecessarily. Record the rationale.
- Identify all queries that will run on hot paths. Plan indexes for those columns and explain why each index is justified.
- If the service reads the same data repeatedly across requests, plan a cache entry for it: specify the key pattern and how long the data should remain valid before re-fetching.

**Messaging**
- Kafka producers must be configured to batch and compress messages. Choose parameter values appropriate for the service's throughput profile and record your reasoning.
- Kafka consumers must be idempotent: plan a deduplication strategy and explain why it is the appropriate choice for this service.
- Planned Kafka topics must include retention bounds that reflect how long the data remains useful.

**API design**
- Every list endpoint must support and enforce pagination with a hard cap on page size. Choose a cap appropriate to the data and the expected client; justify it.
- No endpoint may return an unbounded result set.

**Resilience**
- Any service that calls another service over HTTP must plan a circuit breaker. Choose threshold values and explain the trade-off between sensitivity and false positives.

**Logging**
- Plan the production log level to minimize I/O and storage cost while retaining actionable signal. Verbose logging must be opt-in.

**Analytics service only**
- Raw events must not be stored indefinitely. Plan a pre-aggregation strategy that discards or archives raw events after they have served their purpose, and choose a time window that balances freshness against storage cost.

**Search service only**
- Plan OpenSearch index settings appropriate for the expected data volume and deployment topology. Justify the number of primary shards and replicas chosen.

Do not generate code yet. State assumptions explicitly. If a detail is missing, list it instead of inventing behavior.

Record important planning decisions in DECISIONS.md, including what was decided, why it was decided, which document or requirement justified it, and which files or services are expected to be affected. For every sizing or retention decision, record the cost-efficiency rationale. Check the necessary boxes from PROGRESS.md to reflect the completed steps.

---

#### Prompt 2 — Generate

Now generate the complete runnable implementation for the current phase or service exactly according to the approved plan.

Use ARCHITECTURE.md. Include source code, Dockerfile, dependency manifest, config, tests, and a short README. No pseudocode and no placeholders.

Apply the following cost-efficiency requirements during generation:

**Containerization**
- Multi-stage Dockerfile: builder stage uses the full SDK; runtime stage uses a minimal production image consistent with the plan.
- For Java services, configure container-aware heap sizing via environment variables in the Dockerfile or docker-compose service definition, using the values chosen in the plan.

**Caching**
- Cache all data identified in the plan. Apply TTLs as planned; do not cache data without an expiry.
- Cache any data that is read more than once per request or that is expensive to recompute.

**Database**
- Declare all planned indexes in the schema migration or entity definition.
- Configure the connection pool explicitly in application.yml using the values from the plan. Do not rely on framework defaults.
- Disable connection-holding during view rendering if the framework provides such a setting.

**Messaging**
- Configure Kafka producers with the batching and compression parameters chosen in the plan.
- Implement the planned idempotency check in every Kafka consumer before processing a message.
- Include retention bounds in topic creation for every topic this service owns.

**API**
- Enforce the size cap on every paginated endpoint. Return an appropriate error if the client requests more than the maximum.
- Enable response compression for this service using the approach appropriate to its runtime.

**Resilience**
- Add a circuit breaker on every outbound HTTP call using the threshold values from the plan.

**Logging**
- Configure the production log level to minimize noise and storage cost, as planned.
- Use structured logging so log lines are compact and machine-parseable.

**Analytics service only**
- Implement the time-windowed aggregation pipeline. Raw events consumed from Kafka must be aggregated before persistence. Store aggregates per time bucket using the window chosen in the plan. Do not write every raw event to the database.

**Notification service only**
- Batch outbound notifications: accumulate events for a configurable window before dispatching, to reduce the number of external calls. Use the window size chosen in the plan.

Whenever applicable, also generate:
- unit tests for core business logic,
- integration tests for required endpoints and persistence behavior,
- event production or consumption tests for messaging-based services,
- frontend component/integration tests for critical UI flows.

After generating the implementation, update PROGRESS.md and DECISIONS.md to reflect what was completed in this phase and document every important decision taken during generation, including the reason, affected files, and any cost-efficiency assumptions made.

---

#### Prompt 3 — Validate/Fix

Review the generated output for the current phase or service against ARCHITECTURE.md.

Validate that:
- all required endpoints or interfaces exist,
- the implementation matches the approved plan,
- no extra requirements were invented,
- configuration and containerization are correct,
- relevant validation commands pass,
- required unit tests and integration tests exist and pass for this phase.

Also validate the following cost-efficiency properties. Each item must pass before the phase is considered complete:

- Multi-stage Dockerfile: final stage uses a minimal production image; no build-time tooling present at runtime.
- JVM heap bounds: container-aware sizing enabled; explicit heap limits configured.
- Connection pool size: explicitly configured in application.yml; justified by the service's concurrency profile.
- Connection holding during view rendering: disabled if the framework provides such a setting.
- Cache + TTL: every planned cache key has an explicit expiry; no indefinite caching.
- Kafka producer efficiency: batching and compression configured; values consistent with the plan.
- Kafka retention: retention bounds set on all owned topics; values reflect data usefulness lifetime.
- Idempotent consumer: deduplication logic present before message processing.
- Pagination cap: list endpoints reject requests above the planned cap.
- Response compression: enabled for applicable content types.
- Circuit breaker: present on every outbound HTTP call; thresholds consistent with the plan.
- Log level: production profile configured to minimize noise; verbose logging is opt-in.
- Indexes declared: all hot-path query columns have indexes in the schema.

Run the relevant checks for this phase. Fix missing or incorrect parts. Output only the changed files. Do not modify unrelated files. State assumptions explicitly. Prefer regeneration over patching if the architecture is wrong. Do not consider the phase complete if the implementation or its tests fail, or if any cost-efficiency property above is missing.

Before considering the phase complete, update PROGRESS.md and DECISIONS.md to mark validated checklist items, record fixes made, and document every important decision, correction, deviation, or unresolved issue found during validation.
