-- V3__rename_activity_to_garmin_activity.sql
-- Decouple raw Garmin import table from domain naming.

ALTER TABLE activity RENAME TO garmin_activity;
