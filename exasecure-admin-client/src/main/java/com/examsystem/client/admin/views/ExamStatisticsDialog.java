package com.examsystem.client.admin.views;

import com.examsystem.contracts.AdminExamStatisticsDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ExamStatisticsDialog extends Dialog<Void> {

    public ExamStatisticsDialog(AdminExamStatisticsDto stats) {
        setTitle("Exam Analytics - " + stats.examTitle());
        setHeaderText(null);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefWidth(720);
        pane.setPrefHeight(600);
        pane.getStyleClass().add("product-dialog");

        VBox root = new VBox(18);
        root.setPadding(new Insets(18));

        // Header Title
        VBox titleBox = new VBox(4);
        Label title = new Label(stats.examTitle());
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Performance metrics and score distribution across all submitted attempts.");
        subtitle.getStyleClass().addAll("secondary-text", "small-text");
        subtitle.setWrapText(true);
        titleBox.getChildren().addAll(title, subtitle);

        // Key Metrics Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(12);
        metricsGrid.setVgap(12);

        // Attempt Breakdown (Row 0)
        metricsGrid.add(createMetricCard("Total Attempts", String.valueOf(stats.totalAttempts()), "metric-value"), 0, 0);
        metricsGrid.add(createMetricCard("Submitted", String.valueOf(stats.submittedAttempts()), "metric-value-success"), 1, 0);
        metricsGrid.add(createMetricCard("Active", String.valueOf(stats.activeAttempts()), "metric-value-warning"), 0, 1);
        metricsGrid.add(createMetricCard("Expired", String.valueOf(stats.expiredAttempts()), "metric-value-danger"), 1, 1);

        // Score Aggregates (Row 1)
        String avgStr = stats.averageScore() != null ? String.valueOf(stats.averageScore()) : "N/A";
        String highStr = stats.highestScore() != null ? String.valueOf(stats.highestScore()) : "N/A";
        String lowStr = stats.lowestScore() != null ? String.valueOf(stats.lowestScore()) : "N/A";

        metricsGrid.add(createMetricCard("Average Score", avgStr, "metric-value"), 0, 2);
        metricsGrid.add(createMetricCard("Highest Score", highStr, "metric-value-success"), 1, 2);
        metricsGrid.add(createMetricCard("Lowest Score", lowStr, "metric-value-danger"), 0, 3);

        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50.0);
            metricsGrid.getColumnConstraints().add(col);
        }

        // Score Distribution Section
        VBox distBox = new VBox(10);
        distBox.setPadding(new Insets(14));
        distBox.getStyleClass().add("theme-card");

        Label distTitle = new Label("Score Distribution (Submitted Attempts)");
        distTitle.getStyleClass().add("section-title");
        distBox.getChildren().add(distTitle);

        if (stats.scoreDistribution() != null && !stats.scoreDistribution().isEmpty()) {
            int maxCount = stats.scoreDistribution().values().stream().mapToInt(Integer::intValue).max().orElse(1);
            if (maxCount == 0) maxCount = 1;

            for (var entry : stats.scoreDistribution().entrySet()) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                Label bucketLabel = new Label(entry.getKey());
                bucketLabel.setPrefWidth(70);
                bucketLabel.getStyleClass().add("theme-label-bold");

                ProgressBar bar = new ProgressBar((double) entry.getValue() / maxCount);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(18);
                HBox.setHgrow(bar, Priority.ALWAYS);

                Label countLabel = new Label(entry.getValue() + " student" + (entry.getValue() == 1 ? "" : "s"));
                countLabel.getStyleClass().addAll("secondary-text", "small-text");

                row.getChildren().addAll(bucketLabel, bar, countLabel);
                distBox.getChildren().add(row);
            }
        } else {
            distBox.getChildren().add(new Label("No submitted examination scores available yet."));
        }

        root.getChildren().addAll(titleBox, metricsGrid, distBox);
        pane.setContent(root);
    }

    private VBox createMetricCard(String label, String value, String valueStyleClass) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.getStyleClass().add("theme-card");

        Label valLabel = new Label(value);
        valLabel.getStyleClass().addAll("metric-value", valueStyleClass);

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().addAll("secondary-text", "small-text");

        card.getChildren().addAll(valLabel, nameLabel);
        return card;
    }
}
