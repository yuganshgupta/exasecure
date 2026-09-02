package com.examsystem.application.ports;

import java.time.Instant;

public interface ProctorRepository {
    int saveEvent(int attemptId, String eventType, Instant timestamp);
    void saveEvidence(int eventId, byte[] screenshot, String contentType, Instant timestamp);
    int countEvents(int attemptId, String eventType);
}
