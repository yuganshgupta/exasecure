package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ExamRepository;
import com.examsystem.domain.models.Exam;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Optional;

public class JdbcExamRepository implements ExamRepository {
    private final DataSource dataSource;

    public JdbcExamRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Exam> findById(int id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, title, duration_minutes, created_by, is_active FROM exams WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Exam(
                            rs.getInt("id"),
                            rs.getString("title"),
                            Duration.ofMinutes(rs.getInt("duration_minutes")),
                            rs.getInt("created_by"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DB error", e);
        }
        return Optional.empty();
    }
}
