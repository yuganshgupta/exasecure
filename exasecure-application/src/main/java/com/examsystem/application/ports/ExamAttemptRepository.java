package com.examsystem.application.ports;

import com.examsystem.domain.models.ExamAttempt;
import java.util.Optional;

public interface ExamAttemptRepository {
    Optional<ExamAttempt> findById(int attemptId);
    void calculateAndSetScore(int attemptId, int focusLostCount, int score, java.time.Instant submittedAt);
}
