package com.examsystem.client.api;

import com.examsystem.client.config.ServerConfig;
import com.examsystem.client.session.SessionState;
import com.examsystem.contracts.AuthRequestDto;
import com.examsystem.contracts.AuthResponseDto;
import com.examsystem.contracts.ErrorResponseDto;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.contracts.StudentAttemptHistoryDto;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentQuestionDto;
import com.examsystem.contracts.StudentSaveAnswerRequestDto;
import com.examsystem.contracts.AdminAttemptDetailDto;
import com.examsystem.contracts.AdminExamStatisticsDto;
import com.examsystem.contracts.admin.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApiClient {
    private final ServerConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final SessionState sessionState;

    public ApiClient() {
        this(new ServerConfig(), SessionState.getInstance());
    }

    public ApiClient(ServerConfig config) {
        this(config, SessionState.getInstance());
    }

    public ApiClient(ServerConfig config, SessionState sessionState) {
        this(config, sessionState, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public ApiClient(ServerConfig config, SessionState sessionState, HttpClient httpClient) {
        this.config = config;
        this.sessionState = sessionState;
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ServerConfig getConfig() {
        return config;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    // ==========================================
    // Authentication
    // ==========================================

    public AuthResponseDto login(String username, String password) {
        AuthRequestDto req = new AuthRequestDto(username, password);
        AuthResponseDto res = postJson("/auth/login", req, AuthResponseDto.class, false);
        sessionState.setSession(username, res.fullName(), res.role(), res.token());
        return res;
    }

    public void logout() {
        try {
            if (sessionState.isAuthenticated()) {
                postEmpty("/auth/logout", true);
            }
        } catch (Exception ignored) {
            // Best effort logout on server
        } finally {
            sessionState.clear();
        }
    }

    // ==========================================
    // Student APIs
    // ==========================================

    public List<StudentExamDto> listAvailableExams() {
        return get("/student/exams", new TypeReference<List<StudentExamDto>>() {});
    }

    public StudentAttemptDto startOrResumeAttempt(int examId) {
        return postEmpty("/student/exams/" + examId + "/attempts", StudentAttemptDto.class, true);
    }

    public List<StudentQuestionDto> getAttemptQuestions(int attemptId) {
        return get("/student/attempts/" + attemptId + "/questions", new TypeReference<List<StudentQuestionDto>>() {});
    }

    public Map<Integer, List<Integer>> getAttemptAnswers(int attemptId) {
        return get("/student/attempts/" + attemptId + "/answers", new TypeReference<Map<Integer, List<Integer>>>() {});
    }

    public void saveAnswer(int attemptId, int questionId, List<Integer> selectedOptions) {
        StudentSaveAnswerRequestDto req = new StudentSaveAnswerRequestDto(questionId, selectedOptions);
        postJsonVoid("/student/attempts/" + attemptId + "/answers", req);
    }

    public int submitExam(int attemptId) {
        return postEmpty("/student/attempts/" + attemptId + "/submit", Integer.class, true);
    }

    public List<StudentAttemptHistoryDto> getStudentHistory() {
        return get("/student/history", new TypeReference<List<StudentAttemptHistoryDto>>() {});
    }

    public int recordProctorEvent(int attemptId, String eventType) {
        String body = "eventType=" + URLEncoder.encode(eventType, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + "/student/attempts/" + attemptId + "/events"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded");
        attachAuth(builder);
        builder.POST(HttpRequest.BodyPublishers.ofString(body));

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
        try {
            JsonNode tree = mapper.readTree(response.body());
            return tree.path("eventId").asInt();
        } catch (IOException e) {
            throw new ApiException("Failed to parse eventId response", e);
        }
    }

    public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType) {
        String boundary = "----ExaSecureEvidenceBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] multipart = buildMultipart(boundary, "screenshot", "screenshot." + (contentType.contains("png") ? "png" : "jpg"), contentType, screenshotBytes);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + "/student/attempts/" + attemptId + "/events/" + eventId + "/proctor-evidence"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        attachAuth(builder);
        builder.POST(HttpRequest.BodyPublishers.ofByteArray(multipart));

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
    }

    // ==========================================
    // Admin APIs
    // ==========================================

    public List<AdminUserDto> adminListUsers() {
        return get("/admin/users", new TypeReference<List<AdminUserDto>>() {});
    }

    public int adminCreateUser(AdminCreateUserRequestDto req) {
        AdminMutationResultDto res = postJson("/admin/users", req, AdminMutationResultDto.class, true);
        return res.id();
    }

    public void adminUpdateUser(int userId, AdminUpdateUserRequestDto req) {
        putJsonVoid("/admin/users/" + userId, req);
    }

    public void adminDeactivateUser(int userId) {
        deleteVoid("/admin/users/" + userId);
    }

    public List<AdminExamDto> adminListExams() {
        return get("/admin/exams", new TypeReference<List<AdminExamDto>>() {});
    }

    public List<AdminQuestionDefinitionDto> adminGetExamQuestions(int examId) {
        return get("/admin/exams/" + examId + "/questions", new TypeReference<List<AdminQuestionDefinitionDto>>() {});
    }

    public AdminExamMutationResultDto adminCreateExam(AdminExamDefinitionRequestDto req) {
        return postJson("/admin/exams", req, AdminExamMutationResultDto.class, true);
    }

    public AdminExamMutationResultDto adminReviseExam(int examId, AdminExamDefinitionRequestDto req) {
        return postJson("/admin/exams/" + examId + "/revisions", req, AdminExamMutationResultDto.class, true);
    }

    public void adminDeactivateExam(int examId) {
        deleteVoid("/admin/exams/" + examId);
    }

    public List<AdminAttemptDto> adminGetAttemptHistory(int examId) {
        return get("/admin/exams/" + examId + "/attempts", new TypeReference<List<AdminAttemptDto>>() {});
    }

    public List<AdminAttemptDetailDto> adminGetAttemptDetails(int attemptId) {
        return get("/admin/attempts/" + attemptId + "/details", new TypeReference<List<AdminAttemptDetailDto>>() {});
    }

    public AdminExamStatisticsDto adminGetExamStatistics(int examId) {
        return get("/admin/exams/" + examId + "/statistics", AdminExamStatisticsDto.class);
    }

    public List<AdminProctorEventDto> adminGetProctorEvents(int attemptId) {
        return get("/admin/attempts/" + attemptId + "/events", new TypeReference<List<AdminProctorEventDto>>() {});
    }

    public byte[] adminGetEvidence(int eventId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + "/admin/events/" + eventId + "/evidence"))
                .timeout(Duration.ofSeconds(15))
                .GET();
        attachAuth(builder);

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                String errorMsg = new String(response.body(), StandardCharsets.UTF_8);
                handleErrorStatus(response.statusCode(), errorMsg);
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException("Network error during evidence download: " + e.getMessage(), e);
        }
    }

    // ==========================================
    // Internal HTTP Helpers
    // ==========================================

    private <T> T get(String path, TypeReference<T> typeRef) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        attachAuth(builder);

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
        return parse(response.body(), typeRef);
    }

    private <T> T get(String path, Class<T> responseClass) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        attachAuth(builder);

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
        return parse(response.body(), responseClass);
    }

    private <T> T postJson(String path, Object bodyObj, Class<T> responseClass, boolean authRequired) {
        try {
            String json = mapper.writeValueAsString(bodyObj);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiV1Url() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (authRequired) attachAuth(builder);

            HttpResponse<String> response = send(builder.build());
            handleErrorStatus(response);
            return parse(response.body(), responseClass);
        } catch (IOException e) {
            throw new ApiException("Serialization error: " + e.getMessage(), e);
        }
    }

    private void postJsonVoid(String path, Object bodyObj) {
        try {
            String json = mapper.writeValueAsString(bodyObj);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiV1Url() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            attachAuth(builder);

            HttpResponse<String> response = send(builder.build());
            handleErrorStatus(response);
        } catch (IOException e) {
            throw new ApiException("Serialization error: " + e.getMessage(), e);
        }
    }

    private <T> T postEmpty(String path, Class<T> responseClass, boolean authRequired) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody());
        if (authRequired) attachAuth(builder);

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
        return parse(response.body(), responseClass);
    }

    private void postEmpty(String path, boolean authRequired) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody());
        if (authRequired) attachAuth(builder);

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
    }

    private void putJsonVoid(String path, Object bodyObj) {
        try {
            String json = mapper.writeValueAsString(bodyObj);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiV1Url() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json));
            attachAuth(builder);

            HttpResponse<String> response = send(builder.build());
            handleErrorStatus(response);
        } catch (IOException e) {
            throw new ApiException("Serialization error: " + e.getMessage(), e);
        }
    }

    private void deleteVoid(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiV1Url() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .DELETE();
        attachAuth(builder);

        HttpResponse<String> response = send(builder.build());
        handleErrorStatus(response);
    }

    private void attachAuth(HttpRequest.Builder builder) {
        if (sessionState.isAuthenticated()) {
            builder.header("Authorization", "Bearer " + sessionState.token());
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String detail = (e.getMessage() != null && !e.getMessage().isBlank() && !"null".equalsIgnoreCase(e.getMessage().trim()))
                    ? e.getMessage().trim()
                    : "Connection refused. Please ensure the ExaSecure server is running on " + config.baseUrl();
            throw new ApiException("Server unavailable or connection timed out: " + detail, e);
        }
    }

    private void handleErrorStatus(HttpResponse<String> response) {
        handleErrorStatus(response.statusCode(), response.body());
    }

    private void handleErrorStatus(int statusCode, String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        String message = null;
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                ErrorResponseDto err = mapper.readValue(responseBody, ErrorResponseDto.class);
                message = err.message();
            } catch (Exception ignored) {
                try {
                    JsonNode node = mapper.readTree(responseBody);
                    if (node.has("message")) {
                        message = node.get("message").asText();
                    } else if (node.has("title")) {
                        message = node.get("title").asText();
                    }
                } catch (Exception ignored2) {
                    message = responseBody;
                }
            }
        }

        if (statusCode == 401) {
            sessionState.clear();
            throw new ApiException(401, message != null ? message : "Session expired or invalid credentials");
        }
        if (statusCode == 403) {
            throw new ApiException(403, message != null ? message : "Access forbidden for current user role");
        }
        if (statusCode == 404) {
            throw new ApiException(404, message != null ? message : "Requested resource not found");
        }
        if (statusCode == 409) {
            throw new ApiException(409, message != null ? message : "Conflict: exam attempt already finalized or expired");
        }

        throw new ApiException(statusCode, message != null ? message : "Server error (" + statusCode + ")");
    }

    private <T> T parse(String body, Class<T> clazz) {
        if (clazz == Void.class || body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(body, clazz);
        } catch (IOException e) {
            throw new ApiException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    private <T> T parse(String body, TypeReference<T> typeRef) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(body, typeRef);
        } catch (IOException e) {
            throw new ApiException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    private static byte[] buildMultipart(String boundary, String partName, String filename, String contentType, byte[] data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to construct multipart body", e);
        }
    }
}
