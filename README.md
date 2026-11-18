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

## Docker

The backend needs PostgreSQL and a 32-byte Base64 `DATA_ENCRYPTION_KEY` to
start. It also needs the AI service for prediction requests. Docker image
builds skip tests; run the backend tests separately as described below.

| File | Use | Runtime |
| --- | --- | --- |
| `Dockerfile.local` | Run Maven and the backend for local development. | JDK 25 |
| `Dockerfile.dev` | Build a layered JAR and run a dev image. | JDK 25 |
| `Dockerfile.prod` | Build the same layered JAR for production. | JRE 25 |

All three listen on port `8080` in the container. The local image starts Maven;
the dev and production images start the packaged JAR as a non-root user.

### Run the full stack

Follow the [shared Docker setup](https://github.com/Disease-Prediction-ASET26/disease-prediction-docker)
to configure local secrets, PostgreSQL, and the AI model files. Keep the app
repositories as sibling directories. From `disease-prediction-docker`, run:

```bash
docker compose --profile local up --build
```

The backend is at `http://localhost:8080`. Compose mounts this repository at
`/workspace` and uses `Dockerfile.local`. Restart the backend container after
source changes. To check the packaged images, use the `dev` or `prod` profile:

```bash
docker compose --profile dev up --build
docker compose --profile prod up --build
```

Run one profile at a time. The backend ports are `18080` for dev and `28080`
for production. Run `docker compose --profile local down` to stop the local
stack; use the matching profile for the other stacks. Data is kept. The
production profile is a local image check, not a cluster deployment.

### Build and run a backend image

From this repository, build the image you want to check:

```bash
docker build -f Dockerfile.local -t disease-prediction-be:local .
docker build -f Dockerfile.dev -t disease-prediction-be:dev .
docker build -f Dockerfile.prod -t disease-prediction-be:prod .
```

To run the dev image with this repository's local PostgreSQL, first configure
`.env` and start the database as described in [Local database](#local-database-postgresql-in-docker).
Then run:

```bash
docker run --rm --name backend-check \
  --network disease-prediction-be_default \
  --env-file .env \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/disease_prediction \
  -p 127.0.0.1:18080:8080 disease-prediction-be:dev
```

This example uses the default database name and Compose project name. Adjust
the URL and network if you changed them. Stop the container with Ctrl+C. The
database stays running until `docker compose down`. Replace `:dev` with `:prod`
to check the production image. For predictions, connect an AI service and set
`PREDICTION_SERVICE_BASE_URL` to its address on the same network, or use the
full stack above.

### CI image versions

The shared CI workflow reads the backend's SemVer release version from the root `<version>` in
`pom.xml`. Keep it in strict `X.Y.Z` form, without Maven's `-SNAPSHOT` suffix. CI publishes
`X.Y.Z` from `main`; bump the version before merging a change that needs a new production image.
On `develop`, CI publishes `X.Y.Z-dev.<shortsha>.<run>.<attempt>`. Pull requests use
`X.Y.Z-pr.<number>.<shortsha>.<run>.<attempt>`. GitOps deploys the readable tag with its SHA-256
digest, for example `ghcr.io/disease-prediction-aset26/disease-prediction-be:0.0.1@sha256:<digest>`.

Backend tests use Testcontainers and need the host Docker daemon. Run them from
this repository on the host as described in [Tests and coverage](#tests-and-coverage).

## Prediction service

`POST /api/assessments` calls the Python service at
`PREDICTION_SERVICE_BASE_URL` (default `http://localhost:8000`) using
`POST /predict`. The backend combines free text with checklist symptoms and
their severity and duration, then maps the AI response to the frontend API
contract. It requests the top five results. The HTTP client uses a 2-second
connect timeout and a 90-second response timeout.

Red-flag assessments return immediately with `emergencyDetected: true` and no
prediction result. If the service cannot recognise any symptom, the backend
returns 422. Network failures return 503; other prediction-service errors
return 502. Set `PREDICTION_SERVICE_BASE_URL=http://ai:8000` in Kubernetes or
use the matching service name in the Docker Compose profile.

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

## Authentication (Hanko)

Every `/api/**` request needs a Hanko session token in `Authorization: Bearer <token>`
(MDD Iteration 6). Without one the API answers 401. The backend:

1. checks the token offline: RS256 signature against Hanko's JWKS, expiry, issuer and audience;
2. asks Hanko (`POST /sessions/validate`) whether the session is still active, so logout and
   revocation work before the token expires. The answer is reused for 60 seconds per session.
   An uncached request is refused with 503 if Hanko cannot be reached. A cached active result
   remains usable until its cache expiry;
3. loads the user by the token's `sub` claim (`users.identity_id`) and uses the role stored in this
   database. Roles never come from the token or the request.

The token must contain a verified primary email before the user can create or access a profile,
change consent or submit an assessment. If the email is missing or unverified, the API responds
with `email-not-verified`: 400 for onboarding and 403 for existing profile operations. Verify the
email in Hanko and obtain a new session token before retrying. This also applies to accounts created
during the development demo; their data is not deleted. The backend checks the claim in the token,
not the current email state in Hanko. A previously issued token may contain stale claims until it
expires or its session is revoked.

An environment can quarantine known unsafe Hanko user IDs. A quarantined subject gets 403
`account-quarantined` for application API requests, even if the email claim is verified. This
does not disable the account or remove its Hanko credentials. Verification alone does not make
credentials enrolled during an unsafe registration period trustworthy.

To enable quarantine, set `HANKO_QUARANTINE_ENABLED=true` and set `HANKO_QUARANTINE_FILE` to
a mounted secret file containing one canonical Hanko public user UUID per line. The backend
fails to start if the file is missing, empty, malformed or over 128 KiB. The file is read at
startup; restart all backend replicas after changing it. Protect the subject list and keep it
out of Git and logs. Leave quarantine disabled in environments that do not need it. Recovery
requires a separately verified identity and an operator-reviewed data migration; do not simply
remove an old subject after email verification.

On the first verified login there is no profile yet: `GET /api/me` returns 404, then `POST /api/me` creates
the patient profile and records consent. `POST /api/assessments` needs a patient with an active
consent. Errors are `application/problem+json` with a `code` such as `profile-required` or
`consent-required`.

### Local Hanko

`docker compose up -d` also starts a local Hanko (v3.1.0, the version deployed in the cluster),
its own database, and [Mailpit](https://mailpit.axllent.org/), which catches the passcode emails.
Set `HANKO_SECRETS_KEY` in `.env` first (`openssl rand -base64 32`).

| Service | URL |
| --- | --- |
| Hanko public API | http://localhost:8100 |
| Mailpit (passcode emails) | http://localhost:8025 |

The backend defaults match this local Hanko (issuer `http://localhost:8100`, audience
`pocket-doc-local`), so `./mvnw spring-boot:run` needs no extra settings. Signup is enabled,
passkeys work for `http://localhost:5173`, and Google login is not configured locally.
`docker compose down -v` deletes all local Hanko users. The configuration is in
`local/hanko/config.yaml`.

To use another Hanko instance, set these variables (the dev values are in the docs repo,
`mdd/iteration6/README.md`, "Environments"):

| Variable | Local default |
| --- | --- |
| `HANKO_ISSUER` | `http://localhost:8100` |
| `HANKO_JWKS_URI` | `http://localhost:8100/.well-known/jwks.json` |
| `HANKO_AUDIENCE` | `pocket-doc-local` |
| `HANKO_API_URL` | `http://localhost:8100` |
| `HANKO_SESSION_CHECK_TTL` | `60s` |

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
