package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.models.Question;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitExamUseCaseTest {

    static class StubAnswerRepo implements AnswerRepository {
        boolean savedCorrect = false;
        int countCorrect = 5;
        
        @Override
        public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, boolean isCorrect, Instant timestamp) {
            this.savedCorrect = isCorrect;
        }

        @Override
        public int countCorrectAnswersByAttempt(int attemptId) {
            return countCorrect;
        }
    }

    static class StubAttemptRepo implements ExamAttemptRepository {
        int finalScore = -1;
        
        @Override
        public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {
            this.finalScore = score;
        }
    }

    static class StubQuestionRepo implements QuestionRepository {
        @Override
        public Optional<Question> findById(int id) {
            if (id == 1) {
                return Optional.of(new Question(1, 1, "Q1", "SINGLE", Collections.emptyList(), 2, true));
            }
            return Optional.empty();
        }

        @Override
        public List<Question> findByExamId(int examId) {
            return Collections.emptyList();
        }
    }

    @Test
    void saveAnswer_correctAnswer_savesAsCorrect() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(answerRepo, new StubAttemptRepo(), new StubQuestionRepo());
        
        useCase.saveAnswer(100, 1, List.of(2), Instant.now());
        
        assertThat(answerRepo.savedCorrect).isTrue();
    }

    @Test
    void saveAnswer_wrongAnswer_savesAsIncorrect() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(answerRepo, new StubAttemptRepo(), new StubQuestionRepo());
        
        useCase.saveAnswer(100, 1, List.of(1), Instant.now());
        
        assertThat(answerRepo.savedCorrect).isFalse();
    }

    @Test
    void saveAnswer_invalidQuestion_throws() {
        SubmitExamUseCase useCase = new SubmitExamUseCase(new StubAnswerRepo(), new StubAttemptRepo(), new StubQuestionRepo());
        
        assertThatThrownBy(() -> useCase.saveAnswer(100, 999, List.of(1), Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Question not found");
    }

    @Test
    void submitExam_calculatesAndSetsScore() {
        StubAttemptRepo attemptRepo = new StubAttemptRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(new StubAnswerRepo(), attemptRepo, new StubQuestionRepo());
        
        int score = useCase.submitExam(100, 0, Instant.now());
        
        assertThat(score).isEqualTo(5);
        assertThat(attemptRepo.finalScore).isEqualTo(5);
    }
}
