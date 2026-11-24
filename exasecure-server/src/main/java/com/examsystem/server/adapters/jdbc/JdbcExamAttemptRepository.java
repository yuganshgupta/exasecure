package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.DeadlineNotReachedException;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.domain.models.ExamAttempt;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class JdbcExamAttemptRepository implements ExamAttemptRepository {
    private final DataSource dataSource;
    private final ClockPort clock;

    public JdbcExamAttemptRepository(DataSource dataSource) {
        this(dataSource, Instant::now);
    }

    public JdbcExamAttemptRepository(DataSource dataSource, ClockPort clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public Optional<ExamAttempt> findById(int attemptId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, exam_id, student_id, start_time, end_time, score, status, focus_lost_count, is_flagged, proctoring_status FROM exam_attempts WHERE id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean finalized = "SUBMITTED".equals(rs.getString("status")) || "EXPIRED".equals(rs.getString("status"));
                    Timestamp endTs = rs.getTimestamp("end_time");
                    Integer score = rs.getObject("score") != null ? rs.getInt("score") : null;
                    int focusLostCount = rs.getInt("focus_lost_count");
                    boolean flagged = rs.getBoolean("is_flagged");
                    String proctoringStatus = rs.getString("proctoring_status");
                    return Optional.of(new ExamAttempt(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getInt("student_id"),
                            rs.getTimestamp("start_time").toInstant(),
                            endTs != null ? endTs.toInstant() : null,
                            score,
                            finalized,
                            focusLostCount,
                            flagged,
                            proctoringStatus
                    ));
                }
            }
        } catch (SQLException e) {
            String message = e.getMessage();
            if (!"42S22".equals(e.getSQLState()) || message == null
                    || (!message.contains("is_flagged") && !message.contains("proctoring_status"))) {
                throw new RuntimeException("DB error", e);
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, exam_id, student_id, start_time, end_time, score, status, focus_lost_count FROM exam_attempts WHERE id = ?")) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boolean finalized = "SUBMITTED".equals(rs.getString("status")) || "EXPIRED".equals(rs.getString("status"));
                        Timestamp endTs = rs.getTimestamp("end_time");
                        Integer score = rs.getObject("score") != null ? rs.getInt("score") : null;
                        int focusLostCount = rs.getInt("focus_lost_count");
                        return Optional.of(new ExamAttempt(
                                rs.getInt("id"),
                                rs.getInt("exam_id"),
                                rs.getInt("student_id"),
                                rs.getTimestamp("start_time").toInstant(),
                                endTs != null ? endTs.toInstant() : null,
                                score,
                                finalized,
                                focusLostCount,
                                false,
                                "CLEAN"
                        ));
                    }
                }
            } catch (SQLException fallbackFailure) {
                fallbackFailure.addSuppressed(e);
                throw new RuntimeException("DB error", fallbackFailure);
            }
        }
        return Optional.empty();
    }

    @Override
    public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_attempts SET score = ?, end_time = ?, status = 'SUBMITTED' WHERE id = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, score);
            ps.setTimestamp(2, Timestamp.from(submittedAt));
            ps.setInt(3, attemptId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("Attempt is not ACTIVE or does not exist");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }


    @Override
    public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, java.time.Duration timeLimit) {
        try (Connection conn = dataSource.getConnection()) {
            // Read answers committed by a writer that held the lock during the latest-ID lookup.
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conn.setAutoCommit(false);
            try {
                // The existing user row also serializes requests when no attempt exists yet.
                try (PreparedStatement studentLock = conn.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    studentLock.setInt(1, studentId);
                    try (ResultSet studentRow = studentLock.executeQuery()) {
                        if (!studentRow.next()) throw new SQLException("Student no longer exists");
                    }
                }
                Integer latestId = latestAttemptId(conn, studentId, examId);
                if (latestId != null) {
                    var attempt = lockAttempt(conn, latestId);
                    if (attempt == null) throw new IllegalStateException("Exam attempt was removed. Retry to start again.");
                    if (!"ACTIVE".equals(attempt.status())) {
                        throw new IllegalStateException("Exam attempt is already finalized");
                    }
                    Instant deadline = JdbcAttemptFinalizer.deadline(conn, examId, attempt.start());
                    if (clock.now().isBefore(deadline)) {
                        conn.commit();
                        return new ExamAttempt(latestId, examId, studentId, attempt.start(), null, null, false);
                    }
                    var questions = JdbcQuestionRepository.findByExamId(conn, examId);
                    JdbcAttemptFinalizer.finalizeLocked(conn, latestId, deadline, answers -> GradingPolicy.score(questions, answers));
                } else {
                    requireOpenEnrollment(conn, examId);
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO exam_attempts (exam_id, student_id, start_time, status) VALUES (?, ?, ?, 'ACTIVE')",
                            java.sql.Statement.RETURN_GENERATED_KEYS)) {
                        insert.setInt(1, examId);
                        insert.setInt(2, studentId);
                        insert.setTimestamp(3, Timestamp.from(now));
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Attempt ID was not generated");
                            int id = keys.getInt(1);
                            conn.commit();
                            return new ExamAttempt(id, examId, studentId, now, null, null, false);
                        }
                    }
                }
                conn.commit();
            } catch (Exception | Error failure) {
                rollback(conn, failure);
                throw failure;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB error", e);
        }
        throw new IllegalStateException("Exam attempt expired and has been finalized");
    }

    private static Integer latestAttemptId(Connection conn, int studentId, int examId) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT id FROM exam_attempts WHERE student_id = ? AND exam_id = ? ORDER BY id DESC LIMIT 1")) {
            query.setInt(1, studentId);
            query.setInt(2, examId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getInt(1) : null;
            }
        }
    }

    private static void requireOpenEnrollment(Connection conn, int examId) throws SQLException {
        // Revision and deactivation update this row. Shared locking permits independent student starts.
        try (PreparedStatement query = conn.prepareStatement("SELECT is_active FROM exams WHERE id = ? FOR SHARE")) {
            query.setInt(1, examId);
            try (ResultSet exam = query.executeQuery()) {
                if (!exam.next()) throw new java.util.NoSuchElementException("Exam not found");
                if (!exam.getBoolean(1)) throw new SecurityException("Exam is not active");
            }
        }
    }

    @Override
    public int finalizeAndGradeAttempt(int attemptId, Instant submittedAt,
                                        java.util.function.ToIntFunction<java.util.Map<Integer, java.util.List<Integer>>> grader,
                                        com.examsystem.application.ports.AnswerRepository answerRepository,
                                        boolean deadlineTriggered) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                var attempt = lockAttempt(conn, attemptId);
                if (attempt == null) throw new IllegalArgumentException("Attempt not found");
                if ("SUBMITTED".equals(attempt.status()) && attempt.score() != null) {
                    conn.commit();
                    return attempt.score();
                }
                if (!"ACTIVE".equals(attempt.status())) {
                    throw new IllegalStateException("Attempt cannot be graded (status: " + attempt.status() + ")");
                }
                Instant deadline = JdbcAttemptFinalizer.deadline(conn, attempt.examId(), attempt.start());
                Instant now = clock.now();
                boolean beforeDeadline = now.isBefore(deadline);
                if (beforeDeadline && deadlineTriggered) throw new DeadlineNotReachedException();
                int score = JdbcAttemptFinalizer.finalizeLocked(conn, attemptId, beforeDeadline ? now : deadline, grader);
                conn.commit();
                return score;
            } catch (Exception | Error failure) {
                rollback(conn, failure);
                throw failure;
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB error", e);
        }
    }

    private static LockedAttempt lockAttempt(Connection conn, int attemptId) throws SQLException {
        try (PreparedStatement query = conn.prepareStatement(
                "SELECT exam_id, status, score, start_time FROM exam_attempts WHERE id = ? FOR UPDATE")) {
            query.setInt(1, attemptId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                return new LockedAttempt(row.getInt("exam_id"), row.getString("status"),
                        (Integer) row.getObject("score"), row.getTimestamp("start_time").toInstant());
            }
        }
    }

    private static void rollback(Connection conn, Throwable failure) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private record LockedAttempt(int examId, String status, Integer score, Instant start) {}

    @Override
    public boolean deleteAttempt(int attemptId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Verify attempt exists
                try (PreparedStatement check = conn.prepareStatement("SELECT id FROM exam_attempts WHERE id = ? FOR UPDATE")) {
                    check.setInt(1, attemptId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // 1. Delete proctor evidence blobs owned by this attempt's events
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE pe FROM proctor_evidence_v2 pe "
                                + "JOIN proctor_events ev ON pe.event_id = ev.id "
                                + "WHERE ev.attempt_id = ?")) {
                    ps.setInt(1, attemptId);
                    ps.executeUpdate();
                }

                // 2. Delete proctor events owned by this attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM proctor_events WHERE attempt_id = ?")) {
                    ps.setInt(1, attemptId);
                    ps.executeUpdate();
                }

                // 3. Delete from legacy_proctor_logs if present
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM legacy_proctor_logs WHERE attempt_id = ?")) {
                    ps.setInt(1, attemptId);
                    ps.executeUpdate();
                } catch (SQLException failure) {
                    if (!"42S02".equals(failure.getSQLState()) || failure.getMessage() == null
                            || !failure.getMessage().contains("legacy_proctor_logs")) {
                        throw failure;
                    }
                }

                // 4. Delete student answer options owned by this attempt's answers
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE sao FROM student_answer_options sao "
                                + "JOIN student_answers sa ON sao.answer_id = sa.id "
                                + "WHERE sa.attempt_id = ?")) {
                    ps.setInt(1, attemptId);
                    ps.executeUpdate();
                }

                // 5. Delete student answers owned by this attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM student_answers WHERE attempt_id = ?")) {
                    ps.setInt(1, attemptId);
                    ps.executeUpdate();
                }

                // 6. Delete the exam attempt row itself
                int deleted;
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM exam_attempts WHERE id = ?")) {
                    ps.setInt(1, attemptId);
                    deleted = ps.executeUpdate();
                }

                conn.commit();
                return deleted > 0;
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(conn, failure);
                throw failure;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error deleting attempt " + attemptId, e);
        }
    }
}
