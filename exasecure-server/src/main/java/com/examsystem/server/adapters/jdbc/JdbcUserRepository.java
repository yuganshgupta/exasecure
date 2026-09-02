package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.UserRepository;
import com.examsystem.domain.models.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
    private final DataSource dataSource;

    public JdbcUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, full_name, enrollment_number, section, role FROM users WHERE username = ? AND is_active = 1")) {
            ps.setString(1, username);
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
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getPasswordHash(String username) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username = ? AND is_active = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("password"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }
}
