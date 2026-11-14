# AGENTS.md

Instructions for AI coding agents working in this repository. Keep changes consistent with the
conventions below. See `README.md` for the human-facing version.

## Project

- Java 25, Spring Boot, Maven. Use the wrapper `./mvnw` (never a globally installed `mvn`).
- The HTTP API is **contract-first**: OpenAPI specs under `src/main/resources/openapi/` are the
  single source of truth.

## Commands

- Build + test: `./mvnw clean test` (needs Docker running: database tests use Testcontainers).
  Writes the JaCoCo coverage report to `target/site/jacoco/`.
- Run locally: `./mvnw spring-boot:run` (http://localhost:8080)
- Format: `./mvnw spotless:apply` (google-java-format, AOSP style)
- Regenerate API code from the spec: `./mvnw generate-sources`

Always run `./mvnw clean test` before concluding a change, and `./mvnw spotless:apply` before
committing.

## Contract-first rule (important)

- API interfaces and DTOs are **generated** from the OpenAPI spec into `target/generated-sources/`
  (packages `com.diseaseprediction.api` and `com.diseaseprediction.api.model`).
- To change the API: edit the OpenAPI spec, run `./mvnw generate-sources`, then update the
  controllers/services to match.
- **Never hand-edit generated classes** — they are overwritten on every build, and are not
  committed to git.

## Code conventions

- **Controllers are thin.** A controller implements the generated API interface and only delegates
  to a service. No validation and no business logic in controllers.
- **Logic lives in the service layer.** Validation, orchestration, and domain rules go in services.
- Signal invalid input with an exception mapped to the right HTTP status (e.g.
  `@ResponseStatus(HttpStatus.BAD_REQUEST)`), not with checks inside the controller.
- Keep the DTO↔domain boundary in the service layer.
- Match existing package structure: `controller/`, `service/`, `exception/`, `domain/`.

## Workflow

- Branch off `develop` (e.g. `feat/my-change`); open PRs into `develop`.
- Do not commit generated code (`target/`) or IDE files.
- Pre-commit hooks are configured in `.pre-commit-config.yaml`.

## Scope

- This is the backend only. It orchestrates and delegates prediction to the Python service across
  the SOA boundary; it does not implement the ML model here.
