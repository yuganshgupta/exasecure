package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.QuestionStatistic;
import com.examsystem.models.StudentAnswer;
import com.examsystem.models.StudentAnswerDetail;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StudentAnswerDAO {

    // --- UPDATED: Upsert (Insert or Update) ---
    public void saveAnswer(int attemptId, int questionId, String selectedOptions, boolean isCorrect, Timestamp answerTimestamp) {
        String sql = "INSERT INTO student_answers (attempt_id, question_id, selected_options, is_correct, answer_timestamp) " +
                     "VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE selected_options=?, is_correct=?, answer_timestamp=?";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            ps.setInt(2, questionId);
            ps.setString(3, selectedOptions);
            ps.setBoolean(4, isCorrect);
            ps.setTimestamp(5, answerTimestamp);
            
            ps.setString(6, selectedOptions);
            ps.setBoolean(7, isCorrect);
            ps.setTimestamp(8, answerTimestamp);
            
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.saveAnswer error: " + e.getMessage());
            throw new RuntimeException("Failed to save answer. Please check your network or contact administrator.", e);
        }
    }

    // --- NEW: Get single answer for navigation ---
    public List<Integer> getStudentSelectedOptions(int attemptId, int questionId) {
        String sql = "SELECT selected_options FROM student_answers WHERE attempt_id=? AND question_id=?";
        List<Integer> selectedList = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            ps.setInt(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String sel = rs.getString("selected_options");
                    if (sel != null && !sel.isEmpty()) {
                        String[] parts = sel.split(",");
                        for (String p : parts) {
                            try {
                                selectedList.add(Integer.parseInt(p.trim()));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.getStudentSelectedOptions error: " + e.getMessage());
        }
        return selectedList;
    }

    public List<StudentAnswer> getAnswersForAttemptOrderedByTimestamp(int attemptId) {
        String sql = "SELECT id, attempt_id, question_id, selected_options, is_correct, answer_timestamp " +
                     "FROM student_answers WHERE attempt_id=? ORDER BY answer_timestamp ASC";
        List<StudentAnswer> list = new ArrayList<>();

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StudentAnswer(
                            rs.getInt("id"),
                            rs.getInt("attempt_id"),
                            rs.getInt("question_id"),
                            rs.getString("selected_options"),
                            rs.getBoolean("is_correct"),
                            rs.getTimestamp("answer_timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.getAnswersForAttemptOrderedByTimestamp error: " + e.getMessage());
        }
        return list;
    }

    public int countCorrectAnswersByAttempt(int attemptId) {
        String sql = "SELECT IFNULL(SUM(CASE WHEN is_correct=1 THEN 1 ELSE 0 END), 0) AS score FROM student_answers WHERE attempt_id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("score");
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.countCorrectAnswersByAttempt error: " + e.getMessage());
        }
        return 0;
    }

    public List<StudentAnswerDetail> getDetailedAnswers(int attemptId) {
        String sql = 
            "SELECT q.question_text, " +
            "       sa.selected_options AS selected_text, " +
            "       (SELECT GROUP_CONCAT(o.option_text ORDER BY o.option_number SEPARATOR ', ') " +
            "        FROM options o WHERE o.question_id = q.id AND o.is_correct = TRUE AND o.is_active = TRUE" +
            "       ) AS correct_text, " +
            "       sa.is_correct " +
            "FROM student_answers sa " +
            "JOIN questions q ON sa.question_id = q.id " +
            "WHERE sa.attempt_id = ? " +
            "ORDER BY sa.id ASC";

        List<StudentAnswerDetail> list = new ArrayList<>();
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StudentAnswerDetail(
                        rs.getString("question_text"),
                        rs.getString("selected_text"),
                        rs.getString("correct_text"),
                        rs.getBoolean("is_correct")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.getDetailedAnswers error: " + e.getMessage());
        }
        return list;
    }

    public List<QuestionStatistic> getQuestionStatistics(int examId, String dateFilter, String sectionFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT q.question_text, " +
            "       COUNT(sa.id) as total, " +
            "       SUM(CASE WHEN sa.is_correct THEN 1 ELSE 0 END) as correct " +
            "FROM student_answers sa " +
            "JOIN exam_attempts ea ON sa.attempt_id = ea.id " +
            "JOIN users u ON ea.student_id = u.id " +
            "JOIN questions q ON sa.question_id = q.id " +
            "WHERE ea.exam_id = ? AND q.is_active = TRUE "
        );

        boolean filterDate = dateFilter != null && !dateFilter.equals("All Dates");
        boolean filterSection = sectionFilter != null && !sectionFilter.equals("All Sections");

        if (filterDate) sql.append("AND DATE(ea.start_time) = ? ");
        if (filterSection) sql.append("AND u.section = ? ");

        sql.append("GROUP BY q.id ORDER BY q.id ASC");

        List<QuestionStatistic> stats = new ArrayList<>();
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int index = 1;
            ps.setInt(index++, examId);
            if (filterDate) ps.setString(index++, dateFilter);
            if (filterSection) ps.setString(index++, sectionFilter);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.add(new QuestionStatistic(
                        rs.getString("question_text"),
                        rs.getInt("total"),
                        rs.getInt("correct")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.getQuestionStatistics error: " + e.getMessage());
        }
        return stats;
    }
}