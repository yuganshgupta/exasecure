package com.examsystem.domain.grading;

import com.examsystem.domain.models.Option;
import com.examsystem.domain.models.Question;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradingPolicyTest {

    @Test
    void isAnswerCorrect_singleCorrect() {
        Question q = new Question(1, 1, "Q1", "SINGLE", Collections.emptyList(), 2, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(2))).isTrue();
    }

    @Test
    void isAnswerCorrect_singleIncorrect() {
        Question q = new Question(1, 1, "Q1", "SINGLE", Collections.emptyList(), 2, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(1))).isFalse();
    }

    @Test
    void isAnswerCorrect_singleEmpty() {
        Question q = new Question(1, 1, "Q1", "SINGLE", Collections.emptyList(), 2, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, Collections.emptyList())).isFalse();
    }
    
    @Test
    void isAnswerCorrect_singleMultipleSelections() {
        Question q = new Question(1, 1, "Q1", "SINGLE", Collections.emptyList(), 2, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(2, 3))).isFalse();
    }

    @Test
    void isAnswerCorrect_multiExactMatch() {
        List<Option> options = Arrays.asList(
                new Option(1, "A", true),
                new Option(2, "B", false),
                new Option(3, "C", true)
        );
        Question q = new Question(1, 1, "Q2", "MULTI", options, 0, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(1, 3))).isTrue();
    }

    @Test
    void isAnswerCorrect_multiOrderIndependent() {
        List<Option> options = Arrays.asList(
                new Option(1, "A", true),
                new Option(2, "B", false),
                new Option(3, "C", true)
        );
        Question q = new Question(1, 1, "Q2", "MULTI", options, 0, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(3, 1))).isTrue();
    }

    @Test
    void isAnswerCorrect_multiWrong() {
        List<Option> options = Arrays.asList(
                new Option(1, "A", true),
                new Option(2, "B", false),
                new Option(3, "C", true)
        );
        Question q = new Question(1, 1, "Q2", "MULTI", options, 0, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(1, 2))).isFalse();
    }
    
    @Test
    void isAnswerCorrect_multiMissing() {
        List<Option> options = Arrays.asList(
                new Option(1, "A", true),
                new Option(2, "B", false),
                new Option(3, "C", true)
        );
        Question q = new Question(1, 1, "Q2", "MULTI", options, 0, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(1))).isFalse();
    }
    
    @Test
    void isAnswerCorrect_multiDuplicateSelected() {
        List<Option> options = Arrays.asList(
                new Option(1, "A", true),
                new Option(2, "B", false),
                new Option(3, "C", true)
        );
        Question q = new Question(1, 1, "Q2", "MULTI", options, 0, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, List.of(1, 3, 1))).isTrue();
    }

    @Test
    void isAnswerCorrect_nullInput() {
        assertThat(GradingPolicy.isAnswerCorrect(null, List.of(1))).isFalse();
        Question q = new Question(1, 1, "Q", "SINGLE", Collections.emptyList(), 1, true);
        assertThat(GradingPolicy.isAnswerCorrect(q, null)).isFalse();
    }
}
