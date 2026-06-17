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
- **RLS note:** the service connects with a privileged role that bypasses
  Supabase Row-Level Security, so every query is explicitly scoped by `userId`.
  `userId` is a request parameter today; the intended next step is a JWT filter
  that derives it from the Supabase session.

## Endpoints

| Method | Path                    | Description                                        |
| ------ | ----------------------- | -------------------------------------------------- |
| GET    | `/api/pipeline/funnel`  | Per-stage reach + conversion rates                 |
| GET    | `/api/pipeline/velocity`| Avg days spent per stage transition                |
| GET    | `/api/pipeline/due`     | Follow-ups due on/before a date (default: today)   |

All take `?userId=<uuid>`. `/due` also accepts `?asOf=YYYY-MM-DD`.
OpenAPI UI at `/docs`.

## Run it

The service points at your real Supabase Postgres via env vars.

```bash
cp .env.example .env        # fill in GRIP_DB_URL / GRIP_DB_PASSWORD
export $(grep -v '^#' .env | xargs)
./gradlew bootRun
```

Then:

```bash
USER=<your-supabase-user-uuid>
curl "http://localhost:8080/api/pipeline/funnel?userId=$USER"
curl "http://localhost:8080/api/pipeline/velocity?userId=$USER"
curl "http://localhost:8080/api/pipeline/due?userId=$USER"
```

### Docker

```bash
docker build -t grip-pipeline-service .
docker run --rm -p 8080:8080 \
  -e GRIP_DB_URL=... -e GRIP_DB_USER=... -e GRIP_DB_PASSWORD=... \
  grip-pipeline-service
```

## Tests

```bash
./gradlew build
```

- **Unit tests** (`PipelineAnalyticsServiceTest`) cover the funnel/velocity math
  and its edge cases (empty pipeline, skipped stages, single-event contacts,
  overdue-day arithmetic) with mocked repositories. These always run.
- **Integration tests** (`PipelineEndpointsIT`) hit the live Supabase DB and the
  full HTTP stack. They are **skipped unless `GRIP_DB_URL` is set**, so the build
  is green without credentials. Their assertions are data-agnostic (an unknown
  `userId` yields an empty, well-formed report) so they don't go brittle as real
  data changes.

## Notes

- This repo lives on an external drive; macOS writes AppleDouble `._*` sidecars
  there. The Spring Boot plugin's main-class scan can't parse them, so
  `mainClass` is declared explicitly in `build.gradle.kts` and `._*` is
  gitignored. Run Gradle with `COPYFILE_DISABLE=1` to suppress new sidecars.
- Requires JDK 21 (toolchain-pinned).
