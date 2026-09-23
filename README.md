# grip-pipeline-service

[![CI](https://github.com/Inn-Keeper/grip-pipeline-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Inn-Keeper/grip-pipeline-service/actions/workflows/ci.yml)

A small, polished **Spring Boot (Java 21)** service that adds hiring-pipeline
analytics and follow-up reminders on top of the [Grip](../grip-apps) job-hunt
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
- **Auth:** every `/api` request requires a Supabase session **JWT** (ES256,
  validated with the project's JWKS endpoint). The user is taken from the token's
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
- A **Supabase project** — you need its database connection and Auth JWKS URL.

### 1. Configure secrets

```bash
cp .env.example .env
```

Open `.env` and fill in values from the Supabase dashboard:

| Variable | Where to find it |
| --- | --- |
| `GRIP_DB_URL` | **Connect → Session pooler**. Take the host/port/db only and prefix `jdbc:` — e.g. `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres`. The direct `db.<ref>.supabase.co` host is IPv6-only and often unreachable, so prefer the pooler. |
| `GRIP_DB_USER` | The pooler username, which includes the project ref: `postgres.<project-ref>`. |
| `GRIP_DB_PASSWORD` | **Project Settings → Database → Database password** (reset it there if you don't know it). |
| `GRIP_JWK_SET_URI` | Optional override for another Supabase project: `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`. |

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

## Deploy

`render.yaml` is a Render Blueprint: **Dashboard → Blueprints → New Blueprint
Instance**, point it at this repo, and Render prompts for `GRIP_DB_URL`,
`GRIP_DB_USER` and `GRIP_DB_PASSWORD`. Everything else is in the file.

Two things it configures that are easy to get wrong elsewhere:

- **Heap.** The JVM takes 25% of the container limit by default, which is a
  128 MB heap on a 512 MB instance — not enough for Boot + Hibernate. The
  Dockerfile passes `-XX:MaxRAMPercentage=75`.
- **Origins.** `GRIP_CORS_ALLOWED_ORIGINS` must list the web app's exact
  origin, comma-separated, with no trailing slash. Preview deployments have
  their own origins and are not covered.

The deploy is gated on `GET /actuator/health`, which includes the DataSource
check, so a wrong connection string fails the deploy instead of producing a
service that 500s on every request. That endpoint is public but returns only
`{"status":"UP"}` — no component detail.

On the free plan the instance sleeps after 15 minutes idle, so the daily
reminder sweep would not fire; the blueprint sets `GRIP_REMINDERS_CRON=-` to
disable it. Reminders need a Render Cron Job and a real
`ReminderNotifier` before they mean anything.

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
- **Endpoint IT** (`PipelineEndpointsIT`) drives the full HTTP + JWT stack
  against the same kind of throwaway Postgres. Tokens are ES256-signed by a key
  the test generates and serves from a local JWKS URL, so they pass the same
  validation as Supabase tokens. It asserts that `sub` scopes the data, and it
  checks 401 (no token, wrong signing key), 400 (non-UUID subject), and 404/405/406
  for unknown paths, unsupported methods and non-JSON `Accept`. Also skipped
  without Docker.

Testcontainers needs 1.21.4 or newer for Docker Engine 29; older releases are
refused by the daemon and the ITs skip instead of failing.

### Continuous integration

`.github/workflows/ci.yml` runs `./gradlew build` on every push and PR to
`main`. GitHub's `ubuntu-latest` runners have a Docker daemon, so both ITs
execute there, and **CI needs no secrets**. The test HTML report is uploaded as
a build artifact.

## Notes

- This repo lives on an external drive; macOS writes AppleDouble `._*` sidecars
  there. The Spring Boot plugin's main-class scan can't parse them, so
  `mainClass` is declared explicitly in `build.gradle.kts` and `._*` is
  gitignored. Run Gradle with `COPYFILE_DISABLE=1` to suppress new sidecars.
- Requires JDK 21 (toolchain-pinned).
