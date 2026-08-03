package com.examsystem.models;

import java.sql.Timestamp;

/** One recorded answer for a student's attempt. */
public class StudentAnswer {
    private int id;
    private int attemptId;
    private int questionId;
    private String selectedOptions; // CHANGED
    private boolean correct;
    private Timestamp answerTimestamp;

    public StudentAnswer() {}

    public StudentAnswer(int id, int attemptId, int questionId, String selectedOptions, boolean correct, Timestamp answerTimestamp) {
        this.id = id;
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.selectedOptions = selectedOptions;
        this.correct = correct;
        this.answerTimestamp = answerTimestamp;
    }

    public int getId() { return id; }
    public int getAttemptId() { return attemptId; }
    public int getQuestionId() { return questionId; }
    public String getSelectedOptions() { return selectedOptions; }
    public boolean isCorrect() { return correct; }
    public Timestamp getAnswerTimestamp() { return answerTimestamp; }

    public void setId(int id) { this.id = id; }
    public void setAttemptId(int attemptId) { this.attemptId = attemptId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    public void setSelectedOptions(String selectedOptions) { this.selectedOptions = selectedOptions; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public void setAnswerTimestamp(Timestamp answerTimestamp) { this.answerTimestamp = answerTimestamp; }
}