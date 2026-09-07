package com.examsystem.client.student;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.ui.AppIcon;
import com.examsystem.client.student.views.ExamTakingView;
import com.examsystem.client.student.views.StudentDashboardView;
import com.examsystem.client.student.views.StudentHistoryView;
import com.examsystem.client.student.views.StudentLoginView;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentQuestionDto;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

public class StudentApp extends Application {
    private Stage primaryStage;
    private ApiClient apiClient;
    private StackPane rootLayout;
    private StudentConnection connection;
    private boolean loginVisible;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.connection = new StudentConnection();
        this.apiClient = connection.client();
        this.rootLayout = new StackPane();

        Scene scene = new Scene(rootLayout, 1080, 720);
        com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(scene);
        stage.setTitle("ExaSecure — Student Examination");
        AppIcon.applyTo(stage);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        showLogin();
        stage.show();
    }

    public void showLogin() {
        connection.loggedOut();
        loginVisible = true;
        if (apiClient == null) {
            showServerSetup();
            return;
        }
        if (primaryStage != null && primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(false);
        }
        StudentLoginView loginView = new StudentLoginView(this, apiClient);
        rootLayout.getChildren().setAll(loginView.getView());
    }

    public void showDashboard() {
        loginVisible = false;
        connection.lock();
        if (primaryStage != null && primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(false);
        }
        StudentDashboardView dashboardView = new StudentDashboardView(this, apiClient);
        rootLayout.getChildren().setAll(dashboardView.getView());
    }

    public void showExamTaking(StudentExamDto exam, StudentAttemptDto attempt, List<StudentQuestionDto> questions, Map<Integer, List<Integer>> initialAnswers) {
        if (primaryStage != null) {
            primaryStage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
            primaryStage.setFullScreenExitHint("");
            primaryStage.setFullScreen(true);
        }
        ExamTakingView examView =
                new ExamTakingView(this, apiClient, primaryStage, exam, attempt, questions, initialAnswers);
        rootLayout.getChildren().setAll(examView.getView());
    }

    public void showHistory() {
        if (primaryStage != null && primaryStage.isFullScreen()) {
            primaryStage.setFullScreen(false);
        }
        StudentHistoryView historyView = new StudentHistoryView(this, apiClient);
        rootLayout.getChildren().setAll(historyView.getView());
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public boolean canConfigureServer() { return loginVisible && connection.canConfigure(); }

    public void authenticationStarted() { connection.lock(); }
    public void authenticationFailed() { connection.loggedOut(); }

    public void showServerSetup() {
        if (!loginVisible) return;
        rootLayout.getChildren().setAll(new com.examsystem.client.student.views.StudentServerSetupView(this, connection).getView());
    }

    public void saveServer(String address) throws java.io.IOException {
        if (!loginVisible) throw new IllegalStateException("Log out before changing Server.");
        connection.save(address);
        apiClient = connection.client();
        showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
