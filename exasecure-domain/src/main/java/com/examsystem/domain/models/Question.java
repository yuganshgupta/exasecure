package com.examsystem.domain.models;

import java.util.List;

public record Question(
    int id,
    int examId,
    String text,
    String type,
    List<Option> options,
    int correctOptionNumber,
    boolean isActive
) {}
