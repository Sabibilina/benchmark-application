## **Generation Protocol**

## The application must be generated iteratively and module-by-module rather than in a single pass. Each service should be completed, reviewed, and validated before moving to the next one so that architectural or integration problems can be detected early.

## Generation should begin with the shared deployment environment, including the initial docker-compose.yml and supporting configuration, so that services can be integrated into an existing runtime environment as they are produced.

## **Implementation procedure**

1. ## Start with the shared deployment environment and base configuration.

2. ## Generate one service at a time, including its source code, configuration, and containerization artifacts.

3. ## After each service is generated, validate that it starts successfully and connects to the infrastructure it depends on.

4. ## Refine the generated output iteratively when errors, missing requirements, or integration issues are found.

5. ## Continue until all required services and deployment artifacts have been produced.

## **Generation constraints**

* ## The generated system must satisfy the frozen requirements table and the service specifications.

* ## The implementation must follow a polyglot microservices architecture using at least three different language/framework stacks across the 8 services.

* ## Each service must use one of the approved implementation options listed in the Approved Service-Stack Options section.

* ## All generated code must be runnable and complete; pseudocode and placeholder implementations are not acceptable.

* ## Manual changes made after generation must be explicitly marked in code comments using MANUAL CHANGE: \<reason\>.

## **Validation expectations**

* ## Each generated service should start cleanly in its container.

* ## Each service should connect successfully to its required dependencies.

* ## Protected endpoints should enforce JWT authentication according to the requirements.

* ## Integration behavior should be checked incrementally rather than postponed until the entire system has been generated.

## **Delivery Checklist**

## The generated project must include all artifacts required to build, run, validate, and inspect the application locally. At minimum, the delivery must contain the complete source code, deployment configuration, service containerization artifacts, supporting configuration, and usage documentation needed to reproduce the system.

- [ ] docker-compose.yml for the full application and supporting infrastructure.

- [ ] Source code and containerization artifacts for the Auth Service.

- [ ] Source code, dataset-ingestion logic, and containerization artifacts for the Catalog Service.

- [ ] Source code and containerization artifacts for the Streaming Service.

- [ ] Source code and containerization artifacts for the Playlist Service.

- [ ] Source code and containerization artifacts for the Search Service.

- [ ] Source code and containerization artifacts for the Analytics Service.

- [ ] Source code and containerization artifacts for the Recommendation Service.

- [ ] Source code and containerization artifacts for the Notification Service.

- [ ] Configuration for metrics collection and monitoring support.

- [ ] Load-generator script and workload definition.

- [ ] README with setup, run, validation, and testing instructions.

## **Expected documentation**

* ## Description of the selected technology stack per service.

* ## Notes on any manual changes made after AI generation, marked in the codebase using MANUAL CHANGE: \<reason\>.

* ## Instructions for starting the system, validating the services, and running the load generator.

* ## Description and justification of the selected persistence technology per service.

## **Minimum completion criteria**

* ## All required services start successfully in the local deployment environment.

* ## Required endpoints are implemented and reachable.

* ## Protected endpoints enforce JWT authentication.

* ## Metrics are exposed and collectible through the monitoring setup.

* ## The load generator can execute the main application flows

1. ## **First prompt set**

## **Prompt 1**

Use the attached document as the single source of truth for this project. For this step, do not generate service business logic yet. Create an implementation plan only for the shared deployment environment and repository skeleton. Include:

* proposed repository structure,  
* docker-compose.yml structure,  
* named Docker network,  
* supporting infrastructure needed by the current baseline,  
* service directory layout,  
* config/env file strategy,  
* validation steps for this phase.

Do not generate code yet. State assumptions explicitly and do not invent requirements beyond the document.

## **Prompt 2**

Now generate the repository skeleton and shared deployment environment according to the approved plan and the attached source-of-truth document. Generate:

* top-level folder structure,  
* docker-compose.yml,  
* shared config files,  
* service folders,  
* placeholder Dockerfiles only where needed for the current phase,  
* README with startup instructions for this phase.

Do not implement full service business logic yet. All output must be runnable and consistent with the document.

## **Prompt 3**

Now review the generated repository skeleton and deployment environment against the attached document. Validate that:

* the compose file reflects the required architecture,  
* the shared network is defined,  
* services and infrastructure are organized correctly,  
* the setup supports the later module-by-module workflow,  
* no requirements were added that are not in the document.

Fix any issues and output only the changed files.

2. ## **Second Prompt Set**

   

**Give the LLM the same stable document every time as the source of truth, then work in iterative prompts like plan → generate → validate/fix**

* **Session 1:** repo skeleton \+ docker-compose.yml  
* **Session 2:** Auth Service  
* **Session 3:** Catalog Service \+ seed logic  
* **Session 4:** Playlist Service  
* **Session 5:** Streaming Service  
* **Session 6:** Search Service  
* **Session 7:** Analytics Service  
* **Session 8:** Recommendation Service  
* **Session 9:** Notification Service  
* **Session 10:** monitoring \+ load generator \+ integration fixes

## **Prompt pattern**

Use this pattern every time:

## **Prompt 1 — Plan**

“Use the attached generation brief as the source of truth. For this step, create an implementation plan only for the Auth Service. Include file tree, chosen stack, dependencies, endpoints, env vars, and validation steps. Do not generate code yet.”

## **Prompt 2 — Generate**

“Now generate the complete runnable implementation for the Auth Service exactly according to the approved plan and the generation brief. Include source code, Dockerfile, dependency manifest, config, and a short README. No pseudocode, no placeholders.”

## **Prompt 3 — Validate/Fix**

“Review the generated Auth Service against these acceptance criteria. Fix missing or incorrect parts and output only the changed files.”

* Use the attached generation brief as the only source of truth.  
* Do not invent requirements not present in the brief.  
* Do not modify unrelated files.  
* State assumptions explicitly.  
* Prefer regeneration over patching if the architecture is wrong.
