package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ProctorRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public class JdbcProctorRepository implements ProctorRepository {
    private final DataSource dataSource;

    public JdbcProctorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int saveEvent(int attemptId, String eventType, Instant timestamp) {
        return saveEvent(attemptId, eventType, timestamp, null);
    }

    @Override
    public int saveEvent(int attemptId, String eventType, Instant timestamp, String clientEventId) {
        if (clientEventId != null && !clientEventId.isBlank()) {
            var existing = findEventByClientEventId(attemptId, clientEventId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO proctor_events (attempt_id, event_type, recorded_at, client_event_id) VALUES (?, ?, ?, ?)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, attemptId);
            ps.setString(2, eventType);
            ps.setTimestamp(3, Timestamp.from(timestamp));
            ps.setString(4, clientEventId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
            throw new RuntimeException("No ID generated for proctor_events");
        } catch (Exception e) {
            if (clientEventId != null && !clientEventId.isBlank()) {
                var existing = findEventByClientEventId(attemptId, clientEventId);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            // Fallback for older schemas where client_event_id column might not exist
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO proctor_events (attempt_id, event_type, recorded_at) VALUES (?, ?, ?)",
                         java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, attemptId);
                ps.setString(2, eventType);
                ps.setTimestamp(3, Timestamp.from(timestamp));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (Exception ex) {
                throw new RuntimeException("DB error", ex);
            }
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public Optional<Integer> findEventByClientEventId(int attemptId, String clientEventId) {
        if (clientEventId == null || clientEventId.isBlank()) {
            return Optional.empty();
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM proctor_events WHERE attempt_id = ? AND client_event_id = ?")) {
            ps.setInt(1, attemptId);
            ps.setString(2, clientEventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("id"));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    @Override
    public void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp) {
        saveEvidence(eventId, screenshot, contentType, timestamp, "INITIAL");
    }

    @Override
    public void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp, String evidenceType) {
        String effectiveType = (evidenceType == null || evidenceType.isBlank())
                ? "INITIAL"
                : evidenceType.trim().toUpperCase(Locale.ROOT);

        try (Connection conn = dataSource.getConnection()) {
            // Immutable: check if evidence of this type already exists for this event
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT id FROM proctor_evidence_v2 WHERE event_id = ? AND evidence_type = ?")) {
                check.setInt(1, eventId);
                check.setString(2, effectiveType);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return; // Preserve first accepted artifact permanently
                    }
                }
            } catch (Exception ignored) {
                // Column evidence_type might not exist in old fallback schema
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at, evidence_type) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, eventId);
                ps.setString(2, contentType);
                ps.setInt(3, screenshot.length);
                ps.setBytes(4, screenshot);
                ps.setTimestamp(5, Timestamp.from(timestamp));
                ps.setString(6, effectiveType);
                ps.executeUpdate();
            } catch (Exception e) {
                // Check if failed due to unique index race
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT id FROM proctor_evidence_v2 WHERE event_id = ? AND evidence_type = ?")) {
                    check.setInt(1, eventId);
                    check.setString(2, effectiveType);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) {
                            return; // Race: already inserted, preserve original
                        }
                    }
                } catch (Exception ignored) {}

                // Fallback for older schemas where evidence_type column might not exist
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, eventId);
                    ps.setString(2, contentType);
                    ps.setInt(3, screenshot.length);
                    ps.setBytes(4, screenshot);
                    ps.setTimestamp(5, Timestamp.from(timestamp));
                    ps.executeUpdate();
                } catch (Exception ex) {
                    throw new RuntimeException("DB error saving evidence", ex);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
    }

    @Override
    public void recordReturn(int eventId, Instant returnedAt, long awayDurationMs) {
        boolean isLong = awayDurationMs >= 5000;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE proctor_events SET returned_at = ?, away_duration_ms = ?, "
                             + "is_long_absence = CASE WHEN is_long_absence = 1 THEN 1 ELSE ? END WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.from(returnedAt));
            ps.setLong(2, awayDurationMs);
            ps.setInt(3, isLong ? 1 : 0);
            ps.setInt(4, eventId);
            ps.executeUpdate();
        } catch (Exception e) {
            // Fallback for older schemas
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE proctor_events SET returned_at = ? WHERE id = ?")) {
                ps.setTimestamp(1, Timestamp.from(returnedAt));
                ps.setInt(2, eventId);
                ps.executeUpdate();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void markLongAbsence(int eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE proctor_events SET is_long_absence = 1 WHERE id = ?")) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    @Override
    public Optional<ProctorEventRecord> findEventById(int eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, attempt_id, event_type, recorded_at, returned_at, away_duration_ms, is_long_absence FROM proctor_events WHERE id = ?")) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ret = rs.getTimestamp("returned_at");
                    long dur = rs.getLong("away_duration_ms");
                    Long awayDuration = rs.wasNull() ? null : dur;
                    return Optional.of(new ProctorEventRecord(
                            rs.getInt("id"),
                            rs.getInt("attempt_id"),
                            rs.getString("event_type"),
                            rs.getTimestamp("recorded_at").toInstant(),
                            ret != null ? ret.toInstant() : null,
                            awayDuration,
                            rs.getBoolean("is_long_absence")));
                }
            }
        } catch (Exception e) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, attempt_id, event_type, recorded_at FROM proctor_events WHERE id = ?")) {
                ps.setInt(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new ProctorEventRecord(
                                rs.getInt("id"),
                                rs.getInt("attempt_id"),
                                rs.getString("event_type"),
                                rs.getTimestamp("recorded_at").toInstant(),
                                null, null, false));
                    }
                }
            } catch (Exception ignored) {}
        }
        return Optional.empty();
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
