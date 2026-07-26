package com.examsystem.models;

import java.sql.Timestamp;

public class AttemptSummary {
    private final int attemptId;
    private final String studentName;
    private final String username;
    private final String enrollmentNumber; // New
    private final String section;          // New
    private final int score;
    private final Timestamp startTime;
    private final Timestamp endTime;
    private final int focusLostCount;      // New

    public AttemptSummary(int attemptId, String studentName, String username, 
                          String enrollmentNumber, String section,
                          int score, Timestamp startTime, Timestamp endTime, int focusLostCount) {
        this.attemptId = attemptId;
        this.studentName = studentName;
        this.username = username;
        this.enrollmentNumber = enrollmentNumber;
        this.section = section;
        this.score = score;
        this.startTime = startTime;
        this.endTime = endTime;
        this.focusLostCount = focusLostCount;
    }

    public int getAttemptId() { return attemptId; }
    public String getStudentName() { return studentName; }
    public String getUsername() { return username; }
    public String getEnrollmentNumber() { return enrollmentNumber; }
    public String getSection() { return section; }
    public int getScore() { return score; }
    public Timestamp getStartTime() { return startTime; }
    public Timestamp getEndTime() { return endTime; }
    public int getFocusLostCount() { return focusLostCount; }
}