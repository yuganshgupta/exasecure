package com.examsystem.server;

import com.examsystem.application.ports.ClockPort;
import com.examsystem.application.ports.PasswordEncoder;
import com.examsystem.application.usecases.AuthenticateUserUseCase;
import com.examsystem.application.usecases.AdminUserMutationUseCase;
import com.examsystem.application.usecases.AdminExamDefinitionUseCase;
import com.examsystem.application.usecases.StartExamUseCase;
import com.examsystem.application.usecases.SubmitExamUseCase;
import com.examsystem.server.adapters.crypto.BCryptPasswordEncoder;
import com.examsystem.server.adapters.jdbc.*;
import com.examsystem.server.adapters.time.SystemClockPort;
import com.examsystem.server.auth.AuthMiddleware;
import com.examsystem.server.auth.SessionManager;
import com.examsystem.server.config.AppConfig;
import com.examsystem.server.db.DatabaseMigrator;
import com.examsystem.server.db.DatabaseReadiness;
import com.examsystem.server.http.controllers.AuthController;
import com.examsystem.server.http.controllers.StudentExamController;
import com.examsystem.server.http.controllers.AdminController;
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
        JdbcExamAttemptRepository attemptRepository = new JdbcExamAttemptRepository(dataSource, clockPort);
        JdbcQuestionRepository questionRepository = new JdbcQuestionRepository(dataSource);
        JdbcAnswerRepository answerRepository = new JdbcAnswerRepository(dataSource, clockPort);
        JdbcExamRepository examRepository = new JdbcExamRepository(dataSource);
        SessionManager sessionManager = new SessionManager(dataSource);
        JdbcProctorRepository proctorRepository = new JdbcProctorRepository(dataSource);
        JdbcAdminUserMutationPort adminUserMutationPort = new JdbcAdminUserMutationPort(dataSource);
        JdbcAdminExamDefinitionMutationPort adminExamMutationPort =
                new JdbcAdminExamDefinitionMutationPort(dataSource);

        // Use Cases
        AuthenticateUserUseCase authUseCase = new AuthenticateUserUseCase(userRepository, passwordEncoder);
        StartExamUseCase startUseCase = new StartExamUseCase(attemptRepository, clockPort);
        SubmitExamUseCase submitUseCase = new SubmitExamUseCase(answerRepository, attemptRepository, questionRepository, clockPort);
        AdminUserMutationUseCase adminUserMutationUseCase =
                new AdminUserMutationUseCase(adminUserMutationPort, passwordEncoder);
        AdminExamDefinitionUseCase adminExamDefinitionUseCase =
                new AdminExamDefinitionUseCase(adminExamMutationPort);

        // Controllers
        com.examsystem.application.ports.AdminQueryPort adminQueryPort = new com.examsystem.server.adapters.jdbc.JdbcAdminQueryPort(dataSource);
        com.examsystem.application.ports.StudentQueryPort studentQueryPort = new com.examsystem.server.adapters.jdbc.JdbcStudentQueryPort(dataSource);
        AuthController authController = new AuthController(authUseCase, sessionManager);
        StudentExamController studentController = new StudentExamController(
                startUseCase, submitUseCase, examRepository, questionRepository, attemptRepository, proctorRepository, studentQueryPort, answerRepository, new com.examsystem.application.usecases.RecordProctorIncidentUseCase(new JdbcProctorIncidentPort(dataSource, clockPort)));
        AdminController adminController = new AdminController(
                adminQueryPort, adminUserMutationUseCase, adminExamDefinitionUseCase, questionRepository, attemptRepository);
        AuthMiddleware authMiddleware = new AuthMiddleware(sessionManager);

        var readiness = new DatabaseReadiness(dataSource);

        // App init
        app = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            
            javalinConfig.routes.apiBuilder(() -> {
                io.javalin.apibuilder.ApiBuilder.get("/health", ctx -> ctx.result("OK"));
                io.javalin.apibuilder.ApiBuilder.get("/ready", ctx -> {
                    if (readiness.isReady()) ctx.result("OK");
                    else ctx.status(503).result("NOT READY");
                });
                
                io.javalin.apibuilder.ApiBuilder.path("/api/v1", () -> {
                    io.javalin.apibuilder.ApiBuilder.before("/*", authMiddleware::enforceAuthentication);
                    
                    io.javalin.apibuilder.ApiBuilder.post("/auth/login", authController::login);
                    io.javalin.apibuilder.ApiBuilder.post("/auth/logout", authController::logout);

                    io.javalin.apibuilder.ApiBuilder.get("/student/exams", studentController::listAvailableExams);
                    io.javalin.apibuilder.ApiBuilder.get("/student/history", studentController::listHistory);
                    io.javalin.apibuilder.ApiBuilder.post("/student/exams/{examId}/attempts", studentController::startOrResumeExam);
                    io.javalin.apibuilder.ApiBuilder.get(
                            "/student/attempts/{attemptId}/questions",
                            studentController::getAttemptQuestions);
                    io.javalin.apibuilder.ApiBuilder.get(
                            "/student/attempts/{attemptId}/questions/{questionId}",
                            studentController::getQuestion);
                    io.javalin.apibuilder.ApiBuilder.get(
                            "/student/attempts/{attemptId}/answers",
                            studentController::getAttemptAnswers);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/answers", studentController::saveAnswer);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/submit", studentController::submitExam);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/events", studentController::recordEvent);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/events/{eventId}/proctor-evidence", studentController::uploadEvidence);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/events/{eventId}/return", studentController::recordReturn);
                    io.javalin.apibuilder.ApiBuilder.post("/student/attempts/{attemptId}/events/{eventId}/long-absence", studentController::recordLongAbsence);
                    
                    io.javalin.apibuilder.ApiBuilder.path("/admin", () -> {
                        io.javalin.apibuilder.ApiBuilder.get("/users", adminController::listUsers);
                        io.javalin.apibuilder.ApiBuilder.post("/users", adminController::createUser);
                        io.javalin.apibuilder.ApiBuilder.put("/users/{userId}", adminController::updateUser);
                        io.javalin.apibuilder.ApiBuilder.post("/users/{userId}/deactivate", adminController::deactivateUser);
                        io.javalin.apibuilder.ApiBuilder.post("/users/{userId}/reactivate", adminController::reactivateUser);
                        io.javalin.apibuilder.ApiBuilder.delete("/users/{userId}", adminController::deleteUser);
                        io.javalin.apibuilder.ApiBuilder.get("/exams", adminController::listExams);
                        io.javalin.apibuilder.ApiBuilder.get("/exams/{examId}/questions", adminController::getExamQuestions);
                        io.javalin.apibuilder.ApiBuilder.post("/exams", adminController::createExamDefinition);
                        io.javalin.apibuilder.ApiBuilder.post(
                                "/exams/{examId}/revisions", adminController::reviseExamDefinition);
                        io.javalin.apibuilder.ApiBuilder.delete(
                                "/exams/{examId}", adminController::deactivateExamDefinition);
                        io.javalin.apibuilder.ApiBuilder.post(
                                "/exams/{examId}/reactivate", adminController::reactivateExamDefinition);
                        io.javalin.apibuilder.ApiBuilder.get("/exams/{examId}/attempts", adminController::getAttemptHistory);
                        io.javalin.apibuilder.ApiBuilder.delete("/attempts/{attemptId}", adminController::deleteAttempt);
                        io.javalin.apibuilder.ApiBuilder.get("/exams/{examId}/statistics", adminController::getExamStatistics);
                        io.javalin.apibuilder.ApiBuilder.get("/attempts/{attemptId}/details", adminController::getAttemptDetails);
                        io.javalin.apibuilder.ApiBuilder.get("/attempts/{attemptId}/events", adminController::getProctorEvents);
                        io.javalin.apibuilder.ApiBuilder.get("/events/{eventId}/evidence", adminController::getAttemptEvidence);
                    });
                });
            });

            GlobalExceptionHandler.register(javalinConfig);
        });

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

    public DataSource getDataSource() {
        return dataSource;
    }

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        if ((args.length > 0 && "--bootstrap".equalsIgnoreCase(args[0])) || Boolean.getBoolean("exasecure.bootstrap")) {
            config.requireDevelopmentBootstrap();
            com.examsystem.server.bootstrap.DevBootstrap.run(config);
        }
        if ("1".equals(System.getenv("EXASECURE_PROCESS_CONTROL"))) {
            com.examsystem.server.bootstrap.ManagedServerControl.run(config);
        } else {
            new ExaSecureServer(config).start();
        }
    }
}
