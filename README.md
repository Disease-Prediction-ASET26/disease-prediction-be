# Disease Prediction — Backend

Java 25 / Spring Boot backend for the Disease Prediction system. The API is **contract-first**:
OpenAPI specs under `src/main/resources/openapi/` are the source of truth, and the API interfaces
and DTOs are generated from them at build time.

## Run it

Needs **JDK 25**. Use the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows) — no local Maven needed.

```bash
./mvnw spring-boot:run     # starts on http://localhost:8080
./mvnw clean test          # build + run tests
./mvnw clean package       # build the runnable jar
```

## Develop

1. Branch off `develop` (e.g. `feat/my-change`).
2. Make your change. If you touch an API, follow the contract-first steps below.
3. Format before committing:
   ```bash
   ./mvnw spotless:apply
   ```
4. Run the tests, then open a PR into `develop`.

### Contract-first API changes

The source of truth is the OpenAPI spec(s) in `src/main/resources/openapi/`.

1. Edit the spec.
2. Regenerate (any build goal does it; or run it directly):
   ```bash
   ./mvnw generate-sources
   ```
   Generated API interfaces and DTOs land under `target/generated-sources/`
   (packages `com.diseaseprediction.api` / `.api.model`).
3. Update your controllers / services to match the regenerated interface.

> Don't edit the generated classes by hand — they are overwritten on every build. Change the spec.

## Conventions

- **Controllers** stay thin — they implement the generated API interface and delegate to a service.
  Keep validation and business logic in the service layer, not the controller.
- **Formatting:** google-java-format (AOSP style) via Spotless (`./mvnw spotless:apply`).
- **Pre-commit hooks** are configured in `.pre-commit-config.yaml`:
  ```bash
  pipx install pre-commit && pre-commit install
  ```
- **Generated code is not committed** (it lives under `target/`).
