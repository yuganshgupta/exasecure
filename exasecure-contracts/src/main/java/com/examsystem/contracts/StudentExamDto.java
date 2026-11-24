package com.examsystem.contracts;

public record StudentExamDto(
        int id,
        String title,
        int durationMinutes,
        boolean resumeOnly
) {
    public StudentExamDto(int id, String title, int durationMinutes) {
        this(id, title, durationMinutes, false);
    }
}
