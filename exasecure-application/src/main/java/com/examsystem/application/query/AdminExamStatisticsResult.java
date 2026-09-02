package com.examsystem.application.query;

import java.util.Map;

public record AdminExamStatisticsResult(
        int examId,
        String examTitle,
        int totalAttempts,
        int submittedAttempts,
        int activeAttempts,
        int expiredAttempts,
        Double averageScore,
        Integer highestScore,
        Integer lowestScore,
        Map<String, Integer> scoreDistribution
) {
    public AdminExamStatisticsResult {
        scoreDistribution = scoreDistribution == null ? Map.of() : Map.copyOf(scoreDistribution);
    }
}
