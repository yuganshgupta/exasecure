package com.examsystem.server.http.mappers;

import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.contracts.StudentAttemptHistoryDto;

public final class StudentContractMapper {
    private StudentContractMapper() {}

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
