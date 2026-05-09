# First prompt set

### Prompt 1

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the only sources of truth. If they conflict, stop and report the conflict instead of guessing.

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

Now generate the repository skeleton and shared deployment environment according to the approved plan and the `ARCHITECTURE.md` and `REQUIREMENTS.md` documents. Generate:
- top-level folder structure,
- docker-compose.yml,
- shared config files,
- service folders,
- placeholder Dockerfiles only where needed for the current phase,
- README with startup instructions for this phase.

Do not implement full service business logic yet. All output must be runnable and consistent with the document.

### Prompt 3

Now review the generated repository skeleton and deployment environment against the `ARCHITECTURE.md` and `REQUIREMENTS.md` documents. Validate that:
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
- **Session 9:** monitoring + load generator + integration fixes

## Prompt pattern

Use this pattern for every session.

### **Prompt 1 — Plan**

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the only sources of truth. If they conflict, stop and report the conflict instead of guessing.

Create an implementation plan only for the Auth Service. Include:
- chosen stack
- file tree
- dependencies
- endpoints
- env vars
- persistence approach
- validation steps

Do not generate code yet.
State assumptions explicitly.
If a detail is missing, list it instead of inventing behavior.

### **Prompt 2 — Generate**

Now generate the complete runnable implementation for the Auth Service exactly according to the approved plan.

Use `ARCHITECTURE.md` and `REQUIREMENTS.md` as the only sources of truth.
Include source code, Dockerfile, dependency manifest, config, and a short README.
No pseudocode and no placeholders.

### **Prompt 3 — Validate/Fix**

Review the generated Auth Service against `ARCHITECTURE.md` and `REQUIREMENTS.md`.

Validate that:
- all required endpoints exist
- the implementation matches the approved plan
- no extra requirements were invented
- configuration and containerization are correct
- relevant validation commands pass

Run the relevant checks for this phase.
Fix missing or incorrect parts.
Output only the changed files.
Do not modify unrelated files.
State assumptions explicitly.
Prefer regeneration over patching if the architecture is wrong.