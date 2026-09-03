package com.examsystem.client.admin.views;

import com.examsystem.contracts.AdminAttemptDetailDto;
import com.examsystem.contracts.admin.AdminAttemptDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class AttemptDetailsDialog extends Dialog<Void> {

    public AttemptDetailsDialog(AdminAttemptDto attempt, List<AdminAttemptDetailDto> details) {
        setTitle("Attempt Report Card - " + attempt.studentName());
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(900);
        pane.setPrefHeight(620);
        pane.getStyleClass().add("product-dialog");

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));

        // Header Summary Card
        VBox summaryCard = new VBox(10);
        summaryCard.setPadding(new Insets(12, 16, 12, 16));
        summaryCard.setAlignment(Pos.CENTER_LEFT);
        summaryCard.getStyleClass().addAll("theme-card", "details-panel");

        VBox stuInfo = new VBox(2);
        Label stuName = new Label(attempt.studentName());
        stuName.getStyleClass().add("section-title");
        Label stuSub = new Label((attempt.enrollmentNumber() != null ? "Enrollment: " + attempt.enrollmentNumber() : "Enrollment unavailable")
                + "  •  Started: " + (attempt.startTime() != null ? attempt.startTime() : "Time unavailable"));
        stuSub.getStyleClass().addAll("secondary-text", "small-text");
        stuInfo.getChildren().addAll(stuName, stuSub);

        HBox scoreBox = new HBox(8);
        scoreBox.setAlignment(Pos.CENTER);
        scoreBox.setPadding(new Insets(6, 12, 6, 12));

        int maximumScore = details == null ? 0 : details.stream().mapToInt(AdminAttemptDetailDto::marks).sum();
        String scoreText = attempt.score() == null ? "Pending" : maximumScore > 0
                ? attempt.score() + " / " + maximumScore
                : String.valueOf(attempt.score());
        Label scoreLabel = new Label("Score: " + scoreText);
        scoreLabel.getStyleClass().addAll("metric-value", "metric-value-success");

        Label statusBadge = new Label(attempt.status());
        statusBadge.getStyleClass().add(switch (attempt.status().toUpperCase()) {
            case "ACTIVE" -> "badge-active";
            case "SUBMITTED" -> "badge-submitted";
            case "EXPIRED" -> "badge-expired";
            default -> "badge-neutral";
        });

        Label violationsBadge = new Label("Violations: " + attempt.focusLostCount());
        violationsBadge.getStyleClass().add(attempt.focusLostCount() > 0 ? "badge-warning" : "badge-neutral");

        Label integrityBadge = new Label(attempt.flagged() ? "⚠️ FLAGGED" : "CLEAN");
        integrityBadge.setText(attempt.flagged() ? "FLAGGED" : "CLEAN");
        integrityBadge.getStyleClass().add(attempt.flagged() ? "badge-flagged" : "badge-clean");

        scoreBox.getChildren().addAll(scoreLabel, statusBadge, violationsBadge, integrityBadge);
        summaryCard.getChildren().addAll(stuInfo, scoreBox);

        setOnShowing(e -> {
            if (pane.getScene() != null) {
                com.examsystem.client.theme.ThemeManager.getInstance().applyTheme(pane.getScene());
            }
        });

        // Details Table
        TableView<AdminAttemptDetailDto> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No questions recorded for this examination attempt."));

        TableColumn<AdminAttemptDetailDto, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().questionNumber())));
        numCol.setMaxWidth(45);

        TableColumn<AdminAttemptDetailDto, String> qTextCol = new TableColumn<>("Question");
        qTextCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().questionText()));
        qTextCol.setPrefWidth(220);
        qTextCol.setCellFactory(col -> wrappingCell());

        TableColumn<AdminAttemptDetailDto, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().questionType()));
        typeCol.setMaxWidth(75);

        TableColumn<AdminAttemptDetailDto, String> studentAnsCol = new TableColumn<>("Student's Answer");
        studentAnsCol.setCellValueFactory(d -> {
            var nums = d.getValue().selectedOptionNumbers();
            var texts = d.getValue().selectedOptionTexts();
            if (nums == null || nums.isEmpty()) {
                return new SimpleStringProperty("(No answer)");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nums.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(nums.get(i));
                if (texts != null && i < texts.size()) {
                    sb.append(". ").append(texts.get(i));
                }
            }
            return new SimpleStringProperty(sb.toString());
        });
        studentAnsCol.setPrefWidth(180);
        studentAnsCol.setCellFactory(col -> wrappingCell());

        TableColumn<AdminAttemptDetailDto, String> correctAnsCol = new TableColumn<>("Correct Answer");
        correctAnsCol.setCellValueFactory(d -> {
            var nums = d.getValue().correctOptionNumbers();
            var texts = d.getValue().correctOptionTexts();
            if (nums == null || nums.isEmpty()) {
                return new SimpleStringProperty("-");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nums.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(nums.get(i));
                if (texts != null && i < texts.size()) {
                    sb.append(". ").append(texts.get(i));
                }
            }
            return new SimpleStringProperty(sb.toString());
        });
        correctAnsCol.setPrefWidth(180);
        correctAnsCol.setCellFactory(col -> wrappingCell());

        TableColumn<AdminAttemptDetailDto, String> resultCol = new TableColumn<>("Result");
        resultCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isCorrect() ? "✔ Correct" : "✘ Incorrect"));
        resultCol.setMaxWidth(90);

        TableColumn<AdminAttemptDetailDto, String> marksCol = new TableColumn<>("Marks");
        marksCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().marksAwarded() + " / " + d.getValue().marks()));
        marksCol.setMaxWidth(65);

        table.getColumns().addAll(numCol, qTextCol, typeCol, studentAnsCol, correctAnsCol, resultCol, marksCol);
        if (details != null) {
            table.getItems().addAll(details);
        }

        root.getChildren().addAll(summaryCard, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        pane.setContent(root);
    }

    private TableCell<AdminAttemptDetailDto, String> wrappingCell() {
        return new TableCell<>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.maxWidthProperty().bind(widthProperty().subtract(12));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    label.setText(item);
                    setGraphic(label);
                }
            }
        };
    }
}
