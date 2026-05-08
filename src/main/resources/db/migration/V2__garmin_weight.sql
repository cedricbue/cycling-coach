-- V2__garmin_weight.sql
-- Raw weight data imported from Garmin Connect, decoupled from domain user_weight.

CREATE TABLE garmin_weight
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    date                TEXT    NOT NULL UNIQUE,
    weight_grams        REAL    NOT NULL,
    bmi                 REAL,
    body_fat_percentage REAL,
    muscle_mass_grams   REAL,
    imported_at         TEXT    NOT NULL DEFAULT (datetime('now'))
);
