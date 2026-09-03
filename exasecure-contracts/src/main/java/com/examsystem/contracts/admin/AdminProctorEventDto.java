package com.examsystem.contracts.admin;

import java.util.List;

public record AdminProctorEventDto(
        int id,
        String eventType,
        String recordedAt,
        String returnedAt,
        Long awayDurationMs,
        boolean isLongAbsence,
        boolean hasEvidence,
        List<String> evidenceTypes
) {
    public AdminProctorEventDto(int id, String eventType, String recordedAt, boolean hasEvidence) {
        this(id, eventType, recordedAt, null, null, false, hasEvidence, hasEvidence ? List.of("INITIAL") : List.of());
    }
}
