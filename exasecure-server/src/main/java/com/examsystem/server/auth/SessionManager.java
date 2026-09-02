package com.examsystem.server.auth;

import com.examsystem.domain.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private final DataSource dataSource;

    public SessionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO server_sessions (token, user_id, expires_at) VALUES (?, ?, ?)")) {
            ps.setString(1, token);
            ps.setInt(2, user.id());
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();
            return token;
        } catch (Exception e) {
            log.error("Failed to create session", e);
            throw new RuntimeException("Could not create session");
        }
    }

    public Optional<User> authenticate(String token) {
        if (token == null || token.isEmpty()) return Optional.empty();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT u.id, u.username, u.full_name, u.enrollment_number, u.section, u.role " +
                             "FROM users u " +
                             "JOIN server_sessions s ON u.id = s.user_id " +
                             "WHERE s.token = ? AND s.expires_at > ? AND u.is_active = 1")) {
            ps.setString(1, token);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("enrollment_number"),
                            rs.getString("section"),
                            User.Role.fromString(rs.getString("role"))
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to authenticate session", e);
        }
        return Optional.empty();
    }

    public void invalidate(String token) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM server_sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to invalidate session", e);
        }
    }
}
