package com.examsystem.application.ports;

import java.time.Instant;
import java.util.Optional;

public interface ProctorRepository {
    int saveEvent(int attemptId, String eventType, Instant timestamp);
    void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp);
    boolean eventBelongsToAttempt(int eventId, int attemptId);
    int countEvents(int attemptId, String eventType);

    default int saveEvent(int attemptId, String eventType, Instant timestamp, String clientEventId) {
        return saveEvent(attemptId, eventType, timestamp);
    }

    default Optional<Integer> findEventByClientEventId(int attemptId, String clientEventId) {
        return Optional.empty();
    }

    default void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp, String evidenceType) {
        saveEvidence(eventId, screenshot, contentType, timestamp);
    }

    default void recordReturn(int eventId, Instant returnedAt, long awayDurationMs) {}

    default void markLongAbsence(int eventId) {}

    record ProctorEventRecord(int id, int attemptId, String eventType, Instant recordedAt, Instant returnedAt, Long awayDurationMs, boolean isLongAbsence) {}

    default Optional<ProctorEventRecord> findEventById(int eventId) {
        return Optional.empty();
    }
}
