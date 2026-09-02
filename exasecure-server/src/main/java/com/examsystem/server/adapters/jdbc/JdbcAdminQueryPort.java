package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.AdminQueryPort;
import com.examsystem.application.query.AdminAttemptDetailResult;
import com.examsystem.application.query.AdminAttemptResult;
import com.examsystem.application.query.AdminEvidenceResult;
import com.examsystem.application.query.AdminExamResult;
import com.examsystem.application.query.AdminExamStatisticsResult;
import com.examsystem.application.query.AdminProctorEventResult;
import com.examsystem.application.query.AdminUserResult;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.*;

public class JdbcAdminQueryPort implements AdminQueryPort {
    private final DataSource dataSource;

    public JdbcAdminQueryPort(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<AdminUserResult> listUsers() {
        List<AdminUserResult> users = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, full_name, role, is_active FROM users");
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, full_name, role, is_active, enrollment_number, section FROM users ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new AdminUserResult(rs.getInt("id"), rs.getString("username"), rs.getString("full_name"), rs.getString("role"), rs.getBoolean("is_active")));
                users.add(new AdminUserResult(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getBoolean("is_active"),
                        rs.getString("enrollment_number"),
                        rs.getString("section")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    @Override
    public List<AdminExamResult> listExams() {
        List<AdminExamResult> exams = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, title, duration_minutes, created_by, is_active, "
                             + "version_number, supersedes_exam_id FROM exams");
                             + "version_number, supersedes_exam_id FROM exams ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                exams.add(new AdminExamResult(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getInt("duration_minutes"),
                        rs.getInt("created_by"),
                        rs.getBoolean("is_active"),
                        rs.getInt("version_number"),
                        (Integer) rs.getObject("supersedes_exam_id")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return exams;
    }

    @Override
    public List<AdminAttemptResult> getAttemptHistory(int examId) {
        List<AdminAttemptResult> attempts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT a.id, a.student_id, u.full_name, a.start_time, a.status, a.score FROM exam_attempts a JOIN users u ON a.student_id = u.id WHERE a.exam_id = ? ORDER BY a.start_time DESC")) {
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT a.id, a.student_id, u.full_name, a.start_time, a.status, a.score "
                             + "FROM exam_attempts a JOIN users u ON a.student_id = u.id "
                             + "WHERE a.exam_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    attempts.add(new AdminAttemptResult(rs.getInt("id"), rs.getInt("student_id"), rs.getString("full_name"), rs.getTimestamp("start_time").toInstant(), rs.getString("status"), (Integer) rs.getObject("score")));
                    attempts.add(new AdminAttemptResult(
                            rs.getInt("id"),
                            rs.getInt("student_id"),
                            rs.getString("full_name"),
                            rs.getTimestamp("start_time").toInstant(),
                            rs.getString("status"),
                            (Integer) rs.getObject("score")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return attempts;
    }

    @Override
    public List<AdminAttemptDetailResult> getAttemptDetails(int attemptId) {
        List<AdminAttemptDetailResult> details = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            int examId = -1;
            try (PreparedStatement ps = conn.prepareStatement("SELECT exam_id FROM exam_attempts WHERE id = ?")) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        examId = rs.getInt("exam_id");
                    }
                }
            }
            if (examId == -1) return details;

            // Load student answers map: question_id -> list of selected option numbers
            Map<Integer, List<Integer>> selectedMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.question_id, o.selected_option, a.selected_options "
                            + "FROM student_answers a "
                            + "LEFT JOIN student_answer_options o ON a.id = o.answer_id "
                            + "WHERE a.attempt_id = ?")) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int qId = rs.getInt("question_id");
                        int opt = rs.getInt("selected_option");
                        if (!rs.wasNull() && opt > 0) {
                            selectedMap.computeIfAbsent(qId, k -> new ArrayList<>()).add(opt);
                        } else {
                            String csv = rs.getString("selected_options");
                            if (csv != null && !csv.isBlank()) {
                                for (String part : csv.split(",")) {
                                    try {
                                        int parsed = Integer.parseInt(part.trim());
                                        if (parsed > 0) {
                                            selectedMap.computeIfAbsent(qId, k -> new ArrayList<>()).add(parsed);
                                        }
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                }
            }

            // Load questions
            record QData(int id, String text, String type, int marks) {}
            List<QData> questions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, question_text, question_type FROM questions WHERE exam_id = ? AND is_active = 1 ORDER BY id ASC")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        questions.add(new QData(
                                rs.getInt("id"),
                                rs.getString("question_text"),
                                rs.getString("question_type"),
                                1));
                    }
                }
            }

            int qNum = 1;
            for (QData q : questions) {
                record OptData(int num, String text, boolean correct) {}
                List<OptData> options = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT option_number, option_text, is_correct FROM options WHERE question_id = ? ORDER BY option_number ASC")) {
                    ps.setInt(1, q.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            options.add(new OptData(
                                    rs.getInt("option_number"),
                                    rs.getString("option_text"),
                                    rs.getBoolean("is_correct")));
                        }
                    }
                }

                List<Integer> selectedNums = selectedMap.getOrDefault(q.id(), List.of());
                List<String> selectedTexts = new ArrayList<>();
                for (int sNum : selectedNums) {
                    for (OptData opt : options) {
                        if (opt.num() == sNum) {
                            selectedTexts.add(opt.text());
                            break;
                        }
                    }
                }

                List<Integer> correctNums = new ArrayList<>();
                List<String> correctTexts = new ArrayList<>();
                for (OptData opt : options) {
                    if (opt.correct()) {
                        correctNums.add(opt.num());
                        correctTexts.add(opt.text());
                    }
                }

                Set<Integer> selSet = new HashSet<>(selectedNums);
                Set<Integer> corSet = new HashSet<>(correctNums);
                boolean isCorrect = !corSet.isEmpty() && selSet.equals(corSet);
                int marksAwarded = isCorrect ? q.marks() : 0;

                details.add(new AdminAttemptDetailResult(
                        q.id(),
                        qNum++,
                        q.text(),
                        q.type(),
                        q.marks(),
                        selectedNums,
                        selectedTexts,
                        correctNums,
                        correctTexts,
                        isCorrect,
                        marksAwarded));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return details;
    }

    @Override
    public Optional<AdminExamStatisticsResult> getExamStatistics(int examId) {
        try (Connection conn = dataSource.getConnection()) {
            String examTitle = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT title FROM exams WHERE id = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        examTitle = rs.getString("title");
                    }
                }
            }
            if (examTitle == null) return Optional.empty();

            List<Integer> completedScores = new ArrayList<>();
            int totalAttempts = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT score, status FROM exam_attempts WHERE exam_id = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        totalAttempts++;
                        int sc = rs.getInt("score");
                        boolean wasNull = rs.wasNull();
                        String st = rs.getString("status");
                        if (!wasNull && ("SUBMITTED".equalsIgnoreCase(st) || "EXPIRED".equalsIgnoreCase(st))) {
                            completedScores.add(sc);
                        }
                    }
                }
            }

            int completedCount = completedScores.size();
            double avgScore = 0.0;
            int highest = 0;
            int lowest = 0;
            double passRate = 0.0;
            Map<String, Integer> distribution = new LinkedHashMap<>();

            if (completedCount > 0) {
                int sum = 0;
                highest = completedScores.get(0);
                lowest = completedScores.get(0);
                int passCount = 0;

                int b0 = 0, b1 = 0, b2 = 0, b3plus = 0;
                for (int sc : completedScores) {
                    sum += sc;
                    if (sc > highest) highest = sc;
                    if (sc < lowest) lowest = sc;
                    if (sc > 0) passCount++;

                    if (sc == 0) b0++;
                    else if (sc == 1) b1++;
                    else if (sc == 2) b2++;
                    else b3plus++;
                }

                avgScore = Math.round(((double) sum / completedCount) * 10.0) / 10.0;
                passRate = Math.round(((double) passCount / completedCount) * 1000.0) / 10.0;
                distribution.put("0 pts", b0);
                distribution.put("1 pt", b1);
                distribution.put("2 pts", b2);
                distribution.put("3+ pts", b3plus);
            }

            return Optional.of(new AdminExamStatisticsResult(
                    examId,
                    examTitle,
                    totalAttempts,
                    completedCount,
                    avgScore,
                    highest,
                    lowest,
                    passRate,
                    distribution));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AdminProctorEventResult> getProctorEvents(int attemptId) {
        List<AdminProctorEventResult> events = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT e.id, e.event_type, e.recorded_at, (v.id IS NOT NULL) as has_evidence FROM proctor_events e LEFT JOIN proctor_evidence_v2 v ON e.id = v.event_id WHERE e.attempt_id = ? ORDER BY e.recorded_at ASC")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new AdminProctorEventResult(rs.getInt("id"), rs.getString("event_type"), rs.getTimestamp("recorded_at").toInstant(), rs.getBoolean("has_evidence")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return events;
    }

    @Override
    public Optional<AdminEvidenceResult> getEvidenceByEventId(int eventId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, content_type, screenshot_data FROM proctor_evidence_v2 WHERE event_id = ?")) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AdminEvidenceResult(rs.getInt("id"), eventId, rs.getString("content_type"), rs.getBytes("screenshot_data")));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
