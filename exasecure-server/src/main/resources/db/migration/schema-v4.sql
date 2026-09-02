-- ExaSecure schema version 4 migration

-- 1. Explicit attempt state
ALTER TABLE `exam_attempts` ADD COLUMN `status` ENUM('ACTIVE', 'SUBMITTED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE';

-- Update existing attempts based on end_time presence
UPDATE `exam_attempts` SET `status` = 'SUBMITTED' WHERE `end_time` IS NOT NULL;

-- 2. Server sessions for secure token management
CREATE TABLE IF NOT EXISTS `server_sessions` (
    `token` VARCHAR(100) NOT NULL,
    `user_id` INT NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`token`),
    CONSTRAINT `fk_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Normalized answer selections (instead of CSV)
CREATE TABLE IF NOT EXISTS `student_answer_options` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `answer_id` INT NOT NULL,
    `selected_option` INT NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_answer_options_answer` FOREIGN KEY (`answer_id`) REFERENCES `student_answers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Note: In a real production system with millions of rows, we would write a script to migrate CSV `selected_options` to `student_answer_options`. 
-- For R2, we assume new answers will write to `student_answer_options`, or the server handles both temporarily.

-- Mark v4 as applied
INSERT IGNORE INTO `schema_versions` (`version`, `applied_at`) VALUES (4, CURRENT_TIMESTAMP);
