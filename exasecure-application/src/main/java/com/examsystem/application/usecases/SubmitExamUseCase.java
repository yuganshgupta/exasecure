package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.domain.models.Question;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubmitExamUseCase {

    private final AnswerRepository answerRepository;
    private final ExamAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final com.examsystem.application.ports.ProctorRepository proctorRepository;
    private final ClockPort clockPort;

    public SubmitExamUseCase(AnswerRepository answerRepository, 
                             ExamAttemptRepository attemptRepository,
                             QuestionRepository questionRepository,
                             com.examsystem.application.ports.ProctorRepository proctorRepository,
                             ClockPort clockPort) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.proctorRepository = proctorRepository;
        this.clockPort = clockPort;
    }

    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, java.time.Duration timeLimit) {
        var attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
        if (attempt.isExpired(clockPort.now(), timeLimit)) {
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
        var attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));

        if (attempt.finalized() && attempt.score() != null) {
            return attempt.score();
        }
            
        List<Question> questions = questionRepository.findByExamId(attempt.examId());
        int focusLostCount = proctorRepository.countEvents(attemptId, "FOCUS_LOST");
        
        try {
            return attemptRepository.finalizeAndGradeAttempt(attemptId, submittedAt, focusLostCount, answers -> {
                int correct = 0;
                for (Question q : questions) {
                    List<Integer> selected = answers.getOrDefault(q.id(), java.util.Collections.emptyList());
                    if (GradingPolicy.isAnswerCorrect(q, selected)) {
                        correct++;
                    }
                }
                return correct;
            }, answerRepository);
        } catch (IllegalStateException e) {
            var fresh = attemptRepository.findById(attemptId);
            if (fresh.isPresent() && fresh.get().finalized() && fresh.get().score() != null) {
                return fresh.get().score();
            }
            throw e;
        }
    }
}
