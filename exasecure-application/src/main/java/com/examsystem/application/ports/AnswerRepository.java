package com.examsystem.application.ports;

import java.util.List;

public interface AnswerRepository {
    void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, java.time.Instant timestamp);
    java.util.Map<Integer, List<Integer>> getSelectedOptionsForAttempt(int attemptId);
}
