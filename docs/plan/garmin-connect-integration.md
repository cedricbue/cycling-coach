# Garmin Connect Integration — Technical Plan

## Purpose

Handles all communication with Garmin Connect: SSO authentication, DI OAuth2 token lifecycle,
activity import (JSON + TCX), and weight sync. Co-located in the same Maven project as the
application today but structured so it can be extracted into its own Maven module with no
changes to either side's business logic.

---

## Coupling Contract

This is the boundary that must be preserved. Everything else is an implementation detail
that can change independently on either side.

### Garmin → App (outbound events)

The Garmin packages never call application services directly. They communicate exclusively
via Spring application events:

| Event | Published by | Payload |
|---|---|---|
| `GarminActivityStoredEvent` | `GarminActivityService.storeAll` | `garminActivityId` (PK in `garmin_activity`), `externalId` (Garmin ID) |
| `GarminWeightStoredEvent` | `GarminWeightSyncService.sync` | list of `WeightEntry(date, weightKg)` |

The application side (`ride/`, `user/`, `pmc/`) listens to these events and never imports
from `garmin.connect.*` packages.

### App → Garmin (inbound trigger)

The application triggers sync via a single interface:

```kotlin
// garmin/GarminSyncable.kt
interface GarminSyncable {
    fun sync(): CompletableFuture<Unit>
    val name: String
}
```

`GarminSyncService` discovers all `GarminSyncable` beans and calls them in sequence.
The application has no other compile-time dependency on Garmin internals.

### Token persistence (bridge)

The framework-agnostic `GarminConnect` client depends on a `TokenStore` interface defined
in the client package. The Spring implementation (`GarminTokenStore` in `garmin/internal/`)
bridges the client to the `garmin_token` SQLite table. This is the only place where
Spring DI wires into the otherwise framework-agnostic client.

```
garmin/connect/client/TokenStore.kt     ← interface (no Spring import)
garmin/internal/GarminTokenStore.kt     ← Spring @Component implementing TokenStore
```

### Import rules (enforced by package structure)

```
garmin.connect.client  →  [no app imports]
garmin.connect.activity →  [no app imports; publishes GarminActivityStoredEvent]
garmin.connect.weight   →  [no app imports; publishes GarminWeightStoredEvent]
garmin.internal         →  [imports Spring + garmin.connect.client only]
garmin (orchestration)  →  [imports garmin.connect.* + Spring; no ride/pmc/ftp/user imports]
```

---

## Package Structure

```
com.cyclingcoach.garmin
│
├── GarminController.kt         # implements generated GarminApi; manual sync trigger + auth status
├── GarminSyncJob.kt            # @Scheduled(6h) + ApplicationReadyEvent startup auth
├── GarminSyncService.kt        # discovers GarminSyncable beans; runs them in sequence
├── GarminSyncable.kt           # interface: sync(): CompletableFuture<Unit>
├── GarminProperties.kt         # @ConfigurationProperties(prefix="garmin")
│
├── connect/
│   ├── AbstractGarminConnectTest.kt    # [test] base for Garmin HTTP integration tests
│   │
│   ├── client/                 # Framework-agnostic — no Spring imports
│   │   ├── GarminConnect.kt            # main entry point: login(), getActivities(), getWeightRange()
│   │   ├── GarminConfig.kt             # base URLs, credentials
│   │   ├── GarminTokens.kt             # value object: accessToken, refreshToken, diClientId, expiry
│   │   ├── GarminException.kt
│   │   ├── TokenStore.kt               # interface: load(), save(), deleteAll()
│   │   ├── GarminAuthService.kt        # SSO → DI OAuth2 token exchange; auto-refresh; auto-reauth
│   │   └── GarminHttpClient.kt         # OkHttp wrapper; injects Bearer token; retries on 401
│   │
│   ├── activity/
│   │   ├── GarminActivityService.kt    # storeAll(): fetches page-by-page; upserts garmin_activity;
│   │   │                                # publishes GarminActivityStoredEvent per new row
│   │   ├── GarminActivitySyncService.kt # GarminSyncable impl; manages cursor
│   │   ├── GarminActivityRepository.kt  # jOOQ: upsert, findByExternalId, findWithoutRide
│   │   ├── GarminActivitySyncCursorRepository.kt
│   │   └── GarminActivityStoredEvent.kt # garminActivityId (Long), externalId (String)
│   │
│   └── weight/
│       ├── GarminWeightService.kt       # fetches date-range weight measurements; upserts garmin_weight;
│       │                                 # publishes GarminWeightStoredEvent
│       ├── GarminWeightSyncService.kt    # GarminSyncable impl; manages cursor
│       ├── GarminWeightRepository.kt     # jOOQ: upsert
│       ├── GarminWeightSyncCursorRepository.kt
│       └── GarminWeightStoredEvent.kt    # entries: List<WeightEntry(date, weightKg)>
│
└── internal/
    └── GarminTokenStore.kt     # Spring @Component; implements TokenStore; persists to garmin_token
```

---

## Authentication Flow

Garmin uses a two-step SSO + DI OAuth2 flow. Credentials (`GARMIN_EMAIL`, `GARMIN_PASSWORD`)
are supplied at startup only — they are never persisted.

```
GarminSyncJob.onApplicationReady()
  └─ GarminConnect.login(email, password)
       └─ GarminAuthService
            1. POST sso-base-url/signin  →  SSO ticket
            2. POST di-auth-base-url/oauth2/token  →  DI access + refresh tokens
            3. GarminTokenStore.save(tokens)

On 401 during API call:
  GarminHttpClient detects 401 → GarminAuthService.refreshOrReauth()
    - try refresh token first
    - if refresh fails → full re-login with stored credentials
```

Tokens are persisted in `garmin_token` (single-row table). The table is cleared on each
fresh login so stale tokens cannot accumulate.

---

## Activity Sync

`GarminActivitySyncService` implements `GarminSyncable`. It:

1. Reads the cursor from `garmin_activity_sync_cursor` (the `since` date of the last
   fully-completed sync, or epoch if first run).
2. Calls `GarminActivityService.storeAll(since)`, which pages through
   `/activitylist-service/activities/search/activities` until no new activities are returned.
3. For each new activity, fetches the full JSON detail and the TCX file, then upserts into
   `garmin_activity` (dedup key: `external_id`).
4. Publishes `GarminActivityStoredEvent(garminActivityId, externalId)` per new row.
5. After the loop exits successfully, writes today's date to `garmin_activity_sync_cursor`.

The cursor is only advanced on success — a partial sync failure leaves the cursor at the
last successful sync date, so the next run re-fetches the same window.

Page size is configurable: `garmin.connect.activity.page-size` (default 100).

---

## Weight Sync

`GarminWeightSyncService` implements `GarminSyncable`. It:

1. Reads the cursor from `garmin_weight_sync_cursor`.
2. Calls `GarminWeightService` which fetches `/weight-service/weight/dateRange` for the
   window `[since, today]`.
3. Upserts each measurement into `garmin_weight` (dedup key: `external_id`).
4. Publishes one `GarminWeightStoredEvent` with the full list of new entries.
5. Advances the cursor to today on success.

Weight entries have a numeric `samplePk` as external ID (not a date), which is why the
weight cursor is a separate table from the activity cursor.

---

## Database Schema (`V2__garmin.sql`)

All Garmin tables live in `V2__garmin.sql`. The core application schema (`V1__init.sql`)
has no Garmin table definitions. The FK from `ride.activity_id → garmin_activity.id`
crosses migration files; SQLite does not validate FK target existence at DDL time.

```sql
CREATE TABLE garmin_activity (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT UNIQUE NOT NULL,   -- Garmin activity ID; dedup key
    raw_tcx     TEXT NOT NULL,           -- source of truth for grade/altitude
    raw_json    TEXT,                    -- full Garmin Connect activity JSON
    imported_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE garmin_token (
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    access_token             TEXT NOT NULL,
    refresh_token            TEXT NOT NULL,
    di_client_id             TEXT NOT NULL DEFAULT '',
    access_token_expires_at  TEXT NOT NULL,
    refresh_token_expires_at TEXT NOT NULL,
    created_at               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Single-row cursor; only advanced after a fully-completed sync
CREATE TABLE garmin_activity_sync_cursor (
    id    INTEGER PRIMARY KEY CHECK (id = 1),
    since TEXT NOT NULL
);

CREATE TABLE garmin_weight (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT NOT NULL UNIQUE,   -- numeric samplePk from Garmin
    raw_json    TEXT NOT NULL,
    imported_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE garmin_weight_sync_cursor (
    id    INTEGER PRIMARY KEY,
    since TEXT NOT NULL
);
```

---

## Testing

Integration tests for the Garmin packages extend `AbstractGarminConnectTest`, which provides:
- A full `@SpringBootTest` context (via `@IntegrationTest` meta-annotation)
- A WireMock server wired to all Garmin base URLs via `@DynamicPropertySource`
- `garminConnect: GarminConnect` and `garminTokenStore: GarminTokenStore` autowired
- `@BeforeEach resetConnectState()`: deletes tokens, resets WireMock, stubs the SSO auth flow

Tests that write to Garmin DB tables must add their own `@BeforeEach cleanDatabase()`
to truncate those tables — `AbstractGarminConnectTest` does not do this automatically.

| Test class | What it covers |
|---|---|
| `GarminClientIntegrationTest` | SSO login, DI token exchange, token refresh |
| `GarminActivityImportIntegrationTest` | Full activity fetch + TCX download + `garmin_activity` upsert + `GarminActivityStoredEvent` |
| `GarminWeightSyncIntegrationTest` | Weight fetch + `garmin_weight` upsert + cursor + `GarminWeightStoredEvent` |

End-to-end sync pipeline tests (sync → ride → PMC) live in
`GarminSyncPipelineIntegrationTest` and extend `AbstractApplicationIntegrationTest`
because they cross the Garmin/application boundary.

---

## Future Extraction to Separate Maven Modules

When this code is extracted, the target module structure is:

```
garmin-connect-client/          ← garmin/connect/client/ (zero Spring dependency)
garmin-connect-activity/        ← garmin/connect/activity/ + garmin/internal/
garmin-connect-weight/          ← garmin/connect/weight/ + garmin/internal/
cycling-coach-app/              ← everything else (imports the above as Maven deps)
```

The coupling contract above already enforces the boundary. What changes at extraction time:

1. Spring application events (`GarminActivityStoredEvent`, `GarminWeightStoredEvent`) become
   messages on a shared bus (e.g. Redis Pub/Sub, Kafka, or an in-process broker abstraction).
2. The `TokenStore` interface stays in `garmin-connect-client` and `GarminTokenStore` moves
   to the app module (it accesses the DB, which lives with the app).
3. `V2__garmin.sql` either stays with the Garmin module (if the module owns its own DB) or
   remains in the app's migrations (if they share the SQLite file).
4. `GarminSyncable` and `GarminSyncService` move to a thin orchestration module or the app.

No changes are required to `ride/`, `pmc/`, `ftp/`, `user/`, or `settings/` because they
only depend on the events, not on Garmin internals.
