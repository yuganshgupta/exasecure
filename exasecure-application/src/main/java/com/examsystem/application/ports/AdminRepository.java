package com.examsystem.application.ports;

import java.util.List;
import java.util.Map;

public interface AdminRepository {
    List<Map<String, Object>> listUsers();
    List<Map<String, Object>> listExams();
    List<Map<String, Object>> getAttemptHistory(int examId);
    List<Map<String, Object>> getProctorEvents(int attemptId);
    Map<String, Object> getEvidence(int eventId);
}
