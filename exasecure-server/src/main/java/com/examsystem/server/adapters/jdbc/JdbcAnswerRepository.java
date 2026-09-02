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
    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, Instant timestamp) {
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
    public java.util.Map<Integer, List<Integer>> getSelectedOptionsForAttempt(int attemptId) {
        java.util.Map<Integer, List<Integer>> answers = new java.util.HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sa.question_id, sao.selected_option FROM student_answers sa " +
                     "LEFT JOIN student_answer_options sao ON sa.id = sao.answer_id " +
                     "WHERE sa.attempt_id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int questionId = rs.getInt("question_id");
                    int option = rs.getInt("selected_option");
                    answers.computeIfAbsent(questionId, k -> new java.util.ArrayList<>());
                    if (!rs.wasNull()) {
                        answers.get(questionId).add(option);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return answers;
    }
}
