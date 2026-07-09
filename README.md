# benchmark-application
 
Cloud-native music streaming benchmark application for research on AI-assisted generation and cloud cost-awareness.

## Authors

This project was developed as part of the Computing Science Bachelor's degree at the University of Groningen.

**Students:** Alexandra Gabriela Vasile and Bianca Sabina Iliuta

**Supervisors:** dr. Daniel Feitosa and Prof. Vasilios Andrikopoulos

## Overview

This repository contains a backend-only benchmark application generated and refined with the support of an AI-powered coding assistant. The application is modeled as a music streaming system composed of multiple containerized microservices, supporting infrastructure, deployment configuration, tests, observability, and load-generation artifacts.

The project is used to study how AI-assisted development can support the creation of a reproducible cloud-native benchmark application, and how cost-aware revisions can influence deployment and configuration decisions.

## Application Scope

The system is organized around backend services for common music streaming capabilities, including authentication, catalog management, streaming simulation, playlists, search, analytics, recommendations, and notifications.

The repository is intended to include:

- backend microservice implementations
- service-specific persistence and infrastructure configuration
- Docker-based deployment artifacts
- automated tests and validation evidence
- monitoring and load-generation support
- documented design and cost-awareness decisions

## Documentation

The main project documents are:

- `ARCHITECTURE.md` for system structure and service responsibilities
- `REQUIREMENTS.md` for functional and validation requirements
- `TECH-STACK.md` for implementation technology choices
- `PROGRESS.md` for phase and checklist tracking
- `PROMPTS.md` for the staged AI-assisted generation workflow

Additional decision and evaluation documents may be present in branches that include scalability or cost-aware revisions.

## Branches

The repository contains two independently generated applications. Alexandra's version was generated with ChatGPT Codex, while Sabina's version was generated with Claude Code.

| Branch | App version |
| --- | --- |
| `main` | Shared project overview and common documentation. |
| `alexandra/microservices` | Alexandra's **Version 0**: common backend application |
| `alexandra/baseline` | Alexandra's **Version A**: baseline application|
| `alexandra/refactored` | Alexandra's **Version B**: refactored application. |
| `alexandra/cost-aware` | Alexandra's **Version C**: cost-aware application. |
| `microservices/sabina` | Sabina's **Version 0**: common backend application |
| `baseline/sabina` | Sabina's **Version A**: baseline application. |
| `refactored/sabina` | Sabina's **Version B**: refactored application. |
| `cost-aware/sabina` | Sabina's **Version C**: cost-aware application. |

## Development Workflow

The application is developed through structured prompts, source documents, generated artifacts, and manual validation. AI-generated output is not treated as automatically correct; each phase is checked against the requirements, architecture, technology stack, tests, and runtime evidence.
