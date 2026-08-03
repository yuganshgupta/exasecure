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
            throw new RuntimeException("Failed to finalize exam attempt in database.", e);
        }
    }

    // --- NEW: Insert a proctoring log event ---
    public void logEvent(int attemptId, String message, byte[] screenshotData) {
        String sql = "INSERT INTO proctor_logs (attempt_id, violation_type, screenshot_data) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, attemptId);
            ps.setString(2, message);
            if (screenshotData != null) {
                ps.setBytes(3, screenshotData);
            } else {
                ps.setNull(3, Types.BLOB);
            }
            ps.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.logEvent error: " + e.getMessage());
        }
    }

    // --- NEW: Fetch logs for review ---
    public List<ProctorLog> getProctorLogs(int attemptId) {
        String sql = "SELECT violation_type, screenshot_data, violation_time FROM proctor_logs WHERE attempt_id=? ORDER BY violation_time ASC";
        List<ProctorLog> logs = new ArrayList<>();
        
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    logs.add(new ProctorLog(
                        rs.getString("violation_type"),
                        rs.getTimestamp("violation_time"),
                        rs.getBytes("screenshot_data")
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
        // NOTE: We intentionally do NOT filter by 'u.is_active = TRUE' here.
        // Business Logic: Historical exam attempts and scores must be retained and visible
        // for reporting, even if the student's user account has been soft-deleted.
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
            throw new RuntimeException("Failed to calculate score.", e);
        }
        completeAttempt(attemptId, new Timestamp(System.currentTimeMillis()), score, focusLostCount);
    }


    // --- NEW: Count previous attempts for attempt limit logic ---
    public int getAttemptCount(int studentId, int examId) {
        String sql = "SELECT COUNT(*) FROM exam_attempts WHERE student_id=? AND exam_id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, studentId);
            ps.setInt(2, examId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getAttemptCount error: " + e.getMessage());
        }
        return 0; // Default to 0 if there's an error
    }

    // --- PHASE 4: STUDENT HISTORY ---

    public static class ExamAttemptSummary {
        private int attemptId;
        private String examTitle;
        private int score;
        private int maxScore;
        private double percentage;
        private String status;
        private Timestamp submittedAt;

        public ExamAttemptSummary(int attemptId, String examTitle, int score, int maxScore, double percentage, String status, Timestamp submittedAt) {
            this.attemptId = attemptId;
            this.examTitle = examTitle;
            this.score = score;
            this.maxScore = maxScore;
            this.percentage = percentage;
            this.status = status;
            this.submittedAt = submittedAt;
        }

        public int getAttemptId() { return attemptId; }
        public String getExamTitle() { return examTitle; }
        public int getScore() { return score; }
        public int getMaxScore() { return maxScore; }
        public double getPercentage() { return percentage; }
        public String getStatus() { return status; }
        public Timestamp getSubmittedAt() { return submittedAt; }
    }

    public static class QuestionDetailReport {
        private String questionText;
        private String studentAnswer;
        private String correctAnswer;
        private int pointsAwarded;

        public QuestionDetailReport(String questionText, String studentAnswer, String correctAnswer, int pointsAwarded) {
            this.questionText = questionText;
            this.studentAnswer = studentAnswer;
            this.correctAnswer = correctAnswer;
            this.pointsAwarded = pointsAwarded;
        }

        public String getQuestionText() { return questionText; }
        public String getStudentAnswer() { return studentAnswer; }
        public String getCorrectAnswer() { return correctAnswer; }
        public int getPointsAwarded() { return pointsAwarded; }
    }

    public List<ExamAttemptSummary> getAttemptsByUserId(int userId) {
        String sql = 
            "SELECT ea.id AS attempt_id, e.title AS exam_title, IFNULL(ea.score, 0) AS score, " +
            "       (SELECT COUNT(*) FROM questions q WHERE q.exam_id = e.id AND q.is_active = TRUE) AS max_score, " +
            "       ea.end_time AS submitted_at " +
            "FROM exam_attempts ea " +
            "JOIN exams e ON ea.exam_id = e.id " +
            "WHERE ea.student_id = ? " +
            "ORDER BY ea.end_time DESC";

        List<ExamAttemptSummary> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int attemptId = rs.getInt("attempt_id");
                    String title = rs.getString("exam_title");
                    int score = rs.getInt("score");
                    int maxScore = rs.getInt("max_score");
                    Timestamp submitted = rs.getTimestamp("submitted_at");

                    if (submitted == null) continue; // Skip unsubmitted attempts

                    double pct = maxScore > 0 ? ((double) score / maxScore) * 100 : 0.0;
                    String status = pct >= 50.0 ? "PASS" : "FAIL";

                    list.add(new ExamAttemptSummary(attemptId, title, score, maxScore, pct, status, submitted));
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getAttemptsByUserId error: " + e.getMessage());
        }
        return list;
    }

    public List<QuestionDetailReport> getAttemptDetails(int attemptId) {
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

        List<QuestionDetailReport> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String qText = rs.getString("question_text");
                    String sText = rs.getString("selected_text");
                    String cText = rs.getString("correct_text");
                    boolean isCorrect = rs.getBoolean("is_correct");

                    list.add(new QuestionDetailReport(qText, sText, cText, isCorrect ? 1 : 0));
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getAttemptDetails error: " + e.getMessage());
        }
        return list;
    }

    public List<String[]> getExportableResults(Integer examId) {
        StringBuilder sql = new StringBuilder(
            "SELECT ea.id AS attempt_id, u.username, u.full_name, e.title AS exam_title, IFNULL(ea.score, 0) AS score, " +
            "       (SELECT COUNT(*) FROM questions q WHERE q.exam_id = e.id AND q.is_active = TRUE) AS max_score, " +
            "       ea.end_time AS submitted_at " +
            "FROM exam_attempts ea " +
            "JOIN exams e ON ea.exam_id = e.id " +
            "JOIN users u ON ea.student_id = u.id " +
            "WHERE ea.end_time IS NOT NULL "
        );

        if (examId != null) {
            sql.append("AND ea.exam_id = ? ");
        }
        sql.append("ORDER BY ea.end_time DESC");

        List<String[]> results = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            if (examId != null) {
                ps.setInt(1, examId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int attemptId = rs.getInt("attempt_id");
                    String username = rs.getString("username");
                    String fullName = rs.getString("full_name");
                    String title = rs.getString("exam_title");
                    int score = rs.getInt("score");
                    int maxScore = rs.getInt("max_score");
                    Timestamp submitted = rs.getTimestamp("submitted_at");

                    double pct = maxScore > 0 ? ((double) score / maxScore) * 100 : 0.0;
                    String pctStr = String.format("%.2f", pct) + "%";

                    results.add(new String[]{
                        String.valueOf(attemptId),
                        username,
                        fullName,
                        title,
                        String.valueOf(score),
                        String.valueOf(maxScore),
                        pctStr,
                        submitted.toString()
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamAttemptDAO.getExportableResults error: " + e.getMessage());
        }
        return results;
    }
}
