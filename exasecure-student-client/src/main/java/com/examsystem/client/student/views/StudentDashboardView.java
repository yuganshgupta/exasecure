package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.session.SessionState;
import com.examsystem.client.student.StudentApp;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentQuestionDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Map;

public class StudentDashboardView {
    private final StudentApp app;
    private final ApiClient apiClient;
    private final BorderPane view;
    private final VBox examListContainer;
    private final Label statusLabel;
    private final Button refreshBtn;

    public StudentDashboardView(StudentApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
        this.view = new BorderPane();
        this.examListContainer = new VBox(12);
        this.statusLabel = new Label();
        this.refreshBtn = new Button("Refresh");
        buildUI();
        loadAvailableExams();
    }

    public Parent getView() {
        return view;
    }

    private void buildUI() {
        view.setStyle("-fx-background-color: #f8fafc;");

        // Top Navigation Bar
        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(16, 24, 16, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #1e293b;");

        Label brand = new Label("ExaSecure");
        brand.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        brand.setStyle("-fx-text-fill: white;");

        Label portalBadge = new Label("STUDENT");
        portalBadge.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String displayName = apiClient.getSessionState().fullName() != null
                ? apiClient.getSessionState().fullName()
                : apiClient.getSessionState().username();
        Label userLabel = new Label("Welcome, " + displayName);
        userLabel.setStyle("-fx-text-fill: #e2e8f0; -fx-font-size: 13px;");

        Button historyBtn = new Button("Exam History");
        historyBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand;");
        historyBtn.setOnAction(e -> app.showHistory());

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand;");
        logoutBtn.setOnAction(e -> {
            apiClient.logout();
            app.showLogin();
        });

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        topBar.getChildren().addAll(brand, portalBadge, spacer, themeSelector, userLabel, historyBtn, logoutBtn);
        view.setTop(topBar);

        // Center Content Area
        VBox center = new VBox(20);
        center.setPadding(new Insets(24));
        center.setMaxWidth(960);
        center.setAlignment(Pos.TOP_LEFT);

        HBox sectionHeader = new HBox(16);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label sectionTitle = new Label("Available Examinations");
        sectionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        sectionTitle.setStyle("-fx-text-fill: #0f172a;");

        Label sectionSubtitle = new Label("Click 'Start / Resume Examination' to enter the secure testing interface.");
        sectionSubtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");
        titleBox.getChildren().addAll(sectionTitle, sectionSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        refreshBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 12; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> loadAvailableExams());

        sectionHeader.getChildren().addAll(titleBox, headerSpacer, refreshBtn);

        statusLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");

        ScrollPane scrollPane = new ScrollPane(examListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        center.getChildren().addAll(sectionHeader, statusLabel, scrollPane);

        StackPane centerContainer = new StackPane(center);
        centerContainer.setAlignment(Pos.TOP_CENTER);
        view.setCenter(centerContainer);
    }

    private void loadAvailableExams() {
        examListContainer.getChildren().clear();
        statusLabel.setText("Loading available examinations from server...");
        refreshBtn.setDisable(true);

        AsyncRunner.runAsync(
                apiClient::listAvailableExams,
                (List<StudentExamDto> exams) -> {
                    refreshBtn.setDisable(false);
                    if (exams == null || exams.isEmpty()) {
                        statusLabel.setText("No examinations are currently active or available.");
                        return;
                    }
                    statusLabel.setText("");
                    for (StudentExamDto exam : exams) {
                        examListContainer.getChildren().add(createExamCard(exam));
                    }
                },
                (Throwable err) -> {
                    refreshBtn.setDisable(false);
                    if (err instanceof ApiException apiEx && apiEx.isUnauthorized()) {
                        UiFeedback.showError("Session Expired", "Your session has expired. Please log in again.");
                        app.showLogin();
                        return;
                    }
                    statusLabel.setText("Failed to load examinations: " + err.getMessage());
                }
        );
    }

    private HBox createExamCard(StudentExamDto exam) {
        HBox card = new HBox(20);
        card.getStyleClass().add("theme-card");
        card.setPadding(new Insets(18, 24, 18, 24));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 8, 0, 0, 2);");

        VBox info = new VBox(6);
        Label title = new Label(exam.title());
        title.getStyleClass().add("theme-label-bold");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        HBox meta = new HBox(12);
        Label durationBadge = new Label("Duration: " + exam.durationMinutes() + " mins");
        durationBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 12px; -fx-padding: 3 8; -fx-background-radius: 4px;");

        Label secureBadge = new Label("Proctored & Monitored");
        secureBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-size: 12px; -fx-padding: 3 8; -fx-background-radius: 4px;");

        meta.getChildren().addAll(durationBadge, secureBadge);
        info.getChildren().addAll(title, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button startBtn = new Button("Start / Resume Exam");
        startBtn.setPrefHeight(38);
        startBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        startBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand; -fx-padding: 0 16;");

        startBtn.setOnAction(e -> promptAndLaunchExam(exam, startBtn));

        card.getChildren().addAll(info, spacer, startBtn);
        return card;
    }

    private void promptAndLaunchExam(StudentExamDto exam, Button startBtn) {
        String message = "Examination: " + exam.title() + "\n"
                + "Duration: " + exam.durationMinutes() + " minutes\n\n"
                + "SECURITY & EXAMINATION RULES:\n"
                + "1. This exam environment is actively proctored.\n"
                + "2. Switching away from the exam or losing window focus records a proctoring violation and captures screenshot evidence.\n"
                + "3. Your answers are automatically saved to the server in real time.\n"
                + "4. You can flag questions for review and return to them anytime before submission.\n"
                + "5. When the countdown timer reaches 00:00, your exam will be automatically finalized.\n\n"
                + "Click 'OK' to enter the examination environment.";

        boolean confirmed = UiFeedback.showConfirmation(
                "Examination Pre-Flight Check",
                "Ready to begin: " + exam.title() + "?",
                message
        );
        if (confirmed) {
            launchExam(exam, startBtn);
        }
    }

    private void launchExam(StudentExamDto exam, Button startBtn) {
        startBtn.setDisable(true);
        startBtn.setText("Connecting...");

        AsyncRunner.runAsync(
                () -> {
                    StudentAttemptDto attempt = apiClient.startOrResumeAttempt(exam.id());
                    List<StudentQuestionDto> questions = apiClient.getAttemptQuestions(attempt.id());
                    Map<Integer, List<Integer>> answers = apiClient.getAttemptAnswers(attempt.id());
                    return new ExamData(attempt, questions, answers);
                },
                (ExamData data) -> {
                    startBtn.setDisable(false);
                    startBtn.setText("Start / Resume Exam");
                    if (data.questions.isEmpty()) {
                        UiFeedback.showError("Exam Notice", "This examination currently has no active questions.");
                        return;
                    }
                    app.showExamTaking(exam, data.attempt, data.questions, data.answers);
                },
                (Throwable err) -> {
                    startBtn.setDisable(false);
                    startBtn.setText("Start / Resume Exam");
                    if (err instanceof ApiException apiEx) {
                        UiFeedback.showError("Cannot Start Examination", apiEx.getServerMessage());
                    } else {
                        UiFeedback.showError("Connection Error", "Failed to connect to examination server: " + err.getMessage());
                    }
                }
        );
    }

    private record ExamData(StudentAttemptDto attempt, List<StudentQuestionDto> questions, Map<Integer, List<Integer>> answers) {}
}
