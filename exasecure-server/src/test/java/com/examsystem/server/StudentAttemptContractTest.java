package com.examsystem.server;

import com.examsystem.application.ports.ExamAttemptRepository;
import com.examsystem.application.usecases.StartExamUseCase;
import com.examsystem.domain.models.Exam;
import com.examsystem.domain.models.ExamAttempt;
import com.examsystem.domain.models.User;
import com.examsystem.server.http.controllers.StudentExamController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttemptContractTest {
    @Test
    void rawCreationAndResumeExposeOnlyActiveStudentContractWithOriginalIdentityAndTime() throws Exception {
        Instant original = Instant.parse("2026-09-05T12:00:00.123456789Z");
        var clock = new AtomicReference<>(original);
        var stored = new AtomicReference<ExamAttempt>();
        // A port fixture isolates the HTTP contract from database timestamp precision.
        ExamAttemptRepository attempts = new ExamAttemptRepository() {
            public Optional<ExamAttempt> findById(int id) { return Optional.ofNullable(stored.get()); }
            public void calculateAndSetScore(int id, int count, int score, Instant end) {
                throw new AssertionError("Start/resume must not grade");
            }
            public ExamAttempt startOrResumeAttempt(int studentId, int examId, Instant now, Duration limit) {
                if (stored.get() == null) {
                    stored.set(new ExamAttempt(12, examId, studentId, now, null, null, false));
                    return stored.get();
                }
                var first = stored.get();
                return new ExamAttempt(first.id(), examId, studentId, first.startTime(),
                        null, 0, false, 2, true, "FLAGGED");
            }
        };
        var controller = new StudentExamController(new StartExamUseCase(attempts, clock::get), null,
                id -> Optional.of(new Exam(id, "Contract exam", Duration.ofHours(1), 1, true)),
                null, attempts, null, null, null, null);
        var app = Javalin.create(config -> config.routes.post("/api/v1/student/exams/{examId}/attempts", ctx -> {
            ctx.attribute("user", new User(9, "student", "Student", "", "", User.Role.STUDENT));
            controller.startOrResumeExam(ctx);
        }));
        try {
            app.start(0);
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port()
                    + "/api/v1/student/exams/7/attempts"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            var client = HttpClient.newHttpClient();
            JsonNode created = assertActiveResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
            clock.set(original.plusSeconds(120));
            JsonNode resumed = assertActiveResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
            assertThat(resumed).isEqualTo(created);
            var dto = new ObjectMapper().findAndRegisterModules().treeToValue(
                    created, com.examsystem.contracts.StudentAttemptDto.class);
            assertThat(dto.startTime()).isEqualTo(original);
        } finally {
            app.stop();
        }
    }

    private static JsonNode assertActiveResponse(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = new ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(response.body());
        var fields = new HashSet<String>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "examId", "studentId", "startTime", "endTime", "score", "status");
        assertThat(json.required("id").asInt()).isEqualTo(12);
        assertThat(json.required("examId").asInt()).isEqualTo(7);
        assertThat(json.required("studentId").asInt()).isEqualTo(9);
        assertThat(json.required("status").asText()).isEqualTo("ACTIVE");
        assertThat(json.required("score").isNull()).isTrue();
        assertThat(json.required("endTime").isNull()).isTrue();
        return json;
    }
}
