package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ProctorRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

public class JdbcProctorRepository implements ProctorRepository {
    private final DataSource dataSource;

    public JdbcProctorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int saveEvent(int attemptId, String eventType, Instant timestamp) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO proctor_events (attempt_id, event_type, recorded_at) VALUES (?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, attemptId);
            ps.setString(2, eventType);
            ps.setTimestamp(3, Timestamp.from(timestamp));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
            throw new RuntimeException("No ID generated for proctor_events");
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, eventId);
            ps.setString(2, contentType);
            ps.setInt(3, screenshot.length);
            ps.setBytes(4, screenshot);
            ps.setTimestamp(5, Timestamp.from(timestamp));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public boolean eventBelongsToAttempt(int eventId, int attemptId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM proctor_events WHERE id = ? AND attempt_id = ?")) {
            ps.setInt(1, eventId);
            ps.setInt(2, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public int countEvents(int attemptId, String eventType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM proctor_events WHERE attempt_id = ? AND event_type = ?")) {
            ps.setInt(1, attemptId);
            ps.setString(2, eventType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return 0;
    }
}
