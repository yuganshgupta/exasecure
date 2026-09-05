package com.examsystem.server.db;

import java.sql.*;
import java.util.*;

/** V9-only recovery. DDL may commit independently; each step is inspected on restart. */
final class V10CompatibilityMigration {
    private record Column(String table, String name, String ddl, int type, int width,
                          boolean nullable, String defaultValue) {}
    private static final List<Column> COLUMNS = List.of(
            new Column("proctor_events", "returned_at", "DATETIME NULL", Types.TIMESTAMP, 0, true, null),
            new Column("proctor_events", "away_duration_ms", "BIGINT NULL", Types.BIGINT, 0, true, null),
            new Column("proctor_events", "is_long_absence", "TINYINT(1) NOT NULL DEFAULT 0", Types.TINYINT, 0, false, "0"),
            new Column("proctor_evidence_v2", "evidence_type", "VARCHAR(32) NOT NULL DEFAULT 'INITIAL'", Types.VARCHAR, 32, false, "INITIAL"));

    void migrate(Connection conn) throws SQLException {
        // Validate all known state before any mutation; never suppress DDL errors.
        requireColumn(conn, "proctor_events", "client_event_id");
        requireColumn(conn, "exam_attempts", "is_flagged");
        requireColumn(conn, "exam_attempts", "proctoring_status");
        requireColumn(conn, "proctor_evidence_v2", "id");
        if (!validateIndex(conn, "proctor_events", "uq_events_client_event_id", List.of("attempt_id", "client_event_id")))
            throw inconsistent("V9 unique event index is missing");
        for (Column column : COLUMNS) validateColumn(conn, column);
        boolean indexed = validateIndex(conn, "proctor_evidence_v2", "uq_evidence_event_type", List.of("event_id", "evidence_type"));
        for (Column column : COLUMNS) {
            if (!validateColumn(conn, column)) execute(conn, "ALTER TABLE " + column.table + " ADD COLUMN " + column.name + " " + column.ddl);
        }

        // A deterministic, idempotent DML step needs no separate journal: the label is
        // derived solely from the immutable primary key. No capture-stage inference.
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (Statement query = conn.createStatement();
                 ResultSet rows = query.executeQuery("SELECT id, evidence_type FROM proctor_evidence_v2 FOR UPDATE");
                 PreparedStatement update = conn.prepareStatement("UPDATE proctor_evidence_v2 SET evidence_type = ? WHERE id = ?")) {
                while (rows.next()) {
                    long id = rows.getLong(1);
                    String key = legacyKey(id);
                    String existing = rows.getString(2);
                    if (!"INITIAL".equals(existing) && !key.equals(existing))
                        throw inconsistent("Unexpected pre-V10 evidence label at evidence ID " + id);
                    update.setString(1, key);
                    update.setLong(2, id);
                    update.addBatch();
                }
                update.executeBatch();
            }
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        if (!indexed) execute(conn, "CREATE UNIQUE INDEX uq_evidence_event_type ON proctor_evidence_v2 (event_id, evidence_type)");
        for (Column column : COLUMNS) if (!validateColumn(conn, column)) throw inconsistent("Missing " + column.name);
        if (!validateIndex(conn, "proctor_evidence_v2", "uq_evidence_event_type", List.of("event_id", "evidence_type")))
            throw inconsistent("Missing evidence index");
        try (Statement stmt = conn.createStatement(); ResultSet rows = stmt.executeQuery("SELECT id, evidence_type FROM proctor_evidence_v2")) {
            while (rows.next()) if (!legacyKey(rows.getLong(1)).equals(rows.getString(2))) throw inconsistent("Legacy label verification failed");
        }
        execute(conn, "INSERT INTO schema_versions (version, applied_at) VALUES (10, CURRENT_TIMESTAMP)");
    }

    static String legacyKey(long id) throws SQLException {
        if (id <= 0) throw inconsistent("Evidence ID must be positive");
        String key = "L" + Long.toString(id, 36).toUpperCase(Locale.ROOT);
        if (key.length() > 32) throw inconsistent("Legacy evidence key exceeds storage width");
        return key;
    }

    private static void requireColumn(Connection conn, String table, String name) throws SQLException {
        if (column(conn, table, name).isEmpty()) throw inconsistent("Missing V9 prerequisite " + table + "." + name);
    }

    private record Shape(int type, int width, boolean nullable, String defaultValue, String typeName) {}
    private static Optional<Shape> column(Connection conn, String table, String name) throws SQLException {
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet rows = conn.getMetaData().getColumns(conn.getCatalog(), null, candidate, null)) {
                while (rows.next()) if (name.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) {
                    return Optional.of(new Shape(rows.getInt("DATA_TYPE"), rows.getInt("COLUMN_SIZE"),
                            rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            rows.getString("COLUMN_DEF"), rows.getString("TYPE_NAME")));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean validateColumn(Connection conn, Column expected) throws SQLException {
        var found = column(conn, expected.table, expected.name);
        if (found.isEmpty()) return false;
        Shape actual = found.get();
        // Connector/J can report TINYINT(1) as BIT/BOOLEAN.
        boolean typeMatches = actual.type == expected.type || expected.type == Types.TINYINT && (actual.type == Types.BIT || actual.type == Types.BOOLEAN);
        String def = actual.defaultValue == null ? null : actual.defaultValue.replace("'", "");
        if (!typeMatches || actual.nullable != expected.nullable || expected.width > 0 && actual.width != expected.width
                || !Objects.equals(def, expected.defaultValue) || actual.typeName.toUpperCase(Locale.ROOT).contains("UNSIGNED"))
            throw inconsistent("Incorrect definition of " + expected.table + "." + expected.name + "; expected " + expected.ddl);
        return true;
    }

    private static boolean validateIndex(Connection conn, String table, String name, List<String> expected) throws SQLException {
        SortedMap<Integer, String> columns = new TreeMap<>();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet rows = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, candidate, false, false)) {
                while (rows.next()) if (name.equalsIgnoreCase(rows.getString("INDEX_NAME"))) {
                    if (rows.getBoolean("NON_UNIQUE") || "D".equals(rows.getString("ASC_OR_DESC"))) throw inconsistent("Incorrect index " + name);
                    columns.put(rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        if (columns.isEmpty()) return false;
        if (!new ArrayList<>(columns.values()).equals(expected)) throw inconsistent("Incorrect columns in index " + name);
        if (conn.getMetaData().getDatabaseProductName().equals("MySQL")) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT SUB_PART, INDEX_TYPE FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
                stmt.setString(1, table); stmt.setString(2, name);
                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) if (rows.getObject(1) != null || !"BTREE".equals(rows.getString(2))) throw inconsistent("Incorrect prefix/type in index " + name);
                }
            }
        }
        return true;
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) { stmt.execute(sql); }
    }
    private static SQLException inconsistent(String detail) {
        return new SQLException("V10 compatibility stopped: " + detail + ". Back up the database and repair the schema to the documented V9/V10 definition before restarting.");
    }
}
