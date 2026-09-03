package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ExamAttemptRepository;
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

    public JdbcExamAttemptRepository(DataSource dataSource) {
        this.dataSource = dataSource;
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
        } catch (Exception e) {
            // Fallback for earlier schema if is_flagged column not yet created
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
            } catch (Exception ex) {
                throw new RuntimeException("DB error", ex);
            }
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }

    @Override
    public int incrementFocusLostCount(int attemptId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_attempts SET focus_lost_count = focus_lost_count + 1, "
                             + "is_flagged = CASE WHEN focus_lost_count >= 3 THEN 1 ELSE is_flagged END, "
                             + "proctoring_status = CASE WHEN focus_lost_count >= 3 THEN 'FLAGGED' ELSE proctoring_status END "
                             + "WHERE id = ?")) {
            ps.setInt(1, attemptId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return findById(attemptId).map(ExamAttempt::focusLostCount).orElse(0);
    }

    @Override
    public void flagAttempt(int attemptId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_attempts SET is_flagged = 1, proctoring_status = 'FLAGGED' WHERE id = ?")) {
            ps.setInt(1, attemptId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_attempts SET score = ?, end_time = ?, status = 'SUBMITTED', focus_lost_count = ?, "
                             + "is_flagged = CASE WHEN ? >= 3 THEN 1 ELSE is_flagged END, "
                             + "proctoring_status = CASE WHEN ? >= 3 THEN 'FLAGGED' ELSE proctoring_status END "
                             + "WHERE id = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, score);
            ps.setTimestamp(2, Timestamp.from(submittedAt));
            ps.setInt(3, focusLostCount);
            ps.setInt(4, focusLostCount);
            ps.setInt(5, focusLostCount);
            ps.setInt(6, attemptId);
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
            conn.setAutoCommit(false);
            try {
                // Find active or existing attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, start_time, end_time, score, status FROM exam_attempts WHERE student_id = ? AND exam_id = ? ORDER BY id DESC LIMIT 1")) {
                    ps.setInt(1, studentId);
                    ps.setInt(2, examId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String status = rs.getString("status");
                            boolean finalized = "SUBMITTED".equals(status) || "EXPIRED".equals(status);
                            Timestamp endTs = rs.getTimestamp("end_time");
                            Integer score = rs.getObject("score") != null ? rs.getInt("score") : null;
                            ExamAttempt attempt = new ExamAttempt(
                                    rs.getInt("id"), examId, studentId,
                                    rs.getTimestamp("start_time").toInstant(),
                                    endTs != null ? endTs.toInstant() : null,
                                    score, finalized
                            );
                            if (!attempt.finalized() && !attempt.isExpired(now, timeLimit)) {
                                conn.commit();
                                return attempt;
                            }
                            if (attempt.finalized()) {
                                conn.commit();
                                throw new IllegalStateException("Exam attempt is already finalized");
                            }
                            if (attempt.isExpired(now, timeLimit)) {
                                try (PreparedStatement psExpire = conn.prepareStatement(
                                        "UPDATE exam_attempts SET status = 'EXPIRED', end_time = ? WHERE id = ? AND status = 'ACTIVE'")) {
                                    psExpire.setTimestamp(1, Timestamp.from(now));
                                    psExpire.setInt(2, attempt.id());
                                    psExpire.executeUpdate();
                                }
                                conn.commit();
                                throw new IllegalStateException("Exam attempt has expired");
                            }
                        }
                    }
                }

                // If no attempt exists, create a new attempt
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO exam_attempts (exam_id, student_id, start_time, status) VALUES (?, ?, ?, 'ACTIVE')",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, examId);
                    ps.setInt(2, studentId);
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            conn.commit();
                            return new ExamAttempt(id, examId, studentId, now, null, null, false);
                        }
                    }
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new RuntimeException("DB error", e);
        }
        throw new IllegalStateException("Could not create attempt");
    }

    @Override
    public int finalizeAndGradeAttempt(int attemptId, Instant submittedAt, int focusLostCount,
                                        java.util.function.ToIntFunction<java.util.Map<Integer, java.util.List<Integer>>> grader,
                                        com.examsystem.application.ports.AnswerRepository answerRepository) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock attempt row FOR UPDATE
                try (PreparedStatement psLock = conn.prepareStatement(
                        "SELECT status, score FROM exam_attempts WHERE id = ? FOR UPDATE")) {
                    psLock.setInt(1, attemptId);
                    try (ResultSet rs = psLock.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Attempt not found: " + attemptId);
                        }
                        String status = rs.getString("status");
                        if ("SUBMITTED".equals(status)) {
                            conn.commit();
                            return rs.getObject("score") != null ? rs.getInt("score") : 0;
                        }
                        if (!"ACTIVE".equals(status)) {
                            throw new IllegalStateException("Attempt is not ACTIVE (status: " + status + ")");
                        }
                    }
                }

                // Read answers directly on the same connection under the lock
                java.util.Map<Integer, java.util.List<Integer>> answers = new java.util.HashMap<>();
                try (PreparedStatement psAnswers = conn.prepareStatement(
                        "SELECT sa.question_id, sao.selected_option FROM student_answers sa " +
                        "LEFT JOIN student_answer_options sao ON sa.id = sao.answer_id " +
                        "WHERE sa.attempt_id = ?")) {
                    psAnswers.setInt(1, attemptId);
                    try (ResultSet rs = psAnswers.executeQuery()) {
                        while (rs.next()) {
                            int qId = rs.getInt("question_id");
                            int opt = rs.getInt("selected_option");
                            answers.computeIfAbsent(qId, k -> new java.util.ArrayList<>());
                            if (!rs.wasNull()) {
                                answers.get(qId).add(opt);
                            }
                        }
                    }
                }

                int score = grader.applyAsInt(answers);

                // Update attempt to SUBMITTED with score
                try (PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE exam_attempts SET score = ?, end_time = ?, status = 'SUBMITTED', focus_lost_count = ?, "
                                + "is_flagged = CASE WHEN ? >= 3 THEN 1 ELSE is_flagged END, "
                                + "proctoring_status = CASE WHEN ? >= 3 THEN 'FLAGGED' ELSE proctoring_status END "
                                + "WHERE id = ? AND status = 'ACTIVE'")) {
                    psUpdate.setInt(1, score);
                    psUpdate.setTimestamp(2, Timestamp.from(submittedAt));
                    psUpdate.setInt(3, focusLostCount);
                    psUpdate.setInt(4, focusLostCount);
                    psUpdate.setInt(5, focusLostCount);
                    psUpdate.setInt(6, attemptId);
                    int updated = psUpdate.executeUpdate();
                    if (updated == 0) {
                        throw new IllegalStateException("Attempt was not updated to SUBMITTED");
                    }
                }

                conn.commit();
                return score;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

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
                } catch (Exception ignored) {
                    // Legacy table may not exist in all environments
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
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to delete attempt " + attemptId, e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error deleting attempt " + attemptId, e);
        }
    }
}
