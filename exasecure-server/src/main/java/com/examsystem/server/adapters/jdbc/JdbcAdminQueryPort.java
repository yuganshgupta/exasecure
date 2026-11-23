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
import java.sql.Timestamp;
import java.time.Instant;
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
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, full_name, role, is_active, enrollment_number, section FROM users ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
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
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT a.id, a.student_id, u.full_name, u.enrollment_number, a.start_time, a.status, a.score, "
                             + "a.focus_lost_count, a.is_flagged, a.proctoring_status "
                             + "FROM exam_attempts a JOIN users u ON a.student_id = u.id "
                             + "WHERE a.exam_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int focusLost = rs.getInt("focus_lost_count");
                    boolean isFlagged = rs.getBoolean("is_flagged") || focusLost >= 3;
                    String proctorStatus = isFlagged ? "FLAGGED" : rs.getString("proctoring_status");
                    if (proctorStatus == null || proctorStatus.isBlank()) {
                        proctorStatus = isFlagged ? "FLAGGED" : "CLEAN";
                    }

                    attempts.add(new AdminAttemptResult(
                            rs.getInt("id"),
                            rs.getInt("student_id"),
                            rs.getString("full_name"),
                            rs.getString("enrollment_number"),
                            rs.getTimestamp("start_time").toInstant(),
                            rs.getString("status"),
                            (Integer) rs.getObject("score"),
                            focusLost,
                            isFlagged,
                            proctorStatus));
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

            Map<Integer, List<Integer>> selectedMap = JdbcAnswerRepository.readSelectedOptions(conn, attemptId);

            // 2. Load questions AND their options in a single ordered JOIN query (eliminates N+1)
            record OptData(int num, String text, boolean correct) {}
            class QData {
                final int id;
                final String text;
                final String type;
                final int marks = 1;
                final List<OptData> options = new ArrayList<>();

                QData(int id, String text, String type) {
                    this.id = id;
                    this.text = text;
                    this.type = type;
                }
            }

            Map<Integer, QData> qMap = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT q.id AS q_id, q.question_text, q.question_type, "
                            + "o.option_number, o.option_text, o.is_correct "
                            + "FROM questions q "
                            + "LEFT JOIN options o ON q.id = o.question_id "
                            + "WHERE q.exam_id = ? AND q.is_active = 1 "
                            + "ORDER BY q.id ASC, o.option_number ASC")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int qId = rs.getInt("q_id");
                        QData qData = qMap.computeIfAbsent(qId, k -> {
                            try {
                                return new QData(qId, rs.getString("question_text"), rs.getString("question_type"));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                        int optNum = rs.getInt("option_number");
                        if (!rs.wasNull() && optNum > 0) {
                            qData.options.add(new OptData(
                                    optNum,
                                    rs.getString("option_text"),
                                    rs.getBoolean("is_correct")));
                        }
                    }
                }
            }

            int qNum = 1;
            for (QData q : qMap.values()) {
                List<Integer> selectedNums = selectedMap.getOrDefault(q.id, List.of());
                List<String> selectedTexts = new ArrayList<>();
                for (int sNum : selectedNums) {
                    for (OptData opt : q.options) {
                        if (opt.num() == sNum) {
                            selectedTexts.add(opt.text());
                            break;
                        }
                    }
                }

                List<Integer> correctNums = new ArrayList<>();
                List<String> correctTexts = new ArrayList<>();
                for (OptData opt : q.options) {
                    if (opt.correct()) {
                        correctNums.add(opt.num());
                        correctTexts.add(opt.text());
                    }
                }

                Set<Integer> selSet = new HashSet<>(selectedNums);
                Set<Integer> corSet = new HashSet<>(correctNums);
                boolean isCorrect = !corSet.isEmpty() && selSet.equals(corSet);
                int marksAwarded = isCorrect ? q.marks : 0;

                details.add(new AdminAttemptDetailResult(
                        q.id,
                        qNum++,
                        q.text,
                        q.type,
                        q.marks,
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

            int totalAttempts = 0;
            int submittedAttempts = 0;
            int activeAttempts = 0;
            int expiredAttempts = 0;
            List<Integer> scoredScores = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement("SELECT score, status FROM exam_attempts WHERE exam_id = ?")) {
                ps.setInt(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        totalAttempts++;
                        String st = rs.getString("status");
                        int sc = rs.getInt("score");
                        boolean scoreWasNull = rs.wasNull();

                        if ("SUBMITTED".equalsIgnoreCase(st)) {
                            submittedAttempts++;
                            if (!scoreWasNull) {
                                scoredScores.add(sc);
                            }
                        } else if ("ACTIVE".equalsIgnoreCase(st)) {
                            activeAttempts++;
                        } else if ("EXPIRED".equalsIgnoreCase(st)) {
                            expiredAttempts++;
                        }
                    }
                }
            }

            Double avgScore = null;
            Integer highest = null;
            Integer lowest = null;
            Map<String, Integer> distribution = new LinkedHashMap<>();

            if (!scoredScores.isEmpty()) {
                int sum = 0;
                int high = scoredScores.get(0);
                int low = scoredScores.get(0);
                int b0 = 0, b1 = 0, b2 = 0, b3plus = 0;

                for (int sc : scoredScores) {
                    sum += sc;
                    if (sc > high) high = sc;
                    if (sc < low) low = sc;

                    if (sc == 0) b0++;
                    else if (sc == 1) b1++;
                    else if (sc == 2) b2++;
                    else b3plus++;
                }

                avgScore = Math.round(((double) sum / scoredScores.size()) * 10.0) / 10.0;
                highest = high;
                lowest = low;
                distribution.put("0 pts", b0);
                distribution.put("1 pt", b1);
                distribution.put("2 pts", b2);
                distribution.put("3+ pts", b3plus);
            }

            return Optional.of(new AdminExamStatisticsResult(
                    examId,
                    examTitle,
                    totalAttempts,
                    submittedAttempts,
                    activeAttempts,
                    expiredAttempts,
                    avgScore,
                    highest,
                    lowest,
                    distribution));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AdminProctorEventResult> getProctorEvents(int attemptId) {
        List<AdminProctorEventResult> events = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            record EventRow(int id, String eventType, Instant recordedAt, Instant returnedAt, Long awayDurationMs, boolean isLongAbsence) {}
            List<EventRow> eventRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, event_type, recorded_at, returned_at, away_duration_ms, is_long_absence "
                            + "FROM proctor_events WHERE attempt_id = ? ORDER BY recorded_at ASC")) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ret = rs.getTimestamp("returned_at");
                        long dur = rs.getLong("away_duration_ms");
                        Long awayDuration = rs.wasNull() ? null : dur;
                        eventRows.add(new EventRow(
                                rs.getInt("id"),
                                rs.getString("event_type"),
                                rs.getTimestamp("recorded_at").toInstant(),
                                ret != null ? ret.toInstant() : null,
                                awayDuration,
                                rs.getBoolean("is_long_absence")));
                    }
                }
            } catch (Exception e) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, event_type, recorded_at FROM proctor_events WHERE attempt_id = ? ORDER BY recorded_at ASC")) {
                    ps.setInt(1, attemptId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            eventRows.add(new EventRow(
                                    rs.getInt("id"),
                                    rs.getString("event_type"),
                                    rs.getTimestamp("recorded_at").toInstant(),
                                    null, null, false));
                        }
                    }
                }
            }

            Map<Integer, List<String>> evidenceTypesMap = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT event_id, evidence_type FROM proctor_evidence_v2 WHERE event_id IN "
                            + "(SELECT id FROM proctor_events WHERE attempt_id = ?) ORDER BY id ASC")) {
                ps.setInt(1, attemptId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int evId = rs.getInt("event_id");
                        String evType = rs.getString("evidence_type");
                        evidenceTypesMap.computeIfAbsent(evId, k -> new ArrayList<>()).add(evType);
                    }
                }
            } catch (Exception e) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT event_id FROM proctor_evidence_v2 WHERE event_id IN "
                                + "(SELECT id FROM proctor_events WHERE attempt_id = ?)")) {
                    ps.setInt(1, attemptId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            evidenceTypesMap.computeIfAbsent(rs.getInt("event_id"), k -> new ArrayList<>()).add("INITIAL");
                        }
                    }
                } catch (Exception ignored) {}
            }

            for (EventRow row : eventRows) {
                List<String> types = evidenceTypesMap.getOrDefault(row.id(), List.of());
                events.add(new AdminProctorEventResult(
                        row.id(),
                        row.eventType(),
                        row.recordedAt(),
                        row.returnedAt(),
                        row.awayDurationMs(),
                        row.isLongAbsence(),
                        !types.isEmpty(),
                        types));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return events;
    }

    @Override
    public Optional<AdminEvidenceResult> getEvidenceByEventId(int eventId) {
        return getEvidenceByEventId(eventId, null);
    }

    @Override
    public Optional<AdminEvidenceResult> getEvidenceByEventId(int eventId, String evidenceType) {
        try (Connection conn = dataSource.getConnection()) {
            if (evidenceType != null && !evidenceType.isBlank()) {
                String targetType = evidenceType;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, content_type, screenshot_data, evidence_type FROM proctor_evidence_v2 "
                                + "WHERE event_id = ? AND evidence_type = ? ORDER BY id ASC LIMIT 1")) {
                    ps.setInt(1, eventId);
                    ps.setString(2, targetType);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String type = rs.getString("evidence_type");
                            return Optional.of(new AdminEvidenceResult(
                                    rs.getInt("id"),
                                    eventId,
                                    rs.getString("content_type"),
                                    rs.getBytes("screenshot_data"),
                                    type != null ? type : targetType));
                        }
                    }
                } catch (Exception ignored) {}
                return Optional.empty(); // Strict: when specific type requested and not found, return empty!
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, content_type, screenshot_data, evidence_type FROM proctor_evidence_v2 WHERE event_id = ? ORDER BY id ASC LIMIT 1")) {
                ps.setInt(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AdminEvidenceResult(
                                rs.getInt("id"),
                                eventId,
                                rs.getString("content_type"),
                                rs.getBytes("screenshot_data"),
                                rs.getString("evidence_type")));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
