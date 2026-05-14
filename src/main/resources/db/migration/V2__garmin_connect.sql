-- Garmin Connect integration schema

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
