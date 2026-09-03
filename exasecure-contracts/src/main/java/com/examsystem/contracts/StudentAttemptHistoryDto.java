package com.examsystem.contracts;

public record StudentAttemptHistoryDto(
        String examTitle,
        int attemptId,
        String startTime,
        String endTime,
        Integer score,
        String status,
        boolean flagged
) {
    public StudentAttemptHistoryDto(String examTitle, int attemptId, String startTime, String endTime, Integer score, String status) {
        this(examTitle, attemptId, startTime, endTime, score, status, false);
    }
}
