package com.examsystem.application.query;

import java.time.Instant;

public record AdminAttemptResult(
        int id,
        int studentId,
        String studentName,
        Instant startTime,
        String status,
        Integer score
) {}
