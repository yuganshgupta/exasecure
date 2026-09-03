-- ExaSecure schema version 10 migration: Proctoring Evidence & Duration Enhancement

ALTER TABLE `proctor_events` ADD COLUMN `returned_at` DATETIME NULL;
ALTER TABLE `proctor_events` ADD COLUMN `away_duration_ms` BIGINT NULL;
ALTER TABLE `proctor_events` ADD COLUMN `is_long_absence` TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE `proctor_evidence_v2` ADD COLUMN `evidence_type` VARCHAR(32) NOT NULL DEFAULT 'INITIAL';
CREATE UNIQUE INDEX `uq_evidence_event_type` ON `proctor_evidence_v2` (`event_id`, `evidence_type`);

INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`)
VALUES (10, CURRENT_TIMESTAMP);
