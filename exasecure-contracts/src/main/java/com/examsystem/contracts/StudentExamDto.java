package com.examsystem.contracts;

public record StudentExamDto(
        int id,
        String title,
        int durationMinutes
) {}
