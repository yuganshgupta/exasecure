package com.examsystem.application.ports;

import java.util.List;

public interface AnswerRepository {
    void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, boolean isCorrect, java.time.Instant timestamp);
    int countCorrectAnswersByAttempt(int attemptId);
}
