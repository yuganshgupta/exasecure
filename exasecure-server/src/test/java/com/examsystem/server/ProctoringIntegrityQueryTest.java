package com.examsystem.server;

import com.examsystem.application.query.AdminAttemptResult;
import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.server.adapters.jdbc.JdbcAdminQueryPort;
import com.examsystem.server.adapters.jdbc.JdbcAnswerRepository;
import com.examsystem.server.adapters.jdbc.JdbcExamAttemptRepository;
import com.examsystem.server.adapters.jdbc.JdbcProctorIncidentPort;
import com.examsystem.server.adapters.jdbc.JdbcQuestionRepository;
import com.examsystem.server.adapters.jdbc.JdbcStudentQueryPort;
import com.examsystem.server.db.DatabaseMigrator;
import com.examsystem.server.http.mappers.AdminContractMapper;
import com.examsystem.server.http.mappers.StudentContractMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProctoringIntegrityQueryTest {
    private static final Instant T0 = Instant.parse("2026-11-24T10:00:00Z");
    private HikariDataSource ds;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:integrity_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        ds = new HikariDataSource(config);

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            String v3Sql = new String(getClass().getClassLoader().getResourceAsStream("db/migration/schema-v3.sql").readAllBytes());
            for (String s : v3Sql.split(";")) {
                if (!s.trim().isEmpty()) stmt.execute(s);
            }
        }
        new DatabaseMigrator(ds).migrate();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, username, password, full_name, role, enrollment_number) "
                    + "VALUES (1, 'admin', 'hash', 'Admin', 'ADMIN', NULL), "
                    + "(2, 'stu', 'hash', 'Aditya Singh', 'STUDENT', 'MCA2026005')");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) "
                    + "VALUES (10, 'Networks', 30, 1, 1)");
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type) "
                    + "VALUES (100, 10, 'Which layer routes packets?', 2, 'SINGLE')");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct) "
                    + "VALUES (1001, 100, 1, 'Data Link', 0), (1002, 100, 2, 'Network', 1)");
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    @Test
    void integrityClassificationReflectsZeroOneTwoThreeAndHistoricalViolationsConsistently() throws Exception {
        var incidentPort = new JdbcProctorIncidentPort(ds, () -> T0.plusSeconds(30));
        var attemptRepo = new JdbcExamAttemptRepository(ds, () -> T0.plusSeconds(60));
        var answerRepo = new JdbcAnswerRepository(ds, () -> T0.plusSeconds(20));
        var questionRepo = new JdbcQuestionRepository(ds);
        var studentQuery = new JdbcStudentQueryPort(ds);
        var adminQuery = new JdbcAdminQueryPort(ds);

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (?, 10, 2, ?, 'ACTIVE')")) {
                for (int id : new int[]{1, 2, 3, 4, 6}) {
                    ps.setInt(1, id);
                    ps.setTimestamp(2, Timestamp.from(T0));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, end_time, score, status, focus_lost_count, is_flagged, proctoring_status) "
                            + "VALUES (5, 10, 2, ?, ?, 1, 'SUBMITTED', 1, 0, 'CLEAN')")) {
                ps.setTimestamp(1, Timestamp.from(T0.minusSeconds(3600)));
                ps.setTimestamp(2, Timestamp.from(T0.minusSeconds(2400)));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO proctor_events (attempt_id, event_type, recorded_at, client_event_id) "
                            + "VALUES (5, 'FOCUS_LOST', ?, 'legacy-ev-5')")) {
                ps.setTimestamp(1, Timestamp.from(T0.minusSeconds(3300)));
                ps.executeUpdate();
            }
        }

        // Save correct answers on attempts 1..4 and 6
        for (int attemptId : new int[]{1, 2, 3, 4, 6}) {
            answerRepo.saveAnswer(attemptId, 100, java.util.List.of(2), T0.plusSeconds(15));
        }

        var questions = questionRepo.findByExamId(10);
        var grader = (java.util.function.ToIntBiFunction<java.util.List<com.examsystem.domain.models.Question>, Map<Integer, java.util.List<Integer>>>)
                GradingPolicy::score;

        // Rejected event on Attempt 1 must not fabricate a violation
        assertThatThrownBy(() -> incidentPort.recordIncident(2, 1, "INVALID_TYPE", "bad-1", grader))
                .isInstanceOf(IllegalArgumentException.class);
        attemptRepo.finalizeAndGradeAttempt(1, T0.plusSeconds(60), answers -> GradingPolicy.score(questions, answers), answerRepo, false);

        // Attempt 2: 1 incident, no screenshot upload
        var ev2 = incidentPort.recordIncident(2, 2, "FOCUS_LOST", "ev-2-1", grader);
        assertThat(ev2.violationCount()).isEqualTo(1);
        assertThat(ev2.flagged()).isFalse();
        int score2 = attemptRepo.finalizeAndGradeAttempt(2, T0.plusSeconds(60), answers -> GradingPolicy.score(questions, answers), answerRepo, false);
        assertThat(score2).isEqualTo(1);

        // Attempt 3: 2 incidents + duplicate retry
        incidentPort.recordIncident(2, 3, "FOCUS_LOST", "ev-3-1", grader);
        var ev3b = incidentPort.recordIncident(2, 3, "FOCUS_LOST", "ev-3-2", grader);
        var ev3bRetry = incidentPort.recordIncident(2, 3, "FOCUS_LOST", "ev-3-2", grader);
        assertThat(ev3bRetry).isEqualTo(ev3b);
        assertThat(ev3b.violationCount()).isEqualTo(2);
        int score3 = attemptRepo.finalizeAndGradeAttempt(3, T0.plusSeconds(60), answers -> GradingPolicy.score(questions, answers), answerRepo, false);
        assertThat(score3).isEqualTo(1);

        // Attempt 4: 3 incidents -> auto-terminated
        incidentPort.recordIncident(2, 4, "FOCUS_LOST", "ev-4-1", grader);
        incidentPort.recordIncident(2, 4, "FOCUS_LOST", "ev-4-2", grader);
        var ev4c = incidentPort.recordIncident(2, 4, "FOCUS_LOST", "ev-4-3", grader);
        assertThat(ev4c.violationCount()).isEqualTo(3);
        assertThat(ev4c.flagged()).isTrue();
        assertThat(ev4c.attemptStatus()).isEqualTo("SUBMITTED");

        // Attempt 6: 1 incident before deadline, then deadline-triggered finalization
        incidentPort.recordIncident(2, 6, "FOCUS_LOST", "ev-6-1", grader);
        var deadlineRepo = new JdbcExamAttemptRepository(ds, () -> T0.plusSeconds(31 * 60L));
        int score6 = deadlineRepo.finalizeAndGradeAttempt(6, T0.plusSeconds(31 * 60L), answers -> GradingPolicy.score(questions, answers), answerRepo, true);
        assertThat(score6).isEqualTo(1);

        Map<Integer, StudentAttemptHistoryResult> stuMap = studentQuery.listHistory(2).stream()
                .collect(Collectors.toMap(StudentAttemptHistoryResult::attemptId, Function.identity()));
        Map<Integer, AdminAttemptResult> admMap = adminQuery.getAttemptHistory(10).stream()
                .collect(Collectors.toMap(AdminAttemptResult::id, Function.identity()));

        // Attempt 1: CLEAN
        assertThat(stuMap.get(1).flagged()).isFalse();
        assertThat(StudentContractMapper.toAttemptHistoryDto(stuMap.get(1)).flagged()).isFalse();
        assertThat(admMap.get(1).flagged()).isFalse();
        assertThat(admMap.get(1).proctoringStatus()).isEqualTo("CLEAN");
        assertThat(admMap.get(1).focusLostCount()).isZero();
        assertThat(AdminContractMapper.toAttemptDto(admMap.get(1)).proctoringStatus()).isEqualTo("CLEAN");

        // Attempts 2, 3, 4, 5, 6: ALL FLAGGED across Student and Admin, academic score 1 preserved
        for (int attemptId : new int[]{2, 3, 4, 5, 6}) {
            assertThat(stuMap.get(attemptId).flagged()).isTrue();
            assertThat(stuMap.get(attemptId).score()).isEqualTo(1);
            assertThat(stuMap.get(attemptId).status()).isEqualTo("SUBMITTED");
            assertThat(StudentContractMapper.toAttemptHistoryDto(stuMap.get(attemptId)).flagged()).isTrue();

            assertThat(admMap.get(attemptId).flagged()).isTrue();
            assertThat(admMap.get(attemptId).proctoringStatus()).isEqualTo("FLAGGED");
            assertThat(admMap.get(attemptId).score()).isEqualTo(1);
            assertThat(admMap.get(attemptId).status()).isEqualTo("SUBMITTED");
            assertThat(AdminContractMapper.toAttemptDto(admMap.get(attemptId)).flagged()).isTrue();
            assertThat(AdminContractMapper.toAttemptDto(admMap.get(attemptId)).proctoringStatus()).isEqualTo("FLAGGED");
        }
        assertThat(admMap.get(2).focusLostCount()).isEqualTo(1);
        assertThat(admMap.get(3).focusLostCount()).isEqualTo(2);
        assertThat(admMap.get(4).focusLostCount()).isEqualTo(3);
        assertThat(admMap.get(5).focusLostCount()).isEqualTo(1);
        assertThat(admMap.get(6).focusLostCount()).isEqualTo(1);
    }
}
