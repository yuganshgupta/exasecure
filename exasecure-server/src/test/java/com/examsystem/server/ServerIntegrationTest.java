package com.examsystem.server;

import com.examsystem.server.config.AppConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ServerIntegrationTest {

    private static ExaSecureServer server;
    private static final int PORT = 8089;

    @BeforeAll
    static void setUp() throws Exception {
        System.setProperty("SERVER_PORT", String.valueOf(PORT));
        System.setProperty("DB_URL", "jdbc:mysql://localhost:3306/exasecure_test_r2?allowMultiQueries=true");
        System.setProperty("DB_USER", "root");
        System.setProperty("DB_PASSWORD", "baba1234");
        
        // Clean test DB
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:mysql://localhost:3306/exasecure_test_r2", "root", "baba1234");
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            stmt.execute("DROP TABLE IF EXISTS legacy_proctor_logs, proctor_evidence_v2, proctor_events, proctor_evidence, proctor_logs, student_answer_options, student_answers, exam_attempts, exam_questions, options, questions, exams, server_sessions, schema_versions, users;");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
        
        server = new ExaSecureServer(new AppConfig());
        server.start();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
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
        // Seed users
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:mysql://localhost:3306/exasecure_test_r2", "root", "baba1234");
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT IGNORE INTO users (id, username, password, full_name, role, is_active) VALUES (101, 'student1', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Student One', 'STUDENT', 1)");
            stmt.execute("INSERT IGNORE INTO users (id, username, password, full_name, role, is_active) VALUES (102, 'student2', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Student Two', 'STUDENT', 1)");
            stmt.execute("INSERT IGNORE INTO users (id, username, password, full_name, role, is_active) VALUES (103, 'admin1', '$2a$10$w09ZlM.O/x10QhJv9eU3T.n/fHk69d5uL5Y5YQ/D3qD3D5D5D3D5q', 'Admin One', 'ADMIN', 1)");
            
            stmt.execute("INSERT IGNORE INTO exams (id, title, duration_minutes, created_by, is_active) VALUES (1, 'Test Exam', 60, 103, 1)");
            stmt.execute("INSERT IGNORE INTO exam_attempts (id, exam_id, student_id, start_time, status) VALUES (201, 1, 101, NOW(), 'ACTIVE')");
            
            // Seed a token for student2
            stmt.execute("INSERT IGNORE INTO server_sessions (token, user_id, expires_at) VALUES ('token-stu2', 102, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
            // Seed a token for admin1
            stmt.execute("INSERT IGNORE INTO server_sessions (token, user_id, expires_at) VALUES ('token-admin1', 103, DATE_ADD(NOW(), INTERVAL 1 HOUR))");
        }
        
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
}
