package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.AdminQueryPort;
import com.examsystem.contracts.admin.AdminEvidenceDto;
import com.examsystem.server.http.mappers.AdminContractMapper;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

public class AdminController {
    private final AdminQueryPort adminQueryPort;

    public AdminController(AdminQueryPort adminQueryPort) {
        this.adminQueryPort = adminQueryPort;
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
