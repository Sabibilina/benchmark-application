# Third Prompt Set

Use this additional session after the current application version is implemented and integrated.

- **Session 11:** Cloud cost-efficiency improvement

## Prompt pattern

Use this pattern for Session 11.

### **Prompt 1 - Plan**

Use the current repository as the implementation baseline. Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the sources of truth for required behavior and system boundaries. Use `TECH-STACK.md` as the source of truth for allowed implementation technologies. Use `SCALABILITY.md` as the source of truth for the expected performance characteristics, benchmark phases, and scaling assumptions. If these documents conflict, stop and report the conflict instead of guessing.

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

Use `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, and `SCALABILITY.md`. Make only changes that follow from the approved plan.

Update code, configuration, tests, and documentation where needed. Keep changes focused and avoid speculative optimizations that are not connected to the current app or the performance characteristics in `SCALABILITY.md`.

After generating the changes, update `PROGRESS.md` only to reflect checklist/task completion status for this phase. Document every implementation decision, assumption, cost trade-off, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`.

### **Prompt 3 - Validate/Fix**

Review the generated cost-efficiency changes against `ARCHITECTURE.md`, `REQUIREMENTS.md`, `TECH-STACK.md`, `SCALABILITY.md`, and the approved Session 11 plan.

Validate that:
- the changes are justified by the current app and the workload described in `SCALABILITY.md`,
- cost-saving claims are measurable or at least tied to clear validation evidence,
- relevant validation commands and tests pass.

Run the relevant checks for this phase, including Compose configuration checks and focused tests for any behavior touched by the implementation. Fix missing or incorrect parts. Output only the changed files. Do not modify unrelated files. Do not consider the phase complete if validation fails or if a cost-saving change removes required behavior.

Before considering the phase complete, update `PROGRESS.md` only to mark validated checklist items and phase status. Document every fix, decision, correction, deviation, cost trade-off, unresolved issue, source of evidence, affected file or service, and any other relevant observation in `COST-AWARE-DECISIONS.md`.
