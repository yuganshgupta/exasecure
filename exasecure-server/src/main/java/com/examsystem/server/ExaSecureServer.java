package com.examsystem.server;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.PasswordEncoder;
import com.examsystem.application.usecases.AuthenticateUserUseCase;
import com.examsystem.application.usecases.StartExamUseCase;
import com.examsystem.application.usecases.SubmitExamUseCase;
import com.examsystem.server.adapters.crypto.BCryptPasswordEncoder;
import com.examsystem.server.adapters.jdbc.*;
import com.examsystem.server.adapters.time.SystemClockPort;
import com.examsystem.server.auth.AuthMiddleware;
import com.examsystem.server.auth.SessionManager;
import com.examsystem.server.config.AppConfig;
import com.examsystem.server.db.DatabaseMigrator;
import com.examsystem.server.http.controllers.AuthController;
import com.examsystem.server.http.controllers.StudentExamController;
import com.examsystem.server.http.exceptions.GlobalExceptionHandler;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public class ExaSecureServer {
    private static final Logger log = LoggerFactory.getLogger(ExaSecureServer.class);
    private final AppConfig config;
    private Javalin app;
    private DataSource dataSource;

    public ExaSecureServer(AppConfig config) {
        this.config = config;
    }

    public void start() {
        dataSource = config.createDataSource();
        
        // Migrate DB
        new DatabaseMigrator(dataSource).migrate();

        // Adapters
        ClockPort clockPort = new SystemClockPort();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        JdbcUserRepository userRepository = new JdbcUserRepository(dataSource);
        JdbcExamAttemptRepository attemptRepository = new JdbcExamAttemptRepository(dataSource);
        JdbcQuestionRepository questionRepository = new JdbcQuestionRepository(dataSource);
        JdbcAnswerRepository answerRepository = new JdbcAnswerRepository(dataSource);
        JdbcExamRepository examRepository = new JdbcExamRepository(dataSource);
        SessionManager sessionManager = new SessionManager(dataSource);

        // Use Cases
        AuthenticateUserUseCase authUseCase = new AuthenticateUserUseCase(userRepository, passwordEncoder);
        StartExamUseCase startUseCase = new StartExamUseCase(attemptRepository, clockPort);
        SubmitExamUseCase submitUseCase = new SubmitExamUseCase(answerRepository, attemptRepository, questionRepository, clockPort);

        // Controllers
        AuthController authController = new AuthController(authUseCase, sessionManager);
        StudentExamController studentController = new StudentExamController(startUseCase, submitUseCase, examRepository, questionRepository, attemptRepository);
        AuthMiddleware authMiddleware = new AuthMiddleware(sessionManager);

        // App init
        app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
        });

        app.before("/api/v1/*", authMiddleware::enforceAuthentication);
        
        app.get("/health", ctx -> ctx.result("OK"));

        app.post("/api/v1/auth/login", authController::login);
        app.post("/api/v1/auth/logout", authController::logout);

        app.post("/api/v1/student/exams/{examId}/attempts", studentController::startOrResumeExam);
        app.get("/api/v1/student/questions/{questionId}", studentController::getQuestion);
        app.post("/api/v1/student/attempts/{attemptId}/answers", studentController::saveAnswer);
        app.post("/api/v1/student/attempts/{attemptId}/submit", studentController::submitExam);

        GlobalExceptionHandler.register(app);

        app.start(config.port);
        log.info("ExaSecure Server running on port {}", config.port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
        }
    }

    public static void main(String[] args) {
        new ExaSecureServer(new AppConfig()).start();
    }
}
