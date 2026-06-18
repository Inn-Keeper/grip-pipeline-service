# grip-pipeline-service

[![CI](https://github.com/Inn-Keeper/grip-pipeline-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Inn-Keeper/grip-pipeline-service/actions/workflows/ci.yml)

A small, polished **Spring Boot (Java 21)** service that adds hiring-pipeline
analytics and follow-up reminders on top of the [Grip](../tech-refresh) job-hunt
toolkit. It reads Grip's existing Supabase Postgres tables (`contacts`,
`status_events`) — read-only, no schema ownership.

## Why a separate JVM service?

The React web/mobile apps own CRUD. This service owns the work that is awkward in
a Supabase client app but natural on the JVM:

- **Aggregate analytics** — conversion funnel and stage velocity computed from
  the exact `status_events` transition log.
- **Scheduled jobs** — a daily `@Scheduled` sweep that surfaces due follow-ups.

That separation *is* the architecture story: clients do writes; the Java service
does heavy reads and time-based jobs.

## Architecture

```
web/mobile (writes) ─┐
                     ├──► Supabase Postgres (contacts, status_events)
this service (reads) ─┘
   controller ──► service (velocity) ──► repository (JPA)
   scheduler ───► analytics (due) ───► notifier (logging; swappable for email/push)
```

- **Read-only JPA** entities map the real Grip tables; `ddl-auto: none` so the
  service never mutates the schema (Supabase migrations own it).
- **Auth:** every `/api` request requires a Supabase session **JWT** (HS256,
  validated with the project's JWT secret). The user is taken from the token's
  `sub` claim, so no endpoint accepts a user id from the caller — a token holder
  can only read their own pipeline. The service connects with a role that
  bypasses Supabase RLS, so this token-derived scoping is the isolation boundary.
- **Virtual threads** (`spring.threads.virtual.enabled`): each blocking request
  runs on a Java 21 virtual thread.

## Endpoints

| Method | Path                     | Description                                     |
| ------ | ------------------------ | ----------------------------------------------- |
| GET    | `/api/pipeline/velocity` | Avg days spent per stage transition (read-only) |

All require an `Authorization: Bearer <jwt>` header. OpenAPI UI at `/docs` (public).

## Run it yourself (step by step)

### Prerequisites

- **JDK 21** (the build is pinned to it). Check with `java -version`. On macOS
  with multiple JDKs, point this shell at 21 with
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **Docker** — only needed to *run* the Testcontainers test locally; the app
  itself does not need it (`brew install colima docker && colima start`).
- A **Supabase project** — you need its database connection and JWT secret.

### 1. Configure secrets

```bash
cp .env.example .env
```

Open `.env` and fill in three values from the Supabase dashboard:

| Variable | Where to find it |
| --- | --- |
| `GRIP_DB_URL` | **Connect → Session pooler**. Take the host/port/db only and prefix `jdbc:` — e.g. `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres`. The direct `db.<ref>.supabase.co` host is IPv6-only and often unreachable, so prefer the pooler. |
| `GRIP_DB_USER` | The pooler username, which includes the project ref: `postgres.<project-ref>`. |
| `GRIP_DB_PASSWORD` | **Project Settings → Database → Database password** (reset it there if you don't know it). |
| `GRIP_JWT_SECRET` | **Project Settings → API → JWT Secret**. Required, or the app won't start. |

`.env` is gitignored — never commit it.

### 2. Start the service

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export COPYFILE_DISABLE=1          # only matters on macOS external drives (see Notes)
set -a; . ./.env; set +a           # load .env into the environment
./gradlew bootRun
```

It starts on `http://localhost:8080`. Stop with `Ctrl-C`.

### 3. Get a token and call the API

Every `/api` call needs a Supabase **session JWT** (`access_token`). Easiest
ways to get one:

- Sign in on the Grip web/mobile app and copy `access_token` from the Supabase
  session (e.g. browser devtools → Application → Local Storage), **or**
- Mint one via the Supabase Auth REST API (command below).

```bash
curl -s "https://<project-ref>.supabase.co/auth/v1/token?grant_type=password" \
  -H "apikey: <anon-key>" -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"..."}' | jq -r .access_token
```

Then:

```bash
TOKEN=<paste-access-token>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipeline/velocity
```

No `userId` is needed — it comes from the token. A request with no/invalid
token returns **401**.

### Inspect it visually

Open **<http://localhost:8080/docs>** (Swagger UI). Click **Authorize**, paste
your token, then use **Try it out** on any endpoint to see the live response.
The raw OpenAPI spec is at `/v3/api-docs` (importable into Postman/Bruno).

### Run with Docker instead

```bash
docker build -t grip-pipeline-service .
docker run --rm -p 8080:8080 --env-file .env grip-pipeline-service
```

## Tests

```bash
./gradlew build
```

- **Unit tests** (`PipelineAnalyticsServiceTest`) cover the velocity math
  and its edge cases (empty pipeline, skipped stages, single-event contacts,
  overdue-day arithmetic) with mocked repositories. These always run.
- **Testcontainers IT** (`PipelineAnalyticsTestcontainersIT`) spins a throwaway
  Postgres, applies a schema derived from Grip's real migrations
  (`src/test/resources/db/testcontainers-schema.sql`), seeds contacts so the
  status-event trigger fires, and asserts velocity/due — including
  cross-user isolation. Needs no credentials; **skipped automatically when Docker
  is unavailable** (`disabledWithoutDocker`), so it runs in CI.
- **Live integration test** (`PipelineEndpointsIT`) hits the real Supabase DB
  through the full HTTP + JWT stack (mints its own HS256 token, asserts 401 with
  no token and 400 for a non-UUID subject). **Skipped unless `GRIP_DB_URL` is
  set.** Assertions are data-agnostic (an unknown `sub` yields an empty report)
  so they don't go brittle as real data changes.

To run the live test against your own Supabase, load `.env` first:

```bash
set -a; . ./.env; set +a
./gradlew test --tests 'com.grip.pipeline.web.PipelineEndpointsIT'
```

### Continuous integration

`.github/workflows/ci.yml` runs `./gradlew build` on every push and PR to
`main`. GitHub's `ubuntu-latest` runners have a Docker daemon, so the
Testcontainers IT actually executes there. The live Supabase IT stays skipped
(no `GRIP_DB_URL`), so **CI needs no secrets**. The test HTML report is uploaded
as a build artifact.

## Notes

- This repo lives on an external drive; macOS writes AppleDouble `._*` sidecars
  there. The Spring Boot plugin's main-class scan can't parse them, so
  `mainClass` is declared explicitly in `build.gradle.kts` and `._*` is
  gitignored. Run Gradle with `COPYFILE_DISABLE=1` to suppress new sidecars.
- Requires JDK 21 (toolchain-pinned).
