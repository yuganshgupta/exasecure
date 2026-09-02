package com.examsystem.client.exam;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamTimerTest {

    @Test
    void timerCalculatesRemainingSecondsCorrectly() {
        Instant start = Instant.parse("2026-09-02T10:00:00Z");
        ExamTimer timer = new ExamTimer(start, 60);

        Instant check1 = Instant.parse("2026-09-02T10:30:00Z");
        assertThat(timer.getRemainingSecondsAt(check1)).isEqualTo(1800);
        assertThat(timer.isExpiredAt(check1)).isFalse();
        assertThat(ExamTimer.formatRemainingTime(1800)).isEqualTo("30:00");

        Instant check2 = Instant.parse("2026-09-02T11:00:00Z");
        assertThat(timer.getRemainingSecondsAt(check2)).isEqualTo(0);
        assertThat(timer.isExpiredAt(check2)).isTrue();
        assertThat(ExamTimer.formatRemainingTime(0)).isEqualTo("00:00");

        Instant check3 = Instant.parse("2026-09-02T11:05:00Z");
        assertThat(timer.getRemainingSecondsAt(check3)).isEqualTo(0);
        assertThat(timer.isExpiredAt(check3)).isTrue();
    }

    @Test
    void formatTimeDisplaysMinutesAndSeconds() {
        assertThat(ExamTimer.formatRemainingTime(65)).isEqualTo("01:05");
        assertThat(ExamTimer.formatRemainingTime(5)).isEqualTo("00:05");
        assertThat(ExamTimer.formatRemainingTime(3600)).isEqualTo("60:00");
    }

    @Test
    void invalidConstructorParameters_throw() {
        assertThatThrownBy(() -> new ExamTimer(null, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExamTimer(Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
