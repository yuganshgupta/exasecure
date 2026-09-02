package com.examsystem.application.usecases;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.domain.models.ExamAttempt;
import com.examsystem.domain.models.Option;
import com.examsystem.domain.models.Question;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitExamUseCaseTest {

    static class StubAnswerRepo implements AnswerRepository {
        boolean saved = false;
        java.util.Map<Integer, List<Integer>> answers = new java.util.HashMap<>();
        
        @Override
        public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions, Instant timestamp) {
            this.saved = true;
            this.answers.put(questionId, selectedOptions);
        }

        @Override
        public java.util.Map<Integer, List<Integer>> getSelectedOptionsForAttempt(int attemptId) {
            return answers;
        }
    }

    static class StubAttemptRepo implements ExamAttemptRepository {
        int finalScore = -1;
        boolean isFinalized = false;
        Instant startTime = Instant.now().minus(Duration.ofMinutes(10));
        
        @Override
        public Optional<ExamAttempt> findById(int attemptId) {
            return Optional.of(new ExamAttempt(attemptId, 1, 1, startTime, null, null, isFinalized));
        }

        @Override
        public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {
            this.finalScore = score;
        }

        @Override
        public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, Duration timeLimit) {
            return new ExamAttempt(1, examId, studentId, startTime, null, null, isFinalized);
        }
    }

    static class StubClock implements ClockPort {
        Instant current = Instant.now();
        @Override
        public Instant now() {
            return current;
        }
    }

    static class StubQuestionRepo implements QuestionRepository {
        @Override
        public Optional<Question> findById(int id) {
            if (id == 1) {
                return Optional.of(question());
            }
            return Optional.empty();
        }

        @Override
        public List<Question> findByExamId(int examId) {
            return List.of(question());
        }

        private Question question() {
            return new Question(1, 1, "Q1", "SINGLE", List.of(
                    new Option(501, 1, "Wrong", false),
                    new Option(502, 2, "Right", true)), 2, true);
        }
    }

    static class StubProctorRepo implements com.examsystem.application.ports.ProctorRepository {
        @Override
        public int saveEvent(int attemptId, String eventType, java.time.Instant timestamp) {
            return 1;
        }
        @Override
        public void saveEvidence(int eventId, byte[] screenshot, String contentType, java.time.Instant timestamp) {}
        @Override
        public boolean eventBelongsToAttempt(int eventId, int attemptId) { return true; }
        @Override
        public int countEvents(int attemptId, String eventType) { return 0; }
    }

    @Test
    void saveAnswer_savesCorrectly() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(answerRepo, new StubAttemptRepo(), new StubQuestionRepo(), new StubProctorRepo(), new StubClock());
        
        useCase.saveAnswer(100, 1, List.of(2), Duration.ofMinutes(60));
        
        assertThat(answerRepo.saved).isTrue();
    }

    @Test
    void saveAnswer_invalidQuestion_throws() {
        SubmitExamUseCase useCase = new SubmitExamUseCase(new StubAnswerRepo(), new StubAttemptRepo(), new StubQuestionRepo(), new StubProctorRepo(), new StubClock());
        
        assertThatThrownBy(() -> useCase.saveAnswer(100, 999, List.of(1), Duration.ofMinutes(60)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Question not found");
    }

    @Test
    void saveAnswer_expiredAttempt_throws() {
        StubAttemptRepo attemptRepo = new StubAttemptRepo();
        attemptRepo.startTime = Instant.now().minus(Duration.ofMinutes(120)); // Expired
        
        SubmitExamUseCase useCase = new SubmitExamUseCase(new StubAnswerRepo(), attemptRepo, new StubQuestionRepo(), new StubProctorRepo(), new StubClock());
        
        assertThatThrownBy(() -> useCase.saveAnswer(100, 1, List.of(2), Duration.ofMinutes(60)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("expired or finalized");
    }

    @Test
    void saveAnswer_unknownOptionNumber_throwsWithoutSaving() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(
                answerRepo, new StubAttemptRepo(), new StubQuestionRepo(), new StubProctorRepo(), new StubClock());

        assertThatThrownBy(() -> useCase.saveAnswer(100, 1, List.of(502), Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        assertThat(answerRepo.saved).isFalse();
    }

    @Test
    void saveAnswer_duplicateOptionNumber_throwsWithoutSaving() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        SubmitExamUseCase useCase = new SubmitExamUseCase(
                answerRepo, new StubAttemptRepo(), new StubQuestionRepo(), new StubProctorRepo(), new StubClock());

        assertThatThrownBy(() -> useCase.saveAnswer(100, 1, List.of(2, 2), Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
        assertThat(answerRepo.saved).isFalse();
    }

    @Test
    void saveAnswer_questionFromAnotherExam_throwsWithoutSaving() {
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        QuestionRepository wrongExamQuestions = new StubQuestionRepo() {
            @Override
            public Optional<Question> findById(int id) {
                return Optional.of(new Question(id, 2, "Other exam", "SINGLE",
                        Collections.emptyList(), 1, true));
            }
        };
        SubmitExamUseCase useCase = new SubmitExamUseCase(
                answerRepo, new StubAttemptRepo(), wrongExamQuestions, new StubProctorRepo(), new StubClock());

        assertThatThrownBy(() -> useCase.saveAnswer(100, 2, List.of(1), Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        assertThat(answerRepo.saved).isFalse();
    }

    @Test
    void submitExam_calculatesAndSetsScore() {
        StubAttemptRepo attemptRepo = new StubAttemptRepo();
        StubAnswerRepo answerRepo = new StubAnswerRepo();
        answerRepo.answers.put(1, List.of(2)); // Correct answer for Q1
        
        SubmitExamUseCase useCase = new SubmitExamUseCase(answerRepo, attemptRepo, new StubQuestionRepo(), new StubProctorRepo(), new StubClock());
        
        int score = useCase.submitExam(100, Instant.now());
        
        assertThat(score).isEqualTo(1); // 1 correct answer
        assertThat(attemptRepo.finalScore).isEqualTo(1);
    }
}
