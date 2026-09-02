package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.ExamRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.application.ports.UserRepository;
import com.examsystem.server.adapters.jdbc.JdbcExamAttemptRepository;
import com.examsystem.server.adapters.jdbc.JdbcProctorRepository;
import com.examsystem.domain.models.Exam;
import com.examsystem.domain.models.User;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import java.util.Optional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class AdminController {
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final javax.sql.DataSource dataSource;

    public AdminController(ExamRepository examRepository, QuestionRepository questionRepository, UserRepository userRepository, javax.sql.DataSource dataSource) {
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.dataSource = dataSource;
    }

    public void listExams(Context ctx) {
        List<Exam> exams = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM exams");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                exams.add(new Exam(rs.getInt("id"), rs.getString("title"), java.time.Duration.ofMinutes(rs.getInt("duration_minutes")), rs.getInt("created_by"), rs.getBoolean("is_active")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ctx.json(exams);
    }

    public void listUsers(Context ctx) {
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, full_name, role FROM users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> u = new HashMap<>();
                u.put("id", rs.getInt("id"));
                u.put("username", rs.getString("username"));
                u.put("full_name", rs.getString("full_name"));
                u.put("role", rs.getString("role"));
                users.add(u);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ctx.json(users);
    }

    public void getAttemptEvidence(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT screenshot FROM proctor_evidence WHERE attempt_id = ? ORDER BY recorded_at DESC LIMIT 1")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] data = rs.getBytes(1);
                    ctx.contentType("image/jpeg").result(data);
                } else {
                    throw new NotFoundResponse("No evidence found");
                }
            }
        } catch (Exception e) {
            if (e instanceof NotFoundResponse) throw (NotFoundResponse) e;
            throw new RuntimeException(e);
        }
    }
}
