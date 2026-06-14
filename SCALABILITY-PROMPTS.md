# Cost-Aware Scalability Prompt Set

Use this prompt set to create the cost-aware version of the Docker Compose scalability implementation. The system must remain fully Docker Compose based.

Work in three steps: plan -> generate -> validate/fix.

## 1. Plan

Use the current repository as the implementation baseline. Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the sources of truth for required behavior and system boundaries. Use `TECH-STACK.md` as the source of truth for allowed implementation technologies. If these documents conflict, stop and report the conflict instead of guessing.

The system already runs fully in Docker Compose and must stay fully in Docker Compose. Do not introduce Kubernetes, Helm, Nomad, Swarm, Terraform, managed cloud services, or any other orchestrator/provisioning layer. Keep the solution centered on Dockerfiles, `docker-compose.yml`, Compose overrides/profiles, service configuration, observability, k6, and container-level tuning.

Target scalability plan:

| Metric | Estimate | Rationale |
|---|---:|---|
| Registered users | 1,000,000 | Target |
| DAU | 100,000 | 10% of registered users, industry norm for streaming apps |
| Peak concurrent users | 20,000 | 20% of DAU active at the same time |
| Avg. songs streamed per session | 10 | About 30 min/session at 3 min/song |
| Playback events per second, peak | ~40,000 | 20,000 users times 2 events/song average |
| Auth logins per second, peak | ~500 | Session start plus 1 hour token refresh |
| Catalog/search requests per second, peak | ~4,000 | About 20% of active users browsing at once |
| Playlist mutations per second, peak | ~200 | Lower-frequency write operation |

Your task in this step is only to produce a scaling plan for reaching the target scalability plan above, while taking cloud cost into account. Do not generate implementation code yet unless it is absolutely necessary to illustrate the plan, and if you include any code, keep it minimal and clearly marked as non-final.

Create a concrete, engineering-focused, cost-aware scale plan for 1 million registered users that stays realistic for Docker-based benchmarking.

The plan must address:
- which services should scale horizontally and which should mostly scale vertically,
- different instance counts for different services based on the expected traffic shape,
- database lookup pressure, index strategy, query hotspots, pagination, caching opportunities, read/write split opportunities where relevant, and avoiding unnecessary cross-service calls,
- bottlenecks in OpenSearch, ClickHouse, PostgreSQL, Redis, MongoDB, and Kafka,
- which services are stateless and easy to scale with `docker compose up --scale`, and which services need more careful handling because of state, partitions, or storage,
- backpressure, retries, circuit breakers, queue buildup, and graceful degradation,
- metrics and dashboards needed to decide scaling thresholds using Prometheus and Grafana,
- load-test phases using k6, including what to test first and in what order,
- a recommended scaling order so low-impact services are not optimized first,
- risks, assumptions, and trade-offs,
- whether a load balancer or reverse proxy layer is needed, what role it should play, and how it fits into a Docker-only setup.

Cost-awareness requirements:
- Treat cloud cost as a first-class design constraint while preserving required backend behavior.
- Prefer changes that reduce unnecessary CPU, memory, disk, network, storage growth, idle containers, duplicated work, or over-provisioning.
- Do not optimize by removing required services, required observability, required load generation, required persistence boundaries, or required benchmark evidence.
- Distinguish between resources needed for normal backend runtime, smoke testing, calibration runs, and full benchmark runs.
- Explain the cost trade-off for each meaningful scaling recommendation.
- Tie cost-saving recommendations to the workload table, current repository evidence, or expected benchmark evidence rather than generic advice.

Important constraints:
- Stay inside the current architecture unless a change is clearly justified.
- Do not say "use Kubernetes".
- Do not give generic advice; tie every recommendation to one of the actual services or infrastructure components in this system.
- Explicitly explain why each service may need a different scaling pattern.
- Explicitly discuss database lookup optimization and caching strategy.

Document the plan in `SCALABILITY.md`.

Document every cloud cost decision, assumption, trade-off, source of evidence, affected file or service, and relevant observation in `COST-AWARE-DECISIONS.md`. For each planned change, make the entry easy to analyze later by explicitly recording: what would be modified, where it would be modified, why the change is proposed, which cost driver it targets, what evidence supports it, expected cost impact, expected behavior/performance risk, validation method, and status.

## 2. Generate

Now take the approved cost-aware scaling plan and implement the first serious version of it in this repository.

Use `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, `SCALABILITY.md`, and the current repository. Preserve required backend behavior, service boundaries, Docker Compose runtime, observability, load generation, and documented workload assumptions. Make only changes that follow from the approved plan.

Important constraints:
- Keep everything in Docker and Docker Compose.
- Do not use Kubernetes, Helm, Nomad, Swarm, Terraform, managed cloud services, or any external orchestrator/provisioning layer.
- Stay aligned with the current architecture as much as possible.
- Make the engineering decisions yourself and justify them in `SCALABILITY.md`.
- Treat cloud cost as a first-class design constraint for every code, config, infrastructure, and documentation change.
- If you believe a load balancer or reverse proxy is needed for proper horizontal scaling in Docker, decide that yourself and implement it appropriately.

Your task:
- Make the repository more capable of being benchmarked toward 1,000,000 registered users.
- Make practical, incremental changes that improve scalability, tunability, observability, repeatability, and cloud cost-efficiency.
- Decide how scaling should be expressed in Docker Compose and related config.
- Decide whether different services should be prepared for different instance counts.
- Decide what should be changed in configs, infrastructure wiring, resource settings, service communication, and code paths that clearly block scaling.
- Consider database lookup efficiency, caching opportunities, query/index pressure, messaging throughput, request routing, and cost of repeated work.
- Consider how traffic should enter the system and how it should be distributed across replicas.
- Consider Prometheus, Grafana, and k6 as part of the solution, while avoiding unnecessary idle benchmark overhead outside benchmark runs.
- Keep benchmark evidence collectible so cost and performance can be compared after implementation.

Expected behavior:
- Do not rewrite the project from scratch.
- Do not add unnecessary complexity.
- Keep the whole solution runnable with Docker Compose.
- Do not remove required services, required monitoring, required load-generator support, or required backend behavior to reduce cost.

Document every cloud cost implementation decision, assumption, cost trade-off, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`. For each implemented change, explicitly record: what was modified, where it was modified, why it was modified, which cost driver it targets, what evidence supports it, expected cost impact, behavior/performance risk, validation performed or still needed, and status.

## 3. Validate/Fix

Review the generated scalability and cost-aware changes against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, `SCALABILITY.md`, the approved scaling plan, and the current repository.

Validate that:
- required backend behavior and service boundaries are preserved,
- the solution remains fully Docker Compose based,
- no Kubernetes, Helm, Nomad, Swarm, Terraform, managed cloud services, or external orchestrators/provisioning layers were introduced,
- the implementation matches the approved scalability plan,
- the implementation accounts for the target workload table,
- cost-saving claims are tied to clear evidence, expected impact, and trade-offs,
- cloud cost decisions are documented in `COST-AWARE-DECISIONS.md`,
- scaling is not applied uniformly where the traffic shape justifies different service patterns,
- database, cache, messaging, routing, observability, and load-test changes are consistent with the plan,
- configuration and containerization are correct,
- relevant validation commands pass,
- required tests still pass for any backend behavior touched by the implementation.

Run the relevant checks for this phase. Include at minimum:
- `docker compose config --quiet`,
- Compose config validation for every scalability override/profile introduced or modified,
- `docker compose config --services` or equivalent service-list inspection to confirm all eight backend services and required infrastructure remain available in the documented benchmark path,
- Prometheus config validation if Prometheus config changes,
- Grafana dashboard JSON validation if dashboard files change,
- k6 script inspection if load-generator scripts change,
- focused backend unit or integration tests for any service code behavior changed by the scalability work.

Fix missing or incorrect parts. Output only the changed files. Do not modify unrelated files. State assumptions explicitly. Prefer redesigning the scalability implementation over patching around it if the architecture is wrong. Do not consider the phase complete if validation fails or if a cost-saving change removes required behavior.

Document every validation result, fix, decision, correction, deviation, cost trade-off, unresolved issue, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`. For each validation or fix entry, explicitly record: what was checked or changed, where it was checked or changed, why it mattered for scalability and cloud cost efficiency, validation result, remaining risk, and status.
