package com.examsystem.server.adapters.jdbc;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.ToIntFunction;

/** Caller owns the connection, attempt row lock, transaction, and commit. */
final class JdbcAttemptFinalizer {
    static int finalizeLocked(Connection conn, int attemptId, Instant submittedAt,
                              ToIntFunction<Map<Integer, List<Integer>>> grader) throws SQLException {
        Map<Integer, List<Integer>> answers = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT sa.question_id, sao.selected_option FROM student_answers sa " +
                "LEFT JOIN student_answer_options sao ON sa.id = sao.answer_id WHERE sa.attempt_id = ?")) {
            stmt.setInt(1, attemptId);
            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    int question = rows.getInt(1);
                    int option = rows.getInt(2);
                    boolean absent = rows.wasNull();
                    answers.computeIfAbsent(question, key -> new ArrayList<>());
                    if (!absent) answers.get(question).add(option);
                }
            }
        }
        int score = grader.applyAsInt(answers);
        // Proctor state is already authoritative on the locked row. Never overwrite it.
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE exam_attempts SET score = ?, end_time = ?, status = 'SUBMITTED' WHERE id = ? AND status = 'ACTIVE'")) {
            stmt.setInt(1, score); stmt.setTimestamp(2, Timestamp.from(submittedAt)); stmt.setInt(3, attemptId);
            if (stmt.executeUpdate() != 1) throw new IllegalStateException("Attempt was not updated to SUBMITTED");
        }
        return score;
    }
}
