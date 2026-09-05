package com.examsystem.server.http.controllers;

import com.examsystem.application.ports.AnswerRepository;
import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.ports.ExamRepository;
import com.examsystem.application.ports.ProctorRepository;
import com.examsystem.application.ports.QuestionRepository;
import com.examsystem.application.ports.StudentQueryPort;
import com.examsystem.application.usecases.StartExamUseCase;
import com.examsystem.application.usecases.SubmitExamUseCase;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentOptionDto;
import com.examsystem.contracts.StudentProctorEventResponseDto;
import com.examsystem.contracts.StudentQuestionDto;
import com.examsystem.contracts.StudentSaveAnswerRequestDto;
import com.examsystem.domain.models.Exam;
import com.examsystem.domain.models.ExamAttempt;
import com.examsystem.domain.models.Question;
import com.examsystem.domain.models.User;
import com.examsystem.server.http.mappers.StudentContractMapper;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudentExamController {
    private final StartExamUseCase startUseCase;
    private final SubmitExamUseCase submitUseCase;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ProctorRepository proctorRepository;
    private final StudentQueryPort studentQueryPort;
    private final AnswerRepository answerRepository;
    private final Clock clock;

    public StudentExamController(StartExamUseCase startUseCase,
                                 SubmitExamUseCase submitUseCase,
                                 ExamRepository examRepository,
                                 QuestionRepository questionRepository,
                                 ExamAttemptRepository attemptRepository,
                                 ProctorRepository proctorRepository,
                                 StudentQueryPort studentQueryPort,
                                 AnswerRepository answerRepository) {
        this(startUseCase, submitUseCase, examRepository, questionRepository, attemptRepository, proctorRepository, studentQueryPort, answerRepository, Clock.systemUTC());
    }

    public StudentExamController(StartExamUseCase startUseCase,
                                 SubmitExamUseCase submitUseCase,
                                 ExamRepository examRepository,
                                 QuestionRepository questionRepository,
                                 ExamAttemptRepository attemptRepository,
                                 ProctorRepository proctorRepository,
                                 StudentQueryPort studentQueryPort,
                                 AnswerRepository answerRepository,
                                 Clock clock) {
        this.startUseCase = startUseCase;
        this.submitUseCase = submitUseCase;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.proctorRepository = proctorRepository;
        this.studentQueryPort = studentQueryPort;
        this.answerRepository = answerRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void listAvailableExams(Context ctx) {
        ctx.json(studentQueryPort.listAvailableExams().stream()
                .map(e -> new StudentExamDto(e.id(), e.title(), e.durationMinutes()))
                .toList());
    }

    public void listHistory(Context ctx) {
        User user = ctx.attribute("user");
        ctx.json(studentQueryPort.listHistory(user.id()).stream()
                .map(StudentContractMapper::toAttemptHistoryDto)
                .toList());
    }

    public void startOrResumeExam(Context ctx) {
        User user = ctx.attribute("user");
        int examId = Integer.parseInt(ctx.pathParam("examId"));
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new NotFoundResponse("Exam not found"));
        if (!exam.isActive()) throw new ForbiddenResponse("Exam is not active");

        ExamAttempt attempt = startUseCase.startOrResume(user.id(), examId, exam.duration());
        ctx.json(attempt);
    }

    public void getAttemptQuestions(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        ExamAttempt attempt = enforceOwnership(ctx, attemptId);
        List<Question> questions = questionRepository.findByExamId(attempt.examId());
        List<StudentQuestionDto> dtos = questions.stream()
                .filter(Question::isActive)
                .map(q -> new StudentQuestionDto(
                        q.id(),
                        q.examId(),
                        q.text(),
                        q.type(),
                        q.options().stream()
                                .map(o -> new StudentOptionDto(o.number(), o.text()))
                                .collect(Collectors.toList())))
                .toList();
        ctx.json(dtos);
    }

    public void getAttemptAnswers(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        Map<Integer, List<Integer>> answers = answerRepository.getSelectedOptionsForAttempt(attemptId);
        ctx.json(answers);
    }

    public void getQuestion(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        ExamAttempt attempt = enforceOwnership(ctx, attemptId);
        int questionId = Integer.parseInt(ctx.pathParam("questionId"));
        Question question = questionRepository.findById(questionId).orElseThrow(() -> new NotFoundResponse("Question not found"));
        if (question.examId() != attempt.examId()) {
            throw new NotFoundResponse("Question not found for this attempt");
        }
        
        StudentQuestionDto dto = new StudentQuestionDto(
            question.id(),
            question.examId(),
            question.text(),
            question.type(),
            question.options().stream()
                .map(o -> new StudentOptionDto(o.number(), o.text()))
                .collect(Collectors.toList())
        );
        ctx.json(dto);
    }

    private ExamAttempt enforceOwnership(Context ctx, int attemptId) {
        User user = ctx.attribute("user");
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow(() -> new NotFoundResponse("Attempt not found"));
        if (attempt.studentId() != user.id()) {
            throw new ForbiddenResponse("Cannot access another student's attempt");
        }
        return attempt;
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
        int score = submitUseCase.submitExam(attemptId, java.time.Instant.now(clock));
        ctx.json(score);
    }
    
    public void recordEvent(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);

        String eventType = null;
        String clientEventId = null;

        String ct = ctx.contentType();
        if (ct != null && ct.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(ctx.body());
                if (node.has("eventType")) eventType = node.get("eventType").asText();
                if (node.has("clientEventId")) clientEventId = node.get("clientEventId").asText();
            } catch (Exception ignored) {}
        }
        if (eventType == null) {
            eventType = ctx.formParam("eventType");
        }
        if (clientEventId == null) {
            clientEventId = ctx.formParam("clientEventId");
        }

        if (eventType == null || eventType.isBlank()) {
            throw new BadRequestResponse("Missing eventType");
        }

        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundResponse("Attempt not found"));

        // Save event atomically
        var saveResult = proctorRepository.recordEvent(attemptId, eventType, java.time.Instant.now(clock), clientEventId);
        int eventId = saveResult.eventId();

        // Increment violation count on attempt ONLY if this request actually created the event
        int newCount;
        if (saveResult.createdNew()) {
            newCount = attemptRepository.incrementFocusLostCount(attemptId);
        } else {
            ExamAttempt current = attemptRepository.findById(attemptId).orElse(attempt);
            newCount = current.focusLostCount();
            int tries = 0;
            while (newCount == 0 && tries++ < 30) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                current = attemptRepository.findById(attemptId).orElse(attempt);
                newCount = current.focusLostCount();
            }
        }

        ExamAttempt updated = attemptRepository.findById(attemptId).orElse(attempt);
        if (newCount == 0) {
            newCount = updated.focusLostCount();
        }

        boolean flagged = updated.flagged() || newCount >= 3;
        String status = updated.finalized() ? "SUBMITTED" : "ACTIVE";

        if (saveResult.createdNew() && newCount >= 3) {
            flagged = true;
            attemptRepository.flagAttempt(attemptId);
            if (!updated.finalized()) {
                // Auto submit attempt
                try {
                    submitUseCase.submitExam(attemptId, java.time.Instant.now(clock));
                    status = "SUBMITTED";
                } catch (Exception ignored) {
                }
            }
        }

        ctx.json(new StudentProctorEventResponseDto(eventId, newCount, flagged, status));
    }

    public void uploadEvidence(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        if (!proctorRepository.eventBelongsToAttempt(eventId, attemptId)) {
            throw new NotFoundResponse("Event not found for this attempt");
        }

        var eventOpt = proctorRepository.findEventById(eventId);
        if (eventOpt.isEmpty()) {
            throw new NotFoundResponse("Event not found");
        }
        var event = eventOpt.get();
        if (!"FOCUS_LOST".equalsIgnoreCase(event.eventType())) {
            throw new BadRequestResponse("Evidence can only be attached to FOCUS_LOST events");
        }

        String evidenceType = ctx.formParam("evidenceType");
        String effectiveType = (evidenceType == null || evidenceType.isBlank())
                ? "INITIAL"
                : evidenceType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!effectiveType.equals("INITIAL") && !effectiveType.equals("LONG_ABSENCE")) {
            throw new BadRequestResponse("Invalid evidenceType: must be INITIAL or LONG_ABSENCE");
        }

        var uploadedFile = ctx.uploadedFile("screenshot");
        if (uploadedFile == null) throw new BadRequestResponse("Missing screenshot");

        if (uploadedFile.size() > 5 * 1024 * 1024) throw new BadRequestResponse("Screenshot too large (max 5MB)");
        String contentType = uploadedFile.contentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new BadRequestResponse("Invalid content type, must be image/jpeg or image/png");
        }

        try {
            byte[] data = uploadedFile.content().readAllBytes();
            proctorRepository.saveEvidence(eventId, data, contentType, java.time.Instant.now(), effectiveType);
            ctx.status(200);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save evidence", e);
        }
    }

    public void recordReturn(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        if (!proctorRepository.eventBelongsToAttempt(eventId, attemptId)) {
            throw new NotFoundResponse("Event not found for this attempt");
        }

        var eventOpt = proctorRepository.findEventById(eventId);
        if (eventOpt.isEmpty()) {
            throw new NotFoundResponse("Event not found");
        }
        var event = eventOpt.get();

        if (!"FOCUS_LOST".equalsIgnoreCase(event.eventType())) {
            throw new BadRequestResponse("Event is not a FOCUS_LOST event");
        }

        // Idempotent: if already returned, do not overwrite original return timestamp and duration
        if (event.returnedAt() != null) {
            ctx.status(200);
            return;
        }

        java.time.Instant serverNow = java.time.Instant.now(clock);
        long serverDurationMs = Math.max(0, java.time.Duration.between(event.recordedAt(), serverNow).toMillis());

        // Authoritative duration: strictly derived from server timestamps.
        // Client telemetry is ignored for durable duration and long-absence determination.
        proctorRepository.recordReturn(eventId, serverNow, serverDurationMs);
        ctx.status(200);
    }

    private static final long LONG_ABSENCE_THRESHOLD_MS = 5000;

    public void recordLongAbsence(Context ctx) {
        int attemptId = Integer.parseInt(ctx.pathParam("attemptId"));
        enforceOwnership(ctx, attemptId);
        int eventId = Integer.parseInt(ctx.pathParam("eventId"));
        if (!proctorRepository.eventBelongsToAttempt(eventId, attemptId)) {
            throw new NotFoundResponse("Event not found for this attempt");
        }

        var eventOpt = proctorRepository.findEventById(eventId);
        if (eventOpt.isEmpty()) {
            throw new NotFoundResponse("Event not found");
        }
        var event = eventOpt.get();
        if (!"FOCUS_LOST".equalsIgnoreCase(event.eventType())) {
            throw new BadRequestResponse("Event is not a FOCUS_LOST event");
        }

        java.time.Instant serverNow = java.time.Instant.now(clock);
        java.time.Instant endTime = event.returnedAt() != null ? event.returnedAt() : serverNow;
        long elapsedMs = Math.max(0, java.time.Duration.between(event.recordedAt(), endTime).toMillis());

        // Independent server-side authoritative check: incident must have actually existed for >= 5000 ms
        if (elapsedMs < LONG_ABSENCE_THRESHOLD_MS) {
            throw new BadRequestResponse("Focus-loss incident duration (" + elapsedMs + "ms) has not existed for >= 5 seconds");
        }

        proctorRepository.markLongAbsence(eventId);
        ctx.status(200);
    }
}
