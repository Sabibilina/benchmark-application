# Cost-Aware Prompt Set - Session 9

This file is the cost-aware version of only **Session 9: Monitoring + load generator + integration fixes** from `PROMPTS.md`.

Use `COST-AWARE-DECISIONS.md` for every cost-related decision, assumption, trade-off, validation finding, unresolved issue, and affected file/service. Each entry should make later analysis easy by recording what changed, where, why, the cost driver targeted, evidence, expected cost impact, risk/trade-off, validation, and status.

Work in iterative prompts: plan -> generate -> validate/fix.

## Session 9: Monitoring + Load Generator + Integration Fixes

### Prompt 1 - Plan

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the sources of truth for system shape and validation requirements. Use `TECH-STACK.md` as the source of truth for implementation technology choices. Use `SCALABILITY.md` as the source of truth for benchmark workload shape, scaling assumptions, and observability expectations. If these documents conflict, stop and report the conflict instead of guessing.

Create an implementation plan only for Session 9: Monitoring + load generator + integration fixes. Include:
- chosen monitoring and load-generation stack,
- file tree,
- dependencies,
- exposed monitoring interfaces,
- env vars,
- Docker Compose and network changes,
- Prometheus scrape strategy,
- Grafana dashboard strategy,
- k6 workload strategy,
- integration fixes needed for cross-service runtime behavior,
- validation steps,
- required tests or smoke checks for this phase.

Make the plan cost-aware:
- identify the likely cloud cost drivers
- explain which cost-saving opportunities are worth considering and why,
- distinguish normal backend runtime, smoke-test runtime, and full benchmark runtime,
- describe how cost-related claims will be validated or measured,
- explicitly state any cost/performance/observability trade-offs.

Do not generate code yet.
State assumptions explicitly.
If a detail is missing, list it instead of inventing behavior.

Update `PROGRESS.md` only to mark checklist/task completion status for this planning step. Record ordinary planning decisions in `DESIGN-DECISIONS.md`. Record every cloud cost decision, assumption, trade-off, source of evidence, affected file/service, validation method, and status in `COST-AWARE-DECISIONS.md`.

### Prompt 2 - Generate

Now generate the complete runnable implementation for Session 9 exactly according to the approved plan.

Use `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, and `SCALABILITY.md`.
Include all monitoring config, load-generator scripts, Docker Compose changes, integration fixes, tests/smoke checks where applicable, and documentation updates needed for this phase.
No pseudocode and no placeholders.

Whenever applicable, also generate:
- Prometheus scrape configuration,
- Grafana dashboard provisioning and dashboards,
- k6 workload scripts for the required backend flows,
- Docker Compose wiring for monitoring and load generation,
- integration fixes for shared Docker networking, gateway routing, retry/failure isolation, service readiness, metrics exposure, and benchmark startup order,
- tests or smoke checks that protect the generated behavior.

Apply the approved cost-aware plan while generating:
- avoid speculative optimizations not tied to the plan or repository evidence,
- keep resource, storage, scrape interval, exporter, k6, gateway, and dashboard choices explicit enough to analyze later,
- document any measurable cost impact or trade-off introduced by the implementation,

After generating the implementation, update `PROGRESS.md` only to reflect checklist/task completion status for this phase. Record ordinary implementation decisions in `DESIGN-DECISIONS.md`. Record every cloud cost implementation decision, assumption, trade-off, source of evidence, affected file/service, expected cost impact, risk, validation performed or still needed, and status in `COST-AWARE-DECISIONS.md`.

### Prompt 3 - Validate/Fix

Review the generated Session 9 output against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, `SCALABILITY.md`, and the approved Session 9 plan.

Validate that:
- Prometheus is configured to scrape all required backend service metrics,
- Grafana dashboards cover the required observability views where implemented,
- the load generator covers registration, login, catalog browsing, search, streaming requests, playlist operations, and history queries,
- all eight backend services communicate over the shared named Docker network,
- CPU and memory limits remain configurable per service,
- inter-service HTTP retry and circuit breaker requirements are satisfied where such HTTP calls exist,
- integrated system behavior can be verified through the shared Docker Compose environment,
- configuration and containerization are correct,
- relevant validation commands pass,
- required tests or smoke checks exist and pass for this phase.

Also validate the cost-aware aspects:
- required monitoring, load generation, and integration behavior was not removed or weakened to reduce cost,
- cost-related decisions are justified by source documents, repo evidence, or validation results,
- monitoring scrape intervals, exporters, dashboards, metrics storage, k6 resources, gateway routing, and runtime profiles are measurable or documented with clear expected impact,
- cost trade-offs and remaining risks are recorded in `COST-AWARE-DECISIONS.md`.

Run the relevant checks for this phase. Include Compose configuration checks, Prometheus config validation, Grafana dashboard JSON validation if dashboard files change, k6 script inspection if load-generator scripts change, and focused backend or integration tests for any touched behavior.

Fix missing or incorrect parts.
Output only the changed files.
Do not modify unrelated files.
State assumptions explicitly.
Prefer regeneration over patching if the architecture is wrong.
Do not consider the phase complete if validation fails or if a cost-saving change removes required behavior.

Before considering the phase complete, update `PROGRESS.md` only to mark validated checklist items and phase status. Record ordinary fixes and important decisions in `DESIGN-DECISIONS.md`. Record every cost-related validation result, fix, correction, deviation, trade-off, unresolved issue, source of evidence, affected file/service, remaining risk, and status in `COST-AWARE-DECISIONS.md`.
