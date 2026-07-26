package com.examsystem.dao;

import com.examsystem.db.DatabaseConnector;
import com.examsystem.models.Exam;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** DAO for exams. */
public class ExamDAO {

    public int createExam(String title, int durationMinutes, int createdBy) {
        String sql = "INSERT INTO exams (title, duration_minutes, created_by) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, title);
            ps.setInt(2, durationMinutes);
            ps.setInt(3, createdBy);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamDAO.createExam error: " + e.getMessage());
        }
        return -1;
    }

    public Exam getExamById(int id) {
        String sql = "SELECT id, title, duration_minutes, created_by, created_at FROM exams WHERE id=?";
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Exam(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getInt("duration_minutes"),
                            rs.getInt("created_by"),
                            rs.getTimestamp("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("ExamDAO.getExamById error: " + e.getMessage());
        }
        return null;
    }

    public List<Exam> getAllExams() {
        String sql = "SELECT id, title, duration_minutes, created_by, created_at FROM exams ORDER BY created_at DESC";
        List<Exam> list = new ArrayList<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Exam(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getInt("duration_minutes"),
                        rs.getInt("created_by"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("ExamDAO.getAllExams error: " + e.getMessage());
        }
        return list;
    }
}