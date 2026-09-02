package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
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

    public SubmitExamUseCase(AnswerRepository answerRepository, 
                             ExamAttemptRepository attemptRepository,
                             QuestionRepository questionRepository) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
    }

    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, Instant timestamp) {
        Question question = questionRepository.findById(questionId)
            .orElseThrow(() -> new IllegalArgumentException("Question not found"));
            
        boolean isCorrect = GradingPolicy.isAnswerCorrect(question, selectedOptions);
        answerRepository.saveAnswer(attemptId, questionId, selectedOptions, isCorrect, timestamp);
    }

    public int submitExam(int attemptId, int focusLostCount, Instant submittedAt) {
        int correct = answerRepository.countCorrectAnswersByAttempt(attemptId);
        attemptRepository.calculateAndSetScore(attemptId, focusLostCount, correct, submittedAt);
        return correct;
    }
}
