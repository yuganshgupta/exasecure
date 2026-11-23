package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiException;
import com.examsystem.contracts.StudentAttemptHistoryDto;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionRecoveryTest {
    @Test
    void ordinarySuccessNeedsNoHistoryRequest() {
        var result = SubmissionRecovery.submit(7, () -> 0, () -> {
            throw new AssertionError("history must not be requested");
        });
        assertThat(result).isEqualTo(new SubmissionRecovery.Result(SubmissionRecovery.Status.SUBMITTED, 0));
    }

    @Test
    void lostResponseUsesTheSameAttemptsCommittedScore() {
        var result = SubmissionRecovery.submit(7, SubmissionRecoveryTest::lostResponse,
                () -> List.of(attempt(8, "SUBMITTED", 9), attempt(7, "SUBMITTED", 3)));
        assertThat(result).isEqualTo(new SubmissionRecovery.Result(SubmissionRecovery.Status.SUBMITTED, 3));
    }

    @Test
    void activeObservationDoesNotVerifyAnOutstandingSubmission() {
        var attempts = new AtomicInteger();
        var first = SubmissionRecovery.submit(7, () -> {
            attempts.incrementAndGet();
            return lostResponse();
        }, () -> List.of(attempt(7, "ACTIVE", null)));
        assertThat(first.status()).isEqualTo(SubmissionRecovery.Status.UNCONFIRMED);

        var retry = SubmissionRecovery.submit(7, () -> {
            attempts.incrementAndGet();
            return 2;
        }, List::of);
        assertThat(attempts).hasValue(2);
        assertThat(retry).isEqualTo(new SubmissionRecovery.Result(SubmissionRecovery.Status.SUBMITTED, 2));
    }

    @Test
    void unavailableHistoryLeavesSubmissionUnconfirmed() {
        var result = SubmissionRecovery.submit(7, SubmissionRecoveryTest::lostResponse, () -> {
            throw new ApiException(503, "Server unavailable");
        });
        assertThat(result.status()).isEqualTo(SubmissionRecovery.Status.UNCONFIRMED);
    }

    @Test
    void historicalExpiryDoesNotDisplayItsPlaceholderScore() {
        var result = SubmissionRecovery.submit(7, () -> {
            throw new ApiException(409, "Attempt cannot be graded");
        }, () -> List.of(attempt(7, "EXPIRED", 0)));
        assertThat(result).isEqualTo(new SubmissionRecovery.Result(SubmissionRecovery.Status.EXPIRED, null));
    }

    @Test
    void definitiveEarlyRejectionAndExpiredAuthenticationHaveDifferentRecovery() {
        var early = SubmissionRecovery.submit(7, () -> {
            throw new ApiException(409, "Deadline not reached", "ATTEMPT_DEADLINE_NOT_REACHED");
        }, List::of);
        var unauthorized = SubmissionRecovery.submit(7, SubmissionRecoveryTest::lostResponse, () -> {
            throw new ApiException(401, "Session expired");
        });
        assertThat(early.status()).isEqualTo(SubmissionRecovery.Status.TOO_EARLY);
        assertThat(unauthorized.status()).isEqualTo(SubmissionRecovery.Status.SIGN_IN_REQUIRED);
    }

    private static int lostResponse() {
        throw new ApiException("response lost", new java.net.http.HttpTimeoutException("deadline exceeded"));
    }

    private static StudentAttemptHistoryDto attempt(int id, String status, Integer score) {
        return new StudentAttemptHistoryDto("Exam", id, null, null, score, status);
    }
}
