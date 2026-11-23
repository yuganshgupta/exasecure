package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiException;
import com.examsystem.contracts.StudentAttemptHistoryDto;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class SubmissionRecovery {
    enum Status { SUBMITTED, EXPIRED, TOO_EARLY, SIGN_IN_REQUIRED, UNCONFIRMED }

    record Result(Status status, Integer score) {}

    private SubmissionRecovery() {}

    static Result submit(int attemptId, IntSupplier send, Supplier<List<StudentAttemptHistoryDto>> history) {
        try {
            return new Result(Status.SUBMITTED, send.getAsInt());
        } catch (ApiException failure) {
            if ("ATTEMPT_DEADLINE_NOT_REACHED".equals(failure.getErrorCode())) {
                return new Result(Status.TOO_EARLY, null);
            }
            if (failure.isUnauthorized()) return new Result(Status.SIGN_IN_REQUIRED, null);
        }

        try {
            for (var attempt : history.get()) {
                if (attempt.attemptId() != attemptId) continue;
                if ("SUBMITTED".equals(attempt.status())) return new Result(Status.SUBMITTED, attempt.score());
                if ("EXPIRED".equals(attempt.status())) return new Result(Status.EXPIRED, null);
                // A still-active row can be observed before the original submission commits.
                break;
            }
        } catch (ApiException failure) {
            if (failure.isUnauthorized()) return new Result(Status.SIGN_IN_REQUIRED, null);
        }
        return new Result(Status.UNCONFIRMED, null);
    }
}
