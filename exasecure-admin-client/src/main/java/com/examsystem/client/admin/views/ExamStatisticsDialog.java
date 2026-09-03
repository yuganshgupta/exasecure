package com.examsystem.client.admin.views;

import com.examsystem.contracts.AdminExamStatisticsDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class ExamStatisticsDialog extends Dialog<Void> {

    public ExamStatisticsDialog(AdminExamStatisticsDto stats) {
        setTitle("Exam Analytics - " + stats.examTitle());
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(720);
        pane.setPrefHeight(530);

        VBox root = new VBox(18);
        root.setPadding(new Insets(18));

        // Header Title
        VBox titleBox = new VBox(4);
        Label title = new Label(stats.examTitle());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #0f172a;");
        Label subtitle = new Label("Performance metrics and score distribution across all submitted attempts.");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, subtitle);

        // Key Metrics Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(12);
        metricsGrid.setVgap(12);

        // Attempt Breakdown (Row 0)
        metricsGrid.add(createMetricCard("Total Attempts", String.valueOf(stats.totalAttempts()), "#2563eb"), 0, 0);
        metricsGrid.add(createMetricCard("Submitted / Scored", String.valueOf(stats.submittedAttempts()), "#0891b2"), 1, 0);
        metricsGrid.add(createMetricCard("Active In-Progress", String.valueOf(stats.activeAttempts()), "#d97706"), 2, 0);
        metricsGrid.add(createMetricCard("Expired / Unsubmitted", String.valueOf(stats.expiredAttempts()), "#64748b"), 3, 0);

        // Score Aggregates (Row 1)
        String avgStr = stats.averageScore() != null ? stats.averageScore() + " pts" : "N/A";
        String highStr = stats.highestScore() != null ? stats.highestScore() + " pts" : "N/A";
        String lowStr = stats.lowestScore() != null ? stats.lowestScore() + " pts" : "N/A";

        metricsGrid.add(createMetricCard("Average Score", avgStr, "#7c3aed"), 0, 1);
        metricsGrid.add(createMetricCard("Highest Score", highStr, "#059669"), 1, 1);
        metricsGrid.add(createMetricCard("Lowest Score", lowStr, "#dc2626"), 2, 1);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25.0);
            metricsGrid.getColumnConstraints().add(col);
        }

        // Score Distribution Section
        VBox distBox = new VBox(10);
        distBox.setPadding(new Insets(14));
        distBox.getStyleClass().add("theme-card");

        Label distTitle = new Label("Score Distribution (Submitted Attempts)");
        distTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        distTitle.setStyle("-fx-text-fill: #1e293b;");
        distBox.getChildren().add(distTitle);

        if (stats.scoreDistribution() != null && !stats.scoreDistribution().isEmpty()) {
            int maxCount = stats.scoreDistribution().values().stream().mapToInt(Integer::intValue).max().orElse(1);
            if (maxCount == 0) maxCount = 1;

            for (var entry : stats.scoreDistribution().entrySet()) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                Label bucketLabel = new Label(entry.getKey());
                bucketLabel.setPrefWidth(70);
                bucketLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

                ProgressBar bar = new ProgressBar((double) entry.getValue() / maxCount);
                bar.setPrefWidth(380);
                bar.setPrefHeight(18);
                bar.setStyle("-fx-accent: #3b82f6;");

                Label countLabel = new Label(entry.getValue() + " student" + (entry.getValue() == 1 ? "" : "s"));
                countLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

                row.getChildren().addAll(bucketLabel, bar, countLabel);
                distBox.getChildren().add(row);
            }
        } else {
            distBox.getChildren().add(new Label("No submitted examination scores available yet."));
        }

        root.getChildren().addAll(titleBox, metricsGrid, distBox);
        pane.setContent(root);
    }

    private VBox createMetricCard(String label, String value, String colorHex) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.getStyleClass().add("theme-card");

        Label valLabel = new Label(value);
        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        valLabel.setStyle("-fx-text-fill: " + colorHex + ";");

        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        card.getChildren().addAll(valLabel, nameLabel);
        return card;
    }
}
