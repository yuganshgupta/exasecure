package com.examsystem.application.ports;

import com.examsystem.domain.models.ExamAttempt;
import java.util.Optional;

public interface ExamAttemptRepository {
    Optional<ExamAttempt> findById(int attemptId);
    void calculateAndSetScore(int attemptId, int focusLostCount, int score, java.time.Instant submittedAt);
    ExamAttempt startOrResumeAttempt(int studentId, int examId, java.time.Instant now, java.time.Duration timeLimit);

    default int finalizeAndGradeAttempt(int attemptId, java.time.Instant submittedAt, int focusLostCount,
                                        java.util.function.ToIntFunction<java.util.Map<Integer, java.util.List<Integer>>> grader,
                                        AnswerRepository answerRepository) {
        var answers = answerRepository.getSelectedOptionsForAttempt(attemptId);
        int score = grader.applyAsInt(answers);
        calculateAndSetScore(attemptId, focusLostCount, score, submittedAt);
        return score;
    }

    default int incrementFocusLostCount(int attemptId) {
        return 0;
    }

    default void flagAttempt(int attemptId) {
    }
}
