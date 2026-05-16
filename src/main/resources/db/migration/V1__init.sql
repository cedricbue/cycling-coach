-- Core application schema

CREATE TABLE ride
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_id       INTEGER,              -- no UNIQUE: same external_id may get a new activity_id after reimport
    external_id       TEXT UNIQUE NOT NULL, -- Garmin activity ID, sole dedup key for ride
    date              TEXT        NOT NULL,
    name              TEXT,                 -- activity name from Garmin JSON
    start_time        TEXT,                 -- GMT start time from Garmin JSON
    manufacturer      TEXT,                 -- device/app manufacturer (e.g. ZWIFT, WAHOO)
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
    rpe               INTEGER CHECK (rpe BETWEEN 1 AND 10),
    coach_summary     TEXT,
    notes             TEXT,
    FOREIGN KEY (activity_id) REFERENCES garmin_activity (id)
);

CREATE TABLE user_profile
(
    id         INTEGER PRIMARY KEY CHECK (id = 1),
    max_hr     INTEGER,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
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
    notes     TEXT,
    ride_id   INTEGER REFERENCES ride (id)
);

CREATE UNIQUE INDEX uq_ftp_test_ride_id ON ftp_test (ride_id) WHERE ride_id IS NOT NULL;

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

-- Garmin Connect integration

CREATE TABLE garmin_activity
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT UNIQUE NOT NULL, -- Garmin activity ID, used for dedup
    raw_tcx     TEXT        NOT NULL, -- source of truth; used for grade/altitude data
    raw_json    TEXT,                  -- full Garmin Connect activity JSON; used for pre-calculated metrics
    imported_at TEXT        NOT NULL DEFAULT (datetime('now'))
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

-- Tracks the since-date of the last fully-completed Garmin activity sync
CREATE TABLE garmin_activity_sync_cursor
(
    id    INTEGER PRIMARY KEY CHECK (id = 1),
    since TEXT NOT NULL
);

-- Raw weight measurements imported from Garmin Connect
CREATE TABLE garmin_weight
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT NOT NULL UNIQUE,
    raw_json    TEXT NOT NULL,
    imported_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Dedicated sync cursor for Garmin weight (external_id is numeric samplePk, not a date)
CREATE TABLE garmin_weight_sync_cursor
(
    id    INTEGER PRIMARY KEY,
    since TEXT NOT NULL
);

-- AI coaching

CREATE TABLE daily_recommendation
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    date             TEXT NOT NULL UNIQUE,
    type             TEXT NOT NULL CHECK (type IN ('OUTDOOR', 'OUTDOOR_FUN', 'INDOOR', 'REST')),
    content          TEXT NOT NULL,
    reason           TEXT NOT NULL DEFAULT '',
    weather_snapshot TEXT,
    ai_provider      TEXT NOT NULL,
    generated_at     TEXT NOT NULL
);

-- Bike fit video analysis

CREATE TABLE bike_fit_analysis
(
    id                TEXT PRIMARY KEY,  -- UUID
    status            TEXT NOT NULL CHECK (status IN ('PROCESSING', 'DONE', 'FAILED')),
    video_path        TEXT NOT NULL,     -- relative: data/bike-fit/{uuid}/video.{ext}
    original_filename TEXT NOT NULL,
    pose_model        TEXT NOT NULL CHECK (pose_model IN ('mediapipe', 'rtmpose')),
    pose_schema       TEXT CHECK (pose_schema IN ('mediapipe_33', 'coco_17', 'halpe_26')),
    fps               REAL,
    total_frames      INTEGER,
    error_message     TEXT,             -- set on FAILED
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    completed_at      TEXT
);

CREATE INDEX idx_bike_fit_created ON bike_fit_analysis (created_at DESC);

-- Seed: ensure exactly one user_profile row always exists
INSERT INTO user_profile(id, updated_at)
VALUES (1, datetime('now'));
