package com.examsystem.server.http.mappers;

import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.contracts.StudentAttemptHistoryDto;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.domain.models.ExamAttempt;

public final class StudentContractMapper {
    private StudentContractMapper() {}

    /** Maps only successful active start/resume results; this is not a lifecycle mapper. */
    public static StudentAttemptDto toAttemptDto(ExamAttempt attempt) {
        return new StudentAttemptDto(attempt.id(), attempt.examId(), attempt.studentId(),
                attempt.startTime(), null, null, "ACTIVE");
    }

    public static StudentAttemptHistoryDto toAttemptHistoryDto(StudentAttemptHistoryResult result) {
        return new StudentAttemptHistoryDto(
                result.examTitle(),
                result.attemptId(),
                result.startTime().toString(),
                result.endTime() == null ? null : result.endTime().toString(),
                result.score(),
                result.status(),
                result.flagged());
    }
}
