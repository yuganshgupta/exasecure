package com.examsystem.server.http.mappers;

import com.examsystem.application.query.AdminAttemptResult;
import com.examsystem.application.query.AdminEvidenceResult;
import com.examsystem.application.query.AdminExamResult;
import com.examsystem.application.query.AdminProctorEventResult;
import com.examsystem.application.query.AdminUserResult;
import com.examsystem.contracts.admin.AdminAttemptDto;
import com.examsystem.contracts.admin.AdminEvidenceDto;
import com.examsystem.contracts.admin.AdminExamDto;
import com.examsystem.contracts.admin.AdminProctorEventDto;
import com.examsystem.contracts.admin.AdminUserDto;

public final class AdminContractMapper {
    private AdminContractMapper() {}

    public static AdminUserDto toUserDto(AdminUserResult result) {
        return new AdminUserDto(
                result.id(),
                result.username(),
                result.fullName(),
                result.role(),
                result.active());
    }

    public static AdminExamDto toExamDto(AdminExamResult result) {
        return new AdminExamDto(
                result.id(),
                result.title(),
                result.durationMinutes(),
                result.createdBy(),
                result.active());
    }

    public static AdminAttemptDto toAttemptDto(AdminAttemptResult result) {
        return new AdminAttemptDto(
                result.id(),
                result.studentId(),
                result.studentName(),
                result.startTime().toString(),
                result.status(),
                result.score());
    }

    public static AdminProctorEventDto toProctorEventDto(AdminProctorEventResult result) {
        return new AdminProctorEventDto(
                result.id(),
                result.eventType(),
                result.recordedAt().toString(),
                result.hasEvidence());
    }

    public static AdminEvidenceDto toEvidenceDto(AdminEvidenceResult result) {
        return new AdminEvidenceDto(
                result.id(),
                result.eventId(),
                result.contentType(),
                result.data());
    }
}
