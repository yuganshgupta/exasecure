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
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT e.title, a.id, a.start_time, a.end_time, a.score, a.status, "
                             + "a.focus_lost_count, a.is_flagged, a.proctoring_status "
                             + "FROM exam_attempts a JOIN exams e ON a.exam_id = e.id "
                             + "WHERE a.student_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean isFlagged = rs.getBoolean("is_flagged") || rs.getInt("focus_lost_count") >= 3;
                    history.add(new StudentAttemptHistoryResult(
                            rs.getString("title"),
                            rs.getInt("id"),
                            rs.getTimestamp("start_time").toInstant(),
                            rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
                            (Integer) rs.getObject("score"),
                            rs.getString("status"),
                            isFlagged));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return history;
    }

    @Override
    public List<StudentExamResult> listAvailableExams(int studentId) {
        List<StudentExamResult> exams = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT e.id, e.title, e.duration_minutes, e.is_active
                     FROM exams e
                     WHERE e.is_active = 1 OR (
                         SELECT a.status FROM exam_attempts a
                         WHERE a.exam_id = e.id AND a.student_id = ?
                         ORDER BY a.id DESC LIMIT 1
                     ) = 'ACTIVE'
                     ORDER BY e.id
                     """)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    exams.add(new StudentExamResult(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getInt("duration_minutes"),
                            !rs.getBoolean("is_active")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return exams;
    }
}
