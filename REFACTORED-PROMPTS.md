# Third Prompt Set

Use this additional session after the current application version is implemented and integrated.

- **Session 12:** Cloud cost-efficiency improvement

## Prompt pattern

Use this pattern for Session 11.

### **Prompt 1 - Plan**

Use the current repository as the implementation baseline. Use `ARCHITECTURE.md` as the source of truth for required behavior, system boundaries, allowed implementation technologies, expected performance characteristics, benchmark phases, and scaling assumptions. If any conflicts appear, stop and report the conflict instead of guessing.

Use these workload characteristics from `SCALABILITY.md` when reasoning about cost:
- 1,000,000 registered users,
- about 100,000 daily active users,
- about 20,000 peak concurrent users,
- about 10 songs streamed per session,
- about 40,000 playback events per second at peak,
- about 500 auth logins per second at peak,
- about 4,000 catalog/search requests per second,
- about 200 playlist mutations per second.

Create an implementation plan for making the current app more cloud cost efficient while preserving the required application behavior. Do not generate code yet.

The plan should:
- identify the main cost drivers in the current app,
- explain which cost-saving opportunities are worth pursuing and why,
- prioritize the changes by expected impact and risk,
- define how the result will be validated against the workload and performance characteristics in `SCALABILITY.md`.

State assumptions explicitly.

Update `PROGRESS.md` only to mark checklist/task completion status for this planning step. Document every decision, assumption, cost trade-off, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`.

### **Prompt 2 - Generate**

Now implement the approved cost-efficiency plan for the current app.

Use `ARCHITECTURE.md` and `SCALABILITY.md`. Make only changes that follow from the approved plan.

Update code, configuration, tests, and documentation where needed. Keep changes focused and avoid speculative optimizations that are not connected to the current app or the performance characteristics in `SCALABILITY.md`.

After generating the changes, update `PROGRESS.md` only to reflect checklist/task completion status for this phase. Document every implementation decision, assumption, cost trade-off, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`.

### **Prompt 3 - Validate/Fix**

Review the generated cost-efficiency changes against `ARCHITECTURE.md`, `SCALABILITY.md`, and the approved plan.

Validate that:
- the changes are justified by the current app and the workload described in `SCALABILITY.md`,
- cost-saving claims are measurable or at least tied to clear validation evidence,
- relevant validation commands and tests pass.

Run the relevant checks for this phase, including Compose configuration checks and focused tests for any behavior touched by the implementation. Fix missing or incorrect parts. Output only the changed files. Do not modify unrelated files. Do not consider the phase complete if validation fails or if a cost-saving change removes required behavior.

Before considering the phase complete, update `PROGRESS.md` only to mark validated checklist items and phase status. Document every fix, decision, correction, deviation, cost trade-off, unresolved issue, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`.

### **Prompt 4 - Load Test Validation**

Validate the cost-efficiency changes on the current branch by running a smoke test followed by a full load test, then document the results in a `LOAD-RESULTS-REFACTORED.md` file.

Use `ARCHITECTURE.md`, `SCALABILITY.md`, and `COST-AWARE-DECISIONS.md §6` as reference documents. Do not modify unrelated files.

**Step 1 — Rebuild and restart with a clean slate**

Rebuild all Docker images to pick up the changed Dockerfiles, then bring the stack down with volumes cleared and back up fresh. Wait until all services are healthy before proceeding.

**Step 2 — Smoke test**

Run the built-in smoke scenario. If the smoke test fails, stop, diagnose, fix, and re-run smoke before proceeding to the full load test.

**Step 3 — Full load test**

Seed users, then run the load test using the same configuration as Run 6 on the baseline branch (documented in `LOAD-RESULTS.md`) so the results are directly comparable.

**Step 4 — Save and compare results**

Create `LOAD-RESULTS-REFACTORED.md` following the same structure as `LOAD-RESULTS.md` and write the test results there.

Then update `PROGRESS.md` to mark the load test validation step complete, and add a §9 to `COST-AWARE-DECISIONS.md` summarising what the results confirm or contradict from the validation plan in §6.

Do not consider this phase complete if any service has a higher error rate than Run 6, or if a regression check from `COST-AWARE-DECISIONS.md §6` fails.
