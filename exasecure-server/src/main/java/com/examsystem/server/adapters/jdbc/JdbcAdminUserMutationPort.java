package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.AdminUserMutationPort;
import com.examsystem.domain.models.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcAdminUserMutationPort implements AdminUserMutationPort {
    private final DataSource dataSource;

    public JdbcAdminUserMutationPort(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int createUser(
            String username,
            String encodedPassword,
            String fullName,
            String enrollmentNumber,
            String section,
            User.Role role) {
        String sql = "INSERT INTO users "
                + "(username, password, full_name, enrollment_number, section, role, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindUser(statement, username, encodedPassword, fullName, enrollmentNumber, section, role);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new IllegalStateException("User was created without an ID");
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean updateUser(
            int id,
            String username,
            String encodedPassword,
            String fullName,
            String enrollmentNumber,
            String section,
            User.Role role) {
        String sql = encodedPassword == null
                ? "UPDATE users SET username = ?, full_name = ?, enrollment_number = ?, section = ?, role = ? WHERE id = ?"
                : "UPDATE users SET username = ?, password = ?, full_name = ?, enrollment_number = ?, section = ?, role = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    int index = 1;
                    statement.setString(index++, username);
                    if (encodedPassword != null) {
                        statement.setString(index++, encodedPassword);
                    }
                    statement.setString(index++, fullName);
                    statement.setString(index++, enrollmentNumber);
                    statement.setString(index++, section);
                    statement.setString(index++, role.name());
                    statement.setInt(index, id);
                    updated = statement.executeUpdate();
                }
                if (updated > 0) {
                    deleteSessions(connection, id);
                }
                connection.commit();
                return updated > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean deactivateUser(int id) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET is_active = 0 WHERE id = ? AND is_active = 1")) {
                    statement.setInt(1, id);
                    updated = statement.executeUpdate();
                }
                if (updated > 0) {
                    deleteSessions(connection, id);
                }
                connection.commit();
                return updated > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private static void bindUser(
            PreparedStatement statement,
            String username,
            String encodedPassword,
            String fullName,
            String enrollmentNumber,
            String section,
            User.Role role) throws SQLException {
        statement.setString(1, username);
        statement.setString(2, encodedPassword);
        statement.setString(3, fullName);
        statement.setString(4, enrollmentNumber);
        statement.setString(5, section);
        statement.setString(6, role.name());
    }

    private static void deleteSessions(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM server_sessions WHERE user_id = ?")) {
            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }

    private static RuntimeException translate(SQLException exception) {
        if (exception.getSQLState() != null && exception.getSQLState().startsWith("23")) {
            return new IllegalStateException("Username already exists", exception);
        }
        return new RuntimeException("Database error", exception);
    }
}
