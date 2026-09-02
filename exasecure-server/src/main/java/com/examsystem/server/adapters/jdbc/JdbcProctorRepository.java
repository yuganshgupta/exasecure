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
    public void saveEvent(int attemptId, String eventType, Instant timestamp) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO proctor_logs (attempt_id, event_type, recorded_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, attemptId);
            ps.setString(2, eventType);
            ps.setTimestamp(3, Timestamp.from(timestamp));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public void saveEvidence(int attemptId, byte[] screenshot, Instant timestamp) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO proctor_evidence (attempt_id, screenshot, recorded_at) VALUES (?, ?, ?)")) {
            ps.setInt(1, attemptId);
            ps.setBytes(2, screenshot);
            ps.setTimestamp(3, Timestamp.from(timestamp));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public int countEvents(int attemptId, String eventType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM proctor_logs WHERE attempt_id = ? AND event_type = ?")) {
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
