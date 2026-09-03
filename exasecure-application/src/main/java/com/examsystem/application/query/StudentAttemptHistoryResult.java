package com.examsystem.application.query;

import java.time.Instant;

public record StudentAttemptHistoryResult(
        String examTitle,
        int attemptId,
        Instant startTime,
        Instant endTime,
        Integer score,
        String status,
        boolean flagged
) {
    public StudentAttemptHistoryResult(
            String examTitle,
            int attemptId,
            Instant startTime,
            Instant endTime,
            Integer score,
            String status) {
        this(examTitle, attemptId, startTime, endTime, score, status, false);
    }
}
