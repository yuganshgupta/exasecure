package com.examsystem.contracts.admin;

public record AdminProctorEventDto(int id, String eventType, String recordedAt, boolean hasEvidence) {}
