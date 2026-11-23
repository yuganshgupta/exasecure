package com.examsystem.application.ports;

import com.examsystem.domain.models.ExamAttempt;
import java.util.Optional;

public interface ExamAttemptRepository {
    Optional<ExamAttempt> findById(int attemptId);
    void calculateAndSetScore(int attemptId, int focusLostCount, int score, java.time.Instant submittedAt);
    ExamAttempt startOrResumeAttempt(int studentId, int examId, java.time.Instant now, java.time.Duration timeLimit);

    default int finalizeAndGradeAttempt(int attemptId, java.time.Instant submittedAt,
                                        java.util.function.ToIntFunction<java.util.Map<Integer, java.util.List<Integer>>> grader,
                                        AnswerRepository answerRepository, boolean deadlineTriggered) {
        var attempt = findById(attemptId).orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        if (attempt.finalized() && attempt.score() != null) return attempt.score();
        if (deadlineTriggered) throw new UnsupportedOperationException("Deadline submission requires a locked implementation");
        var answers = answerRepository.getSelectedOptionsForAttempt(attemptId);
        int score = grader.applyAsInt(answers);
        try {
            calculateAndSetScore(attemptId, attempt.focusLostCount(), score, submittedAt);
        } catch (IllegalStateException conflict) {
            var fresh = findById(attemptId);
            if (fresh.isPresent() && fresh.get().finalized() && fresh.get().score() != null) return fresh.get().score();
            throw conflict;
        }
        return score;
    }

    default boolean deleteAttempt(int attemptId) {
        return false;
    }
}
