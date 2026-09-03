package com.examsystem.application.query;

import java.time.Instant;

public record AdminAttemptResult(
        int id,
        int studentId,
        String studentName,
        Instant startTime,
        String status,
        Integer score,
        int focusLostCount,
        boolean flagged,
        String proctoringStatus
) {
    public AdminAttemptResult(int id, int studentId, String studentName, Instant startTime, String status, Integer score) {
        this(id, studentId, studentName, startTime, status, score, 0, false, "CLEAN");
    }
}
