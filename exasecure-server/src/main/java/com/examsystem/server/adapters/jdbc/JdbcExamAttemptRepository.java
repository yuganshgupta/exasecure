package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.domain.models.ExamAttempt;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                     "SELECT id, exam_id, student_id, start_time, end_time, score, status FROM exam_attempts WHERE id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean finalized = "SUBMITTED".equals(rs.getString("status")) || "EXPIRED".equals(rs.getString("status"));
                    Timestamp endTs = rs.getTimestamp("end_time");
                    Integer score = rs.getObject("score") != null ? rs.getInt("score") : null;
                    return Optional.of(new ExamAttempt(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getInt("student_id"),
                            rs.getTimestamp("start_time").toInstant(),
                            endTs != null ? endTs.toInstant() : null,
                            score,
                            finalized
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }

    @Override
    public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE exam_attempts SET score = ?, end_time = ?, status = 'SUBMITTED', focus_lost_count = ? WHERE id = ? AND status = 'ACTIVE'")) {
            ps.setInt(1, score);
            ps.setTimestamp(2, Timestamp.from(submittedAt));
            ps.setInt(3, focusLostCount);
            ps.setInt(4, attemptId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, java.time.Duration timeLimit) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find active attempt
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
                        }
                    }
                }

                // If we reach here, we need to create a new attempt (if policy allows multiple)
                // For ExaSecure, students usually get 1 attempt, but let's just create one.
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
            throw new RuntimeException("DB error", e);
        }
        throw new IllegalStateException("Could not create attempt");
    }
}
