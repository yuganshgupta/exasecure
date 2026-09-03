package com.examsystem.contracts;

public record StudentProctorEventResponseDto(
        int eventId,
        int violationCount,
        boolean flagged,
        String attemptStatus
) {}
