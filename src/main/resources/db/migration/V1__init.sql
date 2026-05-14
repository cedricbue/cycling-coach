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

-- Seed: ensure exactly one user_profile row always exists
INSERT INTO user_profile(id, updated_at)
VALUES (1, datetime('now'));
