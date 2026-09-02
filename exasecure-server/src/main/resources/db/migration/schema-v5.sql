-- ExaSecure schema version 5 migration

CREATE TABLE IF NOT EXISTS `proctor_logs` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `attempt_id` INT NOT NULL,
    `event_type` VARCHAR(50) NOT NULL,
    `recorded_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_logs_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `exam_attempts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `proctor_evidence` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `attempt_id` INT NOT NULL,
    `screenshot` LONGBLOB NOT NULL,
    `recorded_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_evidence_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `exam_attempts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`) VALUES (5, CURRENT_TIMESTAMP);
