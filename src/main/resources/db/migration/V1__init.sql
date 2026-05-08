-- V1__init.sql
-- Initial schema for cycling coach application

CREATE TABLE activity
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT UNIQUE NOT NULL, -- Garmin activity ID, used for dedup
    name        TEXT,
    start_time  TEXT        NOT NULL,
    raw_tcx     TEXT        NOT NULL, -- source of truth; used for grade/altitude data
    raw_json    TEXT                  -- full Garmin Connect activity JSON; used for pre-calculated metrics
);

CREATE TABLE bike
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT    NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE ride
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id       INTEGER UNIQUE,
    date              TEXT NOT NULL,
    distance          REAL,
    elevation_gain    REAL,
    elevation_descent REAL,
    duration          REAL,
    avg_power         REAL,
    max_power         REAL,
    avg_hr            REAL,
    max_hr            REAL,
    avg_cadence       REAL,
    max_cadence       REAL,
    avg_grade         REAL,
    max_grade         REAL,
    normalized_power  REAL,
    intensity_factor  REAL,
    tss               REAL,
    best_power_5s     REAL,
    best_power_30s    REAL,
    best_power_1min   REAL,
    best_power_5min   REAL,
    best_power_10min  REAL,
    best_power_20min  REAL,
    best_power_60min  REAL,
    watts_per_kg      REAL,
    ftp               REAL,
    avg_speed_mps     REAL,
    max_speed_mps     REAL,
    variability_index REAL,
    efficiency_factor REAL,
    bike_id           INTEGER REFERENCES bike (id),
    rpe               INTEGER CHECK (rpe BETWEEN 1 AND 10),
    coach_summary     TEXT,
    notes             TEXT,
    FOREIGN KEY (activity_id) REFERENCES activity (id)
);

CREATE TABLE user_profile
(
    id                INTEGER PRIMARY KEY CHECK (id = 1),
    current_ftp       REAL,
    current_weight_kg REAL,
    updated_at        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE user_weight
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    date      TEXT NOT NULL UNIQUE,
    weight_kg REAL NOT NULL
);

CREATE TABLE ftp_test
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    date      TEXT NOT NULL,
    ftp_value REAL NOT NULL,
    test_type TEXT CHECK (test_type IN ('RAMP_TEST', 'TWENTY_MIN_TEST', 'SIXTY_MIN_TEST', 'UNKNOWN', 'ESTIMATED')),
    weight_kg REAL,
    notes     TEXT
);

CREATE TABLE goal_event
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    event_date  TEXT NOT NULL,
    event_type  TEXT NOT NULL, -- GRAN_FONDO | RACE | SPORTIVE
    distance_km REAL,
    elevation_m REAL,
    notes       TEXT,
    status      TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE training_plan
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    goal_event_id INTEGER NOT NULL REFERENCES goal_event (id),
    generated_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    summary       TEXT,
    status        TEXT    NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE training_week
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id     INTEGER NOT NULL REFERENCES training_plan (id),
    week_number INTEGER NOT NULL,
    start_date  TEXT    NOT NULL,
    phase       TEXT    NOT NULL, -- BASE | BUILD | PEAK | TAPER
    target_tss  REAL,
    notes       TEXT
);

CREATE TABLE planned_workout
(
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    week_id                 INTEGER NOT NULL REFERENCES training_week (id),
    scheduled_date          TEXT    NOT NULL,
    name                    TEXT    NOT NULL,
    workout_type            TEXT    NOT NULL,
    target_duration_seconds INTEGER,
    target_tss              REAL,
    workout_blocks          TEXT    NOT NULL DEFAULT '[]', -- JSON array of interval blocks
    completed_ride_id       INTEGER REFERENCES ride (id)
);

CREATE TABLE nutrition_plan
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    ride_id                   INTEGER REFERENCES ride (id),
    planned_workout_id        INTEGER REFERENCES planned_workout (id),
    target_carbs_per_hour_g   REAL,
    target_fluids_ml_per_hour REAL,
    pre_ride                  TEXT,
    during_ride               TEXT,
    post_ride                 TEXT,
    generated_at              TEXT NOT NULL DEFAULT (datetime('now')),
    CHECK (
        (ride_id IS NOT NULL AND planned_workout_id IS NULL) OR
        (ride_id IS NULL AND planned_workout_id IS NOT NULL)
        )
);

-- DI OAuth2 tokens from Garmin authentication; credentials are never persisted
CREATE TABLE garmin_token
(
    id                       INTEGER PRIMARY KEY AUTOINCREMENT,
    access_token             TEXT NOT NULL,
    refresh_token            TEXT NOT NULL,
    di_client_id             TEXT NOT NULL DEFAULT '',
    access_token_expires_at  TEXT NOT NULL,
    refresh_token_expires_at TEXT NOT NULL,
    created_at               TEXT NOT NULL DEFAULT (datetime('now'))
);

-- One row per calendar day; updated after every ride import or sync
CREATE TABLE training_load
(
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL UNIQUE,
    tss  REAL NOT NULL DEFAULT 0, -- total TSS for the day
    ctl  REAL NOT NULL DEFAULT 0, -- 42-day EWMA (fitness)
    atl  REAL NOT NULL DEFAULT 0, -- 7-day EWMA (fatigue)
    tsb  REAL NOT NULL DEFAULT 0  -- CTL_prev - ATL_prev (form)
);

-- Tracks the since-date used by the last fully-completed Garmin sync.
-- Absent row = first run; GarminSyncService falls back to now() - initialFetchDays.
-- Only written after the sync loop exits without error, so an aborted sync never
-- advances the cursor past un-fetched older activities.
CREATE TABLE garmin_sync_cursor
(
    id    INTEGER PRIMARY KEY CHECK (id = 1),
    since TEXT NOT NULL
);

-- Seed: ensure exactly one user_profile row always exists
INSERT INTO user_profile(id, updated_at)
VALUES (1, datetime('now'));
