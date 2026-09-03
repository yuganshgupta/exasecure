package com.examsystem.application.query;

import java.time.Instant;
import java.util.List;

public record AdminProctorEventResult(
        int id,
        String eventType,
        Instant recordedAt,
        Instant returnedAt,
        Long awayDurationMs,
        boolean isLongAbsence,
        boolean hasEvidence,
        List<String> evidenceTypes
) {
    public AdminProctorEventResult(int id, String eventType, Instant recordedAt, boolean hasEvidence) {
        this(id, eventType, recordedAt, null, null, false, hasEvidence, hasEvidence ? List.of("INITIAL") : List.of());
    }
}
