-- ExaSecure schema version 6 migration

CREATE TABLE IF NOT EXISTS `proctor_events` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `attempt_id` INT NOT NULL,
    `event_type` VARCHAR(50) NOT NULL,
    `recorded_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_events_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `exam_attempts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `proctor_evidence_v2` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `event_id` INT NOT NULL,
    `content_type` VARCHAR(50) NOT NULL DEFAULT 'image/jpeg',
    `byte_size` INT NOT NULL DEFAULT 0,
    `screenshot_data` LONGBLOB NOT NULL,
    `recorded_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_evidence_event` FOREIGN KEY (`event_id`) REFERENCES `proctor_events` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `proctor_evidence`;

INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`) VALUES (6, CURRENT_TIMESTAMP);
