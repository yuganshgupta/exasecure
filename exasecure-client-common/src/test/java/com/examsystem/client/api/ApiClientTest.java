package com.examsystem.client.api;

import com.examsystem.client.config.ServerConfig;
import com.examsystem.client.session.SessionState;
import com.examsystem.contracts.AuthResponseDto;
import com.examsystem.contracts.StudentExamDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiClientTest {
    private HttpServer server;
    private int port;
    private ApiClient client;
    private SessionState sessionState;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.start();

        sessionState = new SessionState();
        ServerConfig config = new ServerConfig("http://localhost:" + port);
        client = new ApiClient(config, sessionState);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void delayedLogoutSendsOnlyTheCapturedTokenAndLeavesNewerLoginIntact() throws Exception {
        var requestStarted = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var authorization = new AtomicReference<String>();
        server.createContext("/api/v1/auth/logout", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestStarted.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("fixture timed out");
                exchange.sendResponseHeaders(401, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        });
        sessionState.setSession("old", "Old", "ADMIN", "fixture-old-token");
        var captured = sessionState.get();
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var logout = worker.submit(() -> client.logoutSession(captured));
            try {
                assertThat(requestStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                sessionState.setSession("new", "New", "ADMIN", "fixture-new-token");
            } finally {
                release.countDown();
            }
            assertThat(logout.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(authorization).hasValue("Bearer fixture-old-token");
        assertThat(sessionState.token()).isEqualTo("fixture-new-token");
    }

    @Test
    void failedRevocationReportsUnconfirmedAndClearsOnlyTheLocalSession() {
        server.createContext("/api/v1/auth/logout", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(503, -1);
            }
        });
        sessionState.setSession("old", "Old", "STUDENT", "fixture-token");
        assertThat(client.logoutSession(sessionState.get())).isFalse();
        assertThat(sessionState.isAuthenticated()).isFalse();
    }

    @Test
    void startAndResumeDeserializeActiveAttemptWithExplicitNullResultFields() {
        sessionState.setSession("student", "Student", "STUDENT", "test-token");
        var method = new AtomicReference<String>();
        server.createContext("/api/v1/student/exams/7/attempts", exchange -> {
            method.set(exchange.getRequestMethod());
            byte[] bytes = """
                    {"id":12,"examId":7,"studentId":9,"startTime":1788609600.123456789,
                     "endTime":null,"score":null,"status":"ACTIVE"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        var created = client.startOrResumeAttempt(7);
        var resumed = client.startOrResumeAttempt(7);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(created).isEqualTo(new com.examsystem.contracts.StudentAttemptDto(
                12, 7, 9, java.time.Instant.parse("2026-09-05T12:00:00.123456789Z"), null, null, "ACTIVE"));
        assertThat(resumed).isEqualTo(created);
    }

    @Test
    void login_populatesSessionState_onSuccess() {
        server.createContext("/api/v1/auth/login", exchange -> {
            String json = """
                    {"token":"test-token-xyz","role":"STUDENT","fullName":"Alice Smith"}
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        AuthResponseDto res = client.login("alice", "password123");
        assertThat(res.token()).isEqualTo("test-token-xyz");
        assertThat(res.role()).isEqualTo("STUDENT");
        assertThat(res.fullName()).isEqualTo("Alice Smith");
        assertThat(sessionState.isAuthenticated()).isTrue();
        assertThat(sessionState.username()).isEqualTo("alice");
        assertThat(sessionState.token()).isEqualTo("test-token-xyz");
        assertThat(sessionState.isStudent()).isTrue();
    }

    @Test
    void authenticatedRequest_sendsBearerTokenHeader() {
        sessionState.setSession("alice", "Alice", "STUDENT", "valid-bearer-token");

        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/api/v1/student/exams", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String json = """
                    [{"id":1,"title":"Java 25 Exam","durationMinutes":60}]
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        List<StudentExamDto> exams = client.listAvailableExams();
        assertThat(exams).hasSize(1);
        assertThat(exams.get(0).title()).isEqualTo("Java 25 Exam");
        assertThat(authHeader.get()).isEqualTo("Bearer valid-bearer-token");
    }

    @Test
    void unauthorized_clearsSession_andThrowsApiException() {
        sessionState.setSession("alice", "Alice", "STUDENT", "expired-token");

        server.createContext("/api/v1/student/exams", exchange -> {
            String json = "{\"message\":\"Session expired\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.listAvailableExams())
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.getStatusCode()).isEqualTo(401);
                    assertThat(apiEx.isUnauthorized()).isTrue();
                });

        assertThat(sessionState.isAuthenticated()).isFalse();
    }

    @Test
    void conflict_throwsApiExceptionWith409() {
        sessionState.setSession("alice", "Alice", "STUDENT", "token");

        server.createContext("/api/v1/student/attempts/55/submit", exchange -> {
            String json = "{\"message\":\"Exam attempt is already finalized\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(409, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.submitExam(55))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiEx = (ApiException) e;
                    assertThat(apiEx.getStatusCode()).isEqualTo(409);
                    assertThat(apiEx.isConflict()).isTrue();
                    assertThat(apiEx.getServerMessage()).contains("already finalized");
                });
    }

    @Test
    void serverDown_throwsApiExceptionWithCause() {
        server.stop(0); // Stop immediately
        assertThatThrownBy(() -> client.listAvailableExams())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Server unavailable");
    }

    @Test
    void structuredErrorCodeSurvivesHttpParsingWithOriginalStatusAndMessage() {
        assertErrorContract(400, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
    }

    @Test
    void legacyErrorWithoutCodeRemainsReadable() {
        assertErrorContract(400, null);
    }

    @Test
    void unknownErrorCodeRemainsOpaque() {
        assertErrorContract(400, "FUTURE_SERVER_CODE");
    }

    @Test
    void errorCodeDoesNotChangeForbiddenNotFoundOrConflictStatus() {
        for (int status : new int[]{403, 404, 409}) {
            assertErrorContract(status, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
            server.removeContext("/api/v1/student/attempts/1/events/2/long-absence");
        }
    }

    private void assertErrorContract(int status, String errorCode) {
        server.createContext("/api/v1/student/attempts/1/events/2/long-absence", exchange -> {
            String json = "{\"code\":" + status + ",\"message\":\"Threshold not yet met\",\"requestId\":\"request-1\""
                    + (errorCode == null ? "" : ",\"errorCode\":\"" + errorCode + "\"") + "}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        assertThatThrownBy(() -> client.recordProctorLongAbsence(1, 2))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    var api = (ApiException) e;
                    assertThat(api.getStatusCode()).isEqualTo(status);
                    assertThat(api.getErrorCode()).isEqualTo(errorCode);
                    assertThat(api.getServerMessage()).isEqualTo("Threshold not yet met");
                });
    }
}
