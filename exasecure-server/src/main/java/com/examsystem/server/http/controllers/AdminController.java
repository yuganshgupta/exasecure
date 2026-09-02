package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.AdminQueryPort;
import com.examsystem.contracts.admin.AdminEvidenceDto;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

public class AdminController {
    private final AdminQueryPort adminQueryPort;

    public AdminController(AdminQueryPort adminQueryPort) {
        this.adminQueryPort = adminQueryPort;
    }

    public void listExams(Context ctx) {
        ctx.json(adminQueryPort.listExams());
    }

    public void listUsers(Context ctx) {
        ctx.json(adminQueryPort.listUsers());
    }
    
    public void getAttemptHistory(Context ctx) {
        int examId = Integer.parseInt(ctx.pathParam("examId"));
        ctx.json(adminQueryPort.getAttemptHistory(examId));
    }
    
    public void getProctorEvents(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        ctx.json(adminQueryPort.getProctorEvents(attemptId));
    }

    public void getAttemptEvidence(Context ctx) {
        // Wait, the new API fetches evidence by eventId, not attemptId. Let's make an endpoint for that.
        // Actually, I can just use getEvidenceByEventId directly.
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        AdminEvidenceDto evidence = adminQueryPort.getEvidenceByEventId(eventId)
            .orElseThrow(() -> new NotFoundResponse("No evidence found"));
        ctx.contentType(evidence.contentType()).result(evidence.data());
    }
}
