package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.student.StudentApp;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.StudentAttemptHistoryDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StudentHistoryView {
    private final StudentApp app;
    private final ApiClient apiClient;
    private final BorderPane view;
    private final TableView<StudentAttemptHistoryDto> table;
    private final Label statusLabel;
    private final Button refreshBtn;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public StudentHistoryView(StudentApp app, ApiClient apiClient) {
        this.app = app;
        this.apiClient = apiClient;
        this.view = new BorderPane();
        this.table = new TableView<>();
        this.statusLabel = new Label();
        this.refreshBtn = new Button("Refresh");
        buildUI();
        loadHistory();
    }

    public Parent getView() {
        return view;
    }

    private void buildUI() {
        view.getStyleClass().add("app-root");

        // Top Bar
        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(16, 24, 16, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("app-header");

        Label brand = new Label("ExaSecure");
        brand.getStyleClass().add("header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button backBtn = new Button("← Back to Dashboard");
        backBtn.setText("Back to Dashboard");
        backBtn.getStyleClass().add("btn-header");
        backBtn.setOnAction(e -> app.showDashboard());

        var themeSelector = com.examsystem.client.theme.ThemeManager.getInstance().createThemeSelector();
        topBar.getChildren().addAll(brand, spacer, themeSelector, backBtn);
        view.setTop(topBar);

        // Center Content
        VBox center = new VBox(16);
        center.setPadding(new Insets(24));
        center.setMaxWidth(960);
        center.setAlignment(Pos.TOP_LEFT);

        HBox sectionHeader = new HBox(16);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label sectionTitle = new Label("My Examination History");
        sectionTitle.getStyleClass().add("page-title");

        Label sectionSubtitle = new Label("Review your past exam attempts, authoritative completion statuses, and final scores.");
        sectionSubtitle.getStyleClass().add("secondary-text");
        sectionSubtitle.setWrapText(true);
        titleBox.getChildren().addAll(sectionTitle, sectionSubtitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> loadHistory());

        sectionHeader.getChildren().addAll(titleBox, headerSpacer, refreshBtn);

        setupTable();

        center.getChildren().addAll(sectionHeader, statusLabel, table);

        StackPane centerContainer = new StackPane(center);
        centerContainer.setAlignment(Pos.TOP_CENTER);
        view.setCenter(centerContainer);
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No previous exam attempts found."));
        table.setMinHeight(240);

        TableColumn<StudentAttemptHistoryDto, String> examCol = new TableColumn<>("Exam Title");
        examCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().examTitle()));

        TableColumn<StudentAttemptHistoryDto, String> startCol = new TableColumn<>("Start Time");
        startCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().startTime() != null ? data.getValue().startTime() : "-"));

        TableColumn<StudentAttemptHistoryDto, String> endCol = new TableColumn<>("End Time");
        endCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().endTime() != null ? data.getValue().endTime() : "-"));

        TableColumn<StudentAttemptHistoryDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> createBadgeCell());

        TableColumn<StudentAttemptHistoryDto, String> scoreCol = new TableColumn<>("Final Score");
        scoreCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().score() != null ? String.valueOf(data.getValue().score()) : "Pending"));
        scoreCol.setPrefWidth(100);

        TableColumn<StudentAttemptHistoryDto, String> integrityCol = new TableColumn<>("Integrity Status");
        integrityCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().flagged() ? "⚠️ FLAGGED FOR REVIEW" : "CLEAN"));
        integrityCol.setPrefWidth(170);
        integrityCol.setCellFactory(col -> createBadgeCell());

        table.getColumns().addAll(examCol, startCol, endCol, statusCol, scoreCol, integrityCol);
    }

    private void loadHistory() {
        table.getItems().clear();
        statusLabel.setText("Loading examination history...");
        refreshBtn.setDisable(true);

        AsyncRunner.runAsync(
                apiClient::getStudentHistory,
                (List<StudentAttemptHistoryDto> history) -> {
                    refreshBtn.setDisable(false);
                    statusLabel.setText("");
                    if (history != null) {
                        table.getItems().addAll(history);
                    }
                },
                (Throwable err) -> {
                    refreshBtn.setDisable(false);
                    statusLabel.setText("Unable to load examination history.");
                    UiFeedback.showError(app.getPrimaryStage(), "History Error",
                            "Unable to load examination history. Please verify that the ExaSecure server is running.");
                }
        );
    }

    private TableCell<StudentAttemptHistoryDto, String> createBadgeCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(item.contains("FLAGGED") ? "FLAGGED" : item);
                badge.getStyleClass().setAll(badgeClass(item));
                setGraphic(badge);
            }
        };
    }

    private String badgeClass(String value) {
        String normalized = value.toUpperCase();
        if (normalized.contains("FLAGGED")) return "badge-flagged";
        return switch (normalized) {
            case "ACTIVE" -> "badge-active";
            case "SUBMITTED" -> "badge-submitted";
            case "EXPIRED" -> "badge-expired";
            case "CLEAN" -> "badge-clean";
            default -> "badge-neutral";
        };
    }
}
