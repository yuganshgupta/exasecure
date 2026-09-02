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
        List<Question> questions = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement psQuestion = conn.prepareStatement("SELECT id, exam_id, question_text, question_type, correct_option_number, is_active FROM questions WHERE exam_id = ? AND is_active = 1")) {
            psQuestion.setInt(1, examId);
            try (ResultSet rsQ = psQuestion.executeQuery()) {
                while (rsQ.next()) {
                    int id = rsQ.getInt("id");
                    List<Option> options = new ArrayList<>();
                    try (PreparedStatement psOptions = conn.prepareStatement("SELECT id, option_text, is_correct FROM options WHERE question_id = ? AND is_active = 1 ORDER BY option_number")) {
                        psOptions.setInt(1, id);
                        try (ResultSet rsO = psOptions.executeQuery()) {
                            while (rsO.next()) {
                                options.add(new Option(rsO.getInt("id"), rsO.getString("option_text"), rsO.getBoolean("is_correct")));
                            }
                        }
                    }
                    questions.add(new Question(
                            id,
                            examId,
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
        return questions;
    }
}
