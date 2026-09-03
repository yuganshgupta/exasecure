package com.examsystem.client.admin.views;

import com.examsystem.contracts.AdminAttemptDetailDto;
import com.examsystem.contracts.admin.AdminAttemptDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;

public class AttemptDetailsDialog extends Dialog<Void> {

    public AttemptDetailsDialog(AdminAttemptDto attempt, List<AdminAttemptDetailDto> details) {
        setTitle("Attempt Report Card - #" + attempt.id());
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(850);
        pane.setPrefHeight(560);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));

        // Header Summary Card
        HBox summaryCard = new HBox(20);
        summaryCard.setPadding(new Insets(12, 16, 12, 16));
        summaryCard.setAlignment(Pos.CENTER_LEFT);
        summaryCard.getStyleClass().add("theme-card");

        VBox stuInfo = new VBox(2);
        Label stuName = new Label(attempt.studentName());
        stuName.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        String enrollText = attempt.enrollmentNumber() != null ? " • Enrollment: " + attempt.enrollmentNumber() : "";
        Label stuSub = new Label("Student ID: #" + attempt.studentId() + enrollText + " • Attempt #" + attempt.id());
        stuSub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        stuInfo.getChildren().addAll(stuName, stuSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox scoreBox = new HBox(8);
        scoreBox.setAlignment(Pos.CENTER);
        scoreBox.setPadding(new Insets(6, 12, 6, 12));

        Label scoreLabel = new Label("Score: " + (attempt.score() != null ? attempt.score() + " pts" : "Pending"));
        scoreLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        scoreLabel.setStyle("-fx-text-fill: #16a34a;");

        Label statusBadge = new Label(attempt.status());
        statusBadge.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

        Label violationsBadge = new Label("Violations: " + attempt.focusLostCount());
        violationsBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

        Label integrityBadge = new Label(attempt.flagged() ? "⚠️ FLAGGED" : "CLEAN");
        integrityBadge.setStyle(attempt.flagged()
                ? "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;"
                : "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 4px;");

        scoreBox.getChildren().addAll(scoreLabel, statusBadge, violationsBadge, integrityBadge);
        summaryCard.getChildren().addAll(stuInfo, spacer, scoreBox);

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
}
