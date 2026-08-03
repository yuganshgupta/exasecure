package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.mindrot.jbcrypt.BCrypt;

public class UserDAO {

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setFullName(rs.getString("full_name"));
        u.setEnrollmentNumber(rs.getString("enrollment_number")); // New
        u.setSection(rs.getString("section"));                   // New
        u.setRole(rs.getString("role"));
        return u;
    }

    public User findByUsernameAndPassword(String username, String password) {
        String sql = "SELECT * FROM users WHERE username=? AND password=? AND is_active=TRUE LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password); 
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("UserDAO.findByUsernameAndPassword error: " + e.getMessage());
        }
        return null;
    }

    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username=? AND is_active=TRUE LIMIT 1";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("UserDAO.findByUsername error: " + e.getMessage());
        }
        return null;
    }

    public User findById(int id) {
        String sql = "SELECT * FROM users WHERE id=? AND is_active=TRUE";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowToUser(rs);
            }
        } catch (SQLException e) {
            System.err.println("UserDAO.findById error: " + e.getMessage());
        }
        return null;
    }

    // --- UPDATED: Register with new fields ---
    public int registerUser(String username, String password, String fullName, String enrollment, String section, String role) {
        String sql = "INSERT INTO users (username, password, full_name, enrollment_number, section, role) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, username);
            ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            ps.setString(3, fullName);
            ps.setString(4, enrollment);
            ps.setString(5, section);
            ps.setString(6, role);
            
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLIntegrityConstraintViolationException dup) {
            System.err.println("UserDAO.registerUser duplicate: " + username);
        } catch (SQLException e) {
            System.err.println("UserDAO.registerUser error: " + e.getMessage());
        }
        return -1;
    }

    // --- UPDATED: Update with new fields ---
    public boolean updateUser(int id, String username, String password, String fullName, String enrollment, String section, String role) {
        StringBuilder sql = new StringBuilder("UPDATE users SET username=?, full_name=?, enrollment_number=?, section=?, role=?");
        if (!password.isEmpty()) {
            sql.append(", password=?");
        }
        sql.append(" WHERE id=?");

        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, enrollment);
            ps.setString(4, section);
            ps.setString(5, role);

            int paramIndex = 6;
            if (!password.isEmpty()) {
                ps.setString(paramIndex++, BCrypt.hashpw(password, BCrypt.gensalt()));
            }
            ps.setInt(paramIndex, id);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO.updateUser error: " + e.getMessage());
            return false;
        }
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE is_active=TRUE ORDER BY id ASC";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRowToUser(rs));
        } catch (SQLException e) {
            System.err.println("UserDAO.findAll error: " + e.getMessage());
        }
        return list;
    }

    public boolean softDeleteUser(int id) {
        String sql = "UPDATE users SET is_active = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO.softDeleteUser error: " + e.getMessage());
        }
        return false;
    }
}