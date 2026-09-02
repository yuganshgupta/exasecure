package com.examsystem.server.http.controllers;

import com.examsystem.application.usecases.AuthenticateUserUseCase;
import com.examsystem.contracts.AuthRequestDto;
import com.examsystem.contracts.AuthResponseDto;
import com.examsystem.domain.models.User;
import com.examsystem.server.auth.SessionManager;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

import java.util.Optional;

public class AuthController {
    private final AuthenticateUserUseCase authUseCase;
    private final SessionManager sessionManager;

    public AuthController(AuthenticateUserUseCase authUseCase, SessionManager sessionManager) {
        this.authUseCase = authUseCase;
        this.sessionManager = sessionManager;
    }

    public void login(Context ctx) {
        AuthRequestDto req = ctx.bodyAsClass(AuthRequestDto.class);
        Optional<User> userOpt = authUseCase.authenticate(req.username(), req.password());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = sessionManager.createSession(user);
            // Optionally set cookie
            ctx.cookie("session_token", token, 86400); // 1 day
            ctx.json(new AuthResponseDto(token, user.role().name(), user.fullName()));
        } else {
            throw new UnauthorizedResponse("Invalid credentials");
        }
    }

    public void logout(Context ctx) {
        String token = ctx.cookie("session_token");
        if (token == null) {
            token = ctx.header("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        if (token != null) {
            sessionManager.invalidate(token);
        }
        ctx.removeCookie("session_token");
        ctx.status(204);
    }
}
