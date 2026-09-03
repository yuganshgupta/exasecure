package com.examsystem.launcher;

import com.examsystem.client.theme.ThemeManager;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
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
            titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
            titleLabel.getStyleClass().add("theme-label-bold");

            Label subtitleLabel = new Label("Operator & Administration Control Center");
            subtitleLabel.setFont(Font.font("Segoe UI", 12));
            subtitleLabel.getStyleClass().add("theme-label-muted");

            var themeSelector = ThemeManager.getInstance().createThemeSelector();

            HBox headerBox = new HBox(12);
            headerBox.setAlignment(Pos.CENTER_LEFT);
            VBox titlesBox = new VBox(2, titleLabel, subtitleLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerBox.getChildren().addAll(titlesBox, spacer, themeSelector);

            serverStatusLabel = new Label("Server: Checking...");
            serverStatusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            updateServerStatusDisplay(false, false);

            startServerBtn = new Button("Start Server");
            startServerBtn.setMaxWidth(Double.MAX_VALUE);
            startServerBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 9 16; -fx-background-radius: 6px; -fx-cursor: hand;");
            startServerBtn.setOnAction(e -> onStartServerOnly());

            startServerAndAdminBtn = new Button("Start Server + Admin");
            startServerAndAdminBtn.setMaxWidth(Double.MAX_VALUE);
            startServerAndAdminBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 16; -fx-background-radius: 6px; -fx-cursor: hand;");
            startServerAndAdminBtn.setOnAction(e -> onStartServerAndAdmin());

            openAdminBtn = new Button("Open Admin Console");
            openAdminBtn.setMaxWidth(Double.MAX_VALUE);
            openAdminBtn.setStyle("-fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 9 16; -fx-background-radius: 6px; -fx-cursor: hand;");
            openAdminBtn.setOnAction(e -> onOpenAdminConsole());

            stopBtn = new Button("Stop Server & Services");
            stopBtn.setMaxWidth(Double.MAX_VALUE);
            stopBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 14; -fx-background-radius: 6px; -fx-cursor: hand;");
            stopBtn.setOnAction(e -> onStopExaSecure());

            infoStatusLabel = new Label("Ready.");
            infoStatusLabel.setFont(Font.font("Segoe UI", 11));
            infoStatusLabel.getStyleClass().add("theme-label-muted");
            infoStatusLabel.setWrapText(true);
            infoStatusLabel.setAlignment(Pos.CENTER);

            VBox card = new VBox(12, headerBox, serverStatusLabel, startServerBtn, startServerAndAdminBtn, openAdminBtn, stopBtn, infoStatusLabel);
            card.getStyleClass().add("theme-card");
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(24));
            card.setStyle("-fx-background-radius: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 16, 0, 0, 4);");

            VBox root = new VBox(card);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));

            Scene scene = new Scene(root, 440, 380);
            ThemeManager.getInstance().applyTheme(scene);

            stage.setTitle("ExaSecure Control Center");
            stage.setScene(scene);
            stage.setResizable(false);

            stage.setOnCloseRequest(e -> onStopExaSecure());
            stage.show();

            // Background initial check of server health
            CompletableFuture.runAsync(() -> {
                boolean healthy = isServerHealthy();
                Platform.runLater(() -> updateServerStatusDisplay(healthy, false));
            });
        }

        private void updateServerStatusDisplay(boolean running, boolean starting) {
            if (starting) {
                serverStatusLabel.setText("Server: Starting...");
                serverStatusLabel.setTextFill(Color.web("#d97706"));
            } else if (running) {
                serverStatusLabel.setText("Server: Running (Port 8089)");
                serverStatusLabel.setTextFill(Color.web("#16a34a"));
            } else {
                serverStatusLabel.setText("Server: Stopped");
                serverStatusLabel.setTextFill(Color.web("#dc2626"));
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

                    Process server = launchModule("exasecure-server");
                    trackProcess(server);

                    boolean healthy = waitForServerHealth();
                    Platform.runLater(() -> {
                        updateServerStatusDisplay(healthy, false);
                        infoStatusLabel.setText(healthy ? "Server started successfully." : "Server did not become ready on port 8089.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Failed to start server: " + ex.getMessage());
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

                        Process server = launchModule("exasecure-server");
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

                    Process admin = launchModule("exasecure-admin-client");
                    trackProcess(admin);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Admin Console launched.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Failed: " + ex.getMessage());
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

                    Process admin = launchModule("exasecure-admin-client");
                    trackProcess(admin);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Admin Console launched.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to open Admin Console: " + ex.getMessage());
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
            Path dir = Path.of("").toAbsolutePath();
            if (Files.exists(dir.resolve("mvnw")) || Files.exists(dir.resolve("mvnw.cmd"))) {
                return dir;
            }
            if (Files.exists(dir.resolve("..").resolve("mvnw")) || Files.exists(dir.resolve("..").resolve("mvnw.cmd"))) {
                return dir.resolve("..").normalize().toAbsolutePath();
            }
            return dir;
        }

        public static Process launchModule(String moduleName) throws IOException {
            Path root = findRepoRoot();
            boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
            String mvnw = root.resolve(isWin ? "mvnw.cmd" : "mvnw").toString();
            ProcessBuilder pb = new ProcessBuilder(mvnw, "exec:java", "-pl", moduleName);
            pb.directory(root.toFile());
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
