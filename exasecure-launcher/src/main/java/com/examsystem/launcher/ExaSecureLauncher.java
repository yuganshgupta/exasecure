package com.examsystem.launcher;

import com.examsystem.client.theme.ThemeManager;
import com.examsystem.client.ui.AppIcon;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExaSecureLauncher {

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    public static class App extends Application {
        private static final String HEALTH_URL = "http://localhost:8089/health";
        private final List<Process> launchedProcesses = Collections.synchronizedList(new ArrayList<>());

        private Label serverStatusLabel;
        private Label infoStatusLabel;
        private Button startServerBtn;
        private Button startServerAndAdminBtn;
        private Button openAdminBtn;
        private Button stopBtn;

        @Override
        public void start(Stage stage) {
            Label titleLabel = new Label("ExaSecure Control Center");
            titleLabel.getStyleClass().add("page-title");

            Label subtitleLabel = new Label("Operator & Administration Control Center");
            subtitleLabel.getStyleClass().addAll("secondary-text", "small-text");

            var themeSelector = ThemeManager.getInstance().createThemeSelector();

            HBox headerBox = new HBox(12);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            VBox titlesBox = new VBox(2, titleLabel, subtitleLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerBox.getChildren().addAll(titlesBox, spacer, themeSelector);

            serverStatusLabel = new Label("Server: Checking...");
            serverStatusLabel.getStyleClass().add("section-title");
            updateServerStatusDisplay(false, false);

            startServerBtn = new Button("Start Server");
            startServerBtn.setMaxWidth(Double.MAX_VALUE);
            startServerBtn.getStyleClass().add("btn-primary");
            startServerBtn.setOnAction(e -> onStartServerOnly());

            startServerAndAdminBtn = new Button("Start Server + Admin");
            startServerAndAdminBtn.setMaxWidth(Double.MAX_VALUE);
            startServerAndAdminBtn.getStyleClass().add("btn-primary");
            startServerAndAdminBtn.setOnAction(e -> onStartServerAndAdmin());

            openAdminBtn = new Button("Open Admin Console");
            openAdminBtn.setMaxWidth(Double.MAX_VALUE);
            openAdminBtn.setText("Open Admin");
            openAdminBtn.getStyleClass().add("btn-secondary");
            openAdminBtn.setOnAction(e -> onOpenAdminConsole());

            stopBtn = new Button("Stop Server");
            stopBtn.setMaxWidth(Double.MAX_VALUE);
            stopBtn.getStyleClass().add("btn-danger");
            stopBtn.setOnAction(e -> onStopExaSecure());

            Button configureServer = new Button("Configure Installed Server");
            configureServer.setMaxWidth(Double.MAX_VALUE);
            configureServer.setOnAction(e -> OperatorSetupDialog.show(stage, new OperatorSettings()));

            infoStatusLabel = new Label("Ready.");
            infoStatusLabel.getStyleClass().addAll("secondary-text", "small-text");
            infoStatusLabel.setWrapText(true);
            infoStatusLabel.setAlignment(Pos.CENTER);

            VBox card = new VBox(12, headerBox, serverStatusLabel, startServerBtn, startServerAndAdminBtn, openAdminBtn, stopBtn, configureServer, infoStatusLabel);
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

            stage.setOnCloseRequest(e -> onStopExaSecure());
            stage.show();

            // Background initial check of server health
            CompletableFuture.runAsync(() -> {
                boolean healthy = isServerHealthy();
                Platform.runLater(() -> updateServerStatusDisplay(healthy, false));
            });
        }

        private void updateServerStatusDisplay(boolean running, boolean starting) {
            serverStatusLabel.getStyleClass().removeAll("status-success", "status-warning", "status-error");
            if (starting) {
                serverStatusLabel.setText("Server: Starting...");
                serverStatusLabel.getStyleClass().add("status-warning");
            } else if (running) {
                serverStatusLabel.setText("Server: Running (Port 8089)");
                serverStatusLabel.getStyleClass().add("status-success");
            } else {
                serverStatusLabel.setText("Server: Stopped");
                serverStatusLabel.getStyleClass().add("status-error");
            }
        }

        private void setButtonsDisabled(boolean disabled) {
            startServerBtn.setDisable(disabled);
            startServerAndAdminBtn.setDisable(disabled);
            openAdminBtn.setDisable(disabled);
            stopBtn.setDisable(disabled);
        }

        private void onStartServerOnly() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Checking server state...");

            CompletableFuture.runAsync(() -> {
                try {
                    if (isServerHealthy()) {
                        Platform.runLater(() -> {
                            updateServerStatusDisplay(true, false);
                            infoStatusLabel.setText("Server is already running on port 8089.");
                            setButtonsDisabled(false);
                        });
                        return;
                    }

                    Platform.runLater(() -> {
                        updateServerStatusDisplay(false, true);
                        infoStatusLabel.setText("Starting ExaSecure Server...");
                    });

                    Process server = launchModule(LaunchTarget.SERVER);
                    trackProcess(server);

                    boolean healthy = waitForServerHealth();
                    Platform.runLater(() -> {
                        updateServerStatusDisplay(healthy, false);
                        infoStatusLabel.setText(healthy ? "Server started successfully." : "Server did not become ready on port 8089.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to start the ExaSecure server. Check local configuration and try again.");
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private void onStartServerAndAdmin() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Checking server state...");

            CompletableFuture.runAsync(() -> {
                try {
                    if (!isServerHealthy()) {
                        Platform.runLater(() -> {
                            updateServerStatusDisplay(false, true);
                            infoStatusLabel.setText("Starting ExaSecure Server...");
                        });

                        Process server = launchModule(LaunchTarget.SERVER);
                        trackProcess(server);

                        boolean healthy = waitForServerHealth();
                        if (!healthy) {
                            Platform.runLater(() -> {
                                updateServerStatusDisplay(false, false);
                                infoStatusLabel.setText("Server did not become ready on port 8089.");
                                setButtonsDisabled(false);
                            });
                            return;
                        }
                    }

                    Platform.runLater(() -> {
                        updateServerStatusDisplay(true, false);
                        infoStatusLabel.setText("Server ready. Launching Admin Console...");
                    });

                    Process admin = launchModule(LaunchTarget.ADMIN);
                    trackProcess(admin);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Admin Console launched.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to start the server and Admin Console. Check local configuration and try again.");
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private void onOpenAdminConsole() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Checking server readiness...");

            CompletableFuture.runAsync(() -> {
                try {
                    if (!isServerHealthy()) {
                        Platform.runLater(() -> {
                            updateServerStatusDisplay(false, false);
                            infoStatusLabel.setText("Server is not running. Please use 'Start Server' or 'Start Server + Admin'.");
                            setButtonsDisabled(false);
                        });
                        return;
                    }

                    Platform.runLater(() -> {
                        updateServerStatusDisplay(true, false);
                        infoStatusLabel.setText("Launching Admin Console...");
                    });

                    Process admin = launchModule(LaunchTarget.ADMIN);
                    trackProcess(admin);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Admin Console launched.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to open the Admin Console. Verify that the server is running and try again.");
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private boolean waitForServerHealth() throws InterruptedException {
            for (int i = 0; i < 30; i++) {
                Thread.sleep(500);
                if (isServerHealthy()) {
                    return true;
                }
            }
            return false;
        }

        private void onStopExaSecure() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Stopping services...");

            CompletableFuture.runAsync(() -> {
                synchronized (launchedProcesses) {
                    for (Process p : launchedProcesses) {
                        stopProcess(p);
                    }
                    launchedProcesses.clear();
                }

                boolean stillHealthy = isServerHealthy();
                Platform.runLater(() -> {
                    updateServerStatusDisplay(stillHealthy, false);
                    infoStatusLabel.setText(stillHealthy ? "Launched processes stopped (external server still active)." : "All services stopped.");
                    setButtonsDisabled(false);
                });
            });
        }

        private void trackProcess(Process p) {
            if (p != null) {
                launchedProcesses.add(p);
            }
        }

        private void stopProcess(Process p) {
            if (p != null && p.isAlive()) {
                try {
                    p.descendants().forEach(ProcessHandle::destroy);
                    p.destroy();
                    if (!p.waitFor(3, TimeUnit.SECONDS)) {
                        p.descendants().forEach(ProcessHandle::destroyForcibly);
                        p.destroyForcibly();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        public static Path findRepoRoot() {
            return DevelopmentCommandFactory.findRepoRoot(Path.of(""));
        }

        static Process launchModule(LaunchTarget target) throws IOException {
            LaunchSpec spec = DevelopmentCommandFactory.create(target);
            ProcessBuilder pb = new ProcessBuilder(spec.command());
            pb.directory(spec.workingDirectory().toFile());
            pb.inheritIO();
            return pb.start();
        }

        public static boolean isServerHealthy() {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(800))
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(HEALTH_URL))
                        .timeout(Duration.ofMillis(800))
                        .GET()
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                return res.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
