package com.examsystem.application.ports;

import java.time.Instant;

public interface ProctorRepository {
    void saveEvent(int attemptId, String eventType, Instant timestamp);
    void saveEvidence(int attemptId, byte[] screenshot, Instant timestamp);
    int countEvents(int attemptId, String eventType);
}
