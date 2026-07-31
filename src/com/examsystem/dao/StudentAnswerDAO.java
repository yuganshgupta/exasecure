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
    public void saveAnswer(int attemptId, int questionId, int selectedOptionNumber, boolean isCorrect, Timestamp answerTimestamp) {
        // This syntax works if you added the UNIQUE constraint.
        // If not, it will just insert. Ideally, run the SQL command I provided.
        String sql = "INSERT INTO student_answers (attempt_id, question_id, selected_option_number, is_correct, answer_timestamp) " +
                     "VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE selected_option_number=?, is_correct=?, answer_timestamp=?";
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            ps.setInt(2, questionId);
            ps.setInt(3, selectedOptionNumber);
            ps.setBoolean(4, isCorrect);
            ps.setTimestamp(5, answerTimestamp);
            
            // Update part
            ps.setInt(6, selectedOptionNumber);
            ps.setBoolean(7, isCorrect);
            ps.setTimestamp(8, answerTimestamp);
            
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.saveAnswer error: " + e.getMessage());
            throw new RuntimeException("Failed to save answer. Please check your network or contact administrator.", e);
        }
    }

    // --- NEW: Get single answer for navigation ---
    public int getStudentSelectedOption(int attemptId, int questionId) {
        String sql = "SELECT selected_option_number FROM student_answers WHERE attempt_id=? AND question_id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            ps.setInt(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("selected_option_number");
            }
        } catch (SQLException e) {
            System.err.println("StudentAnswerDAO.getStudentSelectedOption error: " + e.getMessage());
        }
        return -1; // Not answered yet
    }

    public List<StudentAnswer> getAnswersForAttemptOrderedByTimestamp(int attemptId) {
        String sql = "SELECT id, attempt_id, question_id, selected_option_number, is_correct, answer_timestamp " +
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
                            rs.getInt("selected_option_number"),
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
            "       o_selected.option_text AS selected_text, " +
            "       o_correct.option_text AS correct_text, " +
            "       sa.is_correct " +
            "FROM student_answers sa " +
            "JOIN questions q ON sa.question_id = q.id " +
            "LEFT JOIN options o_selected ON (o_selected.question_id = q.id AND o_selected.option_number = sa.selected_option_number) " +
            "LEFT JOIN options o_correct ON (o_correct.question_id = q.id AND o_correct.option_number = q.correct_option_number) " +
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
            "WHERE ea.exam_id = ? "
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