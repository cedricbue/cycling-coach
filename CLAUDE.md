# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal road cycling training analysis tool. Analyzes rides from Garmin Connect data, provides visualization, coaching, and training insights. Single-user, file-based SQLite database, deployable as a single JAR or Docker container.

## Build & Run Commands

```bash
# Full build (generates code, builds Angular, packages JAR)
mvn package

# Generate all code (OpenAPI interfaces + jOOQ classes) without building
mvn generate-sources

# Run backend in dev mode (port 8080)
mvn spring-boot:run

# Run frontend dev server (port 4200, proxies API to :8080)
cd frontend && ng serve

# Run all tests
mvn verify

# Run a single backend test class
mvn test -Dtest=RideServiceTest

# Run backend unit tests only (skip integration tests)
mvn test -Dgroups="unit"

# Regenerate jOOQ classes from SQLite schema (requires DB to exist at ./data/cycling-coach.db)
mvn jooq-codegen:generate

# Docker Compose (production-like, single container)
docker compose up
```

## Architecture

### API-First Workflow

The API spec at `api-spec/cycling-coach-api.yaml` is the source of truth. `mvn generate-sources` runs openapi-generator twice:
1. **Backend**: Generates Kotlin Spring controller interfaces + DTOs into `src/main/java/com/cyclingcoach/generated/api/` and `generated/model/`
2. **Frontend**: Generates Angular services + TypeScript models into `frontend/src/app/core/api/`

**Never hand-edit anything in `generated/`** — all changes must go through the YAML spec first.

### Backend Package Layout (`com.cyclingcoach`)

Packages are domain-scoped. Each domain owns its controller, service, repository, and MapStruct mapper. Controllers implement the generated interfaces from `generated/api/`; they never expose jOOQ records directly — MapStruct mappers convert them to generated DTOs.

```
config/       — Spring, jOOQ, Flyway, JobRunr, Spring AI wiring only
activity/     — Raw Garmin activity records (TCX stored as-is)
ride/         — Computed cycling metrics from TCX (NP, IF, TSS, best powers)
pmc/          — Training load chain: CTL/ATL/TSB per calendar day
ftp/          — FTP history (auto-detected from 20min best power × 0.95, or estimated)
calendar/     — Calendar aggregation queries
training/     — Goal event, AI training plan, ZWO export
coaching/     — Spring AI integration, on-demand ride analysis
nutrition/    — AI-generated nutrition plans (per ride or planned workout)
sync/         — Garmin SSO via OkHttp, JobRunr-scheduled sync job
settings/     — Read-only projection of Spring properties (@ConfigurationProperties)
generated/    — DO NOT EDIT: openapi-generator output + jOOQ codegen output
```

### Key Domain Rules

- **Activity** stores the raw TCX. **Ride** holds all computed metrics derived from it — NP, IF, TSS, best power intervals, watts/kg.
- **TrainingLoad** is a running EWMA chain (CTL = 42d, ATL = 7d). After any sync, recalculate forward from the earliest affected date — it cannot be derived from a single row.
- TSB = CTL_yesterday − ATL_yesterday (computed from previous day's values, not today's).
- FTP auto-detection: `bestPower20min × 0.95` from the ride's power stream.
- AI responses (coach summary, training plan, nutrition plans) are **persisted on first call** — the AI is not re-invoked for the same resource unless the user explicitly regenerates.
- Garmin credentials are **never stored** — only the session cookies from a successful SSO login (`GarminSession` table).
- The `UserProfile` table always has exactly one row (id = 1).
- `PlannedWorkout.workoutBlocks` is a JSON array of interval blocks (type, durationSeconds, powerLow %FTP, powerHigh %FTP, repeat) — used to render ZWO files for Zwift.

### Database

SQLite, managed by Flyway migrations in `src/main/resources/db/migration/`. jOOQ generates type-safe Kotlin classes from the live schema — run `mvn jooq-codegen:generate` after schema changes. No ORM; all queries go through jOOQ DSL.

### Frontend

Angular 21 with NgRx. NgRx state lives in `+state/` inside each feature directory (the `+` prefix keeps it sorted to the top). HTTP calls come exclusively from the generated services in `core/api/` — no hand-written `HttpClient` calls. Spring Boot serves the Angular build from `src/main/resources/static/` (output of `ng build`).

### AI Integration

Spring AI with Ollama and Anthropic as **switchable providers** (not a fallback chain). Controlled via `AI_PROVIDER=ollama|anthropic` and `AI_MODEL=...` environment variables. The `coaching/` package owns all AI calls.

### Testing

- Backend unit tests: MockK (not Mockito), test one service method in isolation
- Backend integration tests: `@SpringBootTest` + WireMock for Garmin/external APIs; real SQLite for DB round-trips
- Frontend: Jasmine/Karma for components and services

### Feature Branch Convention

`feature/<short-kebab-description>` — e.g., `feature/power-zone-dashboard`

## Domain Reference

Cycling metrics implemented in the `ride/` package:
- **NP** (Normalized Power): 30s rolling mean of power raised to 4th power, averaged, then 4th root
- **IF** (Intensity Factor): NP / FTP
- **TSS** (Training Stress Score): `duration_hours × IF² × 100`
- **CTL/ATL/TSB**: See `docs/knowledge/training-load-metrics.md` for formulas and interpretation

## Configuration

All runtime config via `application.yml` or environment variables — there is no write API for settings. Zone thresholds live under `cycling.zones.power.*` and `cycling.zones.hr.*`. Frontend reads current config from `GET /api/settings`.
