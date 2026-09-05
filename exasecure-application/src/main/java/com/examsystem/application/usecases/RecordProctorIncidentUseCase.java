package com.examsystem.application.usecases;

import com.examsystem.application.ports.ProctorIncidentPort;
import com.examsystem.domain.grading.GradingPolicy;
import com.examsystem.domain.models.User;

public final class RecordProctorIncidentUseCase {
    private final ProctorIncidentPort port;
    public RecordProctorIncidentUseCase(ProctorIncidentPort port) { this.port = port; }

    public ProctorIncidentPort.Result record(User student, int attemptId, String eventType, String clientEventId) {
        if (student == null || student.role() != User.Role.STUDENT) throw new SecurityException("Student access required");
        return port.recordIncident(student.id(), attemptId, eventType, clientEventId, (questions, answers) -> {
            int correct = 0;
            for (var question : questions) {
                if (GradingPolicy.isAnswerCorrect(question, answers.getOrDefault(question.id(), java.util.List.of()))) correct++;
            }
            return correct;
        });
    }
}
