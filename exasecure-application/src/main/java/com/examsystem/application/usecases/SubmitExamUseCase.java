package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.domain.models.Question;

import java.time.Instant;
import java.util.List;

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
        attemptRepository.findById(attemptId).ifPresent(attempt -> {
            if (attempt.isExpired(clockPort.now(), timeLimit)) {
                throw new IllegalStateException("Exam attempt is expired or finalized.");
            }
        });

        Question question = questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));
            
        answerRepository.saveAnswer(attemptId, questionId, selectedOptions, clockPort.now());
    }

    public int submitExam(int attemptId, Instant submittedAt) {
        var attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new IllegalArgumentException("Attempt not found"));
            
        int correct = 0;
        var answers = answerRepository.getSelectedOptionsForAttempt(attemptId);
        List<Question> questions = questionRepository.findByExamId(attempt.examId());
        
        for (Question q : questions) {
            List<Integer> selected = answers.getOrDefault(q.id(), java.util.Collections.emptyList());
            if (GradingPolicy.isAnswerCorrect(q, selected)) {
                correct++;
            }
        }
        
        int focusLostCount = proctorRepository.countEvents(attemptId, "FOCUS_LOST");
        
        attemptRepository.calculateAndSetScore(attemptId, focusLostCount, correct, submittedAt);
        return correct;
    }
}
