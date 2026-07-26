package com.examsystem.models;

/** One answer option for a question. */
public class Option {
    private int id;
    private int questionId;
    private int optionNumber; // 1..4
    private String optionText;

    public Option() {}

    public Option(int id, int questionId, int optionNumber, String optionText) {
        this.id = id;
        this.questionId = questionId;
        this.optionNumber = optionNumber;
        this.optionText = optionText;
    }

    public int getId() { return id; }
    public int getQuestionId() { return questionId; }
    public int getOptionNumber() { return optionNumber; }
    public String getOptionText() { return optionText; }

    public void setId(int id) { this.id = id; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    public void setOptionNumber(int optionNumber) { this.optionNumber = optionNumber; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
}