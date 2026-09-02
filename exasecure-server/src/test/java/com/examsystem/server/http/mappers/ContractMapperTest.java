package com.examsystem.server.http.mappers;

import com.examsystem.application.query.AdminAttemptResult;
import com.examsystem.application.query.AdminEvidenceResult;
import com.examsystem.application.query.AdminExamResult;
import com.examsystem.application.query.AdminProctorEventResult;
import com.examsystem.application.query.AdminUserResult;
import com.examsystem.application.query.StudentAttemptHistoryResult;
import com.examsystem.contracts.StudentAttemptHistoryDto;
import com.examsystem.contracts.admin.AdminAttemptDto;
import com.examsystem.contracts.admin.AdminExamDto;
import com.examsystem.contracts.admin.AdminProctorEventDto;
import com.examsystem.contracts.admin.AdminUserDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ContractMapperTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-02T12:30:00Z");

    @Test
    void mapsAdminQueryResultsToNetworkDtos() {
        assertThat(AdminContractMapper.toUserDto(
                new AdminUserResult(1, "admin", "Admin User", "ADMIN", true)))
                .isEqualTo(new AdminUserDto(1, "admin", "Admin User", "ADMIN", true));

        assertThat(AdminContractMapper.toExamDto(
                new AdminExamResult(2, "Final", 60, 1, true)))
                .isEqualTo(new AdminExamDto(2, "Final", 60, 1, true));

        assertThat(AdminContractMapper.toAttemptDto(
                new AdminAttemptResult(3, 4, "Student User", STARTED_AT, "SUBMITTED", 8)))
                .isEqualTo(new AdminAttemptDto(3, 4, "Student User", STARTED_AT.toString(), "SUBMITTED", 8));

        assertThat(AdminContractMapper.toProctorEventDto(
                new AdminProctorEventResult(5, "FOCUS_LOST", STARTED_AT, true)))
                .isEqualTo(new AdminProctorEventDto(5, "FOCUS_LOST", STARTED_AT.toString(), true));
    }

    @Test
    void mapsEvidenceWithoutSharingTheApplicationResultBuffer() {
        byte[] source = {1, 2, 3, 4};
        AdminEvidenceResult result = new AdminEvidenceResult(6, 5, "image/png", source);
        source[0] = 99;

        byte[] mapped = AdminContractMapper.toEvidenceDto(result).data();
        mapped[1] = 99;

        assertThat(result.data()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void mapsStudentHistoryAndPreservesNullableEndTime() {
        StudentAttemptHistoryDto dto = StudentContractMapper.toAttemptHistoryDto(
                new StudentAttemptHistoryResult("Final", 3, STARTED_AT, null, null, "ACTIVE"));

        assertThat(dto).isEqualTo(new StudentAttemptHistoryDto(
                "Final", 3, STARTED_AT.toString(), null, null, "ACTIVE"));
    }
}
