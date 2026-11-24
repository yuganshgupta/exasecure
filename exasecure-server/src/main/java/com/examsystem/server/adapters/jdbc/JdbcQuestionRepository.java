package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.models.Option;
import com.examsystem.domain.models.Question;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcQuestionRepository implements QuestionRepository {
    private final DataSource dataSource;

    public JdbcQuestionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Question> findById(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement psQuestion = conn.prepareStatement("SELECT id, exam_id, question_text, question_type, correct_option_number, is_active FROM questions WHERE id = ? AND is_active = 1");
             PreparedStatement psOptions = conn.prepareStatement("SELECT id, option_number, option_text, is_correct FROM options WHERE question_id = ? AND is_active = 1 ORDER BY option_number")) {
            
            psQuestion.setInt(1, id);
            try (ResultSet rsQ = psQuestion.executeQuery()) {
                if (rsQ.next()) {
                    List<Option> options = new ArrayList<>();
                    psOptions.setInt(1, id);
                    try (ResultSet rsO = psOptions.executeQuery()) {
                        while (rsO.next()) {
                            options.add(new Option(
                                    rsO.getInt("id"),
                                    rsO.getInt("option_number"),
                                    rsO.getString("option_text"),
                                    rsO.getBoolean("is_correct")
                            ));
                        }
                    }
                    
                    return Optional.of(new Question(
                            rsQ.getInt("id"),
                            rsQ.getInt("exam_id"),
                            rsQ.getString("question_text"),
                            rsQ.getString("question_type"),
                            options,
                            rsQ.getInt("correct_option_number"),
                            rsQ.getBoolean("is_active")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Question> findByExamId(int examId) {
        try (Connection conn = dataSource.getConnection()) {
            return findByExamId(conn, examId);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("DB error", e);
        }
    }

    static List<Question> findByExamId(Connection conn, int examId) throws java.sql.SQLException {
        List<Question> questions = new ArrayList<>();
        try (PreparedStatement query = conn.prepareStatement("""
                SELECT q.id, q.question_text, q.question_type, q.correct_option_number, q.is_active,
                       o.id AS option_id, o.option_number, o.option_text, o.is_correct
                FROM questions q
                LEFT JOIN options o ON o.question_id = q.id AND o.is_active = 1
                WHERE q.exam_id = ? AND q.is_active = 1
                ORDER BY q.id, o.option_number
                """)) {
            query.setInt(1, examId);
            try (ResultSet rows = query.executeQuery()) {
                Question question = null;
                while (rows.next()) {
                    int id = rows.getInt("id");
                    if (question == null || question.id() != id) {
                        question = new Question(id, examId, rows.getString("question_text"),
                                rows.getString("question_type"), new ArrayList<>(),
                                rows.getInt("correct_option_number"), rows.getBoolean("is_active"));
                        questions.add(question);
                    }
                    int optionId = rows.getInt("option_id");
                    if (!rows.wasNull()) {
                        question.options().add(new Option(optionId, rows.getInt("option_number"),
                                rows.getString("option_text"), rows.getBoolean("is_correct")));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return questions;
    }
}
