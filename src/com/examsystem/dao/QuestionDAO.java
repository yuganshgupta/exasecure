package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for questions. */
public class QuestionDAO {

    public int addQuestion(int examId, String questionText, int correctOptionNumber) {
        String sql = "INSERT INTO questions (exam_id, question_text, correct_option_number) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, examId);
            ps.setString(2, questionText);
            ps.setInt(3, correctOptionNumber);
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

    public List<Question> getQuestionsByExamId(int examId) {
        String sql = "SELECT id, exam_id, question_text, correct_option_number FROM questions WHERE exam_id=? ORDER BY id ASC";
        List<Question> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Question(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getString("question_text"),
                            rs.getInt("correct_option_number")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("QuestionDAO.getQuestionsByExamId error: " + e.getMessage());
        }
        return list;
    }

    public Question getById(int id) {
        String sql = "SELECT id, exam_id, question_text, correct_option_number FROM questions WHERE id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Question(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getString("question_text"),
                            rs.getInt("correct_option_number")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("QuestionDAO.getById error: " + e.getMessage());
        }
        return null;
    }
}