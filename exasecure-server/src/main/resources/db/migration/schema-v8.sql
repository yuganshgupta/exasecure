-- ExaSecure schema version 8 migration

ALTER TABLE `exams` ADD COLUMN `version_number` INT NOT NULL DEFAULT 1;
ALTER TABLE `exams` ADD COLUMN `supersedes_exam_id` INT NULL;
CREATE UNIQUE INDEX `uq_exams_supersedes` ON `exams` (`supersedes_exam_id`);
ALTER TABLE `exams` ADD CONSTRAINT `fk_exams_supersedes`
    FOREIGN KEY (`supersedes_exam_id`) REFERENCES `exams` (`id`);

INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`)
VALUES (8, CURRENT_TIMESTAMP);
