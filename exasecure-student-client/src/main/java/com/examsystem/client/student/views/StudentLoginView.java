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
        view.getStyleClass().addAll("app-root", "login-page");

        VBox card = new VBox(16);
        card.getStyleClass().addAll("theme-card", "login-card");
        card.setMaxWidth(430);
        card.setAlignment(Pos.CENTER);

        Label badge = new Label("STUDENT PORTAL");
        badge.getStyleClass().add("portal-badge");

        Label title = new Label("ExaSecure");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("Secure Desktop Examination System");
        subtitle.getStyleClass().add("secondary-text");

        VBox serverInfoBox = new VBox(2);
        serverInfoBox.setAlignment(Pos.CENTER);
        Label serverLabel = new Label("Server: " + apiClient.getConfig().baseUrl());
        serverLabel.getStyleClass().addAll("secondary-text", "small-text");
        serverLabel.setWrapText(true);
        Button changeServer = new Button("Change Server");
        changeServer.setDisable(!app.canConfigureServer());
        changeServer.setOnAction(e -> app.showServerSetup());
        serverInfoBox.getChildren().addAll(serverLabel, changeServer);

        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("Username or Enrollment");
        userLabel.getStyleClass().add("field-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your student username");
        usernameField.setPrefHeight(40);

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("field-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setPrefHeight(40);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().addAll("status-error", "small-text");
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In to Examination Portal");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);
        loginBtn.getStyleClass().add("btn-primary");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);
        spinner.managedProperty().bind(spinner.visibleProperty());

        Runnable performLogin = () -> {
            if (loginBtn.isDisabled()) return;
            String u = usernameField.getText().trim();
            String p = passwordField.getText();
            if (u.isEmpty() || p.isEmpty()) {
                errorLabel.setText("Please enter both username and password.");
                errorLabel.setVisible(true);
                return;
            }

            errorLabel.setVisible(false);
            app.authenticationStarted();
            changeServer.setDisable(true);
            loginBtn.setDisable(true);
            usernameField.setDisable(true);
            passwordField.setDisable(true);
            spinner.setVisible(true);

            AsyncRunner.runAsync(
                    () -> {
                        AuthResponseDto reply = apiClient.login(u, p);
                        if (!"STUDENT".equalsIgnoreCase(reply.role())) apiClient.logout();
                        return reply;
                    },
                    (AuthResponseDto res) -> {
                        spinner.setVisible(false);
                        loginBtn.setDisable(false);
                        usernameField.setDisable(false);
                        passwordField.setDisable(false);

                        if (!"STUDENT".equalsIgnoreCase(res.role())) {
                            app.authenticationFailed();
                            changeServer.setDisable(!app.canConfigureServer());
                            errorLabel.setText("Access denied: This account has the " + res.role() + " role. Please use the Admin Console.");
                            errorLabel.setVisible(true);
                            return;
                        }

                        app.showDashboard();
                    },
                    (Throwable err) -> {
                        app.authenticationFailed();
                        changeServer.setDisable(!app.canConfigureServer());
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

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        StackPane.setAlignment(themeSelector, Pos.TOP_RIGHT);
        StackPane.setMargin(themeSelector, new Insets(24));

        view.getChildren().addAll(card, themeSelector);
    }
}
