package com.examsystem.models;

public class StudentAnswerDetail {
    private final String questionText;
    private final String selectedOptionText;
    private final String correctOptionText;
    private final boolean isCorrect;

    public StudentAnswerDetail(String questionText, String selectedOptionText, String correctOptionText, boolean isCorrect) {
        this.questionText = questionText;
        this.selectedOptionText = selectedOptionText;
        this.correctOptionText = correctOptionText;
        this.isCorrect = isCorrect;
    }
    public String getQuestionText() { return questionText; }
    public String getSelectedOptionText() { return selectedOptionText; }
    public String getCorrectOptionText() { return correctOptionText; }
    public boolean isCorrect() { return isCorrect; }
}