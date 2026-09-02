package com.examsystem.application.query;

public record AdminExamResult(
        int id,
        String title,
        int durationMinutes,
        int createdBy,
        boolean active,
        int versionNumber,
        Integer supersedesExamId
) {}
