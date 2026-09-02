package com.examsystem.contracts.admin;

public record AdminAttemptDto(int id, int studentId, String studentName, String startTime, String status, Integer score) {}
