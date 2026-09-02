package com.examsystem.application.usecases;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.domain.models.ExamAttempt;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartExamUseCaseTest {

    static class StubClock implements ClockPort {
        Instant current = Instant.parse("2026-09-02T12:00:00Z");

        @Override
        public Instant now() {
            return current;
        }
    }

    static class StubAttemptRepo implements ExamAttemptRepository {
        ExamAttempt attemptToReturn;
        RuntimeException toThrow;
        int capturedStudentId;
        int capturedExamId;
        Instant capturedNow;
        Duration capturedTimeLimit;

        @Override
        public Optional<ExamAttempt> findById(int attemptId) {
            return Optional.ofNullable(attemptToReturn);
        }

        @Override
        public void calculateAndSetScore(int attemptId, int focusLostCount, int score, Instant submittedAt) {}

        @Override
        public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, Duration timeLimit) {
            this.capturedStudentId = studentId;
            this.capturedExamId = examId;
            this.capturedNow = now;
            this.capturedTimeLimit = timeLimit;
            if (toThrow != null) {
                throw toThrow;
            }
            return attemptToReturn;
        }
    }

    @Test
    void startOrResume_delegatesToRepositoryWithAuthoritativeTime() {
        StubAttemptRepo repo = new StubAttemptRepo();
        StubClock clock = new StubClock();
        ExamAttempt expected = new ExamAttempt(
                10, 1, 101, clock.now(), null, null, false);
        repo.attemptToReturn = expected;

        StartExamUseCase useCase = new StartExamUseCase(repo, clock);
        ExamAttempt result = useCase.startOrResume(101, 1, Duration.ofMinutes(60));

        assertThat(result).isSameAs(expected);
        assertThat(repo.capturedStudentId).isEqualTo(101);
        assertThat(repo.capturedExamId).isEqualTo(1);
        assertThat(repo.capturedNow).isEqualTo(clock.now());
        assertThat(repo.capturedTimeLimit).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void startOrResume_finalizedAttempt_propagatesException() {
        StubAttemptRepo repo = new StubAttemptRepo();
        StubClock clock = new StubClock();
        repo.toThrow = new IllegalStateException("Exam attempt is already finalized");

        StartExamUseCase useCase = new StartExamUseCase(repo, clock);

        assertThatThrownBy(() -> useCase.startOrResume(101, 1, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already finalized");
    }

    @Test
    void startOrResume_expiredAttempt_propagatesException() {
        StubAttemptRepo repo = new StubAttemptRepo();
        StubClock clock = new StubClock();
        repo.toThrow = new IllegalStateException("Exam attempt has expired");

        StartExamUseCase useCase = new StartExamUseCase(repo, clock);

        assertThatThrownBy(() -> useCase.startOrResume(101, 1, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }
}
