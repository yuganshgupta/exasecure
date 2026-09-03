package com.examsystem.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.examsystem.server.config.AppConfig;
import org.mindrot.jbcrypt.BCrypt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "EXASECURE_MYSQL_TEST_URL", matches = ".+")
class ServerIntegrationTest {

    private static ExaSecureServer server;
    private static final int PORT = 8089;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    @BeforeAll
    static void setUp() throws Exception {
        dbUrl = System.getenv("EXASECURE_MYSQL_TEST_URL");
        dbUser = System.getenv().getOrDefault("EXASECURE_MYSQL_TEST_USER", "root");
        dbPassword = System.getenv().getOrDefault("EXASECURE_MYSQL_TEST_PASSWORD", "");
        if (!dbUrl.matches("(?i)^jdbc:mysql://[^/]+/exasecure_test(?:_[a-z0-9]+)?(?:\\?.*)?$")) {
            throw new IllegalArgumentException(
                    "EXASECURE_MYSQL_TEST_URL must target a disposable exasecure_test database");
        }

        System.setProperty("SERVER_PORT", String.valueOf(PORT));
        System.setProperty("DB_URL", dbUrl);
        System.setProperty("DB_USER", dbUser);
        System.setProperty("DB_PASSWORD", dbPassword);
        
        // Clean test DB
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            stmt.execute("DROP TABLE IF EXISTS legacy_proctor_logs, proctor_evidence_v2, proctor_events, proctor_evidence, proctor_logs, student_answer_options, student_answers, exam_attempts, exam_questions, options, questions, exams, server_sessions, schema_versions, users;");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
        
        server = new ExaSecureServer(new AppConfig());
        server.start();
        seedApiFixtures();
    }

    private static void seedApiFixtures() throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO users (id, username, password, full_name, role, is_active) VALUES (101, 'student1', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Student One', 'STUDENT', 1)");
            stmt.execute("INSERT INTO users (id, username, password, full_name, role, is_active) VALUES (102, 'student2', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Student Two', 'STUDENT', 1)");
            stmt.execute("INSERT INTO users (id, username, password, full_name, role, is_active) VALUES (103, 'admin1', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Admin One', 'ADMIN', 1)");
            stmt.execute("INSERT INTO users (id, username, password, full_name, role, is_active) VALUES (104, 'inactive1', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Inactive Student', 'STUDENT', 0)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (1, 'Test Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (2, 'Other Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (3, 'Option Number Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (4, 'Expired Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (5, 'Concurrent Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (6, 'Lifecycle Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type, is_active) VALUES (401, 1, 'Owned question', 1, 'SINGLE', 1), (402, 2, 'Other question', 1, 'SINGLE', 1), (403, 3, 'Stable numbers', 3, 'MULTI', 1), (404, 4, 'Expired question', 1, 'SINGLE', 1), (405, 5, 'Concurrent question', 1, 'SINGLE', 1), (406, 6, 'Lifecycle question', 1, 'SINGLE', 1)");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct, is_active) VALUES (501, 401, 1, 'Owned correct', 1, 1), (502, 401, 2, 'Owned wrong', 0, 1), (503, 402, 1, 'Other correct', 1, 1), (504, 402, 2, 'Other wrong', 0, 1), (601, 403, 3, 'First stable', 1, 1), (602, 403, 7, 'Middle stable', 0, 1), (603, 403, 9, 'Last stable', 1, 1), (701, 404, 1, 'Exp correct', 1, 1), (702, 404, 2, 'Exp wrong', 0, 1), (801, 405, 1, 'Con correct', 1, 1), (802, 405, 2, 'Con wrong', 0, 1), (901, 406, 1, 'Life correct', 1, 1), (902, 406, 2, 'Life wrong', 0, 1)");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (201, 1, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (202, 2, 102, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (203, 3, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (204, 4, 101, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 2 HOUR), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (205, 5, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (206, 6, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO proctor_events (id, attempt_id, event_type, recorded_at) VALUES (301, 202, 'FOCUS_LOST', NOW())");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-stu1', 101, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-stu2', 102, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-admin1', 103, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-expired', 101, '2000-01-01 00:00:00')");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-inactive', 104, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
        }
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
        System.clearProperty("SERVER_PORT");
        System.clearProperty("DB_URL");
        System.clearProperty("DB_USER");
        System.clearProperty("DB_PASSWORD");
    }

    @Test
    void testHealthEndpoint() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }
    
    @Test
    void testNegativeAuthScenarios() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // 1. Unauthenticated access -> 401
        HttpRequest req1 = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + PORT + "/api/v1/student/history")).GET().build();
        assertThat(client.send(req1, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        
        // 2. Student accessing Admin -> 403
        HttpRequest req2 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + PORT + "/api/v1/admin/users"))
            .header("Authorization", "Bearer token-stu2").GET().build();
        assertThat(client.send(req2, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        
        // 3. Admin accessing Student -> 403
        HttpRequest req3 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/history"))
            .header("Authorization", "Bearer token-admin1").GET().build();
        assertThat(client.send(req3, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        
        // 4. Student accessing another student's attempt -> 403 (Cross-student block)
        HttpRequest req4 = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/201/submit"))
            .header("Authorization", "Bearer token-stu2").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(client.send(req4, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void screenshotBytesSurviveStudentHttpMySqlAndAdminHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest eventRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/201/events"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("eventType=FOCUS_LOST"))
                .build();
        HttpResponse<String> eventResponse = client.send(eventRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(eventResponse.statusCode()).isEqualTo(200);
        int eventId = new ObjectMapper().readTree(eventResponse.body()).required("eventId").asInt();
        byte[] screenshot = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x01, 0x02, 0x7f, (byte) 0x80, (byte) 0xfe, (byte) 0xff,
                0x45, 0x78, 0x61, 0x53, 0x65, 0x63, 0x75, 0x72, 0x65
        };
        String boundary = "ExaSecureEvidenceBoundary7f30a1";
        byte[] multipartBody = multipartScreenshot(boundary, screenshot);
        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/201/events/"
                        + eventId + "/proctor-evidence"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();
        HttpResponse<String> uploadResponse = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        assertEvidenceStoredExactly(eventId, screenshot);

        HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/admin/events/" + eventId + "/evidence"))
                .header("Authorization", "Bearer token-admin1")
                .GET()
                .build();
        HttpResponse<byte[]> downloadResponse = client.send(
                downloadRequest, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(downloadResponse.statusCode()).isEqualTo(200);
        assertThat(downloadResponse.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("image/png"));
        assertThat(downloadResponse.body()).containsExactly(screenshot);
    }

    @Test
    void adminUserMutationsHashPasswordsRevokeSessionsAndSoftDeactivate() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String createBody = """
                {
                  "username": "api-student",
                  "password": "initial-password",
                  "fullName": "API Student",
                  "enrollmentNumber": "API-001",
                  "section": "C",
                  "role": "STUDENT"
                }
                """;

        HttpResponse<String> forbiddenCreate = sendJson(
                client, "POST", "/api/v1/admin/users", "token-stu1", createBody);
        assertThat(forbiddenCreate.statusCode()).isEqualTo(403);

        HttpResponse<String> createResponse = sendJson(
                client, "POST", "/api/v1/admin/users", "token-admin1", createBody);
        assertThat(createResponse.statusCode()).isEqualTo(201);
        int userId = new ObjectMapper().readTree(createResponse.body()).required("id").asInt();
        assertUserPassword(userId, "api-student", "initial-password", true);

        HttpResponse<String> duplicateResponse = sendJson(
                client, "POST", "/api/v1/admin/users", "token-admin1", createBody);
        assertThat(duplicateResponse.statusCode()).isEqualTo(409);

        insertSession("token-api-student-before-update", userId);
        String updateBody = """
                {
                  "username": "api-student-updated",
                  "password": "replacement-password",
                  "fullName": "Updated API Student",
                  "enrollmentNumber": "API-002",
                  "section": "D",
                  "role": "STUDENT"
                }
                """;
        HttpResponse<String> updateResponse = sendJson(
                client, "PUT", "/api/v1/admin/users/" + userId, "token-admin1", updateBody);
        assertThat(updateResponse.statusCode()).isEqualTo(204);
        assertUserPassword(userId, "api-student-updated", "replacement-password", true);
        assertSessionMissing("token-api-student-before-update");

        insertSession("token-api-student-before-deactivate", userId);
        HttpResponse<String> deactivateResponse = sendJson(
                client, "POST", "/api/v1/admin/users/" + userId + "/deactivate", "token-admin1", null);
        assertThat(deactivateResponse.statusCode()).isEqualTo(204);
        assertUserPassword(userId, "api-student-updated", "replacement-password", false);
        assertSessionMissing("token-api-student-before-deactivate");

        HttpResponse<String> reactivateResponse = sendJson(
                client, "POST", "/api/v1/admin/users/" + userId + "/reactivate", "token-admin1", null);
        assertThat(reactivateResponse.statusCode()).isEqualTo(204);
        assertUserPassword(userId, "api-student-updated", "replacement-password", true);

        // Delete user with historical attempt (user 101 has attempt 201) -> 409 Conflict
        HttpResponse<String> conflictDeleteResponse = sendJson(
                client, "DELETE", "/api/v1/admin/users/101", "token-admin1", null);
        assertThat(conflictDeleteResponse.statusCode()).isEqualTo(409);

        // Safe delete unused user -> 204, row deleted from DB
        insertSession("token-api-student-before-delete", userId);
        HttpResponse<String> deleteResponse = sendJson(
                client, "DELETE", "/api/v1/admin/users/" + userId, "token-admin1", null);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
        assertUserMissing(userId);
        assertSessionMissing("token-api-student-before-delete");

        HttpResponse<String> selfDeleteResponse = sendJson(
                client, "DELETE", "/api/v1/admin/users/103", "token-admin1", null);
        assertThat(selfDeleteResponse.statusCode()).isEqualTo(403);
    }

    @Test
    void adminExamMutationsCreateImmutableRevisionsAndKeepAttemptsPinned() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String definition = """
                {
                  "title": "API Definition",
                  "durationMinutes": 50,
                  "questions": [
                    {
                      "text": "Choose one",
                      "type": "SINGLE",
                      "options": [
                        {"text": "Correct", "correct": true},
                        {"text": "Wrong", "correct": false}
                      ]
                    },
                    {
                      "text": "Choose all",
                      "type": "MULTI",
                      "options": [
                        {"text": "First", "correct": true},
                        {"text": "Second", "correct": true},
                        {"text": "Third", "correct": false}
                      ]
                    }
                  ]
                }
                """;

        HttpResponse<String> forbidden = sendJson(
                client, "POST", "/api/v1/admin/exams", "token-stu1", definition);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> create = sendJson(
                client, "POST", "/api/v1/admin/exams", "token-admin1", definition);
        assertThat(create.statusCode()).isEqualTo(201);
        var createJson = new ObjectMapper().readTree(create.body());
        int createdExamId = createJson.required("id").asInt();
        assertThat(createJson.required("versionNumber").asInt()).isEqualTo(1);
        assertDefinition(createdExamId, "API Definition", 1, null, true, 2, 5);
        assertCorrectnessMetadata(createdExamId, 3, 2);

        String revision = """
                {
                  "title": "Test Exam Revision",
                  "durationMinutes": 75,
                  "questions": [
                    {
                      "text": "Revised question",
                      "type": "SINGLE",
                      "options": [
                        {"text": "Safe", "correct": true},
                        {"text": "Unsafe", "correct": false}
                      ]
                    }
                  ]
                }
                """;
        HttpResponse<String> revise = sendJson(
                client, "POST", "/api/v1/admin/exams/1/revisions", "token-admin1", revision);
        assertThat(revise.statusCode()).isEqualTo(201);
        var revisionJson = new ObjectMapper().readTree(revise.body());
        int revisedExamId = revisionJson.required("id").asInt();
        assertThat(revisionJson.required("versionNumber").asInt()).isEqualTo(2);

        assertDefinition(1, "Test Exam", 1, null, false, 1, 2);
        assertDefinition(revisedExamId, "Test Exam Revision", 2, 1, true, 1, 2);
        assertCorrectnessMetadata(revisedExamId, 1, 1);
        assertAttemptStillUsesDefinition(201, 1);

        HttpResponse<String> duplicateRevision = sendJson(
                client, "POST", "/api/v1/admin/exams/1/revisions", "token-admin1", revision);
        assertThat(duplicateRevision.statusCode()).isEqualTo(409);

        int questionId = firstQuestionId(revisedExamId);
        HttpResponse<String> startAttempt = sendJson(
                client, "POST", "/api/v1/student/exams/" + revisedExamId + "/attempts", "token-stu1", null);
        assertThat(startAttempt.statusCode()).isEqualTo(200);
        int revisedAttemptId = new ObjectMapper().readTree(startAttempt.body()).required("id").asInt();
        HttpRequest studentQuestion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/"
                        + revisedAttemptId + "/questions/" + questionId))
                .header("Authorization", "Bearer token-stu1")
                .GET()
                .build();
        HttpResponse<String> studentQuestionResponse = client.send(
                studentQuestion, HttpResponse.BodyHandlers.ofString());
        assertThat(studentQuestionResponse.statusCode()).isEqualTo(200);
        assertNoCorrectnessProperties(studentQuestionResponse.body());

        HttpResponse<String> list = sendJson(
                client, "GET", "/api/v1/admin/exams", "token-admin1", null);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"versionNumber\":2", "\"supersedesExamId\":1");

        HttpResponse<String> deactivate = sendJson(
                client, "DELETE", "/api/v1/admin/exams/" + createdExamId, "token-admin1", null);
        assertThat(deactivate.statusCode()).isEqualTo(204);
        assertDefinition(createdExamId, "API Definition", 1, null, false, 2, 5);

        HttpResponse<String> reactivate = sendJson(
                client, "POST", "/api/v1/admin/exams/" + createdExamId + "/reactivate", "token-admin1", null);
        assertThat(reactivate.statusCode()).isEqualTo(204);
        assertDefinition(createdExamId, "API Definition", 1, null, true, 2, 5);
    }

    @Test
    void sessionAndStudentObjectAuthorizationNegativesWriteNothing() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        assertThat(sendJson(client, "GET", "/api/v1/student/history", "unknown-token", null).statusCode())
                .isEqualTo(401);
        assertThat(sendJson(client, "GET", "/api/v1/student/history", "token-expired", null).statusCode())
                .isEqualTo(401);
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("UPDATE users SET is_active = 0 WHERE id = 104");
        }
        assertThat(sendJson(client, "GET", "/api/v1/student/history", "token-inactive", null).statusCode())
                .isEqualTo(401);

        HttpResponse<String> ownedQuestion = sendJson(
                client, "GET", "/api/v1/student/attempts/201/questions/401", "token-stu1", null);
        assertThat(ownedQuestion.statusCode()).isEqualTo(200);
        assertNoCorrectnessProperties(ownedQuestion.body());

        HttpResponse<String> otherExamQuestion = sendJson(
                client, "GET", "/api/v1/student/attempts/201/questions/402", "token-stu1", null);
        assertThat(otherExamQuestion.statusCode()).isEqualTo(404);

        HttpResponse<String> otherStudentAttempt = sendJson(
                client, "GET", "/api/v1/student/attempts/202/questions/402", "token-stu1", null);
        assertThat(otherStudentAttempt.statusCode()).isEqualTo(403);

        HttpResponse<String> unscopedQuestion = sendJson(
                client, "GET", "/api/v1/student/questions/401", "token-stu1", null);
        assertThat(unscopedQuestion.statusCode()).isEqualTo(404);

        String crossExamAnswer = """
                {"questionId": 402, "selectedOptions": [1]}
                """;
        HttpResponse<String> answerResponse = sendJson(
                client, "POST", "/api/v1/student/attempts/201/answers", "token-stu1", crossExamAnswer);
        assertThat(answerResponse.statusCode()).isEqualTo(400);
        assertDatabaseCount(
                "SELECT COUNT(*) FROM student_answers WHERE attempt_id = 201 AND question_id = 402", 0);

        String boundary = "ExaSecureCrossEventBoundary";
        HttpRequest crossEventEvidence = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT
                        + "/api/v1/student/attempts/201/events/301/proctor-evidence"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipartScreenshot(boundary, new byte[] {1, 2, 3})))
                .build();
        HttpResponse<String> evidenceResponse = client.send(
                crossEventEvidence, HttpResponse.BodyHandlers.ofString());
        assertThat(evidenceResponse.statusCode()).isEqualTo(404);
        assertDatabaseCount("SELECT COUNT(*) FROM proctor_evidence_v2 WHERE event_id = 301", 0);
    }

    @Test
    void optionNumbersRoundTripAndGradeIndependentlyOfDatabaseIds() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> questionResponse = sendJson(
                client, "GET", "/api/v1/student/attempts/203/questions/403", "token-stu1", null);
        assertThat(questionResponse.statusCode()).isEqualTo(200);
        var questionJson = new ObjectMapper().readTree(questionResponse.body());
        assertThat(questionJson.required("options").findValuesAsText("optionNumber"))
                .containsExactly("3", "7", "9");
        assertThat(questionResponse.body()).doesNotContain("\"id\":601", "\"id\":602", "\"id\":603");
        assertNoCorrectnessProperties(questionResponse.body());

        HttpResponse<String> databaseIdSelection = sendJson(
                client, "POST", "/api/v1/student/attempts/203/answers", "token-stu1",
                "{\"questionId\":403,\"selectedOptions\":[601]}");
        assertThat(databaseIdSelection.statusCode()).isEqualTo(400);
        assertDatabaseCount(
                "SELECT COUNT(*) FROM student_answers WHERE attempt_id = 203 AND question_id = 403", 0);

        HttpResponse<String> duplicateSelection = sendJson(
                client, "POST", "/api/v1/student/attempts/203/answers", "token-stu1",
                "{\"questionId\":403,\"selectedOptions\":[3,3]}");
        assertThat(duplicateSelection.statusCode()).isEqualTo(400);
        assertDatabaseCount(
                "SELECT COUNT(*) FROM student_answers WHERE attempt_id = 203 AND question_id = 403", 0);

        HttpResponse<String> validSelection = sendJson(
                client, "POST", "/api/v1/student/attempts/203/answers", "token-stu1",
                "{\"questionId\":403,\"selectedOptions\":[9,3]}");
        assertThat(validSelection.statusCode()).isEqualTo(200);
        assertStoredSelections(203, 403, 3, 9);

        HttpResponse<String> submit = sendJson(
                client, "POST", "/api/v1/student/attempts/203/submit", "token-stu1", null);
        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(submit.body()).asInt()).isEqualTo(1);
        assertAttemptSubmittedWithScore(203, 1);
    }

    @Test
    void attemptLifecycle_startResumeSubmitIdempotencyAndAnswerClearSemantics() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Resume existing active attempt 206 -> idempotent
        HttpResponse<String> resumeResponse = sendJson(
                client, "POST", "/api/v1/student/exams/6/attempts", "token-stu1", null);
        assertThat(resumeResponse.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(resumeResponse.body()).required("id").asInt()).isEqualTo(206);

        // 2. Save answer
        HttpResponse<String> saveFirst = sendJson(
                client, "POST", "/api/v1/student/attempts/206/answers", "token-stu1",
                "{\"questionId\":406,\"selectedOptions\":[1]}");
        assertThat(saveFirst.statusCode()).isEqualTo(200);
        assertStoredSelections(206, 406, 1);

        // 3. Replace answer
        HttpResponse<String> replaceAnswer = sendJson(
                client, "POST", "/api/v1/student/attempts/206/answers", "token-stu1",
                "{\"questionId\":406,\"selectedOptions\":[2]}");
        assertThat(replaceAnswer.statusCode()).isEqualTo(200);
        assertStoredSelections(206, 406, 2);

        // 4. Clear answer with empty list
        HttpResponse<String> clearAnswer = sendJson(
                client, "POST", "/api/v1/student/attempts/206/answers", "token-stu1",
                "{\"questionId\":406,\"selectedOptions\":[]}");
        assertThat(clearAnswer.statusCode()).isEqualTo(200);
        assertStoredSelections(206, 406); // 0 selections

        // 5. Re-save correct answer [1]
        HttpResponse<String> reSave = sendJson(
                client, "POST", "/api/v1/student/attempts/206/answers", "token-stu1",
                "{\"questionId\":406,\"selectedOptions\":[1]}");
        assertThat(reSave.statusCode()).isEqualTo(200);
        assertStoredSelections(206, 406, 1);

        // 6. Submit exam
        HttpResponse<String> submit = sendJson(
                client, "POST", "/api/v1/student/attempts/206/submit", "token-stu1", null);
        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(submit.body()).asInt()).isEqualTo(1);
        assertAttemptSubmittedWithScore(206, 1);

        // 7. Duplicate submit returns same score idempotently
        HttpResponse<String> duplicateSubmit = sendJson(
                client, "POST", "/api/v1/student/attempts/206/submit", "token-stu1", null);
        assertThat(duplicateSubmit.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(duplicateSubmit.body()).asInt()).isEqualTo(1);

        // 8. Post-submit attempt start on exam 6 is rejected with 409 Conflict
        HttpResponse<String> restartAttempt = sendJson(
                client, "POST", "/api/v1/student/exams/6/attempts", "token-stu1", null);
        assertThat(restartAttempt.statusCode()).isEqualTo(409);
        assertDatabaseCount("SELECT COUNT(*) FROM exam_attempts WHERE student_id = 101 AND exam_id = 6", 1);

        // 9. Post-submit answer save on attempt 206 is rejected with 409 Conflict
        HttpResponse<String> postSubmitAnswer = sendJson(
                client, "POST", "/api/v1/student/attempts/206/answers", "token-stu1",
                "{\"questionId\":406,\"selectedOptions\":[2]}");
        assertThat(postSubmitAnswer.statusCode()).isEqualTo(409);
        assertStoredSelections(206, 406, 1);

        // 10. Student history includes submitted attempt
        HttpResponse<String> studentHistory = sendJson(
                client, "GET", "/api/v1/student/history", "token-stu1", null);
        assertThat(studentHistory.statusCode()).isEqualTo(200);
        assertThat(studentHistory.body()).contains("\"examTitle\":\"Lifecycle Exam\"", "\"attemptId\":206", "\"score\":1", "\"status\":\"SUBMITTED\"");

        // 11. Admin exam attempt history includes submitted attempt
        HttpResponse<String> adminHistory = sendJson(
                client, "GET", "/api/v1/admin/exams/6/attempts", "token-admin1", null);
        assertThat(adminHistory.statusCode()).isEqualTo(200);
        assertThat(adminHistory.body()).contains("\"id\":206", "\"score\":1", "\"status\":\"SUBMITTED\"", "\"studentName\":\"Student One\"");
    }

    @Test
    void authoritativeTiming_expiredAttemptTransitionsToExpiredAndRejectsActions() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Calling start/resume on expired attempt 204 transitions it to EXPIRED and returns 409
        HttpResponse<String> startExpired = sendJson(
                client, "POST", "/api/v1/student/exams/4/attempts", "token-stu1", null);
        assertThat(startExpired.statusCode()).isEqualTo(409);
        assertThat(startExpired.body()).contains("expired");

        // 2. Database row updated to EXPIRED with end_time set
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, end_time FROM exam_attempts WHERE id = 204")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("EXPIRED");
                assertThat(rs.getTimestamp("end_time")).isNotNull();
            }
        }

        // 3. Answer saving on expired attempt rejected with 409
        HttpResponse<String> saveOnExpired = sendJson(
                client, "POST", "/api/v1/student/attempts/204/answers", "token-stu1",
                "{\"questionId\":404,\"selectedOptions\":[1]}");
        assertThat(saveOnExpired.statusCode()).isEqualTo(409);
        assertDatabaseCount("SELECT COUNT(*) FROM student_answers WHERE attempt_id = 204", 0);
    }

    @Test
    void concurrentSubmissionSafety_resolvesToIdenticalCommittedScore() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Save answer for attempt 205
        HttpResponse<String> save = sendJson(
                client, "POST", "/api/v1/student/attempts/205/answers", "token-stu1",
                "{\"questionId\":405,\"selectedOptions\":[1]}");
        assertThat(save.statusCode()).isEqualTo(200);

        // Submit concurrently
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var future1 = executor.submit(() -> sendJson(
                client, "POST", "/api/v1/student/attempts/205/submit", "token-stu1", null));
        var future2 = executor.submit(() -> sendJson(
                client, "POST", "/api/v1/student/attempts/205/submit", "token-stu1", null));

        HttpResponse<String> res1 = future1.get();
        HttpResponse<String> res2 = future2.get();
        executor.shutdown();

        assertThat(res1.statusCode()).isEqualTo(200);
        assertThat(res2.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(res1.body()).asInt()).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(res2.body()).asInt()).isEqualTo(1);
        assertAttemptSubmittedWithScore(205, 1);
    }

    @Test
    void studentAndAdminQueryEndpoints_returnExpectedDataWithoutLeakage() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Student lists available exams
        HttpResponse<String> examsRes = sendJson(
                client, "GET", "/api/v1/student/exams", "token-stu1", null);
        assertThat(examsRes.statusCode()).isEqualTo(200);
        assertThat(examsRes.body()).contains("Option Number Exam");

        // 2. Student lists attempt questions without correctness leakage
        HttpResponse<String> questionsRes = sendJson(
                client, "GET", "/api/v1/student/attempts/201/questions", "token-stu1", null);
        assertThat(questionsRes.statusCode()).isEqualTo(200);
        assertThat(questionsRes.body()).doesNotContain("\"correct\"");
        assertThat(questionsRes.body()).contains("Owned question");

        // 3. Student gets saved answers for attempt
        HttpResponse<String> answersRes = sendJson(
                client, "GET", "/api/v1/student/attempts/201/answers", "token-stu1", null);
        assertThat(answersRes.statusCode()).isEqualTo(200);

        // 4. Admin gets questions with correctness
        HttpResponse<String> adminQuestionsRes = sendJson(
                client, "GET", "/api/v1/admin/exams/1/questions", "token-admin1", null);
        assertThat(adminQuestionsRes.statusCode()).isEqualTo(200);
        assertThat(adminQuestionsRes.body()).contains("\"correct\"");

        // 5. Admin gets attempt details with correctness
        HttpResponse<String> attemptDetailsRes = sendJson(
                client, "GET", "/api/v1/admin/attempts/201/details", "token-admin1", null);
        assertThat(attemptDetailsRes.statusCode()).isEqualTo(200);
        assertThat(attemptDetailsRes.body()).contains("questionNumber");

        // 6. Admin gets exam statistics
        HttpResponse<String> statsRes = sendJson(
                client, "GET", "/api/v1/admin/exams/1/statistics", "token-admin1", null);
        assertThat(statsRes.statusCode()).isEqualTo(200);
        assertThat(statsRes.body()).contains("totalAttempts");
    }

    @Test
    void adminAttemptDetails_comprehensiveVerificationOfScoringAndTypes() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Dedicated attempt 293 on Exam 3 (student 102)
        int detailsAttemptId = 293;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (" + detailsAttemptId + ", 3, 102, NOW(), 'ACTIVE')");
        }

        // 1. Student access to admin details endpoint is rejected with 403
        HttpResponse<String> studentDenial = sendJson(
                client, "GET", "/api/v1/admin/attempts/" + detailsAttemptId + "/details", "token-stu1", null);
        assertThat(studentDenial.statusCode()).isEqualTo(403);

        // 2. Non-existent attempt returns empty details list
        HttpResponse<String> notFound = sendJson(
                client, "GET", "/api/v1/admin/attempts/99999/details", "token-admin1", null);
        assertThat(notFound.statusCode()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(notFound.body()).size()).isZero();

        // 3. Exam 3 has question 403: MULTI with options 3 (correct), 7 (wrong), 9 (correct)
        // Save answer with only option 3 (partially correct -> incorrect)
        HttpResponse<String> savePart = sendJson(
                client, "POST", "/api/v1/student/attempts/" + detailsAttemptId + "/answers", "token-stu2",
                "{\"questionId\":403,\"selectedOptions\":[3]}");
        assertThat(savePart.statusCode()).isEqualTo(200);

        HttpResponse<String> detailsRes = sendJson(
                client, "GET", "/api/v1/admin/attempts/" + detailsAttemptId + "/details", "token-admin1", null);
        assertThat(detailsRes.statusCode()).isEqualTo(200);
        var details = new ObjectMapper().readTree(detailsRes.body());
        assertThat(details.size()).isEqualTo(1);

        var q = details.get(0);
        assertThat(q.required("questionId").asInt()).isEqualTo(403);
        assertThat(q.required("questionNumber").asInt()).isEqualTo(1);
        assertThat(q.required("questionType").asText()).isEqualTo("MULTI");
        assertThat(q.required("isCorrect").asBoolean()).isFalse();
        assertThat(q.required("marksAwarded").asInt()).isEqualTo(0);

        // Check selected options and texts
        var selNums = q.required("selectedOptionNumbers");
        assertThat(selNums.size()).isEqualTo(1);
        assertThat(selNums.get(0).asInt()).isEqualTo(3);

        var selTexts = q.required("selectedOptionTexts");
        assertThat(selTexts.size()).isEqualTo(1);
        assertThat(selTexts.get(0).asText()).isEqualTo("First stable");

        // Check correct options (stable option numbers 3 and 9 preserved!)
        var corNums = q.required("correctOptionNumbers");
        assertThat(corNums.size()).isEqualTo(2);
        assertThat(corNums.get(0).asInt()).isEqualTo(3);
        assertThat(corNums.get(1).asInt()).isEqualTo(9);

        var corTexts = q.required("correctOptionTexts");
        assertThat(corTexts.size()).isEqualTo(2);
        assertThat(corTexts.get(0).asText()).isEqualTo("First stable");
        assertThat(corTexts.get(1).asText()).isEqualTo("Last stable");

        // Now update answer to both [3, 9] -> should be scored correct
        HttpResponse<String> saveFull = sendJson(
                client, "POST", "/api/v1/student/attempts/" + detailsAttemptId + "/answers", "token-stu2",
                "{\"questionId\":403,\"selectedOptions\":[3, 9]}");
        assertThat(saveFull.statusCode()).isEqualTo(200);

        HttpResponse<String> detailsFullRes = sendJson(
                client, "GET", "/api/v1/admin/attempts/" + detailsAttemptId + "/details", "token-admin1", null);
        var detailsFull = new ObjectMapper().readTree(detailsFullRes.body());
        var qFull = detailsFull.get(0);
        assertThat(qFull.required("isCorrect").asBoolean()).isTrue();
        assertThat(qFull.required("marksAwarded").asInt()).isEqualTo(1);
    }

    @Test
    void adminExamStatistics_truthfulMetricsAndDistribution() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Student access is rejected with 403
        HttpResponse<String> studentDenial = sendJson(
                client, "GET", "/api/v1/admin/exams/1/statistics", "token-stu1", null);
        assertThat(studentDenial.statusCode()).isEqualTo(403);

        // 2. Non-existent exam returns 404
        HttpResponse<String> notFound = sendJson(
                client, "GET", "/api/v1/admin/exams/99999/statistics", "token-admin1", null);
        assertThat(notFound.statusCode()).isEqualTo(404);

        // 3. Create a dedicated exam for statistics testing
        int statsExamId;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exams (title, duration_minutes, created_by, is_active) VALUES ('Stats Exam', 60, 103, 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                rs.next();
                statsExamId = rs.getInt(1);
            }
        }

        // 3a. Exam with 0 attempts: aggregates must be null and total 0
        HttpResponse<String> zeroStats = sendJson(
                client, "GET", "/api/v1/admin/exams/" + statsExamId + "/statistics", "token-admin1", null);
        assertThat(zeroStats.statusCode()).isEqualTo(200);
        var zeroJson = new ObjectMapper().readTree(zeroStats.body());
        assertThat(zeroJson.required("totalAttempts").asInt()).isZero();
        assertThat(zeroJson.required("submittedAttempts").asInt()).isZero();
        assertThat(zeroJson.required("activeAttempts").asInt()).isZero();
        assertThat(zeroJson.required("expiredAttempts").asInt()).isZero();
        assertThat(zeroJson.get("averageScore").isNull()).isTrue();
        assertThat(zeroJson.get("highestScore").isNull()).isTrue();
        assertThat(zeroJson.get("lowestScore").isNull()).isTrue();
        assertThat(zeroJson.required("scoreDistribution").size()).isZero();

        // 3b. Add ACTIVE and EXPIRED attempts (unscored) -> aggregates must remain null
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exam_attempts (exam_id, student_id, start_time, status) VALUES (" + statsExamId + ", 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (exam_id, student_id, start_time, end_time, status) VALUES (" + statsExamId + ", 102, DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), 'EXPIRED')");
        }

        HttpResponse<String> activeExpiredStats = sendJson(
                client, "GET", "/api/v1/admin/exams/" + statsExamId + "/statistics", "token-admin1", null);
        assertThat(activeExpiredStats.statusCode()).isEqualTo(200);
        var aeJson = new ObjectMapper().readTree(activeExpiredStats.body());
        assertThat(aeJson.required("totalAttempts").asInt()).isEqualTo(2);
        assertThat(aeJson.required("submittedAttempts").asInt()).isZero();
        assertThat(aeJson.required("activeAttempts").asInt()).isEqualTo(1);
        assertThat(aeJson.required("expiredAttempts").asInt()).isEqualTo(1);
        assertThat(aeJson.get("averageScore").isNull()).isTrue();

        // 3c. Add 2 SUBMITTED attempts with scores 0 and 2
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exam_attempts (exam_id, student_id, start_time, end_time, status, score) VALUES (" + statsExamId + ", 101, NOW(), NOW(), 'SUBMITTED', 0)");
            stmt.execute("INSERT INTO exam_attempts (exam_id, student_id, start_time, end_time, status, score) VALUES (" + statsExamId + ", 102, NOW(), NOW(), 'SUBMITTED', 2)");
        }

        HttpResponse<String> fullStats = sendJson(
                client, "GET", "/api/v1/admin/exams/" + statsExamId + "/statistics", "token-admin1", null);
        assertThat(fullStats.statusCode()).isEqualTo(200);
        var fullJson = new ObjectMapper().readTree(fullStats.body());
        assertThat(fullJson.required("totalAttempts").asInt()).isEqualTo(4);
        assertThat(fullJson.required("submittedAttempts").asInt()).isEqualTo(2);
        assertThat(fullJson.required("activeAttempts").asInt()).isEqualTo(1);
        assertThat(fullJson.required("expiredAttempts").asInt()).isEqualTo(1);
        assertThat(fullJson.required("averageScore").asDouble()).isEqualTo(1.0);
        assertThat(fullJson.required("highestScore").asInt()).isEqualTo(2);
        assertThat(fullJson.required("lowestScore").asInt()).isEqualTo(0);

        var dist = fullJson.required("scoreDistribution");
        assertThat(dist.required("0 pts").asInt()).isEqualTo(1);
        assertThat(dist.required("2 pts").asInt()).isEqualTo(1);
    }

    @Test
    void lateAnswerSaveArrivingAfterSubmission_isRejectedByRowLock() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Dedicated attempt 296 on Exam 6 (student 102)
        int lateAttemptId = 296;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (" + lateAttemptId + ", 6, 102, NOW(), 'ACTIVE')");
        }

        // 1. Save answer [1]
        HttpResponse<String> saveInit = sendJson(
                client, "POST", "/api/v1/student/attempts/" + lateAttemptId + "/answers", "token-stu2",
                "{\"questionId\":406,\"selectedOptions\":[1]}");
        assertThat(saveInit.statusCode()).isEqualTo(200);

        // 2. Submit the attempt -> status becomes SUBMITTED
        HttpResponse<String> submitRes = sendJson(
                client, "POST", "/api/v1/student/attempts/" + lateAttemptId + "/submit", "token-stu2", null);
        assertThat(submitRes.statusCode()).isEqualTo(200);
        assertAttemptSubmittedWithScore(lateAttemptId, 1);

        // 3. Try saving answer via HTTP on submitted attempt -> 409 Conflict
        HttpResponse<String> postSubmitHttp = sendJson(
                client, "POST", "/api/v1/student/attempts/" + lateAttemptId + "/answers", "token-stu2",
                "{\"questionId\":406,\"selectedOptions\":[2]}");
        assertThat(postSubmitHttp.statusCode()).isEqualTo(409);

        // 4. Verify direct repository call fails with IllegalStateException
        com.examsystem.server.adapters.jdbc.JdbcAnswerRepository answerRepo =
                new com.examsystem.server.adapters.jdbc.JdbcAnswerRepository(server.getDataSource());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            answerRepo.saveAnswer(lateAttemptId, 406, java.util.List.of(2), java.time.Instant.now());
        });

        // 5. Ensure database state for attempt 296 was not corrupted
        assertStoredSelections(lateAttemptId, 406, 1);
        assertAttemptSubmittedWithScore(lateAttemptId, 1);
    }

    @Test
    void adminCanReactivateDeactivatedUser() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // User 104 is seeded as inactive
        assertDatabaseCount("SELECT COUNT(*) FROM users WHERE id = 104 AND is_active = 1", 0);

        // Reactivate user 104
        HttpResponse<String> reactivateRes = sendJson(
                client, "POST", "/api/v1/admin/users/104/reactivate", "token-admin1", null);
        assertThat(reactivateRes.statusCode()).isEqualTo(204);

        // Verify active in DB
        assertDatabaseCount("SELECT COUNT(*) FROM users WHERE id = 104 AND is_active = 1", 1);

        // Deactivate user 104 again
        HttpResponse<String> deactivateRes = sendJson(
                client, "DELETE", "/api/v1/admin/users/104", "token-admin1", null);
        assertThat(deactivateRes.statusCode()).isEqualTo(204);
        assertDatabaseCount("SELECT COUNT(*) FROM users WHERE id = 104 AND is_active = 1", 0);
    }

    @Test
    void adminCanReactivateDeactivatedExamDefinitionWithoutNewRevision() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Deactivate exam 2
        HttpResponse<String> deactivateRes = sendJson(
                client, "DELETE", "/api/v1/admin/exams/2", "token-admin1", null);
        assertThat(deactivateRes.statusCode()).isEqualTo(204);

        assertDatabaseCount("SELECT COUNT(*) FROM exams WHERE id = 2 AND is_active = 1", 0);

        // Student available exams does not include exam 2
        HttpResponse<String> listBefore = sendJson(
                client, "GET", "/api/v1/student/exams", "token-stu1", null);
        assertThat(listBefore.statusCode()).isEqualTo(200);
        assertThat(listBefore.body()).doesNotContain("\"id\":2");

        // 2. Reactivate exam 2
        HttpResponse<String> reactivateRes = sendJson(
                client, "POST", "/api/v1/admin/exams/2/reactivate", "token-admin1", null);
        assertThat(reactivateRes.statusCode()).isEqualTo(204);

        assertDatabaseCount("SELECT COUNT(*) FROM exams WHERE id = 2 AND is_active = 1", 1);
        // Ensure no new revision was created (version remains unchanged and id remains 2)
        assertDatabaseCount("SELECT COUNT(*) FROM exams WHERE id = 2 AND version_number = 1", 1);

        // Student available exams now includes exam 2
        HttpResponse<String> listAfter = sendJson(
                client, "GET", "/api/v1/student/exams", "token-stu1", null);
        assertThat(listAfter.statusCode()).isEqualTo(200);
        assertThat(listAfter.body()).contains("\"id\":2");
    }

    @Test
    void proctoringThreeViolationsAutoSubmitsAndFlagsAttemptWithRawScoreAndDeduplicatesRetries() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Setup a dedicated active attempt
        int attemptId = 295;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status, focus_lost_count, is_flagged, proctoring_status) "
                             + "VALUES (?, 1, 101, NOW(), 'ACTIVE', 0, 0, 'CLEAN')")) {
            ps.setInt(1, attemptId);
            ps.executeUpdate();
        }

        // Save one correct answer for question 401 (option 1 is correct)
        sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/answers", "token-stu1",
                "{\"questionId\":401,\"selectedOptions\":[1]}");

        // 1st violation
        String clientEventId1 = java.util.UUID.randomUUID().toString();
        HttpResponse<String> v1 = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId1 + "\"}");
        assertThat(v1.statusCode()).isEqualTo(200);
        var json1 = new ObjectMapper().readTree(v1.body());
        assertThat(json1.get("violationCount").asInt()).isEqualTo(1);
        assertThat(json1.get("flagged").asBoolean()).isFalse();
        assertThat(json1.get("attemptStatus").asText()).isEqualTo("ACTIVE");

        // Retry of 1st violation with SAME clientEventId should be idempotent
        HttpResponse<String> v1Retry = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId1 + "\"}");
        assertThat(v1Retry.statusCode()).isEqualTo(200);
        var json1Retry = new ObjectMapper().readTree(v1Retry.body());
        assertThat(json1Retry.get("violationCount").asInt()).isEqualTo(1); // Not incremented

        // 2nd violation
        String clientEventId2 = java.util.UUID.randomUUID().toString();
        HttpResponse<String> v2 = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId2 + "\"}");
        assertThat(v2.statusCode()).isEqualTo(200);
        var json2 = new ObjectMapper().readTree(v2.body());
        assertThat(json2.get("violationCount").asInt()).isEqualTo(2);
        assertThat(json2.get("flagged").asBoolean()).isFalse();
        assertThat(json2.get("attemptStatus").asText()).isEqualTo("ACTIVE");

        // 3rd violation -> AUTO SUBMISSION & FLAGGED
        String clientEventId3 = java.util.UUID.randomUUID().toString();
        HttpResponse<String> v3 = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId3 + "\"}");
        assertThat(v3.statusCode()).isEqualTo(200);
        var json3 = new ObjectMapper().readTree(v3.body());
        assertThat(json3.get("violationCount").asInt()).isEqualTo(3);
        assertThat(json3.get("flagged").asBoolean()).isTrue();
        assertThat(json3.get("attemptStatus").asText()).isEqualTo("SUBMITTED");

        // Verify database state: status=SUBMITTED, is_flagged=1, proctoring_status=FLAGGED, raw score=1
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, score, focus_lost_count, is_flagged, proctoring_status FROM exam_attempts WHERE id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("SUBMITTED");
                assertThat(rs.getInt("score")).isEqualTo(1); // Truthful raw score preserved
                assertThat(rs.getInt("focus_lost_count")).isEqualTo(3);
                assertThat(rs.getBoolean("is_flagged")).isTrue();
                assertThat(rs.getString("proctoring_status")).isEqualTo("FLAGGED");
            }
        }
    }

    @Test
    void singleFocusLossIncident_allowsMultipleSupportingScreenshots_withoutIncreasingStrikes() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        int attemptId = 9801;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM exam_attempts WHERE id = " + attemptId);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status, focus_lost_count, is_flagged, proctoring_status) "
                            + "VALUES (?, 1, 101, NOW(), 'ACTIVE', 0, 0, 'CLEAN')")) {
                ps.setInt(1, attemptId);
                ps.executeUpdate();
            }
        }

        // 1. One focus loss incident = 1 strike
        String clientEventId = UUID.randomUUID().toString();
        HttpResponse<String> eventRes = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId + "\"}");
        assertThat(eventRes.statusCode()).isEqualTo(200);
        var eventJson = new ObjectMapper().readTree(eventRes.body());
        int eventId = eventJson.get("eventId").asInt();
        assertThat(eventJson.get("violationCount").asInt()).isEqualTo(1);
        assertThat(eventJson.get("flagged").asBoolean()).isFalse();
        assertThat(eventJson.get("attemptStatus").asText()).isEqualTo("ACTIVE");

        // 2. Upload INITIAL screenshot evidence (~800ms)
        byte[] initialBytes = new byte[]{11, 22, 33, 44};
        HttpResponse<String> initUploadRes = sendMultipartEvidence(client, attemptId, eventId, "token-stu1", initialBytes, "INITIAL");
        assertThat(initUploadRes.statusCode()).isEqualTo(200);

        // 3. Mark long absence state (~5000ms)
        HttpResponse<String> longAbsenceRes = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events/" + eventId + "/long-absence", "token-stu1", null);
        assertThat(longAbsenceRes.statusCode()).isEqualTo(200);

        // 4. Upload LONG_ABSENCE screenshot evidence (~5000ms)
        byte[] longAbsenceBytes = new byte[]{55, 66, 77, 88};
        HttpResponse<String> longUploadRes = sendMultipartEvidence(client, attemptId, eventId, "token-stu1", longAbsenceBytes, "LONG_ABSENCE");
        assertThat(longUploadRes.statusCode()).isEqualTo(200);

        // 5. Record return (e.g. 8500ms away)
        HttpResponse<String> returnRes = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events/" + eventId + "/return", "token-stu1",
                "{\"returnedAt\":\"" + Instant.now().toString() + "\",\"awayDurationMs\":8500}");
        assertThat(returnRes.statusCode()).isEqualTo(200);

        // Invariant: still strictly 1 strike!
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, focus_lost_count, is_flagged, proctoring_status FROM exam_attempts WHERE id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getInt("focus_lost_count")).isEqualTo(1);
                assertThat(rs.getBoolean("is_flagged")).isFalse();
                assertThat(rs.getString("proctoring_status")).isEqualTo("CLEAN");
            }
        }

        // Check proctor_events table
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT returned_at, away_duration_ms, is_long_absence FROM proctor_events WHERE id = ?")) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("returned_at")).isNotNull();
                assertThat(rs.getLong("away_duration_ms")).isGreaterThanOrEqualTo(0);
                assertThat(rs.getBoolean("is_long_absence")).isTrue();
            }
        }

        // 6. Admin query for proctor events
        HttpResponse<String> adminEventsRes = sendJson(client, "GET", "/api/v1/admin/attempts/" + attemptId + "/events", "token-admin1", null);
        assertThat(adminEventsRes.statusCode()).isEqualTo(200);
        var adminEvents = new ObjectMapper().readTree(adminEventsRes.body());
        assertThat(adminEvents.size()).isEqualTo(1);
        var ev = adminEvents.get(0);
        assertThat(ev.get("id").asInt()).isEqualTo(eventId);
        assertThat(ev.get("isLongAbsence").asBoolean()).isTrue();
        assertThat(ev.get("hasEvidence").asBoolean()).isTrue();
        assertThat(ev.get("evidenceTypes").toString()).contains("INITIAL");
        assertThat(ev.get("evidenceTypes").toString()).contains("LONG_ABSENCE");

        // 7. Verify round-trip bytes for each evidence type
        HttpResponse<byte[]> adminInitEv = fetchEvidence(client, eventId, "token-admin1", "INITIAL");
        assertThat(adminInitEv.statusCode()).isEqualTo(200);
        assertThat(adminInitEv.body()).containsExactly(initialBytes);

        HttpResponse<byte[]> adminLongEv = fetchEvidence(client, eventId, "token-admin1", "LONG_ABSENCE");
        assertThat(adminLongEv.statusCode()).isEqualTo(200);
        assertThat(adminLongEv.body()).containsExactly(longAbsenceBytes);

        // 8. Evidence Immutability: duplicate retry MUST NOT replace original bytes!
        byte[] duplicateRetryBytes = new byte[]{99, 99, 99};
        HttpResponse<String> duplicateUploadRes = sendMultipartEvidence(client, attemptId, eventId, "token-stu1", duplicateRetryBytes, "INITIAL");
        assertThat(duplicateUploadRes.statusCode()).isEqualTo(200);

        HttpResponse<byte[]> adminInitEvAfterDuplicate = fetchEvidence(client, eventId, "token-admin1", "INITIAL");
        assertThat(adminInitEvAfterDuplicate.statusCode()).isEqualTo(200);
        assertThat(adminInitEvAfterDuplicate.body()).containsExactly(initialBytes); // Original preserved!
    }

    @Test
    void studentTelemetryValidation_treatsClientDurationAsUntrusted() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        int attemptId = 9802;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM exam_attempts WHERE id = " + attemptId);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status, focus_lost_count, is_flagged, proctoring_status) "
                            + "VALUES (?, 1, 101, NOW(), 'ACTIVE', 0, 0, 'CLEAN')")) {
                ps.setInt(1, attemptId);
                ps.executeUpdate();
            }
        }

        String clientEventId = UUID.randomUUID().toString();
        HttpResponse<String> eventRes = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events", "token-stu1",
                "{\"eventType\":\"FOCUS_LOST\",\"clientEventId\":\"" + clientEventId + "\"}");
        int eventId = new ObjectMapper().readTree(eventRes.body()).get("eventId").asInt();

        // Send negative client duration: -99999
        HttpResponse<String> ret1 = sendJson(client, "POST", "/api/v1/student/attempts/" + attemptId + "/events/" + eventId + "/return", "token-stu1",
                "{\"returnedAt\":\"" + Instant.now().toString() + "\",\"awayDurationMs\":-99999}");
        assertThat(ret1.statusCode()).isEqualTo(200);

        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT away_duration_ms FROM proctor_events WHERE id = ?")) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("away_duration_ms")).isGreaterThanOrEqualTo(0); // Validated to non-negative
            }
        }
    }

    private static HttpResponse<String> sendMultipartEvidence(
            HttpClient client, int attemptId, int eventId, String token, byte[] screenshotBytes, String evidenceType) throws Exception {
        String boundary = "----TestBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (evidenceType != null) {
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write("Content-Disposition: form-data; name=\"evidenceType\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            baos.write(evidenceType.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write("Content-Disposition: form-data; name=\"screenshot\"; filename=\"screen.jpg\"\r\n".getBytes(StandardCharsets.UTF_8));
        baos.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        baos.write(screenshotBytes);
        baos.write("\r\n".getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/" + attemptId + "/events/" + eventId + "/proctor-evidence"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<byte[]> fetchEvidence(
            HttpClient client, int eventId, String token, String evidenceType) throws Exception {
        String query = evidenceType != null ? "?type=" + evidenceType : "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/admin/events/" + eventId + "/evidence" + query))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void assertDatabaseCount(String sql, int expected) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var statement = conn.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(expected);
        }
    }

    private static void assertStoredSelections(
            int attemptId, int questionId, int... expectedSelections) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT sao.selected_option FROM student_answers sa "
                             + "JOIN student_answer_options sao ON sao.answer_id = sa.id "
                             + "WHERE sa.attempt_id = ? AND sa.question_id = ? "
                             + "ORDER BY sao.selected_option")) {
            statement.setInt(1, attemptId);
            statement.setInt(2, questionId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.List<Integer> actual = new java.util.ArrayList<>();
                while (result.next()) {
                    actual.add(result.getInt(1));
                }
                assertThat(actual).containsExactly(
                        java.util.Arrays.stream(expectedSelections).boxed().toArray(Integer[]::new));
            }
        }
    }

    private static void assertAttemptSubmittedWithScore(int attemptId, int score) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT status, score, end_time FROM exam_attempts WHERE id = ?")) {
            statement.setInt(1, attemptId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("status")).isEqualTo("SUBMITTED");
                assertThat(result.getInt("score")).isEqualTo(score);
                assertThat(result.getTimestamp("end_time")).isNotNull();
            }
        }
    }

    private static void assertNoCorrectnessProperties(String json) {
        assertThat(json)
                .doesNotContain("\"correct\"")
                .doesNotContain("\"isCorrect\"")
                .doesNotContain("\"correctOptionNumber\"");
    }

    private static void assertDefinition(
            int examId,
            String title,
            int version,
            Integer supersedesExamId,
            boolean active,
            int expectedQuestions,
            int expectedOptions) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT title, version_number, supersedes_exam_id, is_active, "
                             + "(SELECT COUNT(*) FROM questions q WHERE q.exam_id = e.id) question_count, "
                             + "(SELECT COUNT(*) FROM options o JOIN questions q ON q.id = o.question_id "
                             + "WHERE q.exam_id = e.id) option_count FROM exams e WHERE e.id = ?")) {
            statement.setInt(1, examId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("title")).isEqualTo(title);
                assertThat(result.getInt("version_number")).isEqualTo(version);
                assertThat((Integer) result.getObject("supersedes_exam_id")).isEqualTo(supersedesExamId);
                assertThat(result.getBoolean("is_active")).isEqualTo(active);
                assertThat(result.getInt("question_count")).isEqualTo(expectedQuestions);
                assertThat(result.getInt("option_count")).isEqualTo(expectedOptions);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertAttemptStillUsesDefinition(int attemptId, int examId) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT exam_id FROM exam_attempts WHERE id = ?")) {
            statement.setInt(1, attemptId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("exam_id")).isEqualTo(examId);
            }
        }
    }

    private static void assertCorrectnessMetadata(
            int examId, int expectedCorrectOptions, int expectedPrimaryCorrectOptions) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT "
                             + "SUM(CASE WHEN o.is_correct = 1 THEN 1 ELSE 0 END) correct_options, "
                             + "SUM(CASE WHEN o.option_number = q.correct_option_number "
                             + "AND o.is_correct = 1 THEN 1 ELSE 0 END) primary_correct_options "
                             + "FROM questions q JOIN options o ON o.question_id = q.id WHERE q.exam_id = ?")) {
            statement.setInt(1, examId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("correct_options")).isEqualTo(expectedCorrectOptions);
                assertThat(result.getInt("primary_correct_options")).isEqualTo(expectedPrimaryCorrectOptions);
            }
        }
    }

    private static int firstQuestionId(int examId) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT id FROM questions WHERE exam_id = ? ORDER BY id LIMIT 1")) {
            statement.setInt(1, examId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt("id");
            }
        }
    }

    private static HttpResponse<String> sendJson(
            HttpClient client, String method, String path, String token, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + path))
                .header("Authorization", "Bearer " + token)
                .method(method, publisher);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertUserPassword(
            int userId, String expectedUsername, String rawPassword, boolean expectedActive) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT username, password, is_active FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("username")).isEqualTo(expectedUsername);
                String storedPassword = result.getString("password");
                assertThat(storedPassword).isNotEqualTo(rawPassword);
                assertThat(BCrypt.checkpw(rawPassword, storedPassword)).isTrue();
                assertThat(result.getBoolean("is_active")).isEqualTo(expectedActive);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void insertSession(String token, int userId) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "INSERT INTO server_sessions (token, user_id, expires_at) "
                             + "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 1 HOUR))")) {
            statement.setString(1, token);
            statement.setInt(2, userId);
            statement.executeUpdate();
        }
    }

    private static void assertSessionMissing(String token) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT COUNT(*) FROM server_sessions WHERE token = ?")) {
            statement.setString(1, token);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    private static byte[] multipartScreenshot(String boundary, byte[] screenshot) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"screenshot\"; filename=\"evidence.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(screenshot);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    private static void assertEvidenceStoredExactly(int eventId, byte[] expected) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT event_id, content_type, byte_size, screenshot_data "
                             + "FROM proctor_evidence_v2 WHERE event_id = ?")) {
            statement.setInt(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("event_id")).isEqualTo(eventId);
                assertThat(result.getString("content_type")).isEqualTo("image/png");
                assertThat(result.getInt("byte_size")).isEqualTo(expected.length);
                assertThat(result.getBytes("screenshot_data")).containsExactly(expected);
                assertThat(result.next()).isFalse();
            }
        }
    }

    private static void assertUserMissing(int userId) throws Exception {
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    @Test
    void testMultiAttemptIsolationAndDeleteAttempt() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // 1. Seed dedicated Exam 99 with Question 991, Options 9901, 9902
        int dedicatedExamId = 99;
        int attemptA = 991; // Student 101
        int attemptB = 992; // Student 102

        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (" + dedicatedExamId + ", 'Isolation Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type, is_active) VALUES (991, " + dedicatedExamId + ", 'Iso Q', 1, 'SINGLE', 1)");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct, is_active) VALUES (9901, 991, 1, 'Iso C', 1, 1), (9902, 991, 2, 'Iso W', 0, 1)");

            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (" + attemptA + ", " + dedicatedExamId + ", 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (" + attemptB + ", " + dedicatedExamId + ", 102, NOW(), 'ACTIVE')");

            // Add answers for Attempt A
            stmt.execute("INSERT INTO student_answers (id, attempt_id, question_id, selected_options, is_correct, answer_timestamp) VALUES (1001, " + attemptA + ", 991, '1', 1, NOW())");
            stmt.execute("INSERT INTO student_answer_options (answer_id, selected_option) VALUES (1001, 1)");

            // Add answers for Attempt B
            stmt.execute("INSERT INTO student_answers (id, attempt_id, question_id, selected_options, is_correct, answer_timestamp) VALUES (1002, " + attemptB + ", 991, '1', 1, NOW())");
            stmt.execute("INSERT INTO student_answer_options (answer_id, selected_option) VALUES (1002, 1)");

            // Add proctor events and evidence for Attempt A
            stmt.execute("INSERT INTO proctor_events (id, attempt_id, event_type, recorded_at) VALUES (1101, " + attemptA + ", 'FOCUS_LOST', NOW())");
            stmt.execute("INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at) VALUES (1101, 'image/png', 4, 0x12345678, NOW())");

            // Add proctor events and evidence for Attempt B
            stmt.execute("INSERT INTO proctor_events (id, attempt_id, event_type, recorded_at) VALUES (1102, " + attemptB + ", 'FOCUS_LOST', NOW())");
            stmt.execute("INSERT INTO proctor_evidence_v2 (event_id, content_type, byte_size, screenshot_data, recorded_at) VALUES (1102, 'image/png', 4, 0x9ABCDEF0, NOW())");
        }

        // 2. Call DELETE /api/v1/admin/attempts/991 (Delete Attempt A)
        HttpResponse<String> deleteResponse = sendJson(
                client, "DELETE", "/api/v1/admin/attempts/" + attemptA, "token-admin1", null);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        // 3. Verify MySQL database state
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             var stmt = conn.createStatement()) {

            // Attempt A is GONE
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM exam_attempts WHERE id = " + attemptA)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
            // Attempt A answers are GONE
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_answers WHERE attempt_id = " + attemptA)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_answer_options WHERE answer_id = 1001")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
            // Attempt A proctor events and evidence are GONE
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM proctor_events WHERE attempt_id = " + attemptA)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM proctor_evidence_v2 WHERE event_id = 1101")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            // Attempt B (Student 102) remains completely INTACT
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM exam_attempts WHERE id = " + attemptB)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_answers WHERE attempt_id = " + attemptB)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM student_answer_options WHERE answer_id = 1002")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM proctor_events WHERE attempt_id = " + attemptB)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM proctor_evidence_v2 WHERE event_id = 1102")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            // Exam questions, options, and definition remain completely INTACT
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM questions WHERE exam_id = " + dedicatedExamId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM options WHERE question_id = 991")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT is_active FROM exams WHERE id = " + dedicatedExamId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }

        // 4. Verify Exam definition remains usable via Student API
        HttpResponse<String> startNewAttempt = sendJson(
                client, "POST", "/api/v1/student/exams/" + dedicatedExamId + "/attempts", "token-stu1", null);
        assertThat(startNewAttempt.statusCode()).isEqualTo(200);
        int newAttemptId = new ObjectMapper().readTree(startNewAttempt.body()).required("id").asInt();
        assertThat(newAttemptId).isGreaterThan(0);
    }

    @Test
    void testThreeViolationsEnforceFlaggedInvariantAcrossAllLayers() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        int attemptId = 299;
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status, focus_lost_count, is_flagged, proctoring_status) "
                             + "VALUES (?, 5, 101, NOW(), 'ACTIVE', 0, 0, 'CLEAN') ON DUPLICATE KEY UPDATE status = 'ACTIVE', focus_lost_count = 0, is_flagged = 0, proctoring_status = 'CLEAN'")) {
            ps.setInt(1, attemptId);
            ps.executeUpdate();
        }

        // Send 1st violation
        HttpRequest ev1 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/" + attemptId + "/events"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("eventType=FOCUS_LOST"))
                .build();
        HttpResponse<String> resp1 = client.send(ev1, HttpResponse.BodyHandlers.ofString());
        assertThat(resp1.statusCode()).isEqualTo(200);
        var json1 = new ObjectMapper().readTree(resp1.body());
        assertThat(json1.required("violationCount").asInt()).isEqualTo(1);
        assertThat(json1.required("flagged").asBoolean()).isFalse();

        // Send 2nd violation
        HttpRequest ev2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/" + attemptId + "/events"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("eventType=FOCUS_LOST"))
                .build();
        HttpResponse<String> resp2 = client.send(ev2, HttpResponse.BodyHandlers.ofString());
        assertThat(resp2.statusCode()).isEqualTo(200);
        var json2 = new ObjectMapper().readTree(resp2.body());
        assertThat(json2.required("violationCount").asInt()).isEqualTo(2);
        assertThat(json2.required("flagged").asBoolean()).isFalse();

        // Send 3rd violation -> MUST trigger termination and flagging!
        HttpRequest ev3 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + PORT + "/api/v1/student/attempts/" + attemptId + "/events"))
                .header("Authorization", "Bearer token-stu1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("eventType=FOCUS_LOST"))
                .build();
        HttpResponse<String> resp3 = client.send(ev3, HttpResponse.BodyHandlers.ofString());
        assertThat(resp3.statusCode()).isEqualTo(200);
        var json3 = new ObjectMapper().readTree(resp3.body());
        assertThat(json3.required("violationCount").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(json3.required("flagged").asBoolean()).isTrue();

        // 2. Verify authoritative state in MySQL database
        try (var conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT focus_lost_count, is_flagged, proctoring_status FROM exam_attempts WHERE id = ?")) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("focus_lost_count")).isGreaterThanOrEqualTo(3);
                assertThat(rs.getBoolean("is_flagged")).isTrue();
                assertThat(rs.getString("proctoring_status")).isEqualTo("FLAGGED");
            }
        }

        // 3. Verify Student History API
        HttpResponse<String> stuHistory = sendJson(
                client, "GET", "/api/v1/student/history", "token-stu1", null);
        assertThat(stuHistory.statusCode()).isEqualTo(200);
        var historyArray = new ObjectMapper().readTree(stuHistory.body());
        boolean foundInStuHistory = false;
        for (var item : historyArray) {
            if (item.required("attemptId").asInt() == attemptId) {
                foundInStuHistory = true;
                assertThat(item.required("flagged").asBoolean()).isTrue();
                break;
            }
        }
        assertThat(foundInStuHistory).isTrue();

        // 4. Verify Admin Attempts API
        HttpResponse<String> adminAttempts = sendJson(
                client, "GET", "/api/v1/admin/exams/5/attempts", "token-admin1", null);
        assertThat(adminAttempts.statusCode()).isEqualTo(200);
        var adminArray = new ObjectMapper().readTree(adminAttempts.body());
        boolean foundInAdminAttempts = false;
        for (var item : adminArray) {
            if (item.required("id").asInt() == attemptId) {
                foundInAdminAttempts = true;
                assertThat(item.required("flagged").asBoolean()).isTrue();
                assertThat(item.required("proctoringStatus").asText()).isEqualTo("FLAGGED");
                assertThat(item.required("focusLostCount").asInt()).isGreaterThanOrEqualTo(3);
                break;
            }
        }
        assertThat(foundInAdminAttempts).isTrue();

        // 5. Verify Admin Attempt Details API
        HttpResponse<String> adminDetails = sendJson(
                client, "GET", "/api/v1/admin/attempts/" + attemptId + "/details", "token-admin1", null);
        assertThat(adminDetails.statusCode()).isEqualTo(200);
    }
}
