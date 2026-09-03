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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

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
        view.setStyle("-fx-background-color: #0f172a;");

        VBox card = new VBox(16);
        card.getStyleClass().add("theme-card");
        card.setMaxWidth(420);
        card.setPadding(new Insets(36));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-radius: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 6);");

        Label badge = new Label("ADMINISTRATIVE CONSOLE");
        badge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-radius: 12px;");

        Label title = new Label("ExaSecure");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #f8fafc;");

        Label subtitle = new Label("System Administration & Proctoring Portal");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        Label serverLabel = new Label("Server: " + apiClient.getConfig().baseUrl());
        serverLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        VBox form = new VBox(12);
        form.setAlignment(Pos.CENTER_LEFT);

        Label userLabel = new Label("Administrator Username");
        userLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter admin username");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-background-radius: 6px; -fx-border-color: #475569; -fx-border-radius: 6px; -fx-padding: 0 10;");

        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter admin password");
        passwordField.setPrefHeight(40);
        passwordField.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-background-radius: 6px; -fx-border-color: #475569; -fx-border-radius: 6px; -fx-padding: 0 10;");

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setTextFill(Color.web("#f87171"));
        errorLabel.setStyle("-fx-font-size: 12px;");
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In as Administrator");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);
        loginBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        loginBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6px; -fx-cursor: hand;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);

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
                    () -> apiClient.login(u, p),
                    (AuthResponseDto res) -> {
                        spinner.setVisible(false);
                        loginBtn.setDisable(false);
                        usernameField.setDisable(false);
                        passwordField.setDisable(false);

                        if (!"ADMIN".equalsIgnoreCase(res.role())) {
                            apiClient.logout();
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
        StackPane.setMargin(themeSelector, new Insets(16));

        view.getChildren().addAll(card, themeSelector);
    }
}
