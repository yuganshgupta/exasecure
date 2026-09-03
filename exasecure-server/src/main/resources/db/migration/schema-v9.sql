-- ExaSecure schema version 9 migration: Proctoring Three-Violation Policy & Flagging

ALTER TABLE `exam_attempts` ADD COLUMN `is_flagged` TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `exam_attempts` ADD COLUMN `proctoring_status` VARCHAR(32) NOT NULL DEFAULT 'CLEAN';

ALTER TABLE `proctor_events` ADD COLUMN `client_event_id` VARCHAR(64) NULL;
CREATE UNIQUE INDEX `uq_events_client_event_id` ON `proctor_events` (`attempt_id`, `client_event_id`);

INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`)
VALUES (9, CURRENT_TIMESTAMP);
