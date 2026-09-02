package com.examsystem.application.ports;

import com.examsystem.application.query.AdminAttemptResult;
import com.examsystem.application.query.AdminEvidenceResult;
import com.examsystem.application.query.AdminExamResult;
import com.examsystem.application.query.AdminProctorEventResult;
import com.examsystem.application.query.AdminUserResult;
import java.util.List;
import java.util.Optional;

public interface AdminQueryPort {
    List<AdminUserResult> listUsers();
    List<AdminExamResult> listExams();
    List<AdminAttemptResult> getAttemptHistory(int examId);
    List<AdminProctorEventResult> getProctorEvents(int attemptId);
    Optional<AdminEvidenceResult> getEvidenceByEventId(int eventId);
}
