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
            stmt.execute("INSERT INTO questions (id, exam_id, question_text, correct_option_number, question_type, is_active) VALUES (401, 1, 'Owned question', 1, 'SINGLE', 1), (402, 2, 'Other question', 1, 'SINGLE', 1), (403, 3, 'Stable numbers', 3, 'MULTI', 1)");
            stmt.execute("INSERT INTO options (id, question_id, option_number, option_text, is_correct, is_active) VALUES (501, 401, 1, 'Owned correct', 1, 1), (502, 401, 2, 'Owned wrong', 0, 1), (503, 402, 1, 'Other correct', 1, 1), (504, 402, 2, 'Other wrong', 0, 1), (601, 403, 3, 'First stable', 1, 1), (602, 403, 7, 'Middle stable', 0, 1), (603, 403, 9, 'Last stable', 1, 1)");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (201, 1, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (202, 2, 102, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (203, 3, 101, NOW(), 'ACTIVE')");
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

        insertSession("token-api-student-before-delete", userId);
        HttpResponse<String> deleteResponse = sendJson(
                client, "DELETE", "/api/v1/admin/users/" + userId, "token-admin1", null);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
        assertUserPassword(userId, "api-student-updated", "replacement-password", false);
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
    }

    @Test
    void sessionAndStudentObjectAuthorizationNegativesWriteNothing() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        assertThat(sendJson(client, "GET", "/api/v1/student/history", "unknown-token", null).statusCode())
                .isEqualTo(401);
        assertThat(sendJson(client, "GET", "/api/v1/student/history", "token-expired", null).statusCode())
                .isEqualTo(401);
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
}
