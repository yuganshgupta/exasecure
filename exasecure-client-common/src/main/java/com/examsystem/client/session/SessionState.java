package com.examsystem.client.session;

import java.util.concurrent.atomic.AtomicReference;

public class SessionState {
    private static final SessionState INSTANCE = new SessionState();

    public static SessionState getInstance() {
        return INSTANCE;
    }

    public record UserSession(
            String username,
            String fullName,
            String role,
            String token
    ) {}

    private final AtomicReference<UserSession> currentSession = new AtomicReference<>(null);

    public void setSession(String username, String fullName, String role, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Session token cannot be blank");
        }
        currentSession.set(new UserSession(username, fullName, role, token));
    }

    public void clear() {
        currentSession.set(null);
    }

    public void clearIfCurrent(UserSession expected) {
        currentSession.compareAndSet(expected, null);
    }

    public boolean isAuthenticated() {
        return currentSession.get() != null;
    }

    public boolean isAdmin() {
        UserSession s = currentSession.get();
        return s != null && "ADMIN".equalsIgnoreCase(s.role());
    }

    public boolean isStudent() {
        UserSession s = currentSession.get();
        return s != null && "STUDENT".equalsIgnoreCase(s.role());
    }

    public String token() {
        UserSession s = currentSession.get();
        return s != null ? s.token() : null;
    }

    public String username() {
        UserSession s = currentSession.get();
        return s != null ? s.username() : null;
    }

    public String fullName() {
        UserSession s = currentSession.get();
        return s != null ? s.fullName() : null;
    }

    public String role() {
        UserSession s = currentSession.get();
        return s != null ? s.role() : null;
    }

    public UserSession get() {
        return currentSession.get();
    }
}
