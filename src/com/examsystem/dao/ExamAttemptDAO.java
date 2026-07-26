package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.AttemptSummary;
import com.examsystem.models.ExamAttempt;
import com.examsystem.models.ProctorLog; // Import the new model

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExamAttemptDAO {

    public int createAttempt(int examId, int studentId, Timestamp startTime) {
        String sql = "INSERT INTO exam_attempts (exam_id, student_id, start_time) VALUES (?,?,?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, examId);
            ps.setInt(2, studentId);
            ps.setTimestamp(3, startTime);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.createAttempt error: " + e.getMessage());
        }
        return -1;
    }

    public void completeAttempt(int attemptId, Timestamp endTime, int score, int focusLostCount) {
        String sql = "UPDATE exam_attempts SET end_time=?, score=?, focus_lost_count=? WHERE id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, endTime);
            ps.setInt(2, score);
            ps.setInt(3, focusLostCount);
            ps.setInt(4, attemptId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.completeAttempt error: " + e.getMessage());
        }
    }

    // --- NEW: Insert a proctoring log event ---
    public void logEvent(int attemptId, String message) {
        String sql = "INSERT INTO proctor_logs (attempt_id, log_message) VALUES (?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, attemptId);
            ps.setString(2, message);
            ps.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.logEvent error: " + e.getMessage());
        }
    }

    // --- NEW: Fetch logs for review ---
    public List<ProctorLog> getProctorLogs(int attemptId) {
        String sql = "SELECT log_message, created_at FROM proctor_logs WHERE attempt_id=? ORDER BY created_at ASC";
        List<ProctorLog> logs = new ArrayList<>();
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    logs.add(new ProctorLog(
                        rs.getString("log_message"),
                        rs.getTimestamp("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getProctorLogs error: " + e.getMessage());
        }
        return logs;
    }

    public boolean deleteAttempt(int attemptId) {
        // Logs will delete automatically via CASCADE if configured in SQL, 
        // but let's be safe and delete them manually just in case.
        String delLogs = "DELETE FROM proctor_logs WHERE attempt_id=?";
        String delAnswers = "DELETE FROM student_answers WHERE attempt_id=?";
        String delAttempt = "DELETE FROM exam_attempts WHERE id=?";
        
        Connection conn = null;
        try {
            conn = DatabaseConnector.getConnection();
            conn.setAutoCommit(false); 

            try (PreparedStatement ps0 = conn.prepareStatement(delLogs)) {
                ps0.setInt(1, attemptId);
                ps0.executeUpdate();
            }
            
            try (PreparedStatement ps1 = conn.prepareStatement(delAnswers)) {
                ps1.setInt(1, attemptId);
                ps1.executeUpdate();
            }

            try (PreparedStatement ps2 = conn.prepareStatement(delAttempt)) {
                ps2.setInt(1, attemptId);
                int rows = ps2.executeUpdate();
                conn.commit();
                return rows > 0;
            }

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            return false;
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) {}
        }
    }

    public ExamAttempt getAttemptById(int attemptId) {
        String sql = "SELECT * FROM exam_attempts WHERE id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ExamAttempt(
                            rs.getInt("id"),
                            rs.getInt("exam_id"),
                            rs.getInt("student_id"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getObject("score") == null ? null : rs.getInt("score")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getAttemptById error: " + e.getMessage());
        }
        return null;
    }

    public List<AttemptSummary> getAttemptSummariesByExamId(int examId) {
        String sql =
            "SELECT ea.id AS attempt_id, u.full_name, u.username, u.enrollment_number, u.section, " +
            "IFNULL(ea.score,0) AS score, ea.start_time, ea.end_time, IFNULL(ea.focus_lost_count, 0) as focus_lost " +
            "FROM exam_attempts ea INNER JOIN users u ON ea.student_id = u.id " +
            "WHERE ea.exam_id=? ORDER BY ea.start_time DESC";

        List<AttemptSummary> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AttemptSummary(
                            rs.getInt("attempt_id"),
                            rs.getString("full_name"),
                            rs.getString("username"),
                            rs.getString("enrollment_number"),
                            rs.getString("section"),
                            rs.getInt("score"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getInt("focus_lost")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getAttemptSummariesByExamId error: " + e.getMessage());
        }
        return list;
    }

    public List<String> getDistinctDatesForExam(int examId) {
        String sql = "SELECT DISTINCT DATE(start_time) as exam_date FROM exam_attempts WHERE exam_id=? ORDER BY exam_date DESC";
        List<String> dates = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date date = rs.getDate("exam_date");
                    if (date != null) {
                        dates.add(date.toString());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getDistinctDatesForExam error: " + e.getMessage());
        }
        return dates;
    }

    public void calculateAndSetScore(int attemptId, int focusLostCount) {
        int score = 0;
        try {
            StudentAnswerDAO answerDAO = new StudentAnswerDAO();
            score = answerDAO.countCorrectAnswersByAttempt(attemptId);
        } catch (Exception e) {
            System.err.println("ExamAttemptDAO.calculateAndSetScore error: " + e.getMessage());
        }
        completeAttempt(attemptId, new Timestamp(System.currentTimeMillis()), score, focusLostCount);
    }
}