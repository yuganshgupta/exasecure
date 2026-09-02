package com.examsystem.domain.models;

import java.time.Duration;

public record Exam(
    int id,
    String title,
    Duration duration,
    int createdBy,
    boolean isActive
) {}
