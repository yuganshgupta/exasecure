package com.examsystem.client.student.views;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamTakingViewTest {

    @Test
    void readingScaleMovesInTenPercentSteps() {
        assertThat(ExamTakingView.adjustedReadingScale(100, 1)).isEqualTo(110);
        assertThat(ExamTakingView.adjustedReadingScale(100, -1)).isEqualTo(90);
    }

    @Test
    void readingScaleStopsAtConfiguredBounds() {
        int increased = ExamTakingView.DEFAULT_READING_SCALE;
        int decreased = ExamTakingView.DEFAULT_READING_SCALE;
        for (int press = 0; press < 20; press++) {
            increased = ExamTakingView.adjustedReadingScale(increased, 1);
            decreased = ExamTakingView.adjustedReadingScale(decreased, -1);
        }

        assertThat(increased).isEqualTo(ExamTakingView.MAX_READING_SCALE);
        assertThat(decreased).isEqualTo(ExamTakingView.MIN_READING_SCALE);
    }
}
