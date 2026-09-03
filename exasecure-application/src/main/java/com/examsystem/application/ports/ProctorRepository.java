package com.examsystem.application.ports;

import java.time.Instant;

public interface ProctorRepository {
    int saveEvent(int attemptId, String eventType, Instant timestamp);
    void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp);
    boolean eventBelongsToAttempt(int eventId, int attemptId);
    int countEvents(int attemptId, String eventType);

    default int saveEvent(int attemptId, String eventType, Instant timestamp, String clientEventId) {
        return saveEvent(attemptId, eventType, timestamp);
    }

    default java.util.Optional<Integer> findEventByClientEventId(int attemptId, String clientEventId) {
        return java.util.Optional.empty();
    }
}
