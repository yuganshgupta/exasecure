package com.examsystem.client.admin.views;

import com.examsystem.client.session.SessionState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceRequestTest {
    @Test
    void rejectsAResponseFromAnEarlierParentSelection() {
        var session = new SessionState.UserSession("reviewer", "Reviewer", "ADMIN", "synthetic");
        var request = new EvidenceRequest(7, session);
        assertThat(request.isCurrent(7, session)).isTrue();
        assertThat(request.isCurrent(8, session)).isFalse();
    }

    @Test
    void rejectsAnEarlierSessionEvenWhenTheSameUserSignsInAgain() {
        var sessions = new SessionState();
        sessions.setSession("reviewer", "Reviewer", "ADMIN", "synthetic");
        var request = new EvidenceRequest(7, sessions.get());
        sessions.clear();
        assertThat(request.isCurrent(7, sessions.get())).isFalse();
        sessions.setSession("reviewer", "Reviewer", "ADMIN", "synthetic");
        assertThat(request.isCurrent(7, sessions.get())).isFalse();
    }
}
