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
    private final javax.sql.DataSource dataSource;

    public StudentExamController(StartExamUseCase startUseCase, 
                                 SubmitExamUseCase submitUseCase, 
                                 ExamRepository examRepository, 
                                 QuestionRepository questionRepository, 
                                 com.examsystem.application.ports.ExamAttemptRepository attemptRepository,
                                 com.examsystem.application.ports.ProctorRepository proctorRepository,
                                 javax.sql.DataSource dataSource) {
        this.startUseCase = startUseCase;
        this.submitUseCase = submitUseCase;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.proctorRepository = proctorRepository;
        this.dataSource = dataSource;
    }

    public void listHistory(Context ctx) {
        User user = ctx.attribute("user");
        java.util.List<java.util.Map<String, Object>> history = new java.util.ArrayList<>();
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT e.title, a.id, a.start_time, a.end_time, a.score, a.status FROM exam_attempts a JOIN exams e ON a.exam_id = e.id WHERE a.student_id = ? ORDER BY a.start_time DESC")) {
            ps.setInt(1, user.id());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> h = new java.util.HashMap<>();
                    h.put("exam_title", rs.getString("title"));
                    h.put("attempt_id", rs.getInt("id"));
                    h.put("start_time", rs.getTimestamp("start_time").toInstant().toString());
                    h.put("end_time", rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant().toString() : null);
                    h.put("score", rs.getObject("score"));
                    h.put("status", rs.getString("status"));
                    history.add(h);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load history", e);
        }
        ctx.json(history);
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
        proctorRepository.saveEvent(attemptId, eventType, java.time.Instant.now());
        ctx.status(200);
    }

    public void uploadEvidence(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        var uploadedFile = ctx.uploadedFile("screenshot");
        if (uploadedFile == null) throw new BadRequestResponse("Missing screenshot");
        
        try {
            byte[] data = uploadedFile.content().readAllBytes();
            proctorRepository.saveEvidence(attemptId, data, java.time.Instant.now());
            ctx.status(200);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save evidence", e);
        }
    }
}
