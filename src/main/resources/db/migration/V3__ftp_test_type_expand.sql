-- V3__ftp_test_type_expand.sql
-- Expand ftp_test.test_type CHECK constraint to include structured test types.
-- SQLite does not support ALTER COLUMN with CHECK constraints, so the table must be recreated.

CREATE TABLE ftp_test_new
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    date      TEXT NOT NULL,
    ftp_value REAL NOT NULL,
    test_type TEXT CHECK (test_type IN ('RAMP_TEST', 'TWENTY_MIN_TEST', 'SIXTY_MIN_TEST', 'UNKNOWN', 'ESTIMATED')),
    notes     TEXT
);

INSERT INTO ftp_test_new (id, date, ftp_value, test_type, notes)
SELECT id,
       date,
       ftp_value,
       CASE test_type
           WHEN 'AUTO_DETECTED' THEN 'TWENTY_MIN_TEST'
           WHEN 'ESTIMATED'     THEN 'ESTIMATED'
           ELSE                      'UNKNOWN'
       END,
       notes
FROM ftp_test;

DROP TABLE ftp_test;
ALTER TABLE ftp_test_new RENAME TO ftp_test;
