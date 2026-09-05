package com.examsystem.application.ports;

import com.examsystem.domain.models.Question;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;

public interface ProctorIncidentPort {
    record Result(int eventId, int violationCount, boolean flagged, String attemptStatus) {}

    /** Lock ownership, duplicate lookup, event, strike, finalization and snapshot in one transaction. */
    Result recordIncident(int studentId, int attemptId, String eventType, String clientEventId,
                          ToIntBiFunction<List<Question>, Map<Integer, List<Integer>>> grader);
}
