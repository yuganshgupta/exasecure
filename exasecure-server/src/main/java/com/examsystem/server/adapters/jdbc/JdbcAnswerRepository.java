package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ClockPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcAnswerRepository implements AnswerRepository {
    private final DataSource dataSource;
    private final ClockPort clock;

    public JdbcAnswerRepository(DataSource dataSource, ClockPort clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, Instant timestamp) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Instant deadline = lockAttemptDeadline(conn, attemptId);
                requireBeforeDeadline(deadline);

                // Check if answer already exists
                int answerId = -1;
                try (PreparedStatement psCheck = conn.prepareStatement("SELECT id FROM student_answers WHERE attempt_id = ? AND question_id = ?")) {
                    psCheck.setInt(1, attemptId);
                    psCheck.setInt(2, questionId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            answerId = rs.getInt(1);
                        }
                    }
                }

                if (answerId != -1) {
                    try (PreparedStatement psUpdate = conn.prepareStatement(
                            "UPDATE student_answers SET answer_timestamp = ? WHERE id = ?")) {
                        psUpdate.setTimestamp(1, Timestamp.from(timestamp));
                        psUpdate.setInt(2, answerId);
                        psUpdate.executeUpdate();
                    }
                    try (PreparedStatement psDeleteOps = conn.prepareStatement("DELETE FROM student_answer_options WHERE answer_id = ?")) {
                        psDeleteOps.setInt(1, answerId);
                        psDeleteOps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psInsert = conn.prepareStatement(
                            "INSERT INTO student_answers (attempt_id, question_id, answer_timestamp) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        psInsert.setInt(1, attemptId);
                        psInsert.setInt(2, questionId);
                        psInsert.setTimestamp(3, Timestamp.from(timestamp));
                        psInsert.executeUpdate();
                        try (ResultSet rs = psInsert.getGeneratedKeys()) {
                            if (rs.next()) {
                                answerId = rs.getInt(1);
                            }
                        }
                    }
                }

                if (answerId != -1 && selectedOptions != null && !selectedOptions.isEmpty()) {
                    try (PreparedStatement psOps = conn.prepareStatement("INSERT INTO student_answer_options (answer_id, selected_option) VALUES (?, ?)")) {
                        for (Integer opt : selectedOptions) {
                            psOps.setInt(1, answerId);
                            psOps.setInt(2, opt);
                            psOps.addBatch();
                        }
                        psOps.executeBatch();
                    }
                }
                // The final clock reading is the acceptance point for a successful commit.
                requireBeforeDeadline(deadline);
                conn.commit();
            } catch (Exception | Error e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    private Instant lockAttemptDeadline(Connection conn, int attemptId) throws SQLException {
        Instant startTime;
        int examId;
        try (PreparedStatement lock = conn.prepareStatement(
                "SELECT status, start_time, exam_id FROM exam_attempts WHERE id = ? FOR UPDATE")) {
            lock.setInt(1, attemptId);
            try (ResultSet row = lock.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalArgumentException("Attempt not found: " + attemptId);
                }
                String status = row.getString("status");
                if (!"ACTIVE".equals(status)) {
                    throw new IllegalStateException("Exam attempt is not ACTIVE (status: " + status + ")");
                }
                startTime = row.getTimestamp("start_time").toInstant();
                examId = row.getInt("exam_id");
            }
        }

        // Exam versions have a fixed duration. Keep the write lock scoped to the attempt.
        try (PreparedStatement exam = conn.prepareStatement("SELECT duration_minutes FROM exams WHERE id = ?")) {
            exam.setInt(1, examId);
            try (ResultSet row = exam.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalStateException("Exam not found for attempt: " + attemptId);
                }
                return startTime.plus(Duration.ofMinutes(row.getInt("duration_minutes")));
            }
        }
    }

    private void requireBeforeDeadline(Instant deadline) {
        if (!clock.now().isBefore(deadline)) {
            throw new IllegalStateException("Exam attempt is expired or finalized.");
        }
    }

    @Override
    public Map<Integer, List<Integer>> getSelectedOptionsForAttempt(int attemptId) {
        try (Connection conn = dataSource.getConnection()) {
            return readSelectedOptions(conn, attemptId);
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    static Map<Integer, List<Integer>> readSelectedOptions(Connection conn, int attemptId) throws SQLException {
        Map<Integer, List<Integer>> answers = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT sa.question_id, sao.selected_option FROM student_answers sa " +
                     "LEFT JOIN student_answer_options sao ON sa.id = sao.answer_id " +
                     "WHERE sa.attempt_id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int questionId = rs.getInt("question_id");
                    int option = rs.getInt("selected_option");
                    List<Integer> selections = answers.computeIfAbsent(questionId, k -> new ArrayList<>());
                    if (!rs.wasNull()) {
                        selections.add(option);
                    }
                }
            }
        }
        return answers;
    }
}
