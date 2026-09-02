package com.examsystem.domain.models;

import java.time.Instant;
import java.time.Duration;

public record ExamAttempt(
    int id,
    int examId,
    int studentId,
    Instant startTime,
    Instant endTime,
    Integer score,
    boolean finalized
) {
    public boolean isExpired(Instant now, Duration timeLimit) {
        if (finalized) return true;
        if (timeLimit == null || timeLimit.isZero()) return false; // unlimited
        return now.isAfter(startTime.plus(timeLimit));
    }
}
