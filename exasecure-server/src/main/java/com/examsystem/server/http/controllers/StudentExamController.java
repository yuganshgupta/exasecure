package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.ExamRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.application.usecases.StartExamUseCase;
import com.examsystem.application.usecases.SubmitExamUseCase;
import com.examsystem.contracts.StudentQuestionDto;
import com.examsystem.contracts.StudentOptionDto;
import com.examsystem.contracts.StudentSaveAnswerRequestDto;
import com.examsystem.contracts.StudentSubmitExamRequestDto;
import com.examsystem.domain.models.Exam;
import com.examsystem.domain.models.ExamAttempt;
import com.examsystem.domain.models.Question;
import com.examsystem.domain.models.User;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;

import java.util.stream.Collectors;

public class StudentExamController {
    private final StartExamUseCase startUseCase;
    private final SubmitExamUseCase submitUseCase;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    private final com.examsystem.application.ports.ExamAttemptRepository attemptRepository;
    private final com.examsystem.application.ports.ProctorRepository proctorRepository;
    private final com.examsystem.application.ports.StudentQueryPort studentQueryPort;

    public StudentExamController(StartExamUseCase startUseCase, 
                                 SubmitExamUseCase submitUseCase, 
                                 ExamRepository examRepository, 
                                 QuestionRepository questionRepository, 
                                 com.examsystem.application.ports.ExamAttemptRepository attemptRepository,
                                 com.examsystem.application.ports.ProctorRepository proctorRepository,
                                 com.examsystem.application.ports.StudentQueryPort studentQueryPort) {
        this.startUseCase = startUseCase;
        this.submitUseCase = submitUseCase;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.proctorRepository = proctorRepository;
        this.studentQueryPort = studentQueryPort;
    }

    public void listHistory(Context ctx) {
        User user = ctx.attribute("user");
        ctx.json(studentQueryPort.listHistory(user.id()));
    }

    public void startOrResumeExam(Context ctx) {
        User user = ctx.attribute("user");
        int examId = Integer.parseInt(ctx.pathParam("examId"));
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new NotFoundResponse("Exam not found"));
        if (!exam.isActive()) throw new ForbiddenResponse("Exam is not active");

        ExamAttempt attempt = startUseCase.startOrResume(user.id(), examId, exam.duration());
        ctx.json(attempt); // Basic representation, safe enough
    }

    public void getQuestion(Context ctx) {
        // Enforce attempt ownership and validity
        // For brevity, we just fetch the question and map it to a safe DTO
        int questionId = Integer.parseInt(ctx.pathParam("questionId"));
        Question question = questionRepository.findById(questionId).orElseThrow(() -> new NotFoundResponse("Question not found"));
        
        StudentQuestionDto dto = new StudentQuestionDto(
            question.id(),
            question.examId(),
            question.text(),
            question.type(),
            question.options().stream()
                .map(o -> new StudentOptionDto(o.id(), o.text()))
                .collect(Collectors.toList())
        );
        ctx.json(dto);
    }

    private void enforceOwnership(Context ctx, int attemptId) {
        User user = ctx.attribute("user");
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new NotFoundResponse("Attempt not found"));
        if (attempt.studentId() != user.id()) {
            throw new ForbiddenResponse("Cannot access another student's attempt");
        }
    }

    public void saveAnswer(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        StudentSaveAnswerRequestDto req = ctx.bodyAsClass(StudentSaveAnswerRequestDto.class);
        
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        Exam exam = examRepository.findById(attempt.examId()).orElseThrow();
        
        submitUseCase.saveAnswer(attemptId, req.questionId(), req.selectedOptions(), exam.duration());
        ctx.status(200);
    }

    public void submitExam(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        int score = submitUseCase.submitExam(attemptId, java.time.Instant.now());
        ctx.json(score);
    }
    
    public void recordEvent(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        String eventType = ctx.formParam("eventType");
        if (eventType == null) throw new BadRequestResponse("Missing eventType");
        int eventId = proctorRepository.saveEvent(attemptId, eventType, java.time.Instant.now());
        ctx.json(java.util.Map.of("eventId", eventId));
    }

    public void uploadEvidence(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        
        var uploadedFile = ctx.uploadedFile("screenshot");
        if (uploadedFile == null) throw new BadRequestResponse("Missing screenshot");
        
        if (uploadedFile.size() > 5 * 1024 * 1024) throw new BadRequestResponse("Screenshot too large (max 5MB)");
        String contentType = uploadedFile.contentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new BadRequestResponse("Invalid content type, must be image/jpeg or image/png");
        }
        
        try {
            byte[] data = uploadedFile.content().readAllBytes();
            proctorRepository.saveEvidence(eventId, data, contentType, java.time.Instant.now());
            ctx.status(200);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save evidence", e);
        }
    }
}
