Perform one final end-to-end validation pass. 

Context:
- Goal: confirm the system is in a valid final state for submission and, only if the evidence truly supports it, check the remaining boxes in the final delivery checklist.
- Be strict: do not mark anything complete unless you can verify it from the codebase, runnable commands, logs, or generated evidence.

What to do:

1. Inspect the current repository state and identify the remaining unchecked items in the final delivery checklist, especially under final delivery / system verification / validation sections in documentation such as:
- PROGRESS.md
- TESTS.md
- README.md
- any delivery or checklist-related docs

2. Run a final validation pass of the project.
At minimum:
- verify Docker Compose configuration is valid
- verify the expected services and infrastructure are wired correctly
- run the most relevant automated tests that are feasible in this environment
- run smoke or end-to-end style validation if available
- confirm load generator scripts and observability pieces are present and consistent with docs
- check that the current backend-only scope is reflected consistently across docs and config

3. For every remaining unchecked checklist item:
- determine whether it is truly satisfied
- collect concrete evidence
- if satisfied, update the checkbox to checked
- if not satisfied, leave it unchecked and explain exactly why

4. Update documentation only where justified by the validation results.
Do not inflate claims.
Do not mark boxes complete based on assumptions.
If a command cannot be run, say so clearly and treat that as missing evidence unless the requirement can be proven another way.

5. Give a final report with:
- what was validated
- what passed
- what failed
- which checklist boxes were checked
- which boxes remain unchecked
- any residual risks before submission

Execution rules:
- prefer repo evidence over guesswork
- be conservative
- keep changes minimal and truthful
- if you hit conflicts between docs and implementation, fix the docs to match reality only when you have strong evidence

Important:
The purpose is not to “make the checklist green.”
The purpose is to produce an honest final validation and only check boxes that are genuinely earned.
