package com.examsystem.models;

import java.sql.Timestamp;

/** A student's attempt at an exam. */
public class ExamAttempt {
    private int id;
    private int examId;
    private int studentId;
    private Timestamp startTime;
    private Timestamp endTime;
    private Integer score;

    public ExamAttempt() {}

    public ExamAttempt(int id, int examId, int studentId, Timestamp startTime, Timestamp endTime, Integer score) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.score = score;
    }

    public int getId() { return id; }
    public int getExamId() { return examId; }
    public int getStudentId() { return studentId; }
    public Timestamp getStartTime() { return startTime; }
    public Timestamp getEndTime() { return endTime; }
    public Integer getScore() { return score; }

    public void setId(int id) { this.id = id; }
    public void setExamId(int examId) { this.examId = examId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
    public void setScore(Integer score) { this.score = score; }
}