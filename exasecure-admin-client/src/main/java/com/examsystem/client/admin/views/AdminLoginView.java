package com.examsystem.client.admin.views;

import com.examsystem.client.admin.AdminApp;
import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.contracts.AuthResponseDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class AdminLoginView {
    private final AdminApp app;
    private final ApiClient apiClient;
    private final StackPane view;

    public AdminLoginView(AdminApp app, ApiClient apiClient) {
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

        Label badge = new Label("ADMINISTRATIVE CONSOLE");
        badge.getStyleClass().addAll("portal-badge", "portal-badge-admin");

        Label title = new Label("ExaSecure");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label("System Administration & Proctoring Portal");
        subtitle.getStyleClass().add("secondary-text");

        Label serverLabel = new Label("Server: " + apiClient.getConfig().baseUrl());
        serverLabel.getStyleClass().addAll("secondary-text", "small-text");

        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("Administrator Username");
        userLabel.getStyleClass().add("field-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter admin username");
        usernameField.setPrefHeight(40);

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("field-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter admin password");
        passwordField.setPrefHeight(40);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().addAll("status-error", "small-text");
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In as Administrator");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);
        loginBtn.getStyleClass().add("btn-admin");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);
        spinner.managedProperty().bind(spinner.visibleProperty());

        Runnable performLogin = () -> {
            String u = usernameField.getText().trim();
            String p = passwordField.getText();
            if (u.isEmpty() || p.isEmpty()) {
                errorLabel.setText("Please enter username and password.");
                errorLabel.setVisible(true);
                return;
            }

            errorLabel.setVisible(false);
            loginBtn.setDisable(true);
            usernameField.setDisable(true);
            passwordField.setDisable(true);
            spinner.setVisible(true);

            AsyncRunner.runAsync(
                    () -> {
                        AuthResponseDto reply = apiClient.login(u, p);
                        if (!"ADMIN".equalsIgnoreCase(reply.role())) apiClient.logout();
                        return reply;
                    },
                    (AuthResponseDto res) -> {
                        spinner.setVisible(false);
                        loginBtn.setDisable(false);
                        usernameField.setDisable(false);
                        passwordField.setDisable(false);

                        if (!"ADMIN".equalsIgnoreCase(res.role())) {
                            errorLabel.setText("Access denied: Account has " + res.role() + " role. Administrator privileges required.");
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
        card.getChildren().addAll(badge, title, subtitle, serverLabel, form, spinner);

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        StackPane.setAlignment(themeSelector, Pos.TOP_RIGHT);
        StackPane.setMargin(themeSelector, new Insets(24));

        view.getChildren().addAll(card, themeSelector);
    }
}
