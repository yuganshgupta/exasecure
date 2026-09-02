package com.examsystem.application.ports;

import com.examsystem.contracts.admin.*;
import java.util.List;
import java.util.Optional;

public interface AdminQueryPort {
    List<AdminUserDto> listUsers();
    List<AdminExamDto> listExams();
    List<AdminAttemptDto> getAttemptHistory(int examId);
    List<AdminProctorEventDto> getProctorEvents(int attemptId);
    Optional<AdminEvidenceDto> getEvidenceByEventId(int eventId);
}
