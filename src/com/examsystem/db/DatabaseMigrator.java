package com.examsystem.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Verifies that the external setup process installed the supported schema.
 *
 * Schema DDL deliberately does not run under the application's restricted
 * database account. Apply or repair migrations with setup.ps1 instead.
 */
public final class DatabaseMigrator {
    private static final int REQUIRED_SCHEMA_VERSION = 3;

    private DatabaseMigrator() {
    }

    public static void migrate() {
        try (Connection conn = DatabaseConnector.getConnection()) {
            if (!tableExists(conn, "schema_versions")) {
                throw new SQLException("schema_versions is missing");
            }

            VersionState versionState = getVersionState(conn);
            if (!versionState.isExactRequiredSet()) {
                throw new SQLException(
                        "unsupported schema version set " + versionState
                                + " (required exactly 1,2," + REQUIRED_SCHEMA_VERSION + ")");
            }

            System.out.println("Database schema verified. Current version: " + REQUIRED_SCHEMA_VERSION);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Database schema is not ready. Run setup.ps1 from the project root. "
                            + e.getMessage(),
                    e);
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return rs.next();
        }
    }

    private static VersionState getVersionState(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*), COALESCE(MIN(version), 0), "
                             + "COALESCE(MAX(version), 0), COALESCE(SUM(version), 0) "
                             + "FROM schema_versions")) {
            if (!rs.next()) {
                return new VersionState(0, 0, 0, 0);
            }
            return new VersionState(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4));
        }
    }

    private static final class VersionState {
        private final int count;
        private final int minimum;
        private final int maximum;
        private final int sum;

        private VersionState(int count, int minimum, int maximum, int sum) {
            this.count = count;
            this.minimum = minimum;
            this.maximum = maximum;
            this.sum = sum;
        }

        private boolean isExactRequiredSet() {
            return count == 3 && minimum == 1 && maximum == REQUIRED_SCHEMA_VERSION && sum == 6;
        }

        @Override
        public String toString() {
            return "{count=" + count + ", min=" + minimum + ", max=" + maximum + ", sum=" + sum + "}";
        }
    }
}
