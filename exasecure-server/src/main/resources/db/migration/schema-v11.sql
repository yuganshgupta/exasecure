-- Stable replay results for incidents created by the atomic V11 application operation.
-- Legacy rows remain NULL: historical response state is unknown.
ALTER TABLE proctor_events ADD COLUMN result_violation_count INT NULL;
ALTER TABLE proctor_events ADD COLUMN result_flagged TINYINT(1) NULL;
ALTER TABLE proctor_events ADD COLUMN result_attempt_status VARCHAR(32) NULL;

INSERT INTO schema_versions (version, applied_at) VALUES (11, CURRENT_TIMESTAMP);
