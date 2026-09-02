package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.StudentQueryPort;
import com.examsystem.contracts.StudentAttemptHistoryDto;
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
    public List<StudentAttemptHistoryDto> listHistory(int studentId) {
        List<StudentAttemptHistoryDto> history = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT e.title, a.id, a.start_time, a.end_time, a.score, a.status FROM exam_attempts a JOIN exams e ON a.exam_id = e.id WHERE a.student_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new StudentAttemptHistoryDto(rs.getString("title"), rs.getInt("id"), rs.getTimestamp("start_time").toInstant().toString(), rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant().toString() : null, (Integer) rs.getObject("score"), rs.getString("status")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return history;
    }
}
