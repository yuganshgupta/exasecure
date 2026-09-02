package com.examsystem.contracts;

public record StudentAttemptHistoryDto(String examTitle, int attemptId, String startTime, String endTime, Integer score, String status) {}
