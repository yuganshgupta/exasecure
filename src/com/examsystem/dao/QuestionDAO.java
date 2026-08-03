package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for questions. */
public class QuestionDAO {

    public int addQuestion(int examId, String questionText, int correctOptionNumber, String questionType) {
        String sql = "INSERT INTO questions (exam_id, question_text, correct_option_number, question_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, examId);
            ps.setString(2, questionText);
            ps.setInt(3, correctOptionNumber);
            ps.setString(4, questionType != null ? questionType : "SINGLE");
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("QuestionDAO.addQuestion error: " + e.getMessage());
        }
        return -1;
    }

    public boolean softDeleteQuestion(int questionId) {
        String sql = "UPDATE questions SET is_active = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("QuestionDAO.softDeleteQuestion error: " + e.getMessage());
        }
        return false;
    }

    public boolean updateQuestion(Question question) {
        String sql = "UPDATE questions SET question_text = ?, correct_option_number = ?, question_type = ? WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, question.getQuestionText());
            ps.setInt(2, question.getCorrectOptionNumber());
            ps.setString(3, question.getQuestionType() != null ? question.getQuestionType() : "SINGLE");
            ps.setInt(4, question.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("QuestionDAO.updateQuestion error: " + e.getMessage());
        }
        return false;
    }

    public List<Question> getQuestionsByExamId(int examId) {
        String sqlQ = "SELECT id, exam_id, question_text, correct_option_number, question_type FROM questions WHERE exam_id=? AND is_active=TRUE ORDER BY id ASC";
        String sqlO = "SELECT id, question_id, option_number, option_text, is_correct " +
                       "FROM options WHERE question_id IN " +
                       "(SELECT id FROM questions WHERE exam_id=? AND is_active=TRUE) " +
                       "AND is_active=TRUE ORDER BY question_id, option_number ASC";
        
        List<Question> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection()) {
            // Fetch questions
            try (PreparedStatement ps = conn.prepareStatement(sqlQ)) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Question(
                                rs.getInt("id"),
                                rs.getInt("exam_id"),
                                rs.getString("question_text"),
                                rs.getInt("correct_option_number"),
                                rs.getString("question_type")
                        ));
                    }
                }
            }
            // Fetch all options in one shot and map by question_id
            java.util.Map<Integer, List<com.examsystem.models.Option>> optMap = new java.util.HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlO)) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int qId = rs.getInt("question_id");
                        optMap.computeIfAbsent(qId, k -> new ArrayList<>())
                            .add(new com.examsystem.models.Option(
                                rs.getInt("id"), qId,
                                rs.getInt("option_number"),
                                rs.getString("option_text"),
                                rs.getBoolean("is_correct")
                            ));
                    }
                }
            }
            for (Question q : list) {
                q.setOptions(optMap.getOrDefault(q.getId(), new ArrayList<>()));
            }
        } catch (SQLException e) {
            System.err.println("QuestionDAO.getQuestionsByExamId error: " + e.getMessage());
        }
        return list;
    }

    public Question getById(int id) {
        String sql = "SELECT id, exam_id, question_text, correct_option_number, question_type FROM questions WHERE id=? AND is_active=TRUE";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Question q = new Question(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getString("question_text"),
                            rs.getInt("correct_option_number"),
                            rs.getString("question_type")
                    );
                    OptionDAO optionDAO = new OptionDAO();
                    q.setOptions(optionDAO.getOptionsByQuestionId(q.getId()));
                    return q;
                }
            }
        } catch (SQLException e) {
            System.err.println("QuestionDAO.getById error: " + e.getMessage());
        }
        return null;
    }
}