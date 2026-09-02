package com.examsystem.contracts.admin;

public record AdminExamDto(int id, String title, int durationMinutes, int createdBy, boolean isActive) {}
