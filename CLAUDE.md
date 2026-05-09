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

# Run all tests
./mvnw verify

# Run a single backend test class
./mvnw test -Dtest=RideServiceTest

# Run backend unit tests only (skip integration tests)
./mvnw test -Dgroups="unit"

# Regenerate jOOQ classes from SQLite schema (requires DB to exist at ./data/cycling-coach.db)
./mvnw jooq-codegen:generate

# Docker Compose (production-like, single container)
docker compose up --build
```

## Architecture

### API-First Workflow

The API spec at `api-spec/cycling-coach-api.yaml` is the source of truth. `./mvnw generate-sources` runs openapi-generator twice:
1. **Backend**: Generates Kotlin Spring controller interfaces + DTOs under `target/generated-sources/openapi/src/main/kotlin/` (packages `com.cyclingcoach.generated.api` and `.generated.model`). jOOQ classes go to `target/generated-sources/jooq/` (`com.cyclingcoach.generated.jooq`). Both directories are added to the Kotlin source set.
2. **Frontend**: Generates Angular services + TypeScript models into `frontend/src/app/core/api/`

**Never hand-edit anything under `target/generated-sources/` or `frontend/src/app/core/api/`** — all changes must go through the YAML spec first.

### Backend Package Layout (`com.cyclingcoach`, `src/main/kotlin/`)

Packages are domain-scoped. Each domain owns its controller, service, repository, and MapStruct mapper. Controllers implement the generated interfaces from `com.cyclingcoach.generated.api`; they never expose jOOQ records directly — MapStruct mappers convert them to generated DTOs.

Currently implemented packages:

```
config/             — Spring, jOOQ, Flyway, Spring AI, async, Garmin client wiring only
ride/               — Raw activity ingestion + computed cycling metrics (NP, IF, TSS, best powers).
                      Split into RideReadService / RideComputeService / RideService;
                      RideEventListener drives compute on activity store; emits RideCalculatedEvent.
                      Includes ActivityFileParser / TcxActivityFileParser (raw TCX → RideInput).
pmc/                — Training load chain: CTL/ATL/TSB per calendar day, recalculated forward
                      from earliest affected date on RideCalculatedEvent.
tcx/                — Framework-agnostic TCX parser (JDK DOM/XPath, no Spring); reusable standalone.
ftp/                — FTP history (auto-detected from 20min best power × 0.95, or estimated).
garmin/             — Garmin sync wiring: GarminController, GarminSyncJob (@Scheduled),
                      GarminSyncService, GarminSyncable interface.
garmin/connect/     — Framework-agnostic Garmin Connect client (activity, client, weight subpkgs).
garmin/internal/    — GarminTokenStore (DI OAuth2 tokens, no credentials).
user/               — UserProfile (single-row, id=1) + Weight history.
settings/           — Read-only projection of Spring properties (@ConfigurationProperties).
```

Planned but not yet implemented (do not assume the package exists): `calendar/`, `training/`, `coaching/`, `nutrition/`. AI integration and ZWO export are aspirational at this stage.

Generated output (DO NOT EDIT): `target/generated-sources/openapi/` and `target/generated-sources/jooq/`.

### Central Event Flow

`Garmin sync (GarminSyncJob/Service)` → activity persisted → `RideEventListener` → `RideComputeService` (parses TCX via `tcx/`, computes metrics via `RideCalculator`) → emits `RideCalculatedEvent` → `pmc/` recalculates the CTL/ATL/TSB chain forward from the earliest affected date. Reads go through `RideReadService` and `pmc/` queries.

### Key Domain Rules

- **Activity** stores the raw TCX. **Ride** holds all computed metrics derived from it — NP, IF, TSS, best power intervals, watts/kg.
- **TrainingLoad** is a running EWMA chain (CTL = 42d, ATL = 7d). After any sync, recalculate forward from the earliest affected date — it cannot be derived from a single row.
- TSB = CTL_yesterday − ATL_yesterday (computed from previous day's values, not today's).
- FTP auto-detection: `bestPower20min × 0.95` from the ride's power stream.
- AI responses (coach summary, training plan, nutrition plans) are **persisted on first call** — the AI is not re-invoked for the same resource unless the user explicitly regenerates.
- Garmin credentials are **never stored** — only the DI OAuth2 tokens from a successful SSO login (`garmin_token` table).
- The `UserProfile` table always has exactly one row (id = 1).
- `PlannedWorkout.workoutBlocks` is a JSON array of interval blocks (type, durationSeconds, powerLow %FTP, powerHigh %FTP, repeat) — used to render ZWO files for Zwift.

### Database

SQLite, managed by Flyway migrations in `src/main/resources/db/migration/`. jOOQ generates type-safe Kotlin classes from the live schema — run `./mvnw jooq-codegen:generate` after schema changes. No ORM; all queries go through jOOQ DSL.

### Frontend

Angular 21 with NgRx. NgRx state lives in `+state/` inside each feature directory (the `+` prefix keeps it sorted to the top). HTTP calls come exclusively from the generated services in `core/api/` — no hand-written `HttpClient` calls. Spring Boot serves the Angular build from `src/main/resources/static/` (output of `ng build`).

### AI Integration

Spring AI with Ollama and Anthropic as **switchable providers** (not a fallback chain). Controlled via `AI_PROVIDER=ollama|anthropic` and `AI_MODEL=...` environment variables. A `coaching/` package is planned to own all AI calls but does not yet exist in the codebase.

### Testing

- Backend unit tests: MockK (not Mockito), test one service method in isolation
- Backend integration tests: `@SpringBootTest` + WireMock for Garmin/external APIs; real SQLite for DB round-trips
- Frontend: Jasmine/Karma for components and services

### Feature Branch Convention

`feature/<short-kebab-description>` — e.g., `feature/power-zone-dashboard`

## Cycling Domain Changes

Any change to cycling business logic — NP/IF/TSS formulas, CTL/ATL/TSB EWMA constants, FTP detection, power zone thresholds, training load recalculation rules, or any other metric derivation — **must be reviewed by the road-cycling-coach agent before implementation**. Invoke it via the `road-cycling-coach` subagent type and describe the proposed change. Do not commit cycling metric logic changes without this review.

## Domain Reference

Cycling metrics implemented in the `ride/` package:
- **NP** (Normalized Power): 30s rolling mean of power raised to 4th power, averaged, then 4th root
- **IF** (Intensity Factor): NP / FTP
- **TSS** (Training Stress Score): `duration_hours × IF² × 100`
- **CTL/ATL/TSB**: See `docs/knowledge/training-load-metrics.md` for formulas and interpretation

## Configuration

All runtime config via `application.yml` or environment variables — there is no write API for settings. Zone thresholds live under `cycling.zones.power.*` and `cycling.zones.hr.*`. Frontend reads current config from `GET /api/settings`.
