package com.examsystem.application.query;

import java.util.Map;

public record AdminExamStatisticsResult(
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
    public AdminExamStatisticsResult {
        scoreDistribution = scoreDistribution == null ? Map.of() : Map.copyOf(scoreDistribution);
    }
}

