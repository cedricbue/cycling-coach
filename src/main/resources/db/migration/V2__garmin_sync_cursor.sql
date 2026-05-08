-- Tracks the since-date used by the last fully-completed Garmin sync.
-- Absent row = first run; GarminSyncService falls back to now() - initialFetchDays.
-- Only written after the sync loop exits without error, so an aborted sync never
-- advances the cursor past un-fetched older activities.
CREATE TABLE garmin_sync_cursor
(
    id    INTEGER PRIMARY KEY CHECK (id = 1),
    since TEXT NOT NULL
);
