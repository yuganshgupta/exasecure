package com.examsystem.contracts;

import java.time.Instant;

public record StudentAttemptDto(
        int id,
        int examId,
        int studentId,
        Instant startTime,
        Instant endTime,
        Integer score,
        String status
) {}
