package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.student.StudentApp;
import com.examsystem.contracts.AuthResponseDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class StudentLoginView {
    private final StudentApp app;
    private final ApiClient apiClient;
    private final StackPane view;

    public StudentLoginView(StudentApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
        this.view = new StackPane();
        buildUI();
    }

    public Parent getView() {
        return view;
    }

    private void buildUI() {
        view.setStyle("-fx-background-color: #f1f5f9;");

        VBox card = new VBox(16);
        card.setMaxWidth(420);
        card.setPadding(new Insets(36));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 16, 0, 0, 4);");

        Label badge = new Label("STUDENT PORTAL");
        badge.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #3730a3; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-radius: 12px;");

        Label title = new Label("ExaSecure");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #0f172a;");

        Label subtitle = new Label("Secure Desktop Examination System");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        VBox serverInfoBox = new VBox(2);
        serverInfoBox.setAlignment(Pos.CENTER);
        Label serverLabel = new Label("Server: " + apiClient.getConfig().baseUrl());
        serverLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        serverInfoBox.getChildren().add(serverLabel);

        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("Username or Enrollment");
        userLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #334155;");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your student username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 0 10;");

        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #334155;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 0 10;");

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setTextFill(Color.web("#dc2626"));
        errorLabel.setStyle("-fx-font-size: 12px;");
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In to Examination Portal");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);
        loginBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        loginBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);

        Runnable performLogin = () -> {
            String u = usernameField.getText().trim();
            String p = passwordField.getText();
            if (u.isEmpty() || p.isEmpty()) {
                errorLabel.setText("Please enter both username and password.");
                errorLabel.setVisible(true);
                return;
            }

            errorLabel.setVisible(false);
            loginBtn.setDisable(true);
            usernameField.setDisable(true);
            passwordField.setDisable(true);
            spinner.setVisible(true);

            AsyncRunner.runAsync(
                    () -> apiClient.login(u, p),
                    (AuthResponseDto res) -> {
                        spinner.setVisible(false);
                        loginBtn.setDisable(false);
                        usernameField.setDisable(false);
                        passwordField.setDisable(false);

                        if (!"STUDENT".equalsIgnoreCase(res.role())) {
                            apiClient.logout();
                            errorLabel.setText("Access denied: This account has the " + res.role() + " role. Please use the Admin Console.");
                            errorLabel.setVisible(true);
                            return;
                        }

                        app.showDashboard();
                    },
                    (Throwable err) -> {
                        spinner.setVisible(false);
                        loginBtn.setDisable(false);
                        usernameField.setDisable(false);
                        passwordField.setDisable(false);

                        if (err instanceof ApiException apiEx && apiEx.getServerMessage() != null && !apiEx.getServerMessage().isBlank() && !"null".equalsIgnoreCase(apiEx.getServerMessage().trim())) {
                            errorLabel.setText(apiEx.getServerMessage());
                        } else {
                            errorLabel.setText("Unable to connect to the ExaSecure server. Please ensure the server is running on " + apiClient.getConfig().baseUrl());
                        }
                        errorLabel.setVisible(true);
                    }
            );
        };

        loginBtn.setOnAction(e -> performLogin.run());
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> performLogin.run());

        form.getChildren().addAll(userLabel, usernameField, passLabel, passwordField, errorLabel, loginBtn);
        card.getChildren().addAll(badge, title, subtitle, serverInfoBox, form, spinner);
        view.getChildren().add(card);
    }
}
