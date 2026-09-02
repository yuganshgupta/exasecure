package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.AdminQueryPort;
import com.examsystem.contracts.admin.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAdminQueryPort implements AdminQueryPort {
    private final DataSource dataSource;

    public JdbcAdminQueryPort(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<AdminUserDto> listUsers() {
        List<AdminUserDto> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, full_name, role, is_active FROM users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new AdminUserDto(rs.getInt("id"), rs.getString("username"), rs.getString("full_name"), rs.getString("role"), rs.getBoolean("is_active")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    @Override
    public List<AdminExamDto> listExams() {
        List<AdminExamDto> exams = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, title, duration_minutes, created_by, is_active FROM exams");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                exams.add(new AdminExamDto(rs.getInt("id"), rs.getString("title"), rs.getInt("duration_minutes"), rs.getInt("created_by"), rs.getBoolean("is_active")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return exams;
    }

    @Override
    public List<AdminAttemptDto> getAttemptHistory(int examId) {
        List<AdminAttemptDto> attempts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT a.id, a.student_id, u.full_name, a.start_time, a.status, a.score FROM exam_attempts a JOIN users u ON a.student_id = u.id WHERE a.exam_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    attempts.add(new AdminAttemptDto(rs.getInt("id"), rs.getInt("student_id"), rs.getString("full_name"), rs.getTimestamp("start_time").toInstant().toString(), rs.getString("status"), (Integer) rs.getObject("score")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return attempts;
    }

    @Override
    public List<AdminProctorEventDto> getProctorEvents(int attemptId) {
        List<AdminProctorEventDto> events = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT e.id, e.event_type, e.recorded_at, (v.id IS NOT NULL) as has_evidence FROM proctor_events e LEFT JOIN proctor_evidence_v2 v ON e.id = v.event_id WHERE e.attempt_id = ? ORDER BY e.recorded_at ASC")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new AdminProctorEventDto(rs.getInt("id"), rs.getString("event_type"), rs.getTimestamp("recorded_at").toInstant().toString(), rs.getBoolean("has_evidence")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return events;
    }

    @Override
    public Optional<AdminEvidenceDto> getEvidenceByEventId(int eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, content_type, screenshot_data FROM proctor_evidence_v2 WHERE event_id = ?")) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AdminEvidenceDto(rs.getInt("id"), eventId, rs.getString("content_type"), rs.getBytes("screenshot_data")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
