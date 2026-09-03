package com.examsystem.contracts.admin;

public record AdminAttemptDto(
        int id,
        int studentId,
        String studentName,
        String startTime,
        String status,
        Integer score,
        int focusLostCount,
        boolean flagged,
        String proctoringStatus
) {
    public AdminAttemptDto(int id, int studentId, String studentName, String startTime, String status, Integer score) {
        this(id, studentId, studentName, startTime, status, score, 0, false, "CLEAN");
    }
}
