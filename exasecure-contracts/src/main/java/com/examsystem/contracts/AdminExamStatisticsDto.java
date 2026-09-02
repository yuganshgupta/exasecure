package com.examsystem.contracts;

import java.util.Map;

public record AdminExamStatisticsDto(
        int examId,
        String examTitle,
        int totalAttempts,
        int completedAttempts,
        double averageScore,
        int highestScore,
        int lowestScore,
        double passRate,
        Map<String, Integer> scoreDistribution
) {
    public AdminExamStatisticsDto {
        scoreDistribution = scoreDistribution == null ? Map.of() : Map.copyOf(scoreDistribution);
    }
}

