package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.StudentQueryPort;
import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.application.query.StudentExamResult;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class JdbcStudentQueryPort implements StudentQueryPort {
    private final DataSource dataSource;

    public JdbcStudentQueryPort(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<StudentAttemptHistoryResult> listHistory(int studentId) {
        List<StudentAttemptHistoryResult> history = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT e.title, a.id, a.start_time, a.end_time, a.score, a.status FROM exam_attempts a JOIN exams e ON a.exam_id = e.id WHERE a.student_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new StudentAttemptHistoryResult(
                            rs.getString("title"),
                            rs.getInt("id"),
                            rs.getTimestamp("start_time").toInstant(),
                            rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
                            (Integer) rs.getObject("score"),
                            rs.getString("status")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return history;
    }

    @Override
    public List<StudentExamResult> listAvailableExams() {
        List<StudentExamResult> exams = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, title, duration_minutes FROM exams WHERE is_active = 1 ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exams.add(new StudentExamResult(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getInt("duration_minutes")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return exams;
    }
}
