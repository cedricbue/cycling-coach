ALTER TABLE garmin_activity ADD COLUMN imported_at TEXT NOT NULL DEFAULT (datetime('now'));

DROP TABLE garmin_weight;
CREATE TABLE garmin_weight (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT    NOT NULL UNIQUE,
    raw_json    TEXT    NOT NULL,
    imported_at TEXT    NOT NULL DEFAULT (datetime('now'))
);
