package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.AdminQueryPort;
import com.examsystem.application.commands.AdminCreateUserCommand;
import com.examsystem.application.commands.AdminUpdateUserCommand;
import com.examsystem.application.usecases.AdminUserMutationUseCase;
import com.examsystem.contracts.admin.AdminCreateUserRequestDto;
import com.examsystem.contracts.admin.AdminEvidenceDto;
import com.examsystem.contracts.admin.AdminMutationResultDto;
import com.examsystem.contracts.admin.AdminUpdateUserRequestDto;
import com.examsystem.domain.models.User;
import com.examsystem.server.http.mappers.AdminContractMapper;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

public class AdminController {
    private final AdminQueryPort adminQueryPort;
    private final AdminUserMutationUseCase userMutationUseCase;

    public AdminController(AdminQueryPort adminQueryPort, AdminUserMutationUseCase userMutationUseCase) {
        this.adminQueryPort = adminQueryPort;
        this.userMutationUseCase = userMutationUseCase;
    }

    public void listExams(Context ctx) {
        ctx.json(adminQueryPort.listExams().stream()
                .map(AdminContractMapper::toExamDto)
                .toList());
    }

    public void listUsers(Context ctx) {
        ctx.json(adminQueryPort.listUsers().stream()
                .map(AdminContractMapper::toUserDto)
                .toList());
    }

    public void createUser(Context ctx) {
        AdminCreateUserRequestDto request = ctx.bodyAsClass(AdminCreateUserRequestDto.class);
        int id = userMutationUseCase.createUser(new AdminCreateUserCommand(
                request.username(), request.password(), request.fullName(),
                request.enrollmentNumber(), request.section(), parseRole(request.role())));
        ctx.status(201).json(new AdminMutationResultDto(id));
    }

    public void updateUser(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("userId"));
        AdminUpdateUserRequestDto request = ctx.bodyAsClass(AdminUpdateUserRequestDto.class);
        boolean updated = userMutationUseCase.updateUser(new AdminUpdateUserCommand(
                id, request.username(), request.password(), request.fullName(),
                request.enrollmentNumber(), request.section(), parseRole(request.role())));
        if (!updated) {
            throw new NotFoundResponse("User not found");
        }
        ctx.status(204);
    }

    public void deactivateUser(Context ctx) {
        int id = Integer.parseInt(ctx.pathParam("userId"));
        User currentUser = ctx.attribute("user");
        if (currentUser.id() == id) {
            throw new ForbiddenResponse("Cannot deactivate your own account");
        }
        if (!userMutationUseCase.deactivateUser(id)) {
            throw new NotFoundResponse("Active user not found");
        }
        ctx.status(204);
    }

    private static User.Role parseRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        try {
            return User.Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Role must be ADMIN or STUDENT");
        }
    }

    public void getAttemptHistory(Context ctx) {
        int examId = Integer.parseInt(ctx.pathParam("examId"));
        ctx.json(adminQueryPort.getAttemptHistory(examId).stream()
                .map(AdminContractMapper::toAttemptDto)
                .toList());
    }

    public void getProctorEvents(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        ctx.json(adminQueryPort.getProctorEvents(attemptId).stream()
                .map(AdminContractMapper::toProctorEventDto)
                .toList());
    }

    public void getAttemptEvidence(Context ctx) {
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        AdminEvidenceDto evidence = adminQueryPort.getEvidenceByEventId(eventId)
                .map(AdminContractMapper::toEvidenceDto)
                .orElseThrow(() -> new NotFoundResponse("No evidence found"));
        ctx.contentType(evidence.contentType()).result(evidence.data());
    }
}
