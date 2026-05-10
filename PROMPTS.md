# First prompt set

### Prompt 1

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the sources of truth for system shape and validation requirements. Use `TECH-STACK.md` as the source of truth for implementation technology choices. If these documents conflict, stop and report the conflict instead of guessing.

For this step, do not generate service business logic yet. Create an implementation plan only for the shared deployment environment and repository skeleton. Include:
- proposed repository structure,
- docker-compose.yml structure,
- named Docker network,
- supporting infrastructure needed by the current baseline,
- service directory layout,
- config/env file strategy,
- validation steps for this phase.

Do not generate code yet. State assumptions explicitly and do not invent requirements beyond the document.

### Prompt 2

Now generate the repository skeleton and shared deployment environment according to the approved plan and the `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` documents. Generate:
- top-level folder structure,
- docker-compose.yml,
- shared config files,
- service folders,
- placeholder Dockerfiles only where needed for the current phase,
- README with startup instructions for this phase.

Do not implement full service business logic yet. All output must be runnable and consistent with the document.

### Prompt 3

Now review the generated repository skeleton and deployment environment against the `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md` documents. Validate that:
- the compose file reflects the required architecture,
- the shared network is defined,
- services and infrastructure are organized correctly,
- the setup supports the later module-by-module workflow,
- no requirements were added that are not in the document.

Fix any issues and output only the changed files.

# Second Prompt Set

Work in iterative prompts: plan → generate → validate/fix.

- **Session 1:** Auth Service
- **Session 2:** Catalog Service + seed logic
- **Session 3:** Playlist Service
- **Session 4:** Streaming Service
- **Session 5:** Search Service
- **Session 6:** Analytics Service
- **Session 7:** Recommendation Service
- **Session 8:** Notification Service
- **Session 9:** Frontend
- **Session 10:** Monitoring + load generator + integration fixes

## Prompt pattern

Use this pattern for every session.

### **Prompt 1 — Plan**

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the sources of truth for system shape and validation requirements. Use `TECH-STACK.md` as the source of truth for implementation technology choices. If these documents conflict, stop and report the conflict instead of guessing.

Create an implementation plan only for the current phase or service. Include:
- chosen stack,
- file tree,
- dependencies,
- endpoints or exposed interfaces,
- env vars,
- persistence or messaging approach where applicable,
- validation steps,
- required unit tests and integration tests for this phase.

Do not generate code yet.
State assumptions explicitly.
If a detail is missing, list it instead of inventing behavior.

### **Prompt 2 — Generate**

Now generate the complete runnable implementation for the current phase or service exactly according to the approved plan.

Use `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md`.
Include source code, Dockerfile, dependency manifest, config, tests, and a short README.
No pseudocode and no placeholders.

Whenever applicable, also generate:
- unit tests for core business logic,
- integration tests for required endpoints and persistence behavior,
- event production or consumption tests for messaging-based services,
- frontend component/integration tests for critical UI flows.

### **Prompt 3 — Validate/Fix**

Review the generated output for the current phase or service against `ARCHITECTURE.md`, `REQUIREMENTS.md`, and `TECH-STACK.md`.

Validate that:
- all required endpoints or interfaces exist,
- the implementation matches the approved plan,
- no extra requirements were invented,
- configuration and containerization are correct,
- relevant validation commands pass,
- required unit tests and integration tests exist and pass for this phase.

Run the relevant checks for this phase.
Fix missing or incorrect parts.
Output only the changed files.
Do not modify unrelated files.
State assumptions explicitly.
Prefer regeneration over patching if the architecture is wrong.
Do not consider the phase complete if the implementation or its tests fail.