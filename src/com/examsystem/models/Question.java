package com.examsystem.models;

/** Represents a multiple-choice question. */
public class Question {
    private int id;
    private int examId;
    private String questionText;
    private int correctOptionNumber; // 1..4
    private java.util.List<Option> options = new java.util.ArrayList<>(); // NEW
    private String questionType = "SINGLE"; // NEW (SINGLE or MULTI)

    public Question() {}

    public Question(int id, int examId, String questionText, int correctOptionNumber) {
        this.id = id;
        this.examId = examId;
        this.questionText = questionText;
        this.correctOptionNumber = correctOptionNumber;
    }

    public Question(int id, int examId, String questionText, int correctOptionNumber, String questionType) {
        this(id, examId, questionText, correctOptionNumber);
        this.questionType = questionType != null ? questionType : "SINGLE";
    }

    public int getId() { return id; }
    public int getExamId() { return examId; }
    public String getQuestionText() { return questionText; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public java.util.List<Option> getOptions() { return options; }
    public String getQuestionType() { return questionType; }

    public void setId(int id) { this.id = id; }
    public void setExamId(int examId) { this.examId = examId; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setCorrectOptionNumber(int correctOptionNumber) { this.correctOptionNumber = correctOptionNumber; }
    public void setOptions(java.util.List<Option> options) { this.options = options; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
}