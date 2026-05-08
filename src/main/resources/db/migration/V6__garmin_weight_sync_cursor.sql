-- Dedicated sync cursor for weight, mirroring garmin_activity_sync_cursor.
-- Needed because external_id is now samplePk (numeric), not a date.
CREATE TABLE garmin_weight_sync_cursor (
    id    INTEGER PRIMARY KEY,
    since TEXT    NOT NULL
);
