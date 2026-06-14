# Plan

The system already runs fully in Docker Compose and must stay fully in Docker Compose. Do not introduce Kubernetes, Helm, Nomad, or any other orchestrator. Keep the whole solution centered on Dockerfiles, docker-compose.yml, Compose overrides/profiles, and container-level tuning only. 

Your task in this step is ONLY to produce a scaling plan for reaching 1,000,000 users. Do not generate implementation code yet unless it is absolutely necessary to illustrate the plan, and if you include any code, keep it minimal and clearly marked as non-final.


I want a concrete, engineering-focused scale plan for 1 million users that stays realistic for Docker-based benchmarking.

You must think about:
1. Which services should scale horizontally and which should mostly scale vertically.
2. Different instance counts for different services, for example x auth instances, y search instances, z streaming instances, based on expected traffic shape rather than treating all services equally.
3. Database lookup pressure, index strategy, query hotspots, pagination, caching opportunities, read/write split opportunities where relevant, and how to reduce unnecessary cross-service calls.
4. Bottlenecks in OpenSearch, ClickHouse, PostgreSQL, Redis, MongoDB, and Kafka.
5. Which services are stateless and easy to scale with docker compose up --scale, and which services need more careful handling because of state, partitions, or storage.
6. Backpressure, retries, circuit breakers, queue buildup, and graceful degradation.
7. Metrics and dashboards needed to decide scaling thresholds using Prometheus and Grafana.
8. Load-test phases using k6, including what to test first and in what order.
9. A recommended scaling order, so we do not optimize low-impact services first.
10. Risks, assumptions, and trade-offs.
11. Consider whether a load balancer or reverse proxy layer is needed, what role it should play, and how it fits into a Docker-only setup.

Important constraints:
- Stay inside the current architecture unless a change is clearly justified.
- Do not say “use Kubernetes.”
- Do not give generic advice; tie every recommendation to one of the actual services in this system.
- Explicitly explain why each service may need different scaling patterns. 
- Explicitly discuss database lookup optimization and caching strategy.


Document this in a new document called SCALABILITY.md

# Generate

Now take the scaling plan and implement the first serious version of it in this repository.

Important constraints:
- Keep everything in Docker and Docker Compose.
- Do not use Kubernetes, Helm, or any external orchestrator.
- Stay aligned with the current architecture as much as possible.
- make the engineering decisions yourself and justify them in [SCALABILITY.md](SCALABILITY.md) 
- If you believe a load balancer or reverse proxy is needed for proper horizontal scaling in Docker, decide that yourself and implement it appropriately.

Your task:
- Make the repository more capable of being benchmarked toward 1,000,000 users.
- Make practical, incremental changes that improve scalability, tunability, observability, and repeatability.
- Decide how scaling should be expressed in Docker Compose and related config.
- Decide whether different services should be prepared for different instance counts.
- Decide what should be changed in configs, infra wiring, resource settings, service communication, and any code paths that clearly block scaling.
- Consider database lookup efficiency, caching opportunities, query/index pressure, messaging throughput, and request routing.
- Consider how traffic should enter the system and how it should be distributed across replicas.
- Consider Prometheus, Grafana, and k6 as part of the solution.

Expected behavior:
- Do not rewrite the project from scratch.
- Do not add unnecessary complexity.
- Keep the whole solution runnable with Docker Compose.

# Update to Target Plan

Compare the current application against the scalability plan below. First inspect SCALABILITY.md, then inspect the relevant implementation files, configuration, Docker Compose setup, service code, tests, and documentation.

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

Task:

1. Determine whether SCALABILITY.md matches the target scalability plan exactly.
2. Determine whether the actual application implementation matches that scalability plan.
3. If SCALABILITY.md does not match, update it to match the target plan.
4. If the application does not match, update the whole app as needed so its architecture, service behavior, configuration, Docker Compose setup, tests, monitoring, and documentation align with the target plan.
5. Do not invent scalability requirements beyond the target plan.
6. If the target plan conflicts with ARCHITECTURE.md, REQUIREMENTS.md, or TECH-STACK.md, stop and report the conflict before making changes.
7. Document every decision in DESIGN-DECISIONS.md. 
8. Run relevant validation checks and tests after making changes. 
9. Also include the table provided above in SCALABILITY.md

Output:
- Briefly state whether the existing scalability plan matched.
- List the files changed.
- Summarize the implementation changes made to align the app.
- Summarize validation commands run and their results.
