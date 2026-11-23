package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.domain.models.Question;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubmitExamUseCase {

    private final AnswerRepository answerRepository;
    private final ExamAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final ClockPort clockPort;

    public SubmitExamUseCase(AnswerRepository answerRepository, 
                             ExamAttemptRepository attemptRepository,
                             QuestionRepository questionRepository,
                             ClockPort clockPort) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.clockPort = clockPort;
    }

    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, Duration timeLimit) {
        var attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        if (attempt.finalized()
                || (timeLimit != null && !clockPort.now().isBefore(attempt.startTime().plus(timeLimit)))) {
            throw new IllegalStateException("Exam attempt is expired or finalized.");
        }

        Question question = questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (question.examId() != attempt.examId()) {
            throw new IllegalArgumentException("Question does not belong to the attempt exam");
        }

        validateSelectedOptions(question, selectedOptions);
            
        answerRepository.saveAnswer(attemptId, questionId, selectedOptions, clockPort.now());
    }

    private static void validateSelectedOptions(Question question, List<Integer> selectedOptions) {
        if (selectedOptions == null) {
            throw new IllegalArgumentException("Selected options are required");
        }
        if (selectedOptions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Selected option numbers cannot be null");
        }

        Set<Integer> uniqueSelections = new HashSet<>(selectedOptions);
        if (uniqueSelections.size() != selectedOptions.size()) {
            throw new IllegalArgumentException("Selected option numbers must be unique");
        }

        Set<Integer> availableNumbers = question.options().stream()
                .map(com.examsystem.domain.models.Option::number)
                .collect(java.util.stream.Collectors.toSet());
        if (!availableNumbers.containsAll(uniqueSelections)) {
            throw new IllegalArgumentException("Selected option number does not belong to the question");
        }
        if ("SINGLE".equals(question.type()) && selectedOptions.size() > 1) {
            throw new IllegalArgumentException("Single-choice questions accept at most one option");
        }
    }

    public int submitExam(int attemptId, Instant submittedAt) {
        return submitExam(attemptId, submittedAt, false);
    }

    public int submitExam(int attemptId, Instant submittedAt, boolean deadlineTriggered) {
        var attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        List<Question> questions = questionRepository.findByExamId(attempt.examId());
        return attemptRepository.finalizeAndGradeAttempt(attemptId, submittedAt,
                answers -> GradingPolicy.score(questions, answers), answerRepository, deadlineTriggered);
    }
}
