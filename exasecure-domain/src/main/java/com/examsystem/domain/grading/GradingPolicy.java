package com.examsystem.domain.grading;

import com.examsystem.domain.models.Question;
import com.examsystem.domain.models.Option;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class GradingPolicy {

    public static boolean isAnswerCorrect(Question question, List<Integer> selectedOptions) {
        if (question == null || selectedOptions == null || selectedOptions.isEmpty()
                || selectedOptions.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(selectedOptions).size() != selectedOptions.size()) {
            return false;
        }

        if ("MULTI".equals(question.type())) {
            Set<Integer> selectedSet = new TreeSet<>(selectedOptions);
            Set<Integer> correctSet = new TreeSet<>();
            List<Option> options = question.options();
            if (options == null) {
                return false;
            }
            for (Option option : options) {
                if (option.correct()) {
                    correctSet.add(option.number());
                }
            }
            return selectedSet.equals(correctSet);
        } else {
            return selectedOptions.size() == 1 && selectedOptions.get(0) == question.correctOptionNumber();
        }
    }
}
