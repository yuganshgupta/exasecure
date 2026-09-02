package com.examsystem.domain.models;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExamAttemptTest {

    @Test
    void isExpired_whenFinalized_isTrue() {
        ExamAttempt attempt = new ExamAttempt(1, 1, 1, Instant.now(), null, null, true);
        assertThat(attempt.isExpired(Instant.now(), Duration.ofMinutes(60))).isTrue();
    }

    @Test
    void isExpired_whenPastTimeLimit_isTrue() {
        Instant start = Instant.now().minus(Duration.ofMinutes(65));
        ExamAttempt attempt = new ExamAttempt(1, 1, 1, start, null, null, false);
        assertThat(attempt.isExpired(Instant.now(), Duration.ofMinutes(60))).isTrue();
    }

    @Test
    void isExpired_whenWithinTimeLimit_isFalse() {
        Instant start = Instant.now().minus(Duration.ofMinutes(10));
        ExamAttempt attempt = new ExamAttempt(1, 1, 1, start, null, null, false);
        assertThat(attempt.isExpired(Instant.now(), Duration.ofMinutes(60))).isFalse();
    }
}
