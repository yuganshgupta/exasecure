package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.AnswerRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class JdbcAnswerRepository implements AnswerRepository {
    private final DataSource dataSource;

    public JdbcAnswerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, boolean isCorrect, Instant timestamp) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
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

                String csv = String.join(",", selectedOptions.stream().map(String::valueOf).toArray(String[]::new));

                if (answerId != -1) {
                    try (PreparedStatement psUpdate = conn.prepareStatement(
                            "UPDATE student_answers SET selected_options = ?, is_correct = ?, answer_timestamp = ? WHERE id = ?")) {
                        psUpdate.setString(1, csv);
                        psUpdate.setBoolean(2, isCorrect);
                        psUpdate.setTimestamp(3, Timestamp.from(timestamp));
                        psUpdate.setInt(4, answerId);
                        psUpdate.executeUpdate();
                    }
                    try (PreparedStatement psDeleteOps = conn.prepareStatement("DELETE FROM student_answer_options WHERE answer_id = ?")) {
                        psDeleteOps.setInt(1, answerId);
                        psDeleteOps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement psInsert = conn.prepareStatement(
                            "INSERT INTO student_answers (attempt_id, question_id, selected_options, is_correct, answer_timestamp) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        psInsert.setInt(1, attemptId);
                        psInsert.setInt(2, questionId);
                        psInsert.setString(3, csv);
                        psInsert.setBoolean(4, isCorrect);
                        psInsert.setTimestamp(5, Timestamp.from(timestamp));
                        psInsert.executeUpdate();
                        try (ResultSet rs = psInsert.getGeneratedKeys()) {
                            if (rs.next()) {
                                answerId = rs.getInt(1);
                            }
                        }
                    }
                }

                if (answerId != -1 && !selectedOptions.isEmpty()) {
                    try (PreparedStatement psOps = conn.prepareStatement("INSERT INTO student_answer_options (answer_id, selected_option) VALUES (?, ?)")) {
                        for (Integer opt : selectedOptions) {
                            psOps.setInt(1, answerId);
                            psOps.setInt(2, opt);
                            psOps.addBatch();
                        }
                        psOps.executeBatch();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public int countCorrectAnswersByAttempt(int attemptId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM student_answers WHERE attempt_id = ? AND is_correct = 1")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return 0;
    }
}
