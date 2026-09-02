package com.examsystem.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

public class DatabaseMigrator {
    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrator.class);
    private final DataSource dataSource;

    public DatabaseMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            int currentVersion = getCurrentVersion(conn);
            log.info("Current schema version: {}", currentVersion);

            // We know we need to reach at least v4
            if (currentVersion < 4) {
                log.info("Migrating schema to v4...");
                executeScript(conn, "db/migration/schema-v4.sql");
                log.info("Successfully migrated to v4");
                currentVersion = 4;
            }
            if (currentVersion < 5) {
                log.info("Migrating schema to v5...");
                executeScript(conn, "db/migration/schema-v5.sql");
                log.info("Successfully migrated to v5");
                currentVersion = 5;
            }
            if (currentVersion < 6) {
                log.info("Migrating schema to v6...");
                executeScript(conn, "db/migration/schema-v6.sql");

                // Programmatic data migration for proctor_logs
                if (columnExists(conn, "proctor_logs", "violation_type")) {
                    log.info("Legacy proctor_logs found. Migrating data...");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("INSERT INTO proctor_events (attempt_id, event_type, recorded_at) SELECT attempt_id, violation_type, violation_time FROM proctor_logs");
                        stmt.execute("INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at) SELECT e.id, 'image/jpeg', LENGTH(l.screenshot_data), l.screenshot_data, l.violation_time FROM proctor_logs l JOIN proctor_events e ON e.attempt_id = l.attempt_id AND e.recorded_at = l.violation_time AND e.event_type = l.violation_type WHERE l.screenshot_data IS NOT NULL");
                        stmt.execute("ALTER TABLE proctor_logs RENAME TO legacy_proctor_logs");
                    }
                }

                log.info("Successfully migrated to v6");
                currentVersion = 6;
            }
        } catch (Exception e) {
            throw new RuntimeException("Database migration failed", e);
        }
    }

    private int getCurrentVersion(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_versions")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.warn("schema_versions table not found. Attempting full v3 initialization...", e);
            try {
                executeScript(conn, "db/migration/schema-v3.sql");
                return 3;
            } catch (Exception ex) {
                log.error("Failed to initialize base schema", ex);
            }
            return 0;
        }
        return 0;
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        DatabaseMetaData metadata = conn.getMetaData();
        String[] tableCandidates = {
                tableName,
                tableName.toUpperCase(Locale.ROOT),
                tableName.toLowerCase(Locale.ROOT)
        };
        String[] columnCandidates = {
                columnName,
                columnName.toUpperCase(Locale.ROOT),
                columnName.toLowerCase(Locale.ROOT)
        };

        for (String tableCandidate : tableCandidates) {
            for (String columnCandidate : columnCandidates) {
                try (ResultSet columns = metadata.getColumns(
                        conn.getCatalog(), null, tableCandidate, columnCandidate)) {
                    if (columns.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void executeScript(Connection conn, String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Migration script not found: " + path);
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] statements = sql.split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String s : statements) {
                    if (s.trim().isEmpty()) continue;
                    stmt.execute(s);
                }
            }
        }
    }
}
