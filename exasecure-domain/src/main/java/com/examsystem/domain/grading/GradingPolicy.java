package com.examsystem.domain.grading;

import com.examsystem.domain.models.Question;
import com.examsystem.domain.models.Option;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class GradingPolicy {

    public static boolean isAnswerCorrect(Question question, List<Integer> selectedOptions) {
        if (question == null || selectedOptions == null || selectedOptions.isEmpty()) {
            return false;
        }

        if ("MULTI".equals(question.type())) {
            Set<Integer> selectedSet = new TreeSet<>(selectedOptions);
            Set<Integer> correctSet = new TreeSet<>();
            List<Option> options = question.options();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).correct()) {
                    correctSet.add(i + 1);
                }
            }
            return selectedSet.equals(correctSet);
        } else {
            return selectedOptions.size() == 1 && selectedOptions.get(0) == question.correctOptionNumber();
        }
    }
}
