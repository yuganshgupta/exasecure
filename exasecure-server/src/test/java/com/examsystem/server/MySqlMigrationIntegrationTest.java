package com.examsystem.server;

import com.examsystem.server.adapters.jdbc.JdbcAnswerRepository;
import com.examsystem.server.adapters.jdbc.JdbcAdminQueryPort;
import com.examsystem.server.adapters.jdbc.JdbcExamAttemptRepository;
import com.examsystem.server.adapters.jdbc.JdbcQuestionRepository;
import com.examsystem.application.usecases.SubmitExamUseCase;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        if (!dbUrl.matches("^jdbc:mysql://localhost:3306/exasecure_test(?:\\?.*)?$")) {
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
            assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 11);
            assertSingleInt(conn, "SELECT COUNT(*) FROM schema_versions", 11);
            assertSingleInt(conn, "SELECT COUNT(*) FROM exam_attempts WHERE is_flagged = 0 AND proctoring_status = 'CLEAN'", 1);
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
                         "SELECT content_type, byte_size, screenshot_data, evidence_type FROM proctor_evidence_v2")) {
                assertThat(evidence.next()).isTrue();
                assertThat(evidence.getString("content_type")).isEqualTo("image/jpeg");
                assertThat(evidence.getInt("byte_size")).isEqualTo(SCREENSHOT.length);
                assertThat(evidence.getBytes("screenshot_data")).containsExactly(SCREENSHOT);
                assertThat(evidence.getString("evidence_type")).isEqualTo("L1");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet event = stmt.executeQuery(
                         "SELECT returned_at, away_duration_ms, is_long_absence FROM proctor_events WHERE id = 1")) {
                assertThat(event.next()).isTrue();
                assertThat(event.getBoolean("is_long_absence")).isFalse();
            }

            assertColumnNullable(conn, "selected_options");
            assertColumnNullable(conn, "is_correct");
            assertSingleInt(conn,
                    "SELECT COUNT(*) FROM exams WHERE id = 1 AND version_number = 1 AND supersedes_exam_id IS NULL",
                    1);
        }

        Instant activeTime = new JdbcExamAttemptRepository(dataSource).findById(1).orElseThrow().startTime().plusSeconds(1800);
        new JdbcAnswerRepository(dataSource, () -> activeTime).saveAnswer(1, 2, List.of(1), activeTime);
        try (Connection conn = dataSource.getConnection()) {
            assertSingleInt(conn,
                    "SELECT COUNT(*) FROM student_answers WHERE question_id = 2 AND selected_options IS NULL AND is_correct IS NULL",
                    1);
            assertSingleInt(conn,
                    "SELECT COUNT(*) FROM student_answer_options sao JOIN student_answers sa ON sa.id = sao.answer_id WHERE sa.question_id = 2 AND sao.selected_option = 1",
                    1);
        }
    }

    @Test
    void adminReviewMatchesStudentAndGradingAfterReplacingAndClearingMigratedAnswer() throws Exception {
        seedV3AnswerFixture();
        dataSource = dataSource();
        var migrator = new DatabaseMigrator(dataSource);
        migrator.migrate();
        var admin = new JdbcAdminQueryPort(dataSource);
        var attempts = new JdbcExamAttemptRepository(dataSource);
        Instant now = attempts.findById(1).orElseThrow().startTime().plusSeconds(1800);
        var studentAnswers = new JdbcAnswerRepository(dataSource, () -> now);
        var submit = new SubmitExamUseCase(studentAnswers, attempts, new JdbcQuestionRepository(dataSource), () -> now);
        Duration timeLimit = Duration.ofHours(1);

        assertAnswerReview(studentAnswers, admin, 1, List.of(1, 2), List.of("A", "B"), 1);
        assertThat(studentAnswers.getSelectedOptionsForAttempt(1)).doesNotContainKey(2);
        assertAdminAnswer(admin, 2, List.of(), List.of(), 0);

        submit.saveAnswer(1, 1, List.of(2), timeLimit);
        assertAnswerReview(studentAnswers, admin, 1, List.of(2), List.of("B"), 0);

        submit.saveAnswer(1, 1, List.of(), timeLimit);
        assertAnswerReview(studentAnswers, admin, 1, List.of(), List.of(), 0);
        assertThat(studentAnswers.getSelectedOptionsForAttempt(1)).doesNotContainKey(2);
        assertAdminAnswer(admin, 2, List.of(), List.of(), 0);

        // Keep a correct answer as a positive control for the stored score.
        submit.saveAnswer(1, 2, List.of(1), timeLimit);
        assertThat(submit.submitExam(1, now)).isEqualTo(1);
        migrator.migrate();
        assertAnswerReview(studentAnswers, admin, 1, List.of(), List.of(), 0);
        assertAnswerReview(studentAnswers, admin, 2, List.of(1), List.of("Yes"), 1);
        assertThat(admin.getAttemptDetails(1).stream().mapToInt(result -> result.marksAwarded()).sum()).isEqualTo(1);
        try (var conn = connection()) {
            assertSingleInt(conn, "SELECT COUNT(*) FROM exam_attempts WHERE id = 1 AND status = 'SUBMITTED' AND score = 1", 1);
            assertOriginalLegacyAnswer(conn);
        }
    }

    @Test
    void historicalSubmittedAnswerRemainsReviewableAfterV7Backfill() throws Exception {
        seedV3AnswerFixture();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            stmt.execute("UPDATE exam_attempts SET end_time = '2026-09-02 10:15:00', score = 1 WHERE id = 1");
        }
        dataSource = dataSource();
        new DatabaseMigrator(dataSource).migrate();

        var studentAnswers = new JdbcAnswerRepository(dataSource, Instant::now);
        var admin = new JdbcAdminQueryPort(dataSource);
        assertAnswerReview(studentAnswers, admin, 1, List.of(1, 2), List.of("A", "B"), 1);
        assertThat(studentAnswers.getSelectedOptionsForAttempt(1)).doesNotContainKey(2);
        assertAdminAnswer(admin, 2, List.of(), List.of(), 0);
        try (var conn = connection()) {
            assertSingleInt(conn, "SELECT COUNT(*) FROM exam_attempts WHERE id = 1 AND status = 'SUBMITTED' "
                    + "AND score = 1 AND end_time = '2026-09-02 10:15:00'", 1);
            assertOriginalLegacyAnswer(conn);
        }
    }

    @Test
    void interruptedV7BackfillRollsBackSelectionsAndMarkerThenRetries() throws Exception {
        seedV3AnswerFixture();
        dataSource = dataSource();
        var interrupted = JdbcTestProbe.wrap(dataSource, (sql, after) -> {
            if (after && sql.startsWith("INSERT INTO schema_versions (version, applied_at) VALUES (7,")) {
                throw new SQLException("interrupted V7 backfill before commit");
            }
        });
        assertThatThrownBy(() -> new DatabaseMigrator(interrupted).migrate())
                .hasRootCauseMessage("interrupted V7 backfill before commit");
        try (var conn = connection()) {
            assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 6);
            assertSingleInt(conn, "SELECT COUNT(*) FROM student_answer_options WHERE answer_id = 1", 0);
            assertOriginalLegacyAnswer(conn);
        }

        new DatabaseMigrator(dataSource).migrate();
        try (var conn = connection()) {
            assertSingleInt(conn, "SELECT COUNT(*) FROM schema_versions WHERE version = 7", 1);
            assertOriginalLegacyAnswer(conn);
        }
        assertAnswerReview(new JdbcAnswerRepository(dataSource, Instant::now), new JdbcAdminQueryPort(dataSource),
                1, List.of(1, 2), List.of("A", "B"), 1);
    }

    @Test
    void laterSchemaWithoutV7BackfillMarkerRequiresRecoveryBeforeFurtherMigration() throws Exception {
        historicalV9();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM schema_versions WHERE version = 7");
        }
        dataSource = dataSource();

        assertThatThrownBy(() -> new DatabaseMigrator(dataSource).migrate())
                .hasRootCauseMessage("V7 answer backfill marker is missing; verify normalized selections against a backup before startup");
        try (var conn = connection()) {
            assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 9);
            assertSingleInt(conn, "SELECT COUNT(*) FROM student_answer_options WHERE answer_id = 1", 0);
            assertOriginalLegacyAnswer(conn);
        }
    }

    private void seedV3AnswerFixture() throws Exception {
        try (var conn = connection()) {
            executeScript(conn, new String(readResource("db/migration/schema-v3.sql"), StandardCharsets.UTF_8));
            seedV3Data(conn);
        }
    }

    private void assertAnswerReview(JdbcAnswerRepository studentAnswers, JdbcAdminQueryPort admin, int questionId,
                                    List<Integer> options, List<String> texts, int marks) {
        assertThat(studentAnswers.getSelectedOptionsForAttempt(1).get(questionId))
                .containsExactlyInAnyOrderElementsOf(options);
        assertAdminAnswer(admin, questionId, options, texts, marks);
    }

    private void assertAdminAnswer(JdbcAdminQueryPort admin, int questionId,
                                   List<Integer> options, List<String> texts, int marks) {
        var result = admin.getAttemptDetails(1).stream().filter(row -> row.questionId() == questionId).findFirst().orElseThrow();
        assertThat(result.selectedOptionNumbers()).as("Admin selections for question %s", questionId)
                .containsExactlyInAnyOrderElementsOf(options);
        assertThat(result.selectedOptionTexts()).containsExactlyInAnyOrderElementsOf(texts);
        assertThat(result.isCorrect()).isEqualTo(marks > 0);
        assertThat(result.marksAwarded()).isEqualTo(marks);
    }

    private void assertOriginalLegacyAnswer(Connection conn) throws Exception {
        assertSingleInt(conn, "SELECT COUNT(*) FROM student_answers WHERE id = 1 AND selected_options = '1, 2' AND is_correct = 1", 1);
    }

    @Test void populatedV9WithOneTwoAndFourEvidenceRows() throws Exception { resumeHistorical(0, false); }
    @Test void partialV10ReturnedAtColumn() throws Exception { resumeHistorical(1, false); }
    @Test void partialV10DurationColumn() throws Exception { resumeHistorical(2, false); }
    @Test void partialV10LongAbsenceColumn() throws Exception { resumeHistorical(3, false); }
    @Test void partialV10EvidenceTypeColumn() throws Exception { resumeHistorical(4, false); }
    @Test void previousUniqueIndexCreationFailed() throws Exception { resumeHistorical(4, true); }

    @Test void crashAfterLegacyRelabelingResumes() throws Exception {
        historicalV9();
        var before = evidenceRows();
        dataSource = dataSource();
        var faulty = JdbcTestProbe.wrap(dataSource, (sql, after) -> {
            if (!after && sql.startsWith("CREATE UNIQUE INDEX uq_evidence_event_type")) throw new java.sql.SQLException("injected index-step interruption");
        });
        assertThatThrownBy(() -> new DatabaseMigrator(faulty).migrate()).hasRootCauseMessage("injected index-step interruption");
        try (var conn = connection()) { assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 9); }
        new DatabaseMigrator(dataSource).migrate();
        assertPreserved(before);
    }

    @Test void indexCreatedButVersionMarkerMissingResumes() throws Exception {
        historicalV9();
        var before = evidenceRows();
        dataSource = dataSource();
        var faulty = JdbcTestProbe.wrap(dataSource, (sql, after) -> {
            if (!after && sql.contains("VALUES (10, CURRENT_TIMESTAMP)")) throw new java.sql.SQLException("injected version-step interruption");
        });
        assertThatThrownBy(() -> new DatabaseMigrator(faulty).migrate()).hasRootCauseMessage("injected version-step interruption");
        new DatabaseMigrator(dataSource).migrate();
        new DatabaseMigrator(dataSource).migrate();
        assertPreserved(before);
    }

    @Test void historicalV10IndexWithoutMarkerResumes() throws Exception {
        historicalV9();
        // V10 could historically finish its index when there was one row per event.
        try (var conn = connection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM proctor_evidence_v2 WHERE id NOT IN (1, 2, 4)");
            addV10Columns(conn, 4);
            stmt.execute("CREATE UNIQUE INDEX uq_evidence_event_type ON proctor_evidence_v2(event_id,evidence_type)");
        }
        var before = evidenceRows();
        dataSource = dataSource(); new DatabaseMigrator(dataSource).migrate();
        assertPreserved(before);
    }

    @Test void alreadySuccessfulV10LabelsAreNeverReinterpreted() throws Exception {
        historicalV9();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM proctor_evidence_v2 WHERE id NOT IN (1, 2, 4)");
            executeScript(conn, new String(readResource("db/migration/schema-v10.sql"), StandardCharsets.UTF_8));
            stmt.execute("UPDATE proctor_evidence_v2 SET evidence_type='LONG_ABSENCE' WHERE id=2");
        }
        var before = evidenceRows();
        dataSource = dataSource(); new DatabaseMigrator(dataSource).migrate(); new DatabaseMigrator(dataSource).migrate();
        assertThat(evidenceRows()).isEqualTo(before);
        try (var conn = connection(); var stmt = conn.createStatement(); var rows = stmt.executeQuery("SELECT evidence_type FROM proctor_evidence_v2 ORDER BY id")) {
            rows.next(); assertThat(rows.getString(1)).isEqualTo("INITIAL");
            rows.next(); assertThat(rows.getString(1)).isEqualTo("LONG_ABSENCE");
        }
    }

    @Test void incorrectColumnDefinitionStopsBeforeRelabeling() throws Exception {
        historicalV9(); var before = evidenceRows();
        try (var conn = connection(); var stmt = conn.createStatement()) { stmt.execute("ALTER TABLE proctor_events ADD returned_at VARCHAR(20) NULL"); }
        dataSource = dataSource();
        assertThatThrownBy(() -> new DatabaseMigrator(dataSource).migrate()).hasRootCauseMessage(
                "V10 compatibility stopped: Incorrect definition of proctor_events.returned_at; expected DATETIME NULL. Back up the database and repair the schema to the documented V9/V10 definition before restarting.");
        assertThat(evidenceRows()).isEqualTo(before);
        try (var conn = connection()) { assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 9); }
    }

    @Test void incorrectIndexDefinitionStops() throws Exception {
        historicalV9(); var before = evidenceRows();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            addV10Columns(conn, 4);
            stmt.execute("CREATE INDEX uq_evidence_event_type ON proctor_evidence_v2(event_id,evidence_type)");
        }
        dataSource = dataSource();
        assertThatThrownBy(() -> new DatabaseMigrator(dataSource).migrate()).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(evidenceRows()).isEqualTo(before);
        try (var conn = connection()) { assertSingleInt(conn, "SELECT MAX(version) FROM schema_versions", 9); }
    }

    @Test void unknownPreV10LabelsStopWithoutInventingProvenance() throws Exception {
        historicalV9();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            addV10Columns(conn, 4); stmt.execute("UPDATE proctor_evidence_v2 SET evidence_type='LONG_ABSENCE' WHERE id=1");
        }
        dataSource = dataSource();
        assertThatThrownBy(() -> new DatabaseMigrator(dataSource).migrate()).hasRootCauseInstanceOf(java.sql.SQLException.class);
        try (var conn = connection()) { assertSingleInt(conn, "SELECT COUNT(*) FROM proctor_evidence_v2 WHERE evidence_type='INITIAL'", 6); }
    }

    @Test void competingMigratorsSerializeAcrossDdlAndVersionMarker() throws Exception {
        historicalV9(); var before = evidenceRows(); dataSource = dataSource();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var secondLock = new java.util.concurrent.CountDownLatch(1);
        var first = JdbcTestProbe.wrap(dataSource, (sql, after) -> {
            if (after && sql.startsWith("ALTER TABLE proctor_events ADD COLUMN returned_at")) {
                entered.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.sql.SQLException("barrier timeout");
            }
        });
        var second = JdbcTestProbe.wrap(dataSource, (sql, after) -> { if (!after && sql.contains("GET_LOCK")) secondLock.countDown(); });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> new DatabaseMigrator(first).migrate());
            try {
                assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var b = executor.submit(() -> new DatabaseMigrator(second).migrate());
                assertThat(secondLock.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(b.isDone()).isFalse();
                release.countDown(); a.get(15, java.util.concurrent.TimeUnit.SECONDS); b.get(15, java.util.concurrent.TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertPreserved(before);
    }

    @Test void everyMigratedArtifactIsListedAndRetrievedThroughAdminHttp() throws Exception {
        historicalV9(); dataSource = dataSource(); new DatabaseMigrator(dataSource).migrate();
        try (var conn = connection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO server_sessions(token,user_id,expires_at) VALUES('migration-admin',1,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 HOUR))");
        }
        System.setProperty("SERVER_PORT", "8089"); System.setProperty("DB_URL", dbUrl);
        System.setProperty("DB_USER", dbUser); System.setProperty("DB_PASSWORD", dbPassword);
        ExaSecureServer app = null;
        try {
            app = new ExaSecureServer(new com.examsystem.server.config.AppConfig()); app.start();
            var client = java.net.http.HttpClient.newHttpClient();
            var listing = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:8089/api/v1/admin/attempts/1/events"))
                    .header("Authorization","Bearer migration-admin").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(listing.statusCode()).isEqualTo(200);
            var events = new com.fasterxml.jackson.databind.ObjectMapper().readTree(listing.body());
            int retrieved = 0;
            for (var event : events) for (var type : event.required("evidenceTypes")) {
                String key=type.asText();
                int evidenceId=Integer.parseInt(key.substring(1),36);
                var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:8089/api/v1/admin/events/"+event.required("id").asInt()+"/evidence?type="+key))
                        .header("Authorization","Bearer migration-admin").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).containsExactly((byte)evidenceId,(byte)0,(byte)255,(byte)42);
                retrieved++;
            }
            assertThat(retrieved).isEqualTo(7);
        } finally {
            if(app!=null) app.stop();
            for(String key:List.of("SERVER_PORT","DB_URL","DB_USER","DB_PASSWORD")) System.clearProperty(key);
        }
    }

    private void resumeHistorical(int columns, boolean failedIndex) throws Exception {
        historicalV9(); var before = evidenceRows();
        try (var conn = connection()) {
            addV10Columns(conn, columns);
            if (failedIndex) assertThatThrownBy(() -> conn.createStatement().execute("CREATE UNIQUE INDEX uq_evidence_event_type ON proctor_evidence_v2(event_id,evidence_type)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
        dataSource = dataSource(); new DatabaseMigrator(dataSource).migrate(); new DatabaseMigrator(dataSource).migrate();
        assertPreserved(before);
    }

    private void historicalV9() throws Exception {
        // Exact historical scripts through V9; populated before invoking the current migrator.
        try (var conn = connection()) {
            executeScript(conn, new String(readResource("db/migration/schema-v3.sql"), StandardCharsets.UTF_8));
            seedV3Data(conn);
            for (int v = 4; v <= 9; v++) executeScript(conn, new String(readResource("db/migration/schema-v" + v + ".sql"), StandardCharsets.UTF_8));
            try (var stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO schema_versions(version) VALUES(7)");
                stmt.execute("INSERT INTO proctor_events(id,attempt_id,event_type,recorded_at) VALUES(101,1,'FOCUS_LOST','2026-09-02 10:06:00'),(102,1,'FOCUS_LOST','2026-09-02 10:06:00'),(103,1,'FOCUS_LOST','2026-09-02 10:07:00')");
            }
            try (var stmt = conn.prepareStatement("INSERT INTO proctor_evidence_v2(id,event_id,content_type,byte_size,screenshot_data,recorded_at) VALUES(?,?,?,? ,?,'2026-09-02 10:06:00')")) {
                for (int id=1; id<=7; id++) {
                    byte[] payload = new byte[]{(byte) id, 0, (byte) 255, 42};
                    stmt.setInt(1,id); stmt.setInt(2,id==1?101:id<=3?102:103); stmt.setString(3,id%2==0?"image/png":"image/jpeg");
                    stmt.setInt(4,payload.length); stmt.setBytes(5,payload); stmt.executeUpdate();
                }
            }
            assertSingleInt(conn,"SELECT MAX(version) FROM schema_versions",9);
        }
    }

    private void addV10Columns(Connection conn, int count) throws Exception {
        String[] ddl = {"ALTER TABLE proctor_events ADD returned_at DATETIME NULL", "ALTER TABLE proctor_events ADD away_duration_ms BIGINT NULL",
                "ALTER TABLE proctor_events ADD is_long_absence TINYINT(1) NOT NULL DEFAULT 0", "ALTER TABLE proctor_evidence_v2 ADD evidence_type VARCHAR(32) NOT NULL DEFAULT 'INITIAL'"};
        try (var stmt=conn.createStatement()) { for(int i=0;i<count;i++) stmt.execute(ddl[i]); }
    }

    private List<String> evidenceRows() throws Exception {
        var result = new java.util.ArrayList<String>();
        try(var conn=connection(); var stmt=conn.createStatement(); var rows=stmt.executeQuery(
                "SELECT p.id,p.event_id,p.recorded_at,p.content_type,p.byte_size,p.screenshot_data,e.attempt_id,a.student_id FROM proctor_evidence_v2 p JOIN proctor_events e ON e.id=p.event_id JOIN exam_attempts a ON a.id=e.attempt_id ORDER BY p.id")) {
            while(rows.next()) result.add(rows.getLong(1)+"|"+rows.getInt(2)+"|"+rows.getTimestamp(3)+"|"+rows.getString(4)+"|"+rows.getInt(5)+"|"+HexFormat.of().formatHex(rows.getBytes(6))+"|"+rows.getInt(7)+"|"+rows.getInt(8));
        }
        return result;
    }

    private void assertPreserved(List<String> before) throws Exception {
        assertThat(evidenceRows()).containsExactlyElementsOf(before);
        var admin = new com.examsystem.server.adapters.jdbc.JdbcAdminQueryPort(dataSource);
        try(var conn=connection(); var stmt=conn.createStatement(); var rows=stmt.executeQuery("SELECT id,event_id,evidence_type,screenshot_data FROM proctor_evidence_v2 ORDER BY id")) {
            while(rows.next()) {
                String key="L"+Long.toString(rows.getLong(1),36).toUpperCase(java.util.Locale.ROOT);
                assertThat(rows.getString(3)).isEqualTo(key);
                assertThat(admin.getEvidenceByEventId(rows.getInt(2), key).orElseThrow().data()).containsExactly(rows.getBytes(4));
            }
            assertSingleInt(conn,"SELECT MAX(version) FROM schema_versions",11);
            assertThatThrownBy(() -> stmt.execute("INSERT INTO proctor_evidence_v2(event_id,evidence_type,recorded_at,screenshot_data) SELECT event_id,evidence_type,recorded_at,screenshot_data FROM proctor_evidence_v2 LIMIT 1")).isInstanceOf(java.sql.SQLException.class);
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
