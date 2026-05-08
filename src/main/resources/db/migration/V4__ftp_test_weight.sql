-- Add weight_kg snapshot to ftp_test so each test record captures the rider's weight at the time of testing.
ALTER TABLE ftp_test ADD COLUMN weight_kg REAL;
