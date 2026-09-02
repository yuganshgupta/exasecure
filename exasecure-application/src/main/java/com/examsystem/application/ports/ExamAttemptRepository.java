package com.examsystem.application.ports;

public interface ExamAttemptRepository {
    void calculateAndSetScore(int attemptId, int focusLostCount, int score, java.time.Instant submittedAt);
}
