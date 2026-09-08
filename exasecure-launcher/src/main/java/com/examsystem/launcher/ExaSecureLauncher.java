package com.examsystem.launcher;

import com.examsystem.client.theme.ThemeManager;
import com.examsystem.client.ui.AppIcon;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.nio.file.Path;

public class ExaSecureLauncher {
    public static void main(String[] args) { Application.launch(App.class, args); }

    public static class App extends Application {
        private final ProcessSupervisor supervisor = new ProcessSupervisor(LaunchSession::current);
        private Label serverStatusLabel;
        private Label infoStatusLabel;
        private Button startServerBtn;
        private Button startServerAndAdminBtn;
        private Button openAdminBtn;
        private Button stopBtn;
        private Button configureServer;
        private Timeline refresh;

        @Override
        public void start(Stage stage) {
            Platform.setImplicitExit(false);
            Label titleLabel = new Label("ExaSecure Control Center");
            titleLabel.getStyleClass().add("page-title");
            Label subtitleLabel = new Label("Operator & Administration Control Center");
            subtitleLabel.getStyleClass().addAll("secondary-text", "small-text");
            HBox headerBox = new HBox(12);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerBox.getChildren().addAll(new VBox(2, titleLabel, subtitleLabel), spacer,
                    ThemeManager.getInstance().createThemeSelector());

            serverStatusLabel = new Label();
            serverStatusLabel.getStyleClass().add("section-title");
            startServerBtn = button("Start Server", "btn-primary", () -> supervisor.startServer());
            startServerAndAdminBtn = button("Start Server + Admin", "btn-primary", () ->
                    supervisor.startServer().thenAccept(result -> {
                        if (result.server() == ProcessSupervisor.ServerState.OWNED_READY
                                || result.server() == ProcessSupervisor.ServerState.EXTERNAL_AVAILABLE) supervisor.startAdmin();
                    }));
            openAdminBtn = button("Open Admin", "btn-secondary", () -> supervisor.startAdmin());
            stopBtn = button("Stop Server", "btn-danger", () -> supervisor.stopServer());
            configureServer = button("Configure Installed Server", "btn-secondary", () -> {
                OperatorSetupDialog.show(stage, new OperatorSettings());
                supervisor.checkAvailability();
            });
            infoStatusLabel = new Label();
            infoStatusLabel.getStyleClass().addAll("secondary-text", "small-text");
            infoStatusLabel.setWrapText(true);
            infoStatusLabel.setAlignment(Pos.CENTER);
            VBox card = new VBox(12, headerBox, serverStatusLabel, startServerBtn,
                    startServerAndAdminBtn, openAdminBtn, stopBtn, configureServer, infoStatusLabel);
            card.getStyleClass().addAll("theme-card", "login-card");
            card.setAlignment(Pos.CENTER);
            VBox root = new VBox(card);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));
            root.getStyleClass().add("app-root");
            Scene scene = new Scene(root, 440, 430);
            ThemeManager.getInstance().applyTheme(scene);
            stage.setTitle("ExaSecure Control Center");
            AppIcon.applyTo(stage);
            stage.setScene(scene);
            stage.setMinWidth(420);
            stage.setMinHeight(430);
            stage.setOnCloseRequest(event -> {
                event.consume();
                supervisor.closeAsync().whenComplete((result, error) -> Platform.runLater(() -> {
                    refresh.stop();
                    render();
                    if (error == null && result.serverPid() == null && result.adminPid() == null) {
                        stage.hide();
                        Platform.exit();
                    } else infoStatusLabel.setText("Owned process cleanup did not finish. Inspect the owned processes before exiting.");
                }));
                render();
            });
            refresh = new Timeline(new KeyFrame(Duration.millis(200), event -> render()));
            refresh.setCycleCount(Timeline.INDEFINITE);
            refresh.play();
            render();
            stage.show();
            supervisor.checkAvailability();
        }

        private Button button(String text, String style, Runnable action) {
            Button button = new Button(text);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add(style);
            button.setOnAction(event -> { action.run(); render(); });
            return button;
        }

        private void render() {
            var result = supervisor.snapshot();
            String status = switch (result.server()) {
                case STOPPED -> "Stopped";
                case STARTING -> "Starting...";
                case STOPPING -> "Stopping...";
                case OWNED_READY -> "Owned and ready (Port " + result.port() + ")";
                case EXTERNAL_AVAILABLE -> "External available (Port " + result.port() + ")";
                case FAILED -> "Failed";
            };
            serverStatusLabel.setText("Server: " + status);
            serverStatusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
            serverStatusLabel.getStyleClass().add(switch (result.server()) {
                case OWNED_READY, EXTERNAL_AVAILABLE -> "status-success";
                case STARTING, STOPPING -> "status-warning";
                default -> "status-error";
            });
            infoStatusLabel.setText(result.message());
            startServerBtn.setDisable(result.closing() || result.busy() || result.serverPid() != null);
            startServerAndAdminBtn.setDisable(result.closing() || result.busy() || result.adminRunning());
            openAdminBtn.setDisable(result.closing() || result.busy() || result.adminRunning());
            // Stop remains available during startup. External availability never enables termination.
            stopBtn.setDisable(result.closing() || result.server() == ProcessSupervisor.ServerState.STOPPING
                    || (result.serverPid() == null && result.server() != ProcessSupervisor.ServerState.STARTING));
            configureServer.setDisable(result.closing() || result.busy() || result.serverPid() != null || result.adminRunning());
        }

        @Override public void stop() {
            if (refresh != null) refresh.stop();
            supervisor.closeAsync();
        }

        public static Path findRepoRoot() { return DevelopmentCommandFactory.findRepoRoot(Path.of("")); }
    }
}
