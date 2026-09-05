-- Backward-compatible, non-destructive MySQL CLI entry point.
--
-- Recommended: run .\setup.ps1 so legacy upgrades, credentials, grants, and
-- validation are handled safely. This file is only a convenience for creating
-- the default empty schema from the repository root with mysql.exe.

CREATE DATABASE IF NOT EXISTS `secure_exam_db`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `secure_exam_db`;

SOURCE setup/sql/schema-v3.sql;

-- Version rows are intentionally not stamped here. Run setup.ps1 so the full
-- schema contract is validated before the application accepts version 3.
