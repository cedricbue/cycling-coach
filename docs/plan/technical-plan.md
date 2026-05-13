# Cycling Coach App — Technical Plan

## Purpose
Personal road cycling training analysis tool. Analyze rides from Garmin Connect data, provide visualization, coaching, and training insights. Single-user, file-based SQLite database, deployable as a single JAR or Docker container.

## Tech Stack

### Backend
| Component | Choice | Notes |
|---|---|---|
| Framework | Spring Boot 4.0.x | Latest stable |
| Language | Kotlin 2.3.x | JVM target 25; type safety, conciseness |
| SQL | jOOQ 3.19.32 | Type-safe queries, code generation from Flyway schema |
| Migrations | Flyway 11.x | Versioned schema migrations; SQLite supported in core |
| Database | SQLite (org.xerial:sqlite-jdbc) | File-based, WAL mode, backup-friendly, no server |
| AI Integration | Spring AI (planned) | Ollama and Anthropic as switchable options (not fallback) |
| Garmin API | OkHttp via `garmin/connect/client/` package | Framework-agnostic `GarminConnect`; handles SSO → DI OAuth2 token exchange, auto-refresh, auto-reauth |
| Job Scheduling | Spring `@Scheduled` | Built-in; 6h default interval |
| Async Execution | `@Async(VIRTUAL_THREAD_EXECUTOR)` | Virtual threads for event listeners and parallel ride compute |
| TCX Parsing | JDK DOM/XPath (`javax.xml.xpath`) | Framework-agnostic, reusable standalone |
| Activity JSON Parsing | Jackson | Garmin Connect activity raw JSON → `GarminActivity` model |
| API Spec | OpenAPI 3.1 YAML | Spec-first; `src/main/resources/api-spec/cycling-coach-api.yaml` |
| API Generator | openapi-generator-maven-plugin 7.x | Generates Spring controller interfaces + DTOs from spec |
| DTO Mapping | Manual | Controllers map jOOQ records → generated DTOs explicitly (no MapStruct) |
| Metrics | Micrometer + Prometheus | Observability via actuator endpoints |
| Testing | MockK (unit), WireMock (integration) | MockK for service unit tests; WireMock for Garmin API stubs |

### Frontend
| Component | Choice | Notes |
|---|---|---|
| Framework | Angular 21 | Standalone components, signals + NgRx |
| State Management | NgRx | Store, Effects per feature |
| Charts | Chart.js | PMC chart, future overlay charts |
| UI Components | Angular Material | Tabs, tables, progress, snackbars |
| Routing | Angular Router | Deeplinks via `?tab=` query params on settings & profile |

### Build & Infrastructure
| Component | Choice | Notes |
|---|---|---|
| Build (backend) | Maven (wrapper `./mvnw`) | Multi-stage: openapi-generate → jooq-codegen → kotlin-compile |
| Build (frontend) | `com.github.eirslett:frontend-maven-plugin` | Installs Node, runs npm ci + ng build; output → `src/main/resources/static/` |
| Package | Single JAR | Angular static assets served by Spring Boot |
| Container | Docker Compose | Single backend service with SQLite volume |
| CI | GitHub Actions | `./mvnw verify` on every push and PR |

---

## Backend Architecture

### Package Layout (`com.cyclingcoach`)

Structured by domain. Controllers implement generated interfaces from `generated/api/`; DTOs come from `generated/model/`. Each domain package owns its service, repository, and controller. Cross-package coupling is via Spring events.

```
com.cyclingcoach
├── config/                  # Infrastructure wiring
│   ├── AsyncConfig.kt              # virtual thread executor (VIRTUAL_THREAD_EXECUTOR)
│   ├── GarminClientConfig.kt       # @Bean for GarminConfig + GarminConnect
│   └── WebConfig.kt
│
├── ride/                    # Computed cycling metrics
│   ├── RidesController.kt          # implements generated RidesApi
│   ├── RideService.kt              # orchestrates: computeForActivity, recomputeRidesFrom, reconcile
│   ├── RideComputeService.kt       # parses raw JSON (and TCX), computes NP/IF/TSS/best-powers,
│   │                                # looks up FTP via FtpTestRepository.findEffectiveAt(date),
│   │                                # rideRepository.save() (COALESCE-protected upsert),
│   │                                # publishes RideCalculatedEvent (configurable via publishEvent param)
│   ├── RideCalculator.kt           # pure functions: NP, bestPower, TSS, IF, VI, EF
│   ├── RideReadService.kt          # query side
│   ├── RideRepository.kt           # jOOQ upsert with COALESCE for FTP-dependent fields
│   ├── RideInput.kt
│   ├── RideEventListener.kt        # @Async on GarminActivityStoredEvent → compute
│   │                                # @EventListener(ApplicationReadyEvent) → reconcile orphans + null-TSS rides
│   ├── RideCalculatedEvent.kt
│   ├── FtpChangeListener.kt        # @EventListener(FtpTestDetectedEvent) → batch recompute from
│   │                                # prevTestDate onward (synchronous, publishEvent=false),
│   │                                # then publishes FtpBackfillCompleteEvent(fromDate)
│   ├── ActivityFileParser.kt       # interface
│   └── TcxActivityFileParser.kt    # @Component adapter wrapping tcx.TcxParser
│
├── pmc/                     # Training load chain
│   ├── PmcController.kt            # implements generated PmcApi; calls ensureUpToDate() lazily
│   ├── TrainingLoadService.kt      # EWMA chain walk; ensureUpToDate() extends chain to today
│   ├── TrainingLoadEventListener.kt # @Async listeners on RideCalculatedEvent and
│   │                                 # FtpBackfillCompleteEvent
│   └── TrainingLoadRepository.kt   # jOOQ: findLatestDate, findByDate, findDailyTssSince, upsert
│
├── ftp/                     # FTP test detection + history
│   ├── FtpController.kt            # implements generated FtpApi; returns FTP history with rideId link
│   ├── FtpTestRepository.kt        # findEffectiveAt(date), findLatestBefore(date), existsByRideId, save
│   ├── FtpTestDetectionService.kt  # name detection, type classification, FTP calculation,
│   │                                # validation against previous FTP (NEEDS_REVIEW for large deltas)
│   ├── FtpEventListener.kt         # @EventListener(RideCalculatedEvent) → detectFtpTest
│   ├── FtpEstimationService.kt     # CP model + weighted fallbacks — NOT used in compute path
│   │                                # (kept for future use; was the old fallback when no FTP test existed)
│   ├── FtpTestType.kt              # RAMP_TEST | TWENTY_MIN_TEST | SIXTY_MIN_TEST | UNKNOWN | ESTIMATED
│   ├── FtpTestDetectedEvent.kt
│   ├── FtpBackfillCompleteEvent.kt # emitted by FtpChangeListener after batch recompute
│   └── RidePowerSample.kt
│
├── tcx/                     # Framework-agnostic TCX parser (no Spring)
│   ├── TcxParser.kt                # JDK DOM/XPath; returns TcxData
│   └── TcxData.kt
│
├── garmin/                  # Spring-wired sync orchestration
│   ├── GarminController.kt         # implements generated GarminApi; manual trigger
│   ├── GarminSyncJob.kt            # @Scheduled + ApplicationReadyEvent startup auth
│   ├── GarminSyncService.kt        # orchestrates all GarminSyncable beans
│   ├── GarminSyncable.kt           # interface for sync providers
│   ├── GarminProperties.kt         # @ConfigurationProperties
│   ├── connect/                    # Framework-agnostic client (no Spring)
│   │   ├── client/                 # GarminConnect, GarminConfig, GarminTokens, GarminException,
│   │   │   │                        # GarminAuthService, GarminHttpClient (OkHttp wrapper)
│   │   │   └── internal/
│   │   ├── activity/               # GarminActivityService, repository, sync cursor, sync service,
│   │   │                            # GarminActivityStoredEvent
│   │   └── weight/                 # GarminWeightService, repository, sync cursor, sync service,
│   │                                # GarminWeightStoredEvent
│   └── internal/
│       └── GarminTokenStore.kt     # Spring impl of client TokenStore — persists DI tokens
│
├── user/                    # Single-row user profile + weight history
│   ├── WeightController.kt         # implements generated WeightApi
│   ├── UserProfileService.kt       # findMaxHr, updateMaxHrIfHigher, weight lookups
│   ├── UserProfileRepository.kt    # jOOQ: findMaxHr, updateMaxHrIfHigher
│   ├── UserProfileEventListener.kt # @Async on RideCalculatedEvent → updateMaxHrIfHigher
│   │                                # @Async on GarminWeightStoredEvent → store weights
│   └── WeightRepository.kt
│
├── settings/                # Read-only settings projection
│   ├── SettingsController.kt       # implements generated SettingsApi
│   ├── SettingsService.kt          # currentFtp from ftp_test, maxHrBpm from user_profile,
│   │                                # zone thresholds from properties
│   └── SettingsProperties.kt       # @ConfigurationProperties(prefix="cycling")
│
└── CyclingCoachApplication.kt
```

Planned but not yet implemented (do not assume the package exists): `calendar/`, `training/`, `coaching/`, `nutrition/`.

Generated output (do not edit): `target/generated-sources/openapi/` and `target/generated-sources/jooq/`.

### Event-Driven Pipeline

After `GarminSyncService` stores a new activity it publishes `GarminActivityStoredEvent`. Listeners run asynchronously on virtual threads.

```
GarminSyncJob (every 6h or manual trigger)
  └─ GarminSyncService → GarminActivitySyncService.sync()
       │   (also runs GarminWeightSyncService.sync())
       └─ GarminActivityService.storeAll
            └─ publishes GarminActivityStoredEvent(garminActivityId)        [per new activity]
                 │
                 ├─ RideEventListener.onGarminActivityStored  (@Async)
                 │   └─ RideComputeService.compute(activityId, null)
                 │        - parses raw_json → GarminActivity
                 │        - resolveRideFtp(date) = FtpTestRepository.findEffectiveAt(date)
                 │        - NP / IF / TSS / W·kg / best powers via RideCalculator
                 │        - rideRepository.save() — COALESCE-protected upsert
                 │        - publishes RideCalculatedEvent(rideId, activityId, date, tss)
                 │
                 │           ┌── TrainingLoadEventListener.onRideCalculated  (@Async)
                 │           │     └─ TrainingLoadService.recalculateFrom(date)
                 │           │
                 │           ├── FtpEventListener.onRideCalculated  (sync — same virtual thread)
                 │           │     └─ FtpTestDetectionService.detectFtpTest
                 │           │          - guard: existsByRideId → skip
                 │           │          - if name matches FTP test pattern:
                 │           │              classify type, calculate FTP, validate
                 │           │              ftpTestRepository.save
                 │           │              publishes FtpTestDetectedEvent
                 │           │                 └─ FtpChangeListener.onFtpTestDetected  (sync)
                 │           │                      - prevTest = findLatestBefore(D)
                 │           │                      - fromDate = prevTest?.date ?: D
                 │           │                      - rideService.recomputeRidesFrom(fromDate)
                 │           │                          (synchronous loop; publishEvent=false
                 │           │                           to avoid N concurrent PMC triggers)
                 │           │                      - publishes FtpBackfillCompleteEvent(fromDate)
                 │           │                          └─ TrainingLoadEventListener.onFtpBackfillComplete  (@Async)
                 │           │                                └─ TrainingLoadService.recalculateFrom(fromDate)
                 │           │
                 │           └── UserProfileEventListener.onRideCalculated  (@Async)
                 │                 └─ reads ride.max_hr → updateMaxHrIfHigher
                 │
                 └─ (parallel) GarminWeightStoredEvent → UserProfileEventListener stores weights
```

**Why two FTP events:** `FtpTestDetectedEvent` is fired by the detection service inside the compute thread and is consumed synchronously by `FtpChangeListener` to do the batch recompute. After the batch, `FtpChangeListener` emits the second event `FtpBackfillCompleteEvent` so the PMC recalc is decoupled from the ride package (no `ride → pmc` import) and runs once per backfill, not N times.

### Startup Reconciliation

`RideEventListener.onApplicationReady()` runs two passes:

1. **`reconcileOrphanedActivities()`** — finds `garmin_activity` rows that have no corresponding `ride` row (crash gap, parse failure) and recomputes them.
2. **`reconcileRidesWithNullTss()`** — finds `ride` rows that have `normalized_power IS NOT NULL` but `tss IS NULL`. These are rides whose original compute happened before any FTP test was saved (race condition during initial sync). With the current point-in-time FTP available, these get TSS via `computeAsync()`. The `COALESCE` in `RideRepository.save()` prevents this from being clobbered by any racing thread.

### PMC Lazy Fill

`PmcController.getPmc()` calls `TrainingLoadService.ensureUpToDate()` before querying. If `training_load.date` is before today, it calls `recalculateFrom(latestDate + 1 day)` to extend the chain with TSS=0 for each rest day. Starting from `latest + 1` (not `latest`) keeps the existing last row's CTL/ATL as the prior — they are not recomputed from a zero prior, which would clobber the chain.

### Key Backend Entities

**garmin_activity** — Raw Garmin Connect activity record. Holds both the original TCX file and the original activity JSON. All derived data flows from these.
- `id`, `external_id` (Garmin activity ID, dedup key), `raw_tcx`, `raw_json`, `imported_at`

**ride** — Computed cycling metrics. One ride per garmin_activity. The unique key is `external_id` (so the same Garmin activity always maps to the same ride row even after reimport). The `activity_id` FK can shift when an activity is replaced.
- Identity: `id`, `activity_id` (FK, non-unique), `external_id` (unique, dedup key)
- Activity: `date`, `name`, `start_time`, `manufacturer`, `distance`, `elevation_gain`, `elevation_descent`, `duration`
- Power/HR/cadence: `avg_*` / `max_*` for power, hr, cadence; `avg_grade`, `max_grade`
- Computed: `normalized_power`, `intensity_factor`, `tss`, `watts_per_kg`, `ftp` (point-in-time value used)
- Best powers: `best_power_5s/30s/1min/5min/10min/20min/60min`
- Speed: `avg_speed_mps`, `max_speed_mps`
- Quality: `variability_index`, `efficiency_factor`
- User: `rpe` (1–10), `coach_summary` (TEXT), `notes` (TEXT)

**training_load** — One row per calendar day. CTL/ATL/TSB are a running chain.
- `id`, `date` (unique), `tss` (daily sum), `ctl` (42d EWMA), `atl` (7d EWMA), `tsb` (CTL_prev − ATL_prev)

**ftp_test** — Sole authority for FTP at a point in time. `FtpTestRepository.findEffectiveAt(date)` returns the latest test with `date ≤ given`.
- `id`, `ride_id` (FK, partial-unique to prevent re-detection duplicates), `date`, `ftp_value`, `test_type` (RAMP_TEST | TWENTY_MIN_TEST | SIXTY_MIN_TEST | UNKNOWN | ESTIMATED), `weight_kg`, `notes`

**user_profile** — Single-row table (id=1). Holds the auto-detected max heart rate (monotonically increasing across rides). FTP is **not** cached here — read from `ftp_test`.
- `id` (always 1), `max_hr` (INTEGER, nullable), `updated_at`

**user_weight** — Body weight at a point in time. `WeightRepository.findWeightAtOrBefore(date)` is used by `RideComputeService` for W·kg.
- `id`, `date` (unique), `weight_kg`

**garmin_token** — DI OAuth2 access + refresh tokens. Credentials never stored.
- `access_token`, `refresh_token`, `di_client_id`, `access_token_expires_at`, `refresh_token_expires_at`, `created_at`

**garmin_activity_sync_cursor** / **garmin_weight_sync_cursor** — Single-row tables tracking the last fully-completed sync's `since` date. Only written after the sync loop exits successfully.

**garmin_weight** — Raw weight measurements from Garmin Connect (external_id + raw_json + imported_at). Propagated into `user_weight` by `UserProfileService.storeWeightMeasurements`.

### Database Schema

Single consolidated migration `V1__init.sql`. The full DDL is the source of truth — see `src/main/resources/db/migration/V1__init.sql`. Key table definitions:

```sql
CREATE TABLE garmin_activity (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT UNIQUE NOT NULL,
    raw_tcx     TEXT        NOT NULL,
    raw_json    TEXT,
    imported_at TEXT        NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE ride (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id       INTEGER,
    external_id       TEXT UNIQUE NOT NULL,
    date              TEXT NOT NULL,
    -- ... (distance, duration, power, HR, cadence, grade, NP, IF, TSS,
    --       best powers, watts/kg, FTP at ride date, speed, VI, EF,
    --       bike_id, rpe, coach_summary, notes)
    FOREIGN KEY (activity_id) REFERENCES garmin_activity(id)
);

CREATE TABLE user_profile (
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    max_hr     INTEGER,                       -- auto-detected from rides
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE user_weight (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    date      TEXT NOT NULL UNIQUE,
    weight_kg REAL NOT NULL
);

CREATE TABLE ftp_test (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    date      TEXT NOT NULL,
    ftp_value REAL NOT NULL,
    test_type TEXT CHECK (test_type IN ('RAMP_TEST','TWENTY_MIN_TEST','SIXTY_MIN_TEST','UNKNOWN','ESTIMATED')),
    weight_kg REAL,
    notes     TEXT,
    ride_id   INTEGER REFERENCES ride(id)
);
CREATE UNIQUE INDEX uq_ftp_test_ride_id ON ftp_test(ride_id) WHERE ride_id IS NOT NULL;

CREATE TABLE training_load (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,
    tss  REAL NOT NULL DEFAULT 0,
    ctl  REAL NOT NULL DEFAULT 0,
    atl  REAL NOT NULL DEFAULT 0,
    tsb  REAL NOT NULL DEFAULT 0
);

CREATE TABLE garmin_token (
    access_token             TEXT NOT NULL,
    refresh_token            TEXT NOT NULL,
    di_client_id             TEXT NOT NULL DEFAULT '',
    access_token_expires_at  TEXT NOT NULL,
    refresh_token_expires_at TEXT NOT NULL,
    -- ...
);

CREATE TABLE garmin_activity_sync_cursor (id INTEGER PRIMARY KEY CHECK (id=1), since TEXT NOT NULL);
CREATE TABLE garmin_weight_sync_cursor   (id INTEGER PRIMARY KEY,             since TEXT NOT NULL);

CREATE TABLE garmin_weight (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT NOT NULL UNIQUE,
    raw_json    TEXT NOT NULL,
    imported_at TEXT NOT NULL DEFAULT (datetime('now'))
);

```

### Race Condition Handling

During the initial Garmin sync, all activities are stored sequentially but `GarminActivityStoredEvent` listeners run in parallel on virtual threads. Without protection:

1. All compute threads start before any FTP test is saved → all rides get null TSS.
2. The FTP test ride's compute eventually detects the test and saves it.
3. `FtpChangeListener` runs the backfill → assigns TSS to all rides in range.
4. **Race**: a parallel compute thread that started before the FTP test was saved finishes after the backfill, calling `save()` with `tss=null`. Without protection, this would clobber the backfill.

**Two layers of defense:**

1. **`RideRepository.save()` uses `COALESCE(excluded.X, ride.X)` for FTP-dependent fields** (`tss`, `ftp`, `intensity_factor`, `watts_per_kg`). A null new value cannot overwrite a non-null existing value. The upsert is monotonic for these fields — values can only move from null to set, never back.

2. **Startup `reconcileRidesWithNullTss()`** sweeps any remaining null-TSS rides on `ApplicationReadyEvent` and recomputes them through the current point-in-time FTP. Catches any rides that were never in any backfill range.

---

## Frontend Architecture

NgRx state lives in `+state/` inside each feature directory (the `+` prefix sorts it to the top). HTTP calls use exclusively the generated services from `core/api/` (produced by openapi-generator). No hand-written `HttpClient` calls. Components use external template + style files (no inline `template:` strings).

```
src/app
├── core/
│   ├── +state/            # App-wide state slice (settings)
│   ├── api/               # Generated services + models (do not edit)
│   ├── guard/
│   └── interceptor/
├── shared/
│   ├── material/          # Material module facade
│   └── util/
├── features/
│   ├── shell/             # App shell, side nav, sync button
│   ├── home/              # Landing
│   ├── dashboard/
│   │   ├── +state/        # actions, reducer, effects, selectors
│   │   └── components/
│   │       ├── metric-card/      # KPI tiles (CTL, ATL, TSB, FTP w/ W·kg)
│   │       ├── pmc-chart/        # CTL / ATL / TSB line chart (no FTP line)
│   │       └── recent-rides/
│   ├── rides/
│   │   ├── +state/
│   │   ├── rides-list/
│   │   └── ride-detail/
│   ├── activities/        # sync trigger
│   ├── user-profile/
│   │   ├── +state/
│   │   └── components/
│   │       ├── ftp-history/       # FTP timeline with "View ride" deep link
│   │       └── weight-history/
│   └── settings/
│       ├── +state/
│       └── components/
│           ├── power-zones/        # %FTP → watts
│           ├── hr-zones/           # %max HR → bpm (capped at max HR for Z5)
│           └── training-zones/     # (merged view component, unused after tab split)
├── components/
│   ├── chart/
│   ├── map/
│   └── common/
└── app.config.ts
```

### UI Conventions

- **Tabs with deeplinks**: settings and user-profile pages use `mat-tab-group` with a `?tab=` query param synced two-way (URL → selected tab on load; tab change → `router.navigate` with `replaceUrl: true`).
  - `/settings?tab=power|hr`
  - `/profile?tab=ftp|weight`
- **PMC chart**: shows only CTL / ATL / TSB. The zero line is rendered darker (`rgba(0,0,0,0.35)`, 1.5 px) to make the TSB baseline easily readable.
- **Power zones**: Coggan 7-zone model, %FTP boundaries default 55 / 75 / 90 / 105 / 120, all configurable in `application.yml` under `cycling.zones.power.*`.
- **HR zones**: 5-zone max HR–referenced model. Zone upper bounds default 60 / 72 / 82 / 92 (% max HR). Z5 upper bound is capped at the recorded max HR — never `∞`. Configurable in `application.yml` under `cycling.zones.hr.*`.

---

## API Endpoints (current)

```
GET    /api/rides                       — paginated ride list
GET    /api/rides/{id}                  — ride detail
GET    /api/pmc?from=&to=               — CTL/ATL/TSB time series (lazy-fills today on rest days)
GET    /api/ftp                         — FTP history; each entry includes rideId where applicable
GET    /api/weight                      — weight history
GET    /api/settings                    — current FTP (from ftp_test), max HR, zone thresholds
POST   /api/garmin/sync                 — manual sync trigger
GET    /api/garmin/auth/status          — auth status
POST   /api/garmin/auth                 — re-authenticate
GET    /health                          — actuator
GET    /actuator/prometheus             — metrics
```

Future endpoints (not yet implemented): `/api/calendar`, `/api/coaching/analyze`.

---

## Configuration

All runtime config via `application.yml` or environment variables — no write API for settings.

```yaml
cycling:
  zones:
    power:                       # % of FTP
      z1Max: 55
      z2Max: 75
      z3Max: 90
      z4Max: 105
      z5Max: 120
    hr:                          # % of max HR
      z1Max: 60
      z2Max: 72
      z3Max: 82
      z4Max: 92                  # Z5 capped at 100% max HR by the UI
```

Garmin auth: `GARMIN_EMAIL` + `GARMIN_PASSWORD` env vars drive startup login. DI OAuth2 tokens persisted in `garmin_token`. Credentials are never persisted.

AI provider (when integrated): `AI_PROVIDER=ollama|anthropic`, `AI_MODEL=...`.

---

## Deployment

### Docker Compose

```yaml
services:
  cycling-coach:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./data:/app/data           # SQLite DB lives here
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - GARMIN_EMAIL=${GARMIN_EMAIL}
      - GARMIN_PASSWORD=${GARMIN_PASSWORD}
      # AI_PROVIDER=ollama or anthropic (when integrated)
```

### Dockerfile

Multi-stage. The `frontend-maven-plugin` installs Node and runs `ng build` inside the Maven phase, so no separate Node stage is needed.

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
COPY frontend ./frontend
RUN mvn -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

---

## Testing Strategy

- **Backend unit** — MockK, `@Tag("unit")`. One service method in isolation. Examples: `RideComputeServiceTest`, `FtpTestDetectionServiceTest`, `TrainingLoadServiceTest`, `RideCalculatorTest`, `TcxParserTest`.
- **Backend integration** — `@SpringBootTest` + WireMock for the Garmin API. Real SQLite. Each test resets the DB via `AbstractApplicationIntegrationTest.resetState()`. Notable suites:
  - `GarminSyncPipelineIntegrationTest` — full sync → ride → PMC → weight pipeline with a seeded FTP test.
  - `FtpBackfillIntegrationTest` — verifies that an FTP test detected after rides exist produces correct TSS for all subsequent rides (covers the race-condition fix).
  - `PmcControllerIntegrationTest` — verifies `ensureUpToDate()` extends the EWMA chain on rest days and is idempotent when today already has a row.
  - `GarminActivityImportIntegrationTest`, `GarminWeightSyncIntegrationTest`, `GarminSyncServiceIntegrationTest`, `GarminClientIntegrationTest` — Garmin client wiring.
- **Frontend** — Vitest via `ng test`.

---

## Development Workflow

### API-First
1. Edit `src/main/resources/api-spec/cycling-coach-api.yaml`
2. `./mvnw generate-sources` — regenerates backend interfaces + DTOs and Angular services + models
3. Implement the generated Spring interface in the corresponding controller
4. Use the generated Angular service via injection — no hand-written HTTP

### Local Dev
1. `./mvnw spring-boot:run` (backend on :8080)
2. `cd frontend && ng serve` (frontend on :4200, proxies API to :8080)
3. Garmin sync runs automatically every 6 h. Manual trigger: `POST /api/garmin/sync` or use the Sync button in the UI.

### Schema Changes
The schema is currently in a single consolidated `V1__init.sql`. While pre-release, prefer extending V1 with a DB reset over stacking incremental migrations. After the first production deployment, switch to additive `V{N}__*.sql` migrations.

---

## Out of Scope (for now)
- Multi-user
- Strava/Wahoo integrations
- Social features
- AI integration (coaching package is reserved but unimplemented)
- ZWO export for Zwift
- Activity calendar view
