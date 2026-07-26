package com.examsystem.models;

public class QuestionStatistic {
    private final String questionText;
    private final int totalAttempts;
    private final int correctCount;

    public QuestionStatistic(String questionText, int totalAttempts, int correctCount) {
        this.questionText = questionText;
        this.totalAttempts = totalAttempts;
        this.correctCount = correctCount;
    }
    public String getQuestionText() { return questionText; }
    public int getTotalAttempts() { return totalAttempts; }
    public int getCorrectCount() { return correctCount; }
    public double getCorrectPercentage() {
        return totalAttempts == 0 ? 0.0 : (double) correctCount / totalAttempts * 100.0;
    }
}