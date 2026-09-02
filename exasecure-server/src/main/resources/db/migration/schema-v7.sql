-- ExaSecure schema version 7 forward fix
--
-- Keep the v3 answer columns for Swing compatibility during migration, but
-- allow the authoritative server to write only normalized selections.

ALTER TABLE `student_answers`
    MODIFY COLUMN `selected_options` VARCHAR(255) NULL;

ALTER TABLE `student_answers`
    MODIFY COLUMN `is_correct` TINYINT(1) NULL;

-- DatabaseMigrator copies validated legacy CSV selections into
-- student_answer_options transactionally before recording version 7.
