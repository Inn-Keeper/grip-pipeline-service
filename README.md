# grip-pipeline-service

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
   controller ──► service (funnel / velocity / due) ──► repository (JPA)
   scheduler ───► notifier (logging; swappable for email/push)
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

| Method | Path                    | Description                                        |
| ------ | ----------------------- | -------------------------------------------------- |
| GET    | `/api/pipeline/funnel`  | Per-stage reach + conversion rates                 |
| GET    | `/api/pipeline/velocity`| Avg days spent per stage transition                |
| GET    | `/api/pipeline/due`     | Follow-ups due on/before a date (default: today)   |

`/due` accepts an optional `?asOf=YYYY-MM-DD`. All require an
`Authorization: Bearer <jwt>` header. OpenAPI UI at `/docs` (public).

## Run it

The service points at your real Supabase Postgres via env vars.

```bash
cp .env.example .env   # fill in GRIP_DB_*, GRIP_JWT_SECRET
set -a; . ./.env; set +a
./gradlew bootRun
```

Then call with a Supabase session token (the `access_token` from a signed-in
client, or mint one with the JWT secret):

```bash
TOKEN=<supabase-access-token>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipeline/funnel
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipeline/velocity
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pipeline/due
```

Or open `http://localhost:8080/docs`, click **Authorize**, paste the token, and
use **Try it out**.

### Docker

```bash
docker build -t grip-pipeline-service .
docker run --rm -p 8080:8080 --env-file .env grip-pipeline-service
```

## Tests

```bash
./gradlew build
```

- **Unit tests** (`PipelineAnalyticsServiceTest`) cover the funnel/velocity math
  and its edge cases (empty pipeline, skipped stages, single-event contacts,
  overdue-day arithmetic) with mocked repositories. These always run.
- **Testcontainers IT** (`PipelineAnalyticsTestcontainersIT`) spins a throwaway
  Postgres, applies a schema derived from Grip's real migrations
  (`src/test/resources/db/testcontainers-schema.sql`), seeds contacts so the
  status-event trigger fires, and asserts funnel/velocity/due — including
  cross-user isolation. Needs no credentials; **skipped automatically when Docker
  is unavailable** (`disabledWithoutDocker`), so it runs in CI.
- **Live integration test** (`PipelineEndpointsIT`) hits the real Supabase DB
  through the full HTTP + JWT stack (mints its own HS256 token, asserts 401 with
  no token and 400 for a non-UUID subject). **Skipped unless `GRIP_DB_URL` is
  set.** Assertions are data-agnostic (an unknown `sub` yields an empty report)
  so they don't go brittle as real data changes.

## Notes

- This repo lives on an external drive; macOS writes AppleDouble `._*` sidecars
  there. The Spring Boot plugin's main-class scan can't parse them, so
  `mainClass` is declared explicitly in `build.gradle.kts` and `._*` is
  gitignored. Run Gradle with `COPYFILE_DISABLE=1` to suppress new sidecars.
- Requires JDK 21 (toolchain-pinned).
