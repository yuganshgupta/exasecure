package com.examsystem.server;

import com.examsystem.server.db.DatabaseMigrator;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationIntegrationTest {

    @Test
    void testH2CompatibleMigrationFromV3() throws Exception {
        String url = "jdbc:h2:mem:migratedb;MODE=MySQL;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            // 1. Manually apply v3 schema to simulate legacy database
            String v3Sql = new String(getClass().getClassLoader().getResourceAsStream("db/migration/schema-v3.sql").readAllBytes());
            for (String s : v3Sql.split(";")) {
                if (!s.trim().isEmpty()) stmt.execute(s);
            }
            
            // 2. Insert representative legacy data
            stmt.execute("INSERT INTO users (id, username, password, full_name, role) VALUES (1, 'legacy_stu', '.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Legacy Student', 'STUDENT')");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (1, 'Legacy Exam', 60, 1, 1)");
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type) VALUES (1, 1, 'Legacy Q?', 1, 'SINGLE')");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct) VALUES (1, 1, 1, 'A', 1), (2, 1, 2, 'B', 0)");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time) VALUES (1, 1, 1, '2023-01-01 10:00:00')");
            // Legacy proctor logs
            stmt.execute("INSERT INTO proctor_logs (id, attempt_id, violation_type, screenshot_data, violation_time) VALUES (1, 1, 'FOCUS_LOST', X'89504E47', '2023-01-01 10:05:00')");
        }
        
        // 3. Run migrator
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword("");
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(config);
        
        DatabaseMigrator migrator = new DatabaseMigrator(ds);
        migrator.migrate();
        
        // 4. Verify results
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            // Check schema version
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_versions")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(9);
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT version_number, supersedes_exam_id FROM exams WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("version_number")).isEqualTo(1);
                assertThat(rs.getObject("supersedes_exam_id")).isNull();
            }

            // Check exam_attempts v9 proctoring columns
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT is_flagged, proctoring_status FROM exam_attempts WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("is_flagged")).isFalse();
                assertThat(rs.getString("proctoring_status")).isEqualTo("CLEAN");
            }
            
            // Check user remained
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            
            // Check proctor_events and proctor_evidence migrated
            try (ResultSet rs = stmt.executeQuery("SELECT event_type FROM proctor_events WHERE attempt_id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("FOCUS_LOST");
            }
            
            try (ResultSet rs = stmt.executeQuery("SELECT content_type, byte_size, screenshot_data FROM proctor_evidence_v2")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("content_type")).isEqualTo("image/jpeg");
                assertThat(rs.getBytes("screenshot_data")).isNotNull();
            }
        }
        
        ds.close();
    }
}
