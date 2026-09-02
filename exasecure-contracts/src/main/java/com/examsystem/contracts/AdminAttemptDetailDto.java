package com.examsystem.contracts;

import java.util.List;

public record AdminAttemptDetailDto(
        int questionId,
        int questionNumber,
        String questionText,
        String questionType,
        int marks,
        List<Integer> selectedOptionNumbers,
        List<String> selectedOptionTexts,
        List<Integer> correctOptionNumbers,
        List<String> correctOptionTexts,
        boolean isCorrect,
        int marksAwarded
) {
    public AdminAttemptDetailDto {
        selectedOptionNumbers = selectedOptionNumbers == null ? List.of() : List.copyOf(selectedOptionNumbers);
        selectedOptionTexts = selectedOptionTexts == null ? List.of() : List.copyOf(selectedOptionTexts);
        correctOptionNumbers = correctOptionNumbers == null ? List.of() : List.copyOf(correctOptionNumbers);
        correctOptionTexts = correctOptionTexts == null ? List.of() : List.copyOf(correctOptionTexts);
    }
}

