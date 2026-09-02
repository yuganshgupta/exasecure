package com.examsystem.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
        private Button startEverythingBtn;
        private Button startStudentBtn;
        private Button startAdminBtn;
        private Button stopBtn;

        @Override
        public void start(Stage stage) {
            Label titleLabel = new Label("ExaSecure V2");
            titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
            titleLabel.setTextFill(Color.web("#0f172a"));

            Label subtitleLabel = new Label("Development & Presentation Launcher");
            subtitleLabel.setFont(Font.font("Segoe UI", 12));
            subtitleLabel.setTextFill(Color.web("#64748b"));

            serverStatusLabel = new Label("Server: Checking...");
            serverStatusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            updateServerStatusDisplay(false, false);

            startEverythingBtn = new Button("Start Everything");
            startEverythingBtn.setMaxWidth(Double.MAX_VALUE);
            startEverythingBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 16; -fx-background-radius: 6px; -fx-cursor: hand;");
            startEverythingBtn.setOnAction(e -> onStartEverything());

            startStudentBtn = new Button("Start Student");
            startStudentBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 14; -fx-background-radius: 6px; -fx-cursor: hand;");
            startStudentBtn.setOnAction(e -> onStartStudent());

            startAdminBtn = new Button("Start Admin");
            startAdminBtn.setStyle("-fx-background-color: #4f46e5; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 14; -fx-background-radius: 6px; -fx-cursor: hand;");
            startAdminBtn.setOnAction(e -> onStartAdmin());

            HBox clientButtons = new HBox(10, startStudentBtn, startAdminBtn);
            clientButtons.setAlignment(Pos.CENTER);
            startStudentBtn.setMaxWidth(Double.MAX_VALUE);
            startAdminBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(startStudentBtn, Priority.ALWAYS);
            HBox.setHgrow(startAdminBtn, Priority.ALWAYS);

            stopBtn = new Button("Stop ExaSecure");
            stopBtn.setMaxWidth(Double.MAX_VALUE);
            stopBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 14; -fx-background-radius: 6px; -fx-cursor: hand;");
            stopBtn.setOnAction(e -> onStopExaSecure());

            infoStatusLabel = new Label("Ready.");
            infoStatusLabel.setFont(Font.font("Segoe UI", 11));
            infoStatusLabel.setTextFill(Color.web("#64748b"));
            infoStatusLabel.setWrapText(true);
            infoStatusLabel.setAlignment(Pos.CENTER);

            VBox root = new VBox(14, titleLabel, subtitleLabel, serverStatusLabel, startEverythingBtn, clientButtons, stopBtn, infoStatusLabel);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(24, 28, 20, 28));
            root.setStyle("-fx-background-color: #f8fafc;");

            Scene scene = new Scene(root, 360, 320);
            stage.setTitle("ExaSecure Launcher");
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
                serverStatusLabel.setText("Server: Running");
                serverStatusLabel.setTextFill(Color.web("#16a34a"));
            } else {
                serverStatusLabel.setText("Server: Stopped");
                serverStatusLabel.setTextFill(Color.web("#dc2626"));
            }
        }

        private void setButtonsDisabled(boolean disabled) {
            startEverythingBtn.setDisable(disabled);
            startStudentBtn.setDisable(disabled);
            startAdminBtn.setDisable(disabled);
            stopBtn.setDisable(disabled);
        }

        private void onStartEverything() {
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

                        boolean healthy = false;
                        for (int i = 0; i < 30; i++) {
                            Thread.sleep(500);
                            if (isServerHealthy()) {
                                healthy = true;
                                break;
                            }
                        }

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
                        infoStatusLabel.setText("Server ready. Launching Student and Admin clients...");
                    });

                    Process student = launchModule("exasecure-student-client");
                    trackProcess(student);

                    Process admin = launchModule("exasecure-admin-client");
                    trackProcess(admin);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("ExaSecure started successfully.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    System.err.println("Start Everything failed: " + ex.getMessage());
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to start ExaSecure: " + ex.getMessage());
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private void onStartStudent() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Checking server readiness...");

            CompletableFuture.runAsync(() -> {
                try {
                    if (!isServerHealthy()) {
                        Platform.runLater(() -> {
                            updateServerStatusDisplay(false, false);
                            infoStatusLabel.setText("Server is not running. Please start server or use 'Start Everything'.");
                            setButtonsDisabled(false);
                        });
                        return;
                    }

                    Platform.runLater(() -> {
                        updateServerStatusDisplay(true, false);
                        infoStatusLabel.setText("Launching Student Client...");
                    });

                    Process student = launchModule("exasecure-student-client");
                    trackProcess(student);

                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Student Client launched.");
                        setButtonsDisabled(false);
                    });
                } catch (Exception ex) {
                    System.err.println("Student launch failed: " + ex.getMessage());
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to start Student Client.");
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private void onStartAdmin() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Checking server readiness...");

            CompletableFuture.runAsync(() -> {
                try {
                    if (!isServerHealthy()) {
                        Platform.runLater(() -> {
                            updateServerStatusDisplay(false, false);
                            infoStatusLabel.setText("Server is not running. Please start server or use 'Start Everything'.");
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
                    System.err.println("Admin launch failed: " + ex.getMessage());
                    Platform.runLater(() -> {
                        infoStatusLabel.setText("Unable to start Admin Console.");
                        setButtonsDisabled(false);
                    });
                }
            });
        }

        private void onStopExaSecure() {
            setButtonsDisabled(true);
            infoStatusLabel.setText("Stopping ExaSecure processes...");

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
                    infoStatusLabel.setText(stillHealthy ? "Launcher processes stopped (external server still active)." : "Server: Stopped");
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

