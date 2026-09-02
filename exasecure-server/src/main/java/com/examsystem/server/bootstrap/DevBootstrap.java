package com.examsystem.server.bootstrap;

import com.examsystem.server.config.AppConfig;
import com.examsystem.server.db.DatabaseMigrator;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DevBootstrap {

    public static void run(AppConfig config) {
        System.out.println("[bootstrap] connecting to database");
        DataSource ds = config.createDataSource();

        try {
            // 1. Check/migrate schema
            System.out.println("[bootstrap] checking schema");
            new DatabaseMigrator(ds).migrate();

            try (Connection conn = ds.getConnection()) {
                // 2. Ensuring admin
                System.out.println("[bootstrap] ensuring admin");
                String adminUser = "admin";
                String adminPass = System.getenv().getOrDefault("DEV_ADMIN_PASSWORD", "Admin@123");
                String adminHash = BCrypt.hashpw(adminPass, BCrypt.gensalt(10));

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password, full_name, role, is_active) " +
                                "VALUES (?, ?, 'System Administrator', 'ADMIN', 1) " +
                                "ON DUPLICATE KEY UPDATE password = VALUES(password), role = 'ADMIN', is_active = 1")) {
                    ps.setString(1, adminUser);
                    ps.setString(2, adminHash);
                    ps.executeUpdate();
                }

                int adminId = 1;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                    ps.setString(1, adminUser);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            adminId = rs.getInt(1);
                        }
                    }
                }

                // 3. Ensuring student
                System.out.println("[bootstrap] ensuring student");
                String studentUser = "student1";
                String studentPass = System.getenv().getOrDefault("DEV_STUDENT_PASSWORD", "Student@123");
                String studentHash = BCrypt.hashpw(studentPass, BCrypt.gensalt(10));

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password, full_name, enrollment_number, section, role, is_active) " +
                                "VALUES (?, ?, 'Jane Student', 'ENR-2026-001', 'A', 'STUDENT', 1) " +
                                "ON DUPLICATE KEY UPDATE password = VALUES(password), role = 'STUDENT', is_active = 1")) {
                    ps.setString(1, studentUser);
                    ps.setString(2, studentHash);
                    ps.executeUpdate();
                }

                // 4. Heal legacy single-choice questions without is_correct flags
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("UPDATE `options` o " +
                            "JOIN questions q ON q.id = o.question_id AND q.correct_option_number = o.option_number " +
                            "SET o.is_correct = 1 " +
                            "WHERE q.question_type = 'SINGLE' AND o.is_correct = 0");
                }

                // 5. Ensuring demo exam
                System.out.println("[bootstrap] ensuring demo exam");
                String sampleTitle = "Computer Science Fundamentals Exam";
                Integer existingExamId = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM exams WHERE title = ?")) {
                    ps.setString(1, sampleTitle);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingExamId = rs.getInt(1);
                        }
                    }
                }

                if (existingExamId != null) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE exams SET is_active = 1 WHERE id = ?")) {
                        ps.setInt(1, existingExamId);
                        ps.executeUpdate();
                    }
                } else {
                    int examId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO exams (title, duration_minutes, created_by, is_active, version_number) " +
                                    "VALUES (?, 30, ?, 1, 1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, sampleTitle);
                        ps.setInt(2, adminId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                examId = keys.getInt(1);
                            } else {
                                examId = 1;
                            }
                        }
                    }

                    // Question 1: SINGLE
                    int q1Id;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO questions (exam_id, question_text, correct_option_number, question_type, is_active) " +
                                    "VALUES (?, 'What is the primary characteristic of an immutable data structure?', 1, 'SINGLE', 1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, examId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            q1Id = keys.getInt(1);
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO options (question_id, option_number, option_text, is_correct, is_active) VALUES (?, ?, ?, ?, 1)")) {
                        ps.setInt(1, q1Id);
                        ps.setInt(2, 1);
                        ps.setString(3, "Its state cannot be modified after creation");
                        ps.setInt(4, 1);
                        ps.addBatch();

                        ps.setInt(1, q1Id);
                        ps.setInt(2, 2);
                        ps.setString(3, "It requires synchronization on all read accesses");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.setInt(1, q1Id);
                        ps.setInt(2, 3);
                        ps.setString(3, "It can only be instantiated once per process");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.setInt(1, q1Id);
                        ps.setInt(2, 4);
                        ps.setString(3, "It is garbage collected immediately upon function return");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.executeBatch();
                    }

                    // Question 2: MULTI
                    int q2Id;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO questions (exam_id, question_text, correct_option_number, question_type, is_active) " +
                                    "VALUES (?, 'Which of the following are ACID properties in relational databases?', 1, 'MULTI', 1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, examId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            q2Id = keys.getInt(1);
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO options (question_id, option_number, option_text, is_correct, is_active) VALUES (?, ?, ?, ?, 1)")) {
                        ps.setInt(1, q2Id);
                        ps.setInt(2, 1);
                        ps.setString(3, "Atomicity");
                        ps.setInt(4, 1);
                        ps.addBatch();

                        ps.setInt(1, q2Id);
                        ps.setInt(2, 2);
                        ps.setString(3, "Consistency");
                        ps.setInt(4, 1);
                        ps.addBatch();

                        ps.setInt(1, q2Id);
                        ps.setInt(2, 3);
                        ps.setString(3, "Isolation");
                        ps.setInt(4, 1);
                        ps.addBatch();

                        ps.setInt(1, q2Id);
                        ps.setInt(2, 4);
                        ps.setString(3, "Indexing");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.executeBatch();
                    }

                    // Question 3: SINGLE
                    int q3Id;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO questions (exam_id, question_text, correct_option_number, question_type, is_active) " +
                                    "VALUES (?, 'Which HTTP status code indicates unauthorized client authentication is required?', 1, 'SINGLE', 1)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, examId);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            q3Id = keys.getInt(1);
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO options (question_id, option_number, option_text, is_correct, is_active) VALUES (?, ?, ?, ?, 1)")) {
                        ps.setInt(1, q3Id);
                        ps.setInt(2, 1);
                        ps.setString(3, "401 Unauthorized");
                        ps.setInt(4, 1);
                        ps.addBatch();

                        ps.setInt(1, q3Id);
                        ps.setInt(2, 2);
                        ps.setString(3, "403 Forbidden");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.setInt(1, q3Id);
                        ps.setInt(2, 3);
                        ps.setString(3, "404 Not Found");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.setInt(1, q3Id);
                        ps.setInt(2, 4);
                        ps.setString(3, "500 Internal Server Error");
                        ps.setInt(4, 0);
                        ps.addBatch();

                        ps.executeBatch();
                    }
                }

                System.out.println("[bootstrap] complete");
            }
        } catch (Exception e) {
            System.err.println("[bootstrap] failed: " + e.getMessage());
            throw new RuntimeException("Bootstrap failed: " + e.getMessage(), e);
        } finally {
            if (ds instanceof com.zaxxer.hikari.HikariDataSource hds) {
                hds.close();
            }
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void main(String[] args) {
        run(new AppConfig());
    }
}
