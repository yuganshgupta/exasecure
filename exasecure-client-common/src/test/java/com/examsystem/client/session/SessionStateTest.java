package com.examsystem.client.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionStateTest {
    private SessionState sessionState;

    @BeforeEach
    void setUp() {
        sessionState = new SessionState();
    }

    @Test
    void unauthenticatedByDefault() {
        assertThat(sessionState.isAuthenticated()).isFalse();
        assertThat(sessionState.token()).isNull();
        assertThat(sessionState.isAdmin()).isFalse();
        assertThat(sessionState.isStudent()).isFalse();
    }

    @Test
    void setSession_storesStudentSession() {
        sessionState.setSession("student1", "Alice Smith", "STUDENT", "opaque-token-123");
        assertThat(sessionState.isAuthenticated()).isTrue();
        assertThat(sessionState.username()).isEqualTo("student1");
        assertThat(sessionState.fullName()).isEqualTo("Alice Smith");
        assertThat(sessionState.role()).isEqualTo("STUDENT");
        assertThat(sessionState.token()).isEqualTo("opaque-token-123");
        assertThat(sessionState.isStudent()).isTrue();
        assertThat(sessionState.isAdmin()).isFalse();
    }

    @Test
    void setSession_storesAdminSession() {
        sessionState.setSession("admin", "Admin User", "ADMIN", "opaque-admin-token");
        assertThat(sessionState.isAuthenticated()).isTrue();
        assertThat(sessionState.isAdmin()).isTrue();
        assertThat(sessionState.isStudent()).isFalse();
    }

    @Test
    void clear_removesSessionState() {
        sessionState.setSession("student1", "Alice Smith", "STUDENT", "opaque-token-123");
        sessionState.clear();
        assertThat(sessionState.isAuthenticated()).isFalse();
        assertThat(sessionState.token()).isNull();
        assertThat(sessionState.username()).isNull();
    }

    @Test
    void blankToken_throwsException() {
        assertThatThrownBy(() -> sessionState.setSession("s", "S", "STUDENT", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
