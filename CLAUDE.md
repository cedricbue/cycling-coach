# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal road cycling training analysis tool. Analyzes rides from Garmin Connect data, provides visualization, coaching, and training insights. Single-user, file-based SQLite database, deployable as a single JAR or Docker container.

## Build & Run Commands

```bash
# Full build (generates code, builds Angular, packages JAR)
./mvnw package

# Generate all code (OpenAPI interfaces + jOOQ classes) without building
./mvnw generate-sources

# Run backend in dev mode (port 8080)
./mvnw spring-boot:run

# Run frontend dev server (port 4200, proxies API to :8080)
cd frontend && ng serve

# Run all tests (backend + frontend)
./mvnw verify

# Run a single backend test class
./mvnw test -Dtest=RideServiceTest

# Run backend unit tests only (skip integration tests)
./mvnw test -Dgroups="unit"

# Run frontend tests only
cd frontend && ng test

# Regenerate jOOQ classes from SQLite schema (requires DB at ./data/cycling-coach.db)
./mvnw jooq-codegen:generate

# Docker Compose (production-like, single container)
cd compose && docker compose up --build
```

## Architecture

### API-First Workflow

The API spec at `src/main/resources/api-spec/cycling-coach-api.yaml` is the source of truth. `./mvnw generate-sources` runs openapi-generator twice:
1. **Backend**: Generates Kotlin Spring controller interfaces + DTOs under `target/generated-sources/openapi/src/main/kotlin/` (packages `com.cyclingcoach.generated.api` and `.generated.model`). jOOQ classes go to `target/generated-sources/jooq/` (`com.cyclingcoach.generated.jooq`). Both are on the Kotlin source path.
2. **Frontend**: Generates Angular services + TypeScript models into `frontend/src/app/core/api/`

**Never hand-edit anything under `target/generated-sources/` or `frontend/src/app/core/api/`** — all changes must go through the YAML spec first.

### Backend Package Layout (`com.cyclingcoach`, `src/main/kotlin/`)

Packages are domain-scoped. Each domain owns its controller, service, and repository. Controllers implement the generated interfaces from `com.cyclingcoach.generated.api`; they never expose jOOQ records directly — they map manually to generated DTOs (no MapStruct).

Currently implemented packages:

```
config/             — Spring async (virtual thread executor), Garmin client wiring, web config.
ride/               — Raw activity ingestion + computed cycling metrics (NP, IF, TSS, best powers).
                      RideComputeService computes; RideService orchestrates and serves API reads.
                      RideEventListener (@Async) handles GarminActivityStoredEvent → compute,
                      ApplicationReadyEvent → reconcile, and FtpTestDetectedEvent → batch recompute.
                      Includes ActivityFileParser / TcxActivityFileParser (TCX → RideInput).
pmc/                — Training load chain: CTL/ATL/TSB per calendar day. TrainingLoadEventListener
                      recalculates forward from earliest affected date on RideCalculatedEvent
                      and FtpBackfillCompleteEvent. PmcController calls ensureUpToDate() lazily
                      on GET /api/pmc to fill the chain through today on rest days.
tcx/                — Framework-agnostic TCX parser (JDK DOM/XPath, no Spring); reusable standalone.
ftp/                — FTP history (ftp_test table). FtpTestRepository.findEffectiveAt(date) is the
                      sole authority for the FTP in effect on a given date. FtpTestDetectionService
                      classifies/calculates/validates FTP from named test rides (idempotent via
                      ftp_test.ride_id unique). FtpEstimationService exists but is not used in the
                      compute path (kept for reference). Publishes FtpTestDetectedEvent and
                      FtpBackfillCompleteEvent.
garmin/             — Garmin sync wiring: GarminController, GarminSyncJob (@Scheduled),
                      GarminSyncService, GarminSyncable interface.
garmin/connect/     — Framework-agnostic Garmin Connect client.
  client/           — GarminConnect, auth, OkHttp wrapper (no Spring).
  activity/         — GarminActivityService, sync cursor, GarminActivityStoredEvent.
  weight/           — GarminWeightService, sync cursor, GarminWeightStoredEvent.
garmin/internal/    — GarminTokenStore (DI OAuth2 tokens; no credentials).
user/               — UserProfile (single-row, id=1; holds auto-detected max_hr).
                      UserProfileEventListener:
                        - @Async listener on RideCalculatedEvent → updates max_hr if higher
                        - @Async listener on GarminWeightStoredEvent → stores weight measurements
                      WeightRepository (upsert + point-in-time lookup). WeightController for /api/weight.
settings/           — Read-only projection of Spring properties (@ConfigurationProperties).
                      AppSettings includes currentFtp (from ftp_test) and maxHrBpm (from user_profile).
Profiles            — Top-level object (`Profiles.kt`) with string constants for every Spring profile:
                      Profiles.TEST ("test") and Profiles.LOCAL ("local"). Lives in main source so
                      both production beans and test annotations can import it without a test-scoped dep.
```

Planned but not yet implemented (do not assume the package exists): `calendar/`, `coaching/`. AI integration is aspirational at this stage.

Generated output (DO NOT EDIT): `target/generated-sources/openapi/` and `target/generated-sources/jooq/`.

### Central Event Flow

```
GarminSyncJob → GarminSyncService → GarminActivityService.storeAll
  → GarminActivityStoredEvent (per new activity)
      → RideEventListener (@Async) → RideComputeService.compute()
          - looks up FTP via FtpTestRepository.findEffectiveAt(rideDate) — point-in-time
          - looks up weight via UserProfileService.findLatestWeightKg
          - computes NP / IF / TSS / W·kg / best powers
          - rideRepository.save() (upsert; COALESCE-protects FTP-dependent fields)
          - publishes RideCalculatedEvent
              → TrainingLoadEventListener (@Async) → recalculateFrom(date)
              → FtpEventListener → FtpTestDetectionService.detectFtpTest
                  - if ride is named "ftp"/"ramp test"/etc. AND no ftp_test row exists for this rideId:
                    classify type, calculate FTP, validate, save → publish FtpTestDetectedEvent
                      → RideEventListener (@Async) → recomputeRidesFrom(prevTestDate or event.date)
                          (publishEvent=false to avoid N concurrent PMC triggers)
                          → publishes FtpBackfillCompleteEvent(fromDate)
                              → TrainingLoadEventListener (@Async) → recalculateFrom(fromDate)
              → UserProfileEventListener (@Async) → updates max_hr if ride's max_hr is higher
```

Reads go through `RideService` and `pmc/` queries.

### Key Domain Rules

- **garmin_activity** stores the raw Garmin Connect activity (JSON + TCX). **ride** holds all computed metrics derived from it — NP, IF, TSS, best power intervals, watts/kg, FTP-at-ride-date.
- **FTP authority**: `ftp_test` is the sole source of truth for FTP. `FtpTestRepository.findEffectiveAt(date)` returns the FTP value from the latest test with `date ≤ rideDate`, or null. The `user_profile` table no longer caches `current_ftp`.
- **Point-in-time FTP**: rides use the FTP in effect at their date. If no FTP test exists on or before the ride date, IF/TSS/W·kg stay null (no estimation in the compute path).
- **FTP backfill on detection**: when a new FTP test is detected at date `D`, `RideEventListener.onFtpTestDetected` (@Async) recomputes all rides from `prevTestDate` (or `D` if no previous test) through today — using `publishEvent=false` to suppress per-ride PMC triggers — then publishes one `FtpBackfillCompleteEvent` so PMC recalculates once from the earliest affected date.
- **Race-condition safe upsert**: during parallel sync, `RideRepository.save()` uses `COALESCE(excluded.X, ride.X)` for FTP-dependent fields (`tss`, `ftp`, `intensity_factor`, `watts_per_kg`). A concurrent compute thread arriving late with `tss=null` cannot overwrite a value that the backfill already set.
- **Null-TSS reconciliation on startup**: `RideEventListener.onApplicationReady()` calls `reconcileOrphanedActivities()` (activities without a ride row) and `reconcileRidesWithNullTss()` (rides with NP but no TSS) — re-runs them through the current point-in-time FTP lookup.
- **TrainingLoad** is a running EWMA chain (CTL = 42d, ATL = 7d). After any sync, recalculate forward from the earliest affected date — it cannot be derived from a single row.
- **PMC lazy fill on rest days**: `PmcController.getPmc()` calls `TrainingLoadService.ensureUpToDate()` first. If `training_load.date` < today, it runs `recalculateFrom(latestDate + 1 day)` to extend the chain to today with TSS=0 for each rest day. Starts from `latestDate + 1` (not `latestDate`) so the existing last row's values are used as the prior — never overwriting them with a zero-prior recompute.
- **TSB** = CTL_yesterday − ATL_yesterday (computed from previous day's values, not today's).
- **FTP auto-detection** from named test rides (20-min, 60-min, ramp): `FtpTestDetectionService` classifies type from name + power profile, computes FTP (`bestPower20min × 0.95`, `bestPower1min × 0.75`, or `bestPower60min`), validates against bounds (60–550 W) and previous test (large delta → NEEDS_REVIEW).
- **Max HR auto-detection**: `UserProfileEventListener.onRideCalculated` reads each ride's `max_hr` and updates `user_profile.max_hr` only if the new value is higher. Monotonic-increase; never overwritten by a lower value.
- **HR zones** are computed as percentages of `user_profile.max_hr` (5 zones, max HR–referenced — not LTHR-referenced).
- **Power zones** are computed as percentages of FTP (Coggan 7-zone model).
- Garmin credentials are **never stored** — only the DI OAuth2 tokens from a successful SSO login (`garmin_token` table).
- The `user_profile` table always has exactly one row (id = 1).
- AI responses (when implemented) will be **persisted on first call** — the AI is not re-invoked for the same resource unless the user explicitly regenerates.

### Database

SQLite, managed by Flyway migrations in `src/main/resources/db/migration/`. jOOQ generates type-safe Kotlin classes from the live schema. No ORM; all queries go through jOOQ DSL.

The schema is split into two migrations by domain:
- **`V1__init.sql`** — core application domain: `ride`, `user_profile`, `user_weight`, `ftp_test`, `training_load`
- **`V2__garmin.sql`** — Garmin Connect integration: `garmin_activity`, `garmin_token`, `garmin_activity_sync_cursor`, `garmin_weight`, `garmin_weight_sync_cursor`

`ride.activity_id` has a FK to `garmin_activity.id` (defined in V1, referencing a table created in V2). SQLite does not validate FK target existence at DDL time — only at DML time — so the ordering is safe.

The project is pre-release and single-user — when the schema needs to evolve, prefer editing V1/V2 with a DB reset over stacking new migrations, until the first production deployment.

**Schema change workflow:**
1. Add a new `V{N}__description.sql` migration file (or edit V1/V2 and reset the DB)
2. Apply it to the local DB — either let `./mvnw spring-boot:run` apply it on startup, or run `sqlite3 data/cycling-coach.db < src/main/resources/db/migration/V{N}__description.sql` manually
3. If applied manually, also register it in `flyway_schema_history` — Flyway validates checksums on every build and will fail with `checksum mismatch` if a migration exists in the DB but not in the history table
4. Run `./mvnw jooq-codegen:generate` to regenerate jOOQ classes

jOOQ maps SQLite INTEGER primary keys as `Int?` in generated record classes — cast with `.toLong()` when your domain model uses `Long`.

### Frontend

Angular 21 with NgRx. NgRx state lives in `+state/` inside each feature directory (the `+` prefix keeps it sorted to the top). HTTP calls come exclusively from the generated services in `core/api/` — no hand-written `HttpClient` calls. Spring Boot serves the Angular build from `src/main/resources/static/` (output of `ng build`).

Feature directories under `frontend/src/app/features/`:
- `dashboard/` — KPI cards (CTL/ATL/TSB/FTP), PMC chart (CTL/ATL/TSB only — no FTP/W·kg line), recent rides
- `rides/` — list + detail
- `activities/` — sync button
- `user-profile/` — tabs (FTP history with link to ride detail, weight history), deeplinkable via `?tab=ftp|weight`
- `settings/` — tabs (power zones %FTP, HR zones %max HR), deeplinkable via `?tab=power|hr`
- `home/`, `shell/`

### AI Integration

Spring AI with Ollama and Anthropic as **switchable providers** (not a fallback chain). Controlled via `AI_PROVIDER=ollama|anthropic` and `AI_MODEL=...` environment variables. A `coaching/` package is planned to own all AI calls but does not yet exist in the codebase.

### Testing

- Backend unit tests: MockK (not Mockito), tagged `@Tag("unit")`, test one service method in isolation
- Backend integration tests: two base classes, both annotated with `@IntegrationTest` (the project's meta-annotation that composes `@SpringBootTest + @ActiveProfiles(Profiles.TEST) + @Tag("integration")`). Concrete test classes must **not** redeclare any of these three annotations — they inherit all three via the base.
  - `AbstractApplicationIntegrationTest` — full application context + WireMock + jOOQ DSL + `@BeforeEach resetState()` that truncates all tables. Use for tests that exercise the full pipeline (sync → ride → PMC).
  - `AbstractGarminConnectTest` — lighter context targeting only the Garmin Connect HTTP client. Provides `garminConnect`, `garminTokenStore`, and WireMock. Does **not** truncate DB tables — tests that write to specific tables must add their own `@BeforeEach cleanDatabase()`. Used by `GarminWeightSyncIntegrationTest`, `GarminActivityImportIntegrationTest`, and `GarminClientIntegrationTest`.
  - `ProfileEnforcementTest` — `@Tag("unit")` reflective test that scans the classpath and fails if any `@SpringBootTest` class (including through superclass inheritance) is missing `@ActiveProfiles(Profiles.TEST)`. Prevents future regressions without Spring context overhead.
- Notable integration suites:
  - `GarminSyncPipelineIntegrationTest` — full sync → ride → PMC → weight pipeline
  - `FtpBackfillIntegrationTest` — FTP test detected after rides exist → backfill produces correct TSS for later rides
  - `PmcControllerIntegrationTest` — `ensureUpToDate()` extends chain on rest days
- Frontend: Vitest (via `ng test` / `@angular/build:unit-test`)

### Feature Branch Convention

`feat/<short-kebab-description>` — e.g., `feat/power-zone-dashboard`

All PRs target `main`.

## Cycling Domain Changes

Any change to cycling business logic — NP/IF/TSS formulas, CTL/ATL/TSB EWMA constants, FTP detection, power zone thresholds, HR zone thresholds, training load recalculation rules, or any other metric derivation — **must be reviewed by the road-cycling-coach agent before implementation**. Invoke it via the `road-cycling-coach` subagent type and describe the proposed change. Do not commit cycling metric logic changes without this review.

## Domain Reference

Cycling metrics implemented in the `ride/` package:
- **NP** (Normalized Power): 30s rolling mean of power raised to 4th power, averaged, then 4th root
- **IF** (Intensity Factor): NP / FTP (where FTP comes from `findEffectiveAt(rideDate)`)
- **TSS** (Training Stress Score): `duration_hours × IF² × 100`
- **W/kg**: NP / latest known body weight (from `user_weight` via `WeightRepository.findWeightAtOrBefore`)
- **CTL/ATL/TSB**: See `docs/knowledge/training-load-metrics.md` for formulas and interpretation

## Configuration

All runtime config via `application.yml` or environment variables — there is no write API for settings. Zone thresholds live under `cycling.zones.power.*` (% of FTP) and `cycling.zones.hr.*` (% of max HR). Frontend reads current config from `GET /api/settings`, which also includes `currentFtp` (from `ftp_test`) and `maxHrBpm` (from `user_profile`).
