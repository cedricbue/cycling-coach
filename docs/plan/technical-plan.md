# Cycling Coach App — Technical Plan

## Purpose

Personal road cycling training analysis tool. Analyzes rides from Garmin Connect data,
provides visualization, coaching, and training insights. Single-user, file-based SQLite
database, deployable as a single JAR or Docker container.

Garmin Connect integration is documented separately in
[`garmin-connect-integration.md`](garmin-connect-integration.md). This plan covers
everything the Garmin integration delivers data *to*: ride computation, training load,
FTP detection, user profile, settings, and the frontend.

---

## Tech Stack

### Backend
| Component | Choice | Notes |
|---|---|---|
| Framework | Spring Boot 4.0.x | Latest stable |
| Language | Kotlin 2.3.x | JVM target 25; type safety, conciseness |
| SQL | jOOQ 3.19.32 | Type-safe queries, code generation from Flyway schema |
| Migrations | Flyway 11.x | Versioned schema migrations; SQLite supported in core |
| Database | SQLite (org.xerial:sqlite-jdbc) | File-based, WAL mode, backup-friendly, no server |
| AI Integration | Spring AI 2.0.0-M6 | Anthropic and Ollama switchable via `AI_PROVIDER` env var. Daily coaching recommendation. |
| Weather | Open-Meteo API | Free, no API key — lat/lon only. Abstracted behind `WeatherProvider` interface. |
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

### API-First Workflow

The API spec at `src/main/resources/api-spec/cycling-coach-api.yaml` is the source of truth.
`./mvnw generate-sources` runs openapi-generator twice:

1. **Backend**: generates Kotlin Spring controller interfaces + DTOs under
   `target/generated-sources/openapi/` (`com.cyclingcoach.generated.api` / `.generated.model`).
   jOOQ classes go to `target/generated-sources/jooq/` (`com.cyclingcoach.generated.jooq`).
2. **Frontend**: generates Angular services + TypeScript models into `frontend/src/app/core/api/`.

Never hand-edit anything under `target/generated-sources/` or `frontend/src/app/core/api/`.

### Package Layout (`com.cyclingcoach`, `src/main/kotlin/`)

Packages are domain-scoped. Each domain owns its controller, service, and repository.
Controllers implement generated interfaces and map manually to generated DTOs (no MapStruct).
Cross-domain coupling is via Spring events only — no direct service calls across packages.

```
com.cyclingcoach
│
├── Profiles.kt              # string constants for Spring profile names (TEST, LOCAL)
│
├── config/
│   ├── AsyncConfig.kt              # virtual thread executor (VIRTUAL_THREAD_EXECUTOR)
│   ├── GarminClientConfig.kt       # @Bean for GarminConfig + GarminConnect
│   └── WebConfig.kt
│
├── ride/                    # Computed cycling metrics — entry point is GarminActivityStoredEvent
│   ├── RidesController.kt          # implements generated RidesApi
│   ├── RideService.kt              # orchestrates: computeForActivity, recomputeRidesFrom, reconcile
│   ├── RideComputeService.kt       # parses raw_json → GarminActivity, computes NP/IF/TSS/best-powers;
│   │                                # looks up FTP via FtpTestRepository.findEffectiveAt(date);
│   │                                # rideRepository.save() (COALESCE-protected upsert);
│   │                                # publishes RideCalculatedEvent (publishEvent param controls this)
│   ├── RideCalculator.kt           # pure functions: NP, bestPower, TSS, IF, VI, EF
│   ├── RideRepository.kt           # jOOQ upsert with COALESCE for FTP-dependent fields;
│   │                                # findRidePage / findRideDetail for API reads
│   ├── RideInput.kt
│   ├── RideEventListener.kt        # @Async on GarminActivityStoredEvent → compute
│   │                                # @EventListener(ApplicationReadyEvent) → reconcile orphans + null-TSS rides
│   │                                # @Async on FtpTestDetectedEvent → batch recompute from
│   │                                # prevTestDate onward (publishEvent=false),
│   │                                # then publishes FtpBackfillCompleteEvent(fromDate)
│   ├── RideCalculatedEvent.kt
│   ├── ActivityFileParser.kt       # interface
│   └── TcxActivityFileParser.kt    # @Component adapter wrapping tcx.TcxParser
│
├── pmc/                     # Training load chain: CTL/ATL/TSB per calendar day
│   ├── PmcController.kt            # implements generated PmcApi; calls ensureUpToDate() lazily
│   ├── TrainingLoadService.kt      # EWMA chain walk; ensureUpToDate() extends chain to today
│   ├── TrainingLoadEventListener.kt # @Async on RideCalculatedEvent and FtpBackfillCompleteEvent
│   └── TrainingLoadRepository.kt   # jOOQ: findLatestDate, findByDate, findDailyTssSince, upsert
│
├── ftp/                     # FTP test detection + history
│   ├── FtpController.kt            # implements generated FtpApi; returns FTP history with rideId link
│   ├── FtpTestRepository.kt        # findEffectiveAt(date), findLatestBefore(date), existsByRideId, save
│   ├── FtpTestDetectionService.kt  # name detection, type classification, FTP calculation,
│   │                                # validation against previous FTP (NEEDS_REVIEW for large deltas)
│   ├── FtpEventListener.kt         # @EventListener(RideCalculatedEvent) → detectFtpTest
│   ├── FtpEstimationService.kt     # CP model + weighted fallbacks — NOT in compute path (kept for reference)
│   ├── FtpTestType.kt              # RAMP_TEST | TWENTY_MIN_TEST | SIXTY_MIN_TEST | UNKNOWN | ESTIMATED
│   ├── FtpTestDetectedEvent.kt
│   ├── FtpBackfillCompleteEvent.kt # emitted by RideEventListener after batch recompute
│   └── RidePowerSample.kt
│
├── tcx/                     # Framework-agnostic TCX parser (no Spring)
│   ├── TcxParser.kt                # JDK DOM/XPath; returns TcxData
│   └── TcxData.kt
│
├── user/                    # Single-row user profile + weight history
│   ├── WeightController.kt         # implements generated WeightApi
│   ├── UserProfileService.kt       # findMaxHr, updateMaxHrIfHigher, weight lookups
│   ├── UserProfileRepository.kt    # jOOQ: findMaxHr, updateMaxHrIfHigher
│   ├── UserProfileEventListener.kt # @Async on RideCalculatedEvent → updateMaxHrIfHigher
│   │                                # @Async on GarminWeightStoredEvent → storeWeightMeasurements
│   └── WeightRepository.kt         # upsert + findWeightAtOrBefore(date) for W·kg lookup
│
├── settings/                # Read-only settings projection
│   ├── SettingsController.kt       # implements generated SettingsApi
│   ├── SettingsService.kt          # currentFtp from ftp_test, maxHrBpm from user_profile,
│   │                                # zone thresholds from properties, aiProvider/aiModel from AiProperties
│   └── SettingsProperties.kt       # @ConfigurationProperties(prefix="cycling")
│
├── weather/                 # Weather abstraction — provider interface + implementations
│   ├── WeatherProvider.kt          # interface: fetchWeather(lat, lon): WeatherData
│   ├── WeatherData.kt              # data class: hourly temps/precip/wind/gusts, sunrise/sunset,
│   │                                # computed: minTemp, maxPrecipProb, maxWindGust, wouldBeDark
│   └── OpenMeteoClient.kt          # @Component implementing WeatherProvider via Open-Meteo free API
│                                    # (no API key; lat/lon only). Uses OkHttp + Jackson.
│                                    # Future providers: implement WeatherProvider, register as @Primary
│                                    # or use @ConditionalOnProperty to switch without code changes.
│
├── coaching/                # AI daily training recommendation
│   ├── CoachingController.kt       # implements generated CoachingApi (GET /api/coaching/recommendation)
│   ├── CoachingService.kt          # orchestrator: cache check → weather → PMC/FTP/rides context
│   │                                # → prompt build → AI call → JSON parse → DB upsert → return DTO
│   │                                # Injects WeatherProvider (not OpenMeteoClient) — swappable.
│   ├── DailyRecommendationRepository.kt  # jOOQ: findByDate(LocalDate), upsert()
│   └── OllamaHealthChecker.kt      # checks Ollama reachability at startup, logs WARN if down
│
└── CyclingCoachApplication.kt
```

`config/` now also contains:
- `AiConfig.kt` — `@Configuration` bean creating `ChatClient` from the active provider
  (reads `ai.provider` env var; injected with nullable `AnthropicChatModel?` and `OllamaChatModel?`
  to avoid startup failure when only one provider is configured). Runs Ollama health check on `@PostConstruct`.
- `AiProperties.kt` — `@ConfigurationProperties(prefix="ai")` with `provider` and `model` fields.

Garmin packages (`garmin/`, `garmin/connect/`, `garmin/internal/`) are documented in
[`garmin-connect-integration.md`](garmin-connect-integration.md). Application packages
listed above have **no compile-time imports** from `garmin.connect.*` — they receive
integration data exclusively via Spring events.

Planned but not yet implemented: `calendar/`, `nutrition/`.

Generated output (do not edit): `target/generated-sources/openapi/` and `target/generated-sources/jooq/`.

---

## Event-Driven Pipeline

The application treats `GarminActivityStoredEvent` and `GarminWeightStoredEvent` as inbound
integration events. The Garmin package publishes them; the application packages consume them.

```
[Garmin integration] publishes GarminActivityStoredEvent(garminActivityId, externalId)
     │
     └─ RideEventListener.onGarminActivityStored  (@Async, virtual thread)
          └─ RideComputeService.compute(activityId, publishEvent=true)
               - reads garmin_activity row (raw_json + raw_tcx)
               - parses raw_json → GarminActivity
               - resolveRideFtp(date) = FtpTestRepository.findEffectiveAt(date)  ← point-in-time
               - NP / IF / TSS / W·kg / best powers via RideCalculator
               - rideRepository.save()  ← COALESCE-protected upsert
               - publishes RideCalculatedEvent(rideId, activityId, date, tss)
                    │
                    ├─ TrainingLoadEventListener.onRideCalculated  (@Async)
                    │     └─ TrainingLoadService.recalculateFrom(date)
                    │
                    ├─ FtpEventListener.onRideCalculated  (sync — same virtual thread)
                    │     └─ FtpTestDetectionService.detectFtpTest(ride)
                    │          - guard: existsByRideId → skip if already detected
                    │          - if name matches FTP test pattern:
                    │              classify type, calculate FTP, validate bounds + delta
                    │              ftpTestRepository.save()
                    │              publishes FtpTestDetectedEvent
                    │                 └─ RideEventListener.onFtpTestDetected  (@Async)
                    │                      - prevTest = findLatestBefore(D)
                    │                      - fromDate = prevTest?.date ?: D
                    │                      - rideService.recomputeRidesFrom(fromDate)
                    │                          (sequential loop; publishEvent=false
                    │                           to avoid N concurrent PMC triggers)
                    │                      - publishes FtpBackfillCompleteEvent(fromDate)
                    │                          └─ TrainingLoadEventListener.onFtpBackfillComplete  (@Async)
                    │                                └─ TrainingLoadService.recalculateFrom(fromDate)
                    │
                    └─ UserProfileEventListener.onRideCalculated  (@Async)
                          └─ reads ride.max_hr → updateMaxHrIfHigher

[Garmin integration] publishes GarminWeightStoredEvent(entries)
     └─ UserProfileEventListener.onGarminWeightStored  (@Async)
          └─ UserProfileService.storeWeightMeasurements(entries) → user_weight upserts
```

**Why two FTP events:** `FtpTestDetectedEvent` is fired by the detection service inside the compute thread and is consumed (@Async) by `RideEventListener.onFtpTestDetected` to do the batch recompute. After the batch, it emits `FtpBackfillCompleteEvent` so the PMC recalc is decoupled from the ride package (no `ride → pmc` import) and runs once per backfill, not N times.

---

## Key Domain Rules

- **FTP authority**: `ftp_test` is the sole source of truth. `FtpTestRepository.findEffectiveAt(date)` returns the FTP from the latest test with `date ≤ rideDate`, or null. `user_profile` does not cache FTP.
- **Point-in-time FTP**: rides use the FTP in effect at their date. If no test exists on or before the ride date, IF/TSS/W·kg remain null — no estimation in the compute path.
- **FTP backfill**: when a new test is detected at date D, `FtpChangeListener` recomputes all rides from `prevTestDate` (or D if no previous) through today with `publishEvent=false`, then publishes one `FtpBackfillCompleteEvent` so PMC recalculates once.
- **COALESCE upsert**: `RideRepository.save()` uses `COALESCE(excluded.X, ride.X)` for FTP-dependent fields (`tss`, `ftp`, `intensity_factor`, `watts_per_kg`). A late-arriving compute with null values cannot overwrite a backfill-set value.
- **Startup reconciliation**: `RideEventListener.onApplicationReady()` runs `reconcileOrphanedActivities()` (activities without a ride row) and `reconcileRidesWithNullTss()` (rides with NP but no TSS) using the current point-in-time FTP.
- **TrainingLoad chain**: CTL (42d EWMA) and ATL (7d EWMA) form a running chain — they cannot be derived from a single row. After any sync, recalculate forward from the earliest affected date.
- **PMC lazy fill**: `PmcController.getPmc()` calls `TrainingLoadService.ensureUpToDate()` first. If `training_load.date < today`, it extends the chain from `latestDate + 1` (not from `latestDate`) to avoid recomputing the existing last row with a zero prior.
- **TSB** = CTL_yesterday − ATL_yesterday (previous day's values, not today's).
- **Max HR auto-detection**: `UserProfileEventListener` updates `user_profile.max_hr` only when a ride's `max_hr` is higher — monotonically increasing, never overwritten by a lower value.
- **HR zones**: 5 zones, max HR–referenced (% of `user_profile.max_hr`).
- **Power zones**: Coggan 7-zone model (% of FTP).

---

## Database Schema

Schema is split into two migrations:

- **`V1__init.sql`** — core application domain: `ride`, `user_profile`, `user_weight`, `ftp_test`, `training_load`
- **`V2__garmin.sql`** — Garmin Connect integration tables (see [`garmin-connect-integration.md`](garmin-connect-integration.md))

`ride.activity_id` has a FK to `garmin_activity.id` (V1 → V2). SQLite does not validate FK
target existence at DDL time — the ordering is safe.

```sql
-- V1__init.sql

CREATE TABLE ride (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id       INTEGER,              -- nullable FK to garmin_activity.id
    external_id       TEXT UNIQUE NOT NULL, -- dedup key (Garmin activity ID)
    date              TEXT NOT NULL,
    name              TEXT,
    start_time        TEXT,
    manufacturer      TEXT,
    distance          REAL,
    elevation_gain    REAL,
    elevation_descent REAL,
    duration          REAL,
    avg_power         REAL, max_power         REAL,
    avg_hr            REAL, max_hr            REAL,
    avg_cadence       REAL, max_cadence       REAL,
    avg_grade         REAL, max_grade         REAL,
    normalized_power  REAL,
    intensity_factor  REAL,
    tss               REAL,
    best_power_5s     REAL, best_power_30s    REAL, best_power_1min  REAL,
    best_power_5min   REAL, best_power_10min  REAL, best_power_20min REAL, best_power_60min REAL,
    watts_per_kg      REAL,
    ftp               REAL,                  -- point-in-time FTP used for this ride
    avg_speed_mps     REAL, max_speed_mps     REAL,
    variability_index REAL,
    efficiency_factor REAL,
    rpe               INTEGER CHECK (rpe BETWEEN 1 AND 10),
    coach_summary     TEXT,
    notes             TEXT,
    FOREIGN KEY (activity_id) REFERENCES garmin_activity (id)
);

CREATE TABLE user_profile (
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    max_hr     INTEGER,           -- auto-detected from rides; monotonically increasing
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
    ride_id   INTEGER REFERENCES ride (id)
);
CREATE UNIQUE INDEX uq_ftp_test_ride_id ON ftp_test (ride_id) WHERE ride_id IS NOT NULL;

CREATE TABLE training_load (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,
    tss  REAL NOT NULL DEFAULT 0,   -- total TSS for the day
    ctl  REAL NOT NULL DEFAULT 0,   -- 42-day EWMA (fitness)
    atl  REAL NOT NULL DEFAULT 0,   -- 7-day EWMA (fatigue)
    tsb  REAL NOT NULL DEFAULT 0    -- CTL_prev − ATL_prev (form)
);

INSERT INTO user_profile(id, updated_at) VALUES (1, datetime('now'));
```

**Schema change workflow:** while pre-release, prefer editing V1/V2 with a DB reset
(`rm data/cycling-coach.db`) over stacking new migration files. After the first production
deployment, switch to additive `V{N}__*.sql` migrations only.

Workflow for new migrations:
1. Add `V{N}__description.sql`
2. Apply to local DB (let `./mvnw spring-boot:run` do it, or apply manually)
3. If applied manually, register in `flyway_schema_history` — Flyway validates checksums on every build
4. Run `./mvnw jooq-codegen:generate` to regenerate jOOQ classes

jOOQ maps SQLite INTEGER primary keys as `Int?` — cast with `.toLong()` when your domain model uses `Long`.

---

## Race Condition Handling

During the initial Garmin sync all activities are stored sequentially but
`GarminActivityStoredEvent` listeners run in parallel on virtual threads:

1. All compute threads start before any FTP test is saved → all rides get null TSS.
2. The FTP test ride's compute eventually detects the test and saves it.
3. `RideEventListener.onFtpTestDetected` runs the backfill → assigns TSS to all rides in range.
4. **Race**: a parallel compute thread that started before the FTP test was saved finishes after the backfill, calling `save()` with `tss=null`. Without protection, this would clobber the backfill.

**Two layers of defense:**

1. **COALESCE upsert** — `RideRepository.save()` uses `COALESCE(excluded.X, ride.X)` for
   FTP-dependent fields. A null new value cannot overwrite a non-null existing value.
2. **Startup reconciliation** — `reconcileRidesWithNullTss()` on `ApplicationReadyEvent`
   sweeps remaining null-TSS rides and recomputes them through the current point-in-time FTP.

---

## Frontend Architecture

NgRx state lives in `+state/` inside each feature directory. HTTP calls come exclusively
from the generated services in `core/api/` — no hand-written `HttpClient` calls.
Components use external template + style files (no inline `template:` strings).

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
│   ├── home/
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
│   │       ├── ftp-history/      # FTP timeline with "View ride" deep link
│   │       └── weight-history/
│   └── settings/
│       ├── +state/
│       └── components/
│           ├── power-zones/      # %FTP → watts
│           └── hr-zones/         # %max HR → bpm (capped at max HR for Z5)
├── components/
│   ├── chart/
│   ├── map/
│   └── common/
└── app.config.ts
```

### UI Conventions

- **Tabs with deeplinks**: `mat-tab-group` with `?tab=` query param synced two-way.
  - `/settings?tab=power|hr`
  - `/profile?tab=ftp|weight`
- **PMC chart**: CTL / ATL / TSB only. Zero line rendered at `rgba(0,0,0,0.35)`, 1.5 px.
- **Power zones**: Coggan 7-zone, %FTP boundaries default 55 / 75 / 90 / 105 / 120.
- **HR zones**: 5-zone max HR–referenced. Z5 upper bound capped at recorded max HR.

---

## API Endpoints

```
GET    /api/rides                  — paginated ride list
GET    /api/rides/{id}             — ride detail
GET    /api/pmc?from=&to=          — CTL/ATL/TSB time series (lazy-fills today on rest days)
GET    /api/ftp                    — FTP history; each entry includes rideId where applicable
GET    /api/weight                 — weight history
GET    /api/settings               — current FTP (from ftp_test), max HR, zone thresholds
POST   /api/garmin/sync            — manual sync trigger
GET    /api/garmin/auth/status     — auth status
POST   /api/garmin/auth            — re-authenticate
GET    /health                     — actuator
GET    /actuator/prometheus        — metrics
```

Future: `/api/calendar`, `/api/coaching/analyze`.

---

## Configuration

All runtime config via `application.yml` or environment variables — no write API for settings.

```yaml
cycling:
  zones:
    power:                 # % of FTP (Coggan 7-zone)
      z1Max: 55
      z2Max: 75
      z3Max: 90
      z4Max: 105
      z5Max: 120
    hr:                    # % of max HR (5-zone)
      z1Max: 60
      z2Max: 72
      z3Max: 82
      z4Max: 92            # Z5 upper bound capped at 100% max HR in the UI
```

Garmin auth: `GARMIN_EMAIL` + `GARMIN_PASSWORD` env vars. DI OAuth2 tokens persisted in
`garmin_token`. Credentials never persisted.

AI provider (when integrated): `AI_PROVIDER=ollama|anthropic`, `AI_MODEL=...`.

---

## Testing Strategy

- **Backend unit** — MockK, `@Tag("unit")`. One service method in isolation. No Spring context.
  Examples: `RideComputeServiceTest`, `FtpTestDetectionServiceTest`, `TrainingLoadServiceTest`,
  `RideCalculatorTest`, `TcxParserTest`.

- **Backend integration** — Both base classes carry `@IntegrationTest` (project meta-annotation:
  `@SpringBootTest + @ActiveProfiles(Profiles.TEST) + @Tag("integration")`). Concrete test classes
  inherit all three and must not redeclare them.
  - `AbstractApplicationIntegrationTest` — full context + WireMock + jOOQ DSL +
    `@BeforeEach resetState()` that truncates all tables.
  - `AbstractGarminConnectTest` — lighter Garmin-client-only context. See
    [`garmin-connect-integration.md`](garmin-connect-integration.md).
  - `ProfileEnforcementTest` — `@Tag("unit")` reflective test; fails if any `@SpringBootTest`
    class in the codebase is missing `@ActiveProfiles(Profiles.TEST)`.

- **Notable integration suites** (all extend `AbstractApplicationIntegrationTest`):
  - `GarminSyncPipelineIntegrationTest` — full sync → ride → PMC → weight pipeline.
  - `FtpBackfillIntegrationTest` — FTP test detected after rides → backfill → correct TSS.
  - `PmcControllerIntegrationTest` — `ensureUpToDate()` extends the EWMA chain on rest days.

- **Frontend** — Vitest via `ng test`.

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
      - ./data:/app/data        # SQLite DB
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - GARMIN_EMAIL=${GARMIN_EMAIL}
      - GARMIN_PASSWORD=${GARMIN_PASSWORD}
```

### Dockerfile

Multi-stage. `frontend-maven-plugin` installs Node and runs `ng build` inside the Maven
phase — no separate Node stage needed.

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

## Development Workflow

### Local Dev
1. `./mvnw spring-boot:run` — backend on :8080
2. `cd frontend && ng serve` — frontend on :4200, proxies API to :8080
3. Garmin sync runs automatically every 6 h. Manual trigger: `POST /api/garmin/sync`.

### API-First
1. Edit `src/main/resources/api-spec/cycling-coach-api.yaml`
2. `./mvnw generate-sources` — regenerates backend interfaces + DTOs and Angular services + models
3. Implement the generated Spring interface in the controller
4. Use the generated Angular service via injection

---

## Out of Scope (for now)
- Multi-user
- Strava / Wahoo integrations
- Social features
- AI coaching (package reserved, unimplemented)
- ZWO export for Zwift
- Activity calendar view
