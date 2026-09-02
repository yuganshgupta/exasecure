package com.examsystem.contracts;

import java.util.Map;

public record AdminExamStatisticsDto(
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
    public AdminExamStatisticsDto {
        scoreDistribution = scoreDistribution == null ? Map.of() : Map.copyOf(scoreDistribution);
    }
}
