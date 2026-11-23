package com.examsystem.server.adapters.jdbc;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ProctorIncidentPort;
import com.examsystem.domain.models.Question;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.ToIntBiFunction;

public final class JdbcProctorIncidentPort implements ProctorIncidentPort {
    private final DataSource dataSource;
    private final ClockPort clock;
    public JdbcProctorIncidentPort(DataSource dataSource, ClockPort clock) {
        this.dataSource = dataSource; this.clock = clock;
    }

    @Override public Result recordIncident(int studentId, int attemptId, String eventType, String clientEventId,
            ToIntBiFunction<List<Question>, Map<Integer, List<Integer>>> grader) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int examId;
                int count;
                boolean flagged;
                String status;
                Instant start;
                try (PreparedStatement lock = conn.prepareStatement(
                        "SELECT student_id, exam_id, start_time, status, focus_lost_count, is_flagged FROM exam_attempts WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, attemptId);
                    try (ResultSet row = lock.executeQuery()) {
                        if (!row.next()) throw new NoSuchElementException("Attempt not found");
                        if (row.getInt("student_id") != studentId) throw new SecurityException("Cannot access another student's attempt");
                        examId = row.getInt("exam_id"); count = row.getInt("focus_lost_count");
                        flagged = row.getBoolean("is_flagged"); status = row.getString("status");
                        start = row.getTimestamp("start_time").toInstant();
                    }
                }
                // Bound the key before lookup; no trimming or case canonicalization of idempotency keys.
                if (clientEventId == null || clientEventId.isBlank() || clientEventId.length() > 64
                        || !clientEventId.equals(clientEventId.strip()) || clientEventId.chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("clientEventId must be nonblank and at most 64 characters without surrounding whitespace or controls");
                try (PreparedStatement query = conn.prepareStatement(
                        "SELECT id, event_type, client_event_id, result_violation_count, result_flagged, result_attempt_status FROM proctor_events WHERE attempt_id = ? AND client_event_id = ?")) {
                    query.setInt(1, attemptId); query.setString(2, clientEventId);
                    try (ResultSet row = query.executeQuery()) {
                        if (row.next()) {
                            if (!Objects.equals(eventType, row.getString("event_type")) || !clientEventId.equals(row.getString("client_event_id")))
                                throw new IllegalArgumentException("clientEventId conflicts with the existing incident");
                            Integer storedCount = (Integer) row.getObject("result_violation_count");
                            Object storedFlag = row.getObject("result_flagged");
                            String storedStatus = row.getString("result_attempt_status");
                            boolean legacy = storedCount == null && storedFlag == null && storedStatus == null;
                            if (!legacy && (storedCount == null || storedFlag == null || storedStatus == null))
                                throw new IllegalStateException("Incomplete incident result snapshot");
                            Result result = legacy ? new Result(row.getInt("id"), count, flagged, status)
                                    : new Result(row.getInt("id"), storedCount, row.getBoolean("result_flagged"), storedStatus);
                            conn.commit();
                            return result; // Legacy fallback is never written as a historical snapshot.
                        }
                    }
                }
                if (!"FOCUS_LOST".equals(eventType)) throw new IllegalArgumentException("Unsupported eventType; expected FOCUS_LOST");
                if (!"ACTIVE".equals(status)) throw new IllegalStateException("Attempt is not ACTIVE");
                if (count < 0 || count >= 3) throw new IllegalStateException("Attempt strike state does not allow a new incident");
                Instant now = clock.now();
                if (!now.isBefore(JdbcAttemptFinalizer.deadline(conn, examId, start))) {
                    throw new IllegalStateException("Exam attempt has expired");
                }
                int eventId;
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO proctor_events (attempt_id, event_type, recorded_at, client_event_id) VALUES (?, 'FOCUS_LOST', ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, attemptId); insert.setTimestamp(2, Timestamp.from(now)); insert.setString(3, clientEventId);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Incident ID was not generated");
                        eventId = keys.getInt(1);
                    }
                }
                int newCount = count + 1;
                flagged = flagged || newCount == 3;
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE exam_attempts SET focus_lost_count = ?, is_flagged = ?, proctoring_status = CASE WHEN ? THEN 'FLAGGED' ELSE proctoring_status END WHERE id = ?")) {
                    update.setInt(1, newCount); update.setBoolean(2, flagged); update.setBoolean(3, flagged); update.setInt(4, attemptId);
                    if (update.executeUpdate() != 1) throw new SQLException("Strike update failed");
                }
                if (newCount == 3) {
                    List<Question> questions = JdbcQuestionRepository.findByExamId(conn, examId);
                    JdbcAttemptFinalizer.finalizeLocked(conn, attemptId, now, answers -> grader.applyAsInt(questions, answers));
                    status = "SUBMITTED";
                }
                try (PreparedStatement snapshot = conn.prepareStatement(
                        "UPDATE proctor_events SET result_violation_count = ?, result_flagged = ?, result_attempt_status = ? WHERE id = ?")) {
                    snapshot.setInt(1, newCount); snapshot.setBoolean(2, flagged); snapshot.setString(3, status); snapshot.setInt(4, eventId);
                    if (snapshot.executeUpdate() != 1) throw new SQLException("Incident snapshot update failed");
                }
                Result result = new Result(eventId, newCount, flagged, status);
                conn.commit();
                return result;
            } catch (Exception | Error e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Incident transaction failed", e);
        }
    }
}
