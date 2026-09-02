package com.examsystem.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.examsystem.server.config.AppConfig;
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
            stmt.execute("INSERT INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (1, 'Test Exam', 60, 103, 1)");
            stmt.execute("INSERT INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (201, 1, 101, NOW(), 'ACTIVE')");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-stu1', 101, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-stu2', 102, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            stmt.execute("INSERT INTO server_sessions (token, user_id, expires_at) VALUES ('token-admin1', 103, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
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
