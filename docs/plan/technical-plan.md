# Cycling Coach App - Technical Plan

## Purpose
Personal road cycling training analysis tool. Analyze rides from Garmin Connect data, provide visualization, coaching, and training insights.

## Tech Stack

### Backend
| Component | Choice                               | Notes                                                                                                |
|---|--------------------------------------|------------------------------------------------------------------------------------------------------|
| Framework | Spring Boot 4.0.x (4.0.6)           | Latest stable                                                                                        |
| Language | Kotlin 2.3.x (2.3.21)               | JVM target 25; type safety, conciseness                                                              |
| SQL | jOOQ 3.19.x (3.19.32)               | Type-safe queries, code generation from Flyway schema                                                |
| Migrations | Flyway 11.x (11.14.1)               | Versioned schema migrations; SQLite supported in core                                                |
| Database | SQLite (org.xerial:sqlite-jdbc)      | File-based, backup-friendly, no server                                                               |
| AI Integration | Spring AI 2.0.x (add when GA)       | Ollama and Anthropic as switchable options (not fallback); deferred until 2.0 GA                     |
| Garmin API | OkHttp 4.x via `client/garmin/` package | Framework-agnostic `GarminClient`; handles SSO → DI OAuth2 token exchange, auto-refresh, auto-reauth |
| Job Scheduling | Spring `@Scheduled`                  | Built-in; 6h default interval, configurable via `sync.interval-ms`                                  |
| TCX Parsing | Jackson XML (tools.jackson)          | Jackson 3 coordinates; parse TCX from DB storage                                                     |
| API Spec | OpenAPI 3.1 YAML                     | Spec-first; lives in `api-spec/cycling-coach-api.yaml`                                               |
| API Generator | openapi-generator-maven-plugin 7.x   | Generates Spring controller interfaces + DTOs from spec; implementations provided by domain services |
| DTO Mapping | MapStruct                            | Compile-time mapping from jOOQ models to generated DTOs                                              |
| Metrics | Micrometer + Prometheus              | Observability                                                                                        |
| Testing | WireMock                             | Mock Garmin/external APIs                                                                            |

### Frontend
| Component | Choice | Notes |
|---|---|---|
| Framework | Angular 21 | Latest LTS-friendly |
| State Management | NgRx | Store, Effects, Router, Entity |
| Charts | Chart.js + angular2-chartjs | Overlay charts, zone bars, trend lines |
| Maps | Leaflet + angular-leaflet | GPX route display with color-coded gradient |
| UI Components | Angular Material | Native Angular integration |

### Build & Infrastructure
| Component | Choice | Notes |
|---|---|---|
| Build (backend) | Maven 3.9.x | pom.xml with Kotlin |
| Build (frontend) | com.github.eirslett:frontend-maven-plugin 1.15.x | Installs Node, runs npm ci + ng build; output written to src/main/resources/static/ |
| Package | Single JAR | Angular static served by Spring Boot |
| Container | Docker Compose | Single backend service with SQLite volume |
| CI | GitHub Actions | `mvn verify` on every push and PR — runs unit + integration tests |

## Application Architecture

### Backend Packages

Structured by domain. Controllers implement generated interfaces from `generated/api/`; DTOs come from `generated/model/`. Each domain package owns its service, repository, and MapStruct mapper.

```
com.cyclingcoach
├── config/              # Infrastructure only: Spring, jOOQ, Flyway, Spring AI, async, Garmin client wiring
│   ├── GarminClientConfig.kt      # @Bean for GarminConfig + GarminClient (reads garmin.connect.* properties)
│   └── AsyncConfig.kt             # virtual thread executor bean for @Async (VIRTUAL_THREAD_EXECUTOR)
├── activity/            # Garmin-synced activities: list, detail
│   ├── ActivityController.kt  # implements generated ActivitiesApi
│   ├── ActivityService.kt
│   ├── ActivityRepository.kt
│   └── ActivityMapper.kt      # jOOQ record → generated DTO
├── ride/                # Ride calculations: NP, IF, TSS; zone breakdowns
│   ├── RideService.kt
│   ├── RideRepository.kt
│   └── RideMapper.kt
├── pmc/                 # Training load chain: CTL, ATL, TSB
│   ├── PmcController.kt       # implements generated PmcApi
│   ├── PmcService.kt
│   └── TrainingLoadRepository.kt
├── ftp/                 # FTP history (auto-detected / estimated)
│   ├── FtpController.kt       # implements generated FtpApi
│   ├── FtpService.kt
│   └── FtpRepository.kt
├── calendar/            # Calendar aggregation queries
│   ├── CalendarController.kt  # implements generated CalendarApi
│   └── CalendarService.kt
├── training/            # Goal event, AI-generated training plan, ZWO export
│   ├── TrainingController.kt  # implements generated TrainingApi
│   ├── TrainingPlanService.kt # orchestrates AI generation + persistence
│   ├── ZwoExportService.kt    # renders PlannedWorkout blocks → ZWO XML
│   └── TrainingRepository.kt
├── coaching/            # Spring AI integration, on-demand ride analysis
│   ├── CoachingController.kt  # implements generated CoachingApi
│   └── CoachingService.kt
├── nutrition/           # AI-generated nutrition plans for rides and planned workouts
│   ├── NutritionController.kt # implements generated NutritionApi
│   ├── NutritionService.kt    # calls AI with ride/workout context, persists result
│   └── NutritionRepository.kt
├── client/garmin/       # Framework-agnostic Garmin Connect client (no Spring dependency)
│   ├── GarminClient.kt            # public API: login, refreshToken, getActivities, downloadTcx
│   ├── GarminConfig.kt            # SSO + DI auth + API base URLs (data class)
│   ├── GarminTokens.kt            # access/refresh token model with isExpired() helpers
│   ├── TokenStore.kt              # pluggable persistence interface (save/load/delete)
│   ├── GarminActivity.kt          # Garmin activity/activityType data models
│   ├── GarminException.kt         # sealed hierarchy: GarminAuthException, GarminApiException, etc.
│   └── internal/
│       ├── GarminAuthService.kt   # SSO POST → serviceTicketId → DI token exchange (tries multiple client IDs)
│       └── GarminHttpClient.kt    # OkHttp wrapper (GET, postForm, postJson)
├── sync/                # Garmin sync: @Scheduled job, Spring wiring, TokenStore implementation
│   ├── GarminSyncJob.kt           # @Scheduled every 6h + ApplicationReadyEvent startup auth
│   ├── GarminSyncService.kt       # orchestrates sync: fetches activity list, downloads TCX, persists via ActivityService
│   ├── AsyncGarminSyncService.kt  # @Async wrapper using virtual thread executor (for non-blocking trigger endpoint)
│   ├── GarminTokenStore.kt        # implements TokenStore; persists DI OAuth2 tokens to garmin_token table via jOOQ
│   ├── GarminProperties.kt        # @ConfigurationProperties(prefix="sync.garmin"): email, password, initialFetchDays
│   └── SyncController.kt          # implements generated SyncApi
├── settings/            # Zone thresholds (read-only API, configured via Spring properties)
│   ├── SettingsController.kt  # implements generated SettingsApi
│   └── SettingsProperties.kt  # @ConfigurationProperties binding
├── generated/           # All generated sources — excluded from manual edits
│   ├── api/             # Controller interfaces from openapi-generator
│   ├── model/           # DTOs from openapi-generator
│   └── jooq/            # jOOQ table/record classes
└── CyclingCoachApplication.kt
```

### Event-Driven Post-Sync Processing

After `GarminSyncService` stores a new activity it publishes an `ActivityStoredEvent` via Spring's `ApplicationEventPublisher`. Listeners run in the same thread (synchronous, ordered) so no extra infrastructure is needed.

```
GarminSyncService
  └─ publishes ActivityStoredEvent(activityId, date)
       │
       ├─ RideService          @EventListener — parses TCX, computes NP/IF/TSS/best-powers, writes Ride row
       │                        also auto-detects FTP (bestPower20min × 0.95) and records FtpTest if improved
       │
       └─ TrainingLoadService  @EventListener(condition) — runs after RideService writes TSS;
                                recalculates CTL/ATL/TSB chain forward from event.date
```

**Why synchronous events instead of direct calls:**
- `GarminSyncService` stays decoupled from `RideService` and `TrainingLoadService`
- Ordering is explicit (ride must be computed before training load needs its TSS)
- No message broker needed for a single-node app
- Easy to test each listener in isolation with a synthetic event

**Ordering guarantee:** Spring `@EventListener` methods on the same event run in declaration order within the same `ApplicationContext`. To make the order explicit, annotate `TrainingLoadService`'s listener with `@Order(2)` and `RideService`'s with `@Order(1)`.

**Event class** (in `sync/` package, owned by the publisher):
```kotlin
data class ActivityStoredEvent(val activityId: Long, val date: LocalDate)
```

**Startup consistency check** — `RideService` also listens to `ApplicationReadyEvent` and queries for any `Activity` row that has no corresponding `Ride` row (crash-gap or first-run). For each orphan it directly calls its own `computeFromActivity(activityId)`, which is the same method the `ActivityStoredEvent` listener calls. This closes the gap where the process died after persisting an activity but before the calculation completed:

```
On ApplicationReadyEvent:
  SELECT a.id, a.start_time FROM activity a
  LEFT JOIN ride r ON r.activity_id = a.id
  WHERE r.id IS NULL
  → for each result: computeFromActivity(activityId)
  → then: TrainingLoadService.recalculateFrom(earliestOrphanDate)
```

TrainingLoad is recalculated once after all orphans are processed (not per-activity) to avoid redundant chain walks.

### Key Backend Entities

**Activity** — Raw record of a Garmin activity as downloaded during sync. Holds the original TCX file and the minimal indexed fields needed for list and calendar queries. Everything else is derived from rawTcx or computed into Ride.
- id, externalId (for Garmin dedup), name, startTime, lastSyncTime
- rawTcx (TEXT, full TCX file — source of truth)

**Ride** — Computed cycling metrics derived by parsing the Activity's TCX. One Ride per Activity. Contains all values needed for analysis, charting, and PMC calculations.
- id, activityId (FK), date, distance, elevationGain, elevationDescent, duration
- avgPower, maxPower, avgHR, maxHR, avgCadence, maxCadence
- avgGrade, maxGrade
- normalizedPower (NP, 30s rolling 4th-power mean), intensityFactor (IF = NP/FTP), tss (TSS = duration_h × IF² × 100)
- bestPower5s, bestPower30s, bestPower1min, bestPower5min, bestPower10min, bestPower20min, bestPower60min (peak mean power for each duration, derived from a rolling-window pass over the TCX power stream; bestPower20min × 0.95 drives FTP auto-detection)
- wattsPerKg (NP / weight at ride date, looked up from UserWeight)
- ftp (FTP at time of ride, auto-detected or estimated)
- bikeId (FK to Bike, nullable)
- rpe (INTEGER 1–10, nullable — subjective effort rating; complements TSS for tracking how the body responds to load)
- coachSummary (TEXT, nullable — AI-generated analysis, persisted on first call so the AI is not invoked again for the same ride)
- notes (TEXT)

**TrainingLoad** — One row per calendar day representing the Performance Management Chart state. CTL/ATL/TSB are a running chain — each day depends on the previous — so they must be persisted and recalculated forward from the earliest affected date after any sync.
- id, date (UNIQUE)
- tss (sum of all ride TSS that day)
- ctl (42-day EWMA of TSS — fitness), atl (7-day EWMA of TSS — fatigue), tsb (CTL_prev − ATL_prev — form)

**FtpTest** — A detected or estimated FTP value at a point in time. Used to determine the correct FTP when calculating IF and TSS for rides around that date.
- id, date, ftpValue, testType: AUTO_DETECTED | ESTIMATED
- notes (TEXT)

**GoalEvent** — The rider's single active target event. Drives training plan generation. Only one active goal at a time.
- id, name, eventDate, eventType (GRAN_FONDO | RACE | SPORTIVE), distanceKm, elevationM, notes
- status: ACTIVE | COMPLETED | CANCELLED

**TrainingPlan** — AI-generated multi-week plan for the active GoalEvent. Persisted on first generation so the AI is not called again unless explicitly regenerated.
- id, goalEventId (FK), generatedAt, summary (TEXT — AI overview of the plan), status: ACTIVE | COMPLETED | CANCELLED

**TrainingWeek** — One week within a TrainingPlan. Represents a training phase block (e.g. base, build, peak, taper).
- id, planId (FK), weekNumber, startDate, phase (BASE | BUILD | PEAK | TAPER), targetTss, notes

**PlannedWorkout** — A single structured training session within a week. Stores interval blocks as JSON so a ZWO file can be generated for Zwift import. Power targets are expressed as % of FTP (Zwift resolves them against the rider's FTP).
- id, weekId (FK), scheduledDate, name, workoutType (ENDURANCE | INTERVALS | SWEET_SPOT | THRESHOLD | RECOVERY | LONG_RIDE)
- targetDurationSeconds, targetTss
- workoutBlocks (TEXT, JSON array of interval blocks — each block has type, durationSeconds, powerLow % FTP, powerHigh % FTP, repeat)
- completedRideId (FK to Ride, nullable — linked when the rider completes this workout)

**Bike** — A named bike owned by the rider. Linked to rides to track per-bike usage (total distance, ride count).
- id, name, isActive

**UserProfile** — Single-row table holding the rider's current FTP and weight. Updated automatically when a new FTP is detected or when the user logs a new weight. Used as the default for live calculations without querying history tables.
- id (always 1), currentFtp, currentWeightKg, updatedAt

**UserWeight** — Rider weight at a point in time. Used to calculate W/kg for each ride by looking up the closest entry on or before the ride date.
- id, date, weightKg


**NutritionPlan** — AI-generated nutrition guidance for a ride or planned workout. Persisted on first generation. Structured fields allow concrete targets (carbs/h, fluids/h) alongside narrative guidance for pre/during/post ride. Linked to either a `Ride` (actual) or `PlannedWorkout` (planned) — not both.
- id, rideId (FK, nullable), plannedWorkoutId (FK, nullable)
- targetCarbsPerHourG (REAL — g/h during ride, scales with intensity and duration)
- targetFluidsMlPerHour (REAL)
- preRide (TEXT — meal timing and composition before the ride)
- duringRide (TEXT — product suggestions, timing, amounts per hour)
- postRide (TEXT — recovery window, protein + carb targets)
- generatedAt

**GarminTokens** (`garmin_token` table) — Persisted DI OAuth2 access and refresh tokens from a successful Garmin SSO login. Credentials are never stored. `GarminClient` automatically refreshes the access token before expiry; falls back to full re-auth using in-memory credentials from startup. When the refresh token expires the app re-authenticates on startup from `sync.garmin.email`/`sync.garmin.password` env vars.
- id, access_token, refresh_token, di_client_id, access_token_expires_at, refresh_token_expires_at, created_at

### Database Schema

```sql
-- Migrations managed by Flyway
-- V1__init.sql
CREATE TABLE activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT UNIQUE NOT NULL,  -- Garmin activity ID, used for dedup
    name TEXT,
    start_time TEXT NOT NULL,
    last_sync_time TEXT,
    raw_tcx TEXT NOT NULL              -- source of truth; all metrics derived from here
);

CREATE TABLE ride (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id INTEGER UNIQUE,
    date TEXT NOT NULL,
    distance REAL,
    elevation_gain REAL,
    elevation_descent REAL,
    duration REAL,
    avg_power REAL,
    max_power REAL,
    avg_hr REAL,
    max_hr REAL,
    avg_cadence REAL,
    max_cadence REAL,
    avg_grade REAL,
    max_grade REAL,
    normalized_power REAL,
    intensity_factor REAL,
    tss REAL,
    best_power_5s REAL,
    best_power_30s REAL,
    best_power_1min REAL,
    best_power_5min REAL,
    best_power_10min REAL,
    best_power_20min REAL,
    best_power_60min REAL,
    watts_per_kg REAL,
    ftp REAL,
    bike_id INTEGER REFERENCES bike(id),
    rpe INTEGER CHECK(rpe BETWEEN 1 AND 10),
    coach_summary TEXT,
    notes TEXT,
    FOREIGN KEY (activity_id) REFERENCES activity(id)
);

-- Single row (id = 1); updated when FTP is detected or weight is logged
CREATE TABLE goal_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    event_date TEXT NOT NULL,
    event_type TEXT NOT NULL,  -- GRAN_FONDO | RACE | SPORTIVE
    distance_km REAL,
    elevation_m REAL,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE training_plan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    goal_event_id INTEGER NOT NULL REFERENCES goal_event(id),
    generated_at TEXT NOT NULL DEFAULT (datetime('now')),
    summary TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE training_week (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL REFERENCES training_plan(id),
    week_number INTEGER NOT NULL,
    start_date TEXT NOT NULL,
    phase TEXT NOT NULL,  -- BASE | BUILD | PEAK | TAPER
    target_tss REAL,
    notes TEXT
);

CREATE TABLE planned_workout (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_id INTEGER NOT NULL REFERENCES training_week(id),
    scheduled_date TEXT NOT NULL,
    name TEXT NOT NULL,
    workout_type TEXT NOT NULL,
    target_duration_seconds INTEGER,
    target_tss REAL,
    workout_blocks TEXT NOT NULL,  -- JSON array of interval blocks
    completed_ride_id INTEGER REFERENCES ride(id)
);

CREATE TABLE bike (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE user_profile (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    current_ftp REAL,
    current_weight_kg REAL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE user_weight (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,
    weight_kg REAL NOT NULL
);

CREATE TABLE ftp_test (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,
    ftp_value REAL NOT NULL,
    test_type TEXT CHECK(test_type IN ('AUTO_DETECTED', 'ESTIMATED')),
    notes TEXT
);

CREATE TABLE nutrition_plan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ride_id INTEGER REFERENCES ride(id),
    planned_workout_id INTEGER REFERENCES planned_workout(id),
    target_carbs_per_hour_g REAL,
    target_fluids_ml_per_hour REAL,
    pre_ride TEXT,
    during_ride TEXT,
    post_ride TEXT,
    generated_at TEXT NOT NULL DEFAULT (datetime('now')),
    CHECK (
        (ride_id IS NOT NULL AND planned_workout_id IS NULL) OR
        (ride_id IS NULL AND planned_workout_id IS NOT NULL)
    )
);

-- DI OAuth2 tokens from Garmin authentication; credentials are never persisted
CREATE TABLE garmin_token (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    di_client_id TEXT NOT NULL DEFAULT '',
    access_token_expires_at TEXT NOT NULL,
    refresh_token_expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- One row per calendar day; updated after every ride import or sync
CREATE TABLE training_load (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,
    tss REAL NOT NULL DEFAULT 0,   -- total TSS for the day
    ctl REAL NOT NULL DEFAULT 0,   -- 42-day EWMA (fitness)
    atl REAL NOT NULL DEFAULT 0,   -- 7-day EWMA (fatigue)
    tsb REAL NOT NULL DEFAULT 0    -- CTL_prev - ATL_prev (form)
);
```

### jOOQ Generation

```xml
<!-- pom.xml — OpenAPI stub generation (runs twice: backend interfaces + Angular services) -->
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.6.0</version>
    <executions>
        <!-- Backend: Kotlin Spring controller interfaces + DTOs -->
        <execution>
            <id>generate-backend</id>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/api-spec/cycling-coach-api.yaml</inputSpec>
                <generatorName>kotlin-spring</generatorName>
                <apiPackage>com.cyclingcoach.generated.api</apiPackage>
                <modelPackage>com.cyclingcoach.generated.model</modelPackage>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <useSpringBoot3>true</useSpringBoot3>
                    <useTags>true</useTags>
                </configOptions>
            </configuration>
        </execution>
        <!-- Frontend: Angular services + TypeScript models -->
        <execution>
            <id>generate-frontend</id>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/api-spec/cycling-coach-api.yaml</inputSpec>
                <generatorName>typescript-angular</generatorName>
                <output>${project.basedir}/frontend/src/app/core/api</output>
                <configOptions>
                    <ngVersion>17</ngVersion>
                    <providedInRoot>true</providedInRoot>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- pom.xml — frontend build -->
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.15.0</version>
    <configuration>
        <workingDirectory>frontend</workingDirectory>
        <installDirectory>target</installDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-npm</id>
            <goals><goal>install-node-and-npm</goal></goals>
            <configuration><nodeVersion>v20.19.0</nodeVersion></configuration>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals><goal>npm</goal></goals>
        </execution>
        <execution>
            <id>ng-build</id>
            <goals><goal>npm</goal></goals>
            <phase>generate-resources</phase>
            <configuration>
                <arguments>run build -- --configuration production --output-path=../src/main/resources/static</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- pom.xml — jOOQ codegen -->
<plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <configuration>
        <jdbc>
            <driver>org.sqlite.JDBC</driver>
            <url>jdbc:sqlite:./data/cycling-coach.db</url>
        </jdbc>
        <generator>
            <name>org.jooq.codegen.KotlinGenerator</name>
        </generator>
    </configuration>
</plugin>
```

## Frontend Structure

NgRx state lives in a `+state/` folder inside each feature (standard NgRx schematics convention; `+` sorts it to the top).

```
src/app
├── core/
│   ├── guard/
│   ├── interceptor/
│   ├── api/            # Generated Angular services + TypeScript models (do not edit)
│   └── +state/         # App-wide state: settings (read-only from /api/settings)
├── shared/
│   ├── material/       # Angular Material module
│   └── util/
├── features/
│   ├── activities/     # List/detail pages
│   │   └── +state/     # actions, reducer, effects, selectors
│   ├── calendar/       # Activity calendar
│   │   └── +state/
│   ├── pmc/            # Performance Management Chart
│   │   └── +state/
│   ├── ftp/            # FTP history
│   │   └── +state/
│   ├── training-plan/  # Goal event + AI training plan + ZWO download
│   │   └── +state/
│   ├── trends/         # Trend analysis
│   │   └── +state/
│   ├── comparison/     # Side-by-side ride comparison
│   │   └── +state/
│   └── coaching/       # AI coach chat
│       └── +state/
├── components/
│   ├── chart/          # Overlay chart, zone bars, heatmaps
│   ├── map/            # Leaflet GPX map
│   └── common/         # Reusable components
└── app.config.ts
```

## Key Features

### Activity Calendar
- Month/year grid with daily ride count
- Click to navigate to activity detail

### Activity Detail
- **Overlay chart**: speed, HR, power, grade on shared time axis
- **Zone bars**: power zones, HR zones below chart
- **GPX map**: Leaflet with color-coded gradient (HR or power)
- **Summary stats**: distance, elevation, duration, avg/max values
- **AI Coach** (on-demand): button sends activity data to selected AI, shows response
- **RPE input**: optional rating after ride

### Performance Management Chart (PMC)
> Background: [knowledge/training-load-metrics.md](../knowledge/training-load-metrics.md)
- Line chart: CTL (fitness), ATL (fatigue), TSB (form) over a rolling date range
- Per-ride TSS displayed on activity detail
- TSB zone coloring: green (race-ready +5→+25), yellow (neutral), red (overreaching < −30)
- Recompute trigger: any import or sync recalculates the full `training_load` chain forward from the earliest affected date

### Training Plan
- User defines a single active goal event (name, date, type, distance, elevation)
- AI generates a structured multi-week plan based on current CTL, FTP, W/kg, and weeks until the event
- Plan is divided into phases: Base → Build → Peak → Taper
- Each week shows planned workouts with type, target duration, and target TSS
- Each planned workout displays structured intervals and can be downloaded as a **ZWO file** for direct Zwift import
- Completed rides can be linked to planned workouts to track planned vs actual TSS
- Plan is persisted on first generation — AI is not re-invoked unless the user explicitly regenerates

### Nutrition Plan
- AI-generated nutrition guidance for each ride and each planned workout
- Structured output: pre-ride meal timing, during-ride carbs per hour (g/h) and fluids (ml/h), post-ride recovery targets
- Carbs/h and fluids/h scale with ride duration and intensity (TSS, IF)
- Persisted on first generation — AI not re-invoked unless explicitly regenerated
- Visible as a tab on activity detail and on each planned workout in the training plan

### Trends & Comparison
- Multi-activity overlay charts (pace comparison)
- FTP progression over time
- VO2max estimation

### Settings
- All configuration via `application.yml` / environment variables (no write API)
- AI provider: `AI_PROVIDER=ollama|anthropic`, model: `AI_MODEL=...`
- Zone thresholds: `cycling.zones.power.*`, `cycling.zones.hr.*`
- Garmin auth: `sync.garmin.email` + `sync.garmin.password` env vars drive startup login; DI OAuth2 tokens persisted in `garmin_token` table; credentials never stored
- Frontend reads current config from `GET /api/settings` (read-only projection of Spring properties)

## Deployment

### Docker Compose

```yaml
version: '3.8'
services:
  cycling-coach:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./data:/app/data
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - AI_PROVIDER=ollama    # or anthropic
      - OLLAMA_BASE_URL=http://host.docker.internal:11434
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      # No Garmin credentials here — authenticate via POST /api/sync/authenticate after first start
```

### Dockerfile (multi-stage)

frontend-maven-plugin handles Node/npm inside the Maven build — no separate Node stage needed.

```dockerfile
# Build: Maven installs Node, builds Angular, packages JAR
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
COPY frontend ./frontend
RUN mvn package -q -DskipTests

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

## Development Workflow

### API-First Workflow
1. Edit `api-spec/cycling-coach-api.yaml`
2. `mvn generate-sources` — generates Spring controller interfaces + DTOs (`generated/api/`, `generated/model/`) and Angular services + models (`frontend/src/app/core/api/`)
3. Implement the generated Spring interfaces in each domain controller
4. Angular services are ready to inject — no hand-written HTTP calls

### Local Dev
1. `mvn spring-boot:run` (backend on :8080)
2. `ng serve` (frontend on :4200, proxies API to :8080)
3. Garmin sync fires automatically every 6 h via `@Scheduled`; trigger manually via `POST /api/sync/trigger`

### API Endpoints (Phase 1)
```
GET    /api/activities              - list activities
GET    /api/activities/{id}         - activity detail
POST   /api/sync/authenticate        - SSO login (credentials in body, never stored; DI OAuth2 tokens saved to DB)
POST   /api/sync/trigger             - trigger Garmin sync (requires valid session)
GET    /api/sync/status              - session validity + last sync time
GET    /api/calendar                - calendar data
GET    /api/trends/{metric}          - trend data
GET    /api/pmc                      - CTL/ATL/TSB time series (query params: from, to)
GET    /api/goal                            - active goal event
POST   /api/goal                            - create goal event (replaces any existing active goal)
GET    /api/training-plan                   - current training plan (weeks + workouts)
POST   /api/training-plan/generate          - trigger AI plan generation for active goal
GET    /api/training-plan/workouts/{id}/zwo - download ZWO file for Zwift import
GET    /api/ftp                             - FTP history (auto-detected / estimated)
POST   /api/coaching/analyze                    - on-demand AI ride analysis
POST   /api/nutrition/generate/ride/{id}        - generate nutrition plan for a ride
POST   /api/nutrition/generate/workout/{id}     - generate nutrition plan for a planned workout
GET    /api/nutrition/ride/{id}                 - get persisted nutrition plan for a ride
GET    /api/nutrition/workout/{id}              - get persisted nutrition plan for a planned workout
GET    /api/settings                - app settings (read-only; configured via application.yml / env vars)
GET    /health                      - health check
```

## MVP Scope (Phase 1)
1. jOOQ + Flyway schema setup
2. Garmin sync via Spring RestClient + `@Scheduled`
3. TCX parsing from synced Garmin data
4. `ActivityStoredEvent` → `RideService` computes NP/IF/TSS/best-powers, auto-detects FTP
5. `ActivityStoredEvent` → `TrainingLoadService` recalculates CTL/ATL/TSB chain from earliest affected date
6. Activity list + detail pages (TSS visible on detail)
7. Performance Management Chart (CTL/ATL/TSB)
8. Overlay chart with HR/power/speed/grade
9. GPX map with Leaflet
10. Activity calendar
11. SQLite persistence (single-file, WAL mode)

## Out of Scope (Later)
- Social features
- Multi-user
- Advanced workout plans
- Strava/Wahoo/other integrations
- Heatmaps
