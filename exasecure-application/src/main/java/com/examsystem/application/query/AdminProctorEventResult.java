package com.examsystem.application.query;

import java.time.Instant;

public record AdminProctorEventResult(
        int id,
        String eventType,
        Instant recordedAt,
        boolean hasEvidence
) {}
