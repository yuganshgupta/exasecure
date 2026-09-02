package com.examsystem.server.auth;

import com.examsystem.domain.models.User;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.ForbiddenResponse;

import java.util.Optional;

public class AuthMiddleware {
    private final SessionManager sessionManager;

    public AuthMiddleware(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void enforceAuthentication(Context ctx) {
        // Skip auth for login/health
        if (ctx.path().equals("/api/v1/auth/login") || ctx.path().equals("/health")) {
            return;
        }

        String token = ctx.cookie("session_token");
        if (token == null) {
            token = ctx.header("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }

        if (token == null) {
            throw new UnauthorizedResponse("No session token provided");
        }

        Optional<User> userOpt = sessionManager.authenticate(token);
        if (userOpt.isEmpty()) {
            throw new UnauthorizedResponse("Invalid or expired session");
        }

        User user = userOpt.get();
        ctx.attribute("user", user);

        // Basic role check based on path
        if (ctx.path().startsWith("/api/v1/admin") && user.role() != User.Role.ADMIN) {
            throw new ForbiddenResponse("Admin privileges required");
        }
        if (ctx.path().startsWith("/api/v1/student") && user.role() != User.Role.STUDENT) {
            throw new ForbiddenResponse("Student privileges required");
        }
    }
}
