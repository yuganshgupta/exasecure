package com.examsystem.server;

import com.examsystem.server.adapters.jdbc.JdbcAnswerRepository;
import com.examsystem.server.db.DatabaseMigrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "EXASECURE_MYSQL_TEST_URL", matches = ".+")
class MySqlMigrationIntegrationTest {
    private static final String V1_SCHEMA_SHA256 =
            "602aaf1b7f39e6a14723b56d82c6fd0574c9d64a000d4a6bc1d6d584f7ba96a3";
    private static final byte[] SCREENSHOT = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, (byte) 0xff, 0x10, 0x20
    };

    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dbUrl = System.getenv("EXASECURE_MYSQL_TEST_URL");
        dbUser = System.getenv().getOrDefault("EXASECURE_MYSQL_TEST_USER", "root");
        dbPassword = System.getenv().getOrDefault("EXASECURE_MYSQL_TEST_PASSWORD", "");
        if (!dbUrl.matches("(?i)^jdbc:mysql://[^/]+/exasecure_test(?:_[a-z0-9]+)?(?:\\?.*)?$")) {
            throw new IllegalArgumentException(
                    "EXASECURE_MYSQL_TEST_URL must target a disposable exasecure_test database");
        }
        cleanDatabase();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }
        cleanDatabase();
    }

    @Test
    void migratesImmutableV1SchemaAndRepresentativeDataIdempotently() throws Exception {
        byte[] v1Schema = readResource("db/migration/schema-v3.sql");
        assertThat(normalizedSha256(v1Schema)).isEqualTo(V1_SCHEMA_SHA256);

        try (Connection conn = connection()) {
            executeScript(conn, new String(v1Schema, StandardCharsets.UTF_8));
            seedV3Data(conn);
        }

        dataSource = dataSource();
        DatabaseMigrator migrator = new DatabaseMigrator(dataSource);
        migrator.migrate();
        migrator.migrate();

        try (Connection conn = dataSource.getConnection()) {
            assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 7);
            assertSingleInt(conn, "SELECT COUNT(*) FROM schema_versions", 7);
            assertSingleInt(conn, "SELECT COUNT(*) FROM users", 2);
            assertSingleInt(conn, "SELECT COUNT(*) FROM exams", 1);
            assertSingleInt(conn, "SELECT COUNT(*) FROM questions", 2);
            assertSingleInt(conn, "SELECT COUNT(*) FROM exam_attempts WHERE status = 'ACTIVE'", 1);
            assertSingleInt(conn, "SELECT COUNT(*) FROM student_answers", 1);
            assertSingleInt(conn, "SELECT COUNT(*) FROM proctor_events", 1);
            assertSingleInt(conn, "SELECT COUNT(*) FROM proctor_evidence_v2", 1);

            try (Statement stmt = conn.createStatement();
                 ResultSet rows = stmt.executeQuery(
                         "SELECT selected_option FROM student_answer_options WHERE answer_id = 1 ORDER BY selected_option")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.next()).isFalse();
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet evidence = stmt.executeQuery(
                         "SELECT content_type, byte_size, screenshot_data FROM proctor_evidence_v2")) {
                assertThat(evidence.next()).isTrue();
                assertThat(evidence.getString("content_type")).isEqualTo("image/jpeg");
                assertThat(evidence.getInt("byte_size")).isEqualTo(SCREENSHOT.length);
                assertThat(evidence.getBytes("screenshot_data")).containsExactly(SCREENSHOT);
            }

            assertColumnNullable(conn, "selected_options");
            assertColumnNullable(conn, "is_correct");
        }

        new JdbcAnswerRepository(dataSource).saveAnswer(1, 2, List.of(1), Instant.parse("2026-09-02T12:00:00Z"));
        try (Connection conn = dataSource.getConnection()) {
            assertSingleInt(conn,
                    "SELECT COUNT(*) FROM student_answers WHERE question_id = 2 AND selected_options IS NULL AND is_correct IS NULL",
                    1);
            assertSingleInt(conn,
                    "SELECT COUNT(*) FROM student_answer_options sao JOIN student_answers sa ON sa.id = sao.answer_id WHERE sa.question_id = 2 AND sao.selected_option = 1",
                    1);
        }
    }

    private void seedV3Data(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO schema_versions (version) VALUES (1), (2), (3)");
            stmt.execute("INSERT INTO users (id, username, password, full_name, role) VALUES (1, 'legacy_admin', 'test-hash', 'Legacy Admin', 'admin'), (2, 'legacy_student', 'test-hash', 'Legacy Student', 'student')");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by) VALUES (1, 'Legacy Exam', 60, 1)");
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type) VALUES (1, 1, 'Legacy multi?', 1, 'MULTI'), (2, 1, 'Current write?', 1, 'SINGLE')");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct) VALUES (1, 1, 1, 'A', 1), (2, 1, 2, 'B', 1), (3, 2, 1, 'Yes', 1)");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time) VALUES (1, 1, 2, '2026-09-02 10:00:00')");
            stmt.execute("INSERT INTO student_answers (id, attempt_id, question_id, selected_options, is_correct, answer_timestamp) VALUES (1, 1, 1, '1, 2', 1, '2026-09-02 10:05:00')");
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO proctor_logs (id, attempt_id, violation_type, screenshot_data, violation_time) VALUES (1, 1, 'FOCUS_LOST', ?, '2026-09-02 10:06:00')")) {
            insert.setBytes(1, SCREENSHOT);
            insert.executeUpdate();
        }
    }

    private void assertColumnNullable(Connection conn, String columnName) throws Exception {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'student_answers' AND column_name = ?")) {
            query.setString(1, columnName);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("YES");
            }
        }
    }

    private void assertSingleInt(Connection conn, String sql, int expected) throws Exception {
        try (Statement stmt = conn.createStatement(); ResultSet row = stmt.executeQuery(sql)) {
            assertThat(row.next()).isTrue();
            assertThat(row.getInt(1)).isEqualTo(expected);
        }
    }

    private void executeScript(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for (String statement : sql.split(";")) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement);
                }
            }
        }
    }

    private byte[] readResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return input.readAllBytes();
        }
    }

    private String normalizedSha256(byte[] content) throws Exception {
        String normalized = new String(content, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private void cleanDatabase() throws Exception {
        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("DROP TABLE IF EXISTS legacy_proctor_logs, proctor_evidence_v2, proctor_events, proctor_evidence, proctor_logs, student_answer_options, student_answers, exam_attempts, exam_questions, options, questions, exams, server_sessions, schema_versions, users");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
