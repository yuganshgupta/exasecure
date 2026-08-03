package com.examsystem.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseMigrator {

    public static void migrate() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            ensureSchemaVersionsTable(conn);

            int currentVersion = getCurrentVersion(conn);
            
            if (currentVersion < 1) {
                runV1Migration(conn);
                setVersion(conn, 1);
                currentVersion = 1;
            }

            if (currentVersion < 2) {
                runV2Migration(conn);
                setVersion(conn, 2);
                currentVersion = 2;
            }

            if (currentVersion < 3) {
                runV3Migration(conn);
                setVersion(conn, 3);
                currentVersion = 3;
            }
            
            System.out.println("Database migration completed. Current version: " + currentVersion);
        } catch (SQLException e) {
            System.err.println("Database migration failed!");
            e.printStackTrace();
        }
    }

    private static void ensureSchemaVersionsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS schema_versions (" +
                     "version INT PRIMARY KEY, " +
                     "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static int getCurrentVersion(Connection conn) throws SQLException {
        String sql = "SELECT MAX(version) FROM schema_versions";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void setVersion(Connection conn, int version) throws SQLException {
        String sql = "INSERT INTO schema_versions (version) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }

    private static void runV1Migration(Connection conn) throws SQLException {
        // Assume V1 is already run if tables exist, otherwise we would dump database.sql here.
        // For this project, tables already exist, but if it's a fresh install, they might not.
        // We'll just check if 'users' table exists. If it does, we assume V1 is applied.
        boolean v1Exists = false;
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "users", null)) {
            if (rs.next()) {
                v1Exists = true;
            }
        }
        
        if (!v1Exists) {
            // Ideally we'd execute the full database.sql script here, but since the database
            // is already seeded in this legacy system, we'll assume it exists or fail fast.
            System.err.println("Warning: Fresh V1 install from scratch is not fully implemented in Migrator.");
            System.err.println("Please run database.sql manually first.");
        }
    }

    private static void runV2Migration(Connection conn) throws SQLException {
        String[] stmts = {
            "ALTER TABLE users ADD COLUMN is_active BOOLEAN DEFAULT TRUE",
            "ALTER TABLE exams ADD COLUMN is_active BOOLEAN DEFAULT TRUE",
            "ALTER TABLE questions ADD COLUMN is_active BOOLEAN DEFAULT TRUE",
            "ALTER TABLE options ADD COLUMN is_active BOOLEAN DEFAULT TRUE",
            "ALTER TABLE options ADD COLUMN is_correct BOOLEAN DEFAULT FALSE"
        };
        
        try (Statement stmt = conn.createStatement()) {
            for (String sql : stmts) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    // Ignore duplicate column errors if they were already added in Phase 1
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate column name")) {
                        System.out.println("Column already exists, skipping: " + sql);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    private static void runV3Migration(Connection conn) throws SQLException {
        String[] stmts = {
            "ALTER TABLE questions ADD COLUMN question_type VARCHAR(20) DEFAULT 'SINGLE'",
            "ALTER TABLE student_answers CHANGE selected_option_number selected_options VARCHAR(255)"
        };
        
        try (Statement stmt = conn.createStatement()) {
            for (String sql : stmts) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Duplicate column name")) {
                        System.out.println("Column already exists, skipping: " + sql);
                    } else if (e.getMessage() != null && e.getMessage().contains("Unknown column")) {
                        // This might happen if 'selected_option_number' was already renamed.
                        System.out.println("Column might already be renamed, skipping: " + sql);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }
}
