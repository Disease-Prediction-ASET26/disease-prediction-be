# Disease Prediction — Backend

Java 25 / Spring Boot backend for the Disease Prediction system. The API is **contract-first**:
OpenAPI specs under `src/main/resources/openapi/` are the source of truth, and the API interfaces
and DTOs are generated from them at build time.

## Run it

Needs **JDK 25**. Use the Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows) — no local Maven needed.

```bash
./mvnw spring-boot:run     # starts on http://localhost:8080 (needs the local database, below)
./mvnw clean test          # build + run tests + coverage report
./mvnw clean package       # build the runnable jar
```

## Tests and coverage

The tests need **Docker running**: database tests use [Testcontainers](https://testcontainers.com)
to start a throwaway PostgreSQL 18, run the Flyway migrations on it, and delete it afterwards.
They don't touch your local development database.

`./mvnw clean test` (or `./mvnw verify`) also writes a **JaCoCo coverage report**:

- `target/site/jacoco/index.html`: open in a browser for coverage per package, class and line.
- `target/site/jacoco/jacoco.xml`: the same data for tools such as SonarQube.

Classes generated from the OpenAPI spec are excluded from the report. No minimum coverage is
enforced yet.

## Local database (PostgreSQL in Docker)

Needs Docker (Docker Desktop, OrbStack, or Colima). The database is defined in `compose.yaml`.

```bash
cp .env.example .env       # once; then set POSTGRES_PASSWORD and DATA_ENCRYPTION_KEY in .env
openssl rand -base64 32    # generates a value for DATA_ENCRYPTION_KEY
docker compose up -d       # start PostgreSQL 18 on localhost:5433
docker compose ps          # STATUS should show "healthy"
docker compose down        # stop (data is kept)
docker compose down -v     # stop and delete all local data
```

Connect: host `localhost`, port `5433`, database and user `disease_prediction`, password from
`.env`. Port 5433 is used so it doesn't clash with a PostgreSQL already installed on 5432.
`.env` holds your local password and is gitignored, so never commit it.

**Field-level encryption.** Personal data (`users.full_name`, `patients.date_of_birth`) is
stored encrypted with AES-256-GCM, using the key in `DATA_ENCRYPTION_KEY`. The backend refuses to
start without a valid key. If you lose or change the key, existing encrypted values can no longer
be read; for local development, `docker compose down -v` resets the database.

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
