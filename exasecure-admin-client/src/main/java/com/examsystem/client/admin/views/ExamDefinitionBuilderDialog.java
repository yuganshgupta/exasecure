package com.examsystem.client.admin.views;

import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.admin.AdminExamDefinitionRequestDto;
import com.examsystem.contracts.admin.AdminExamDto;
import com.examsystem.contracts.admin.AdminOptionDefinitionDto;
import com.examsystem.contracts.admin.AdminQuestionDefinitionDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

public class ExamDefinitionBuilderDialog extends Dialog<AdminExamDefinitionRequestDto> {
    private final TextField titleField = new TextField();
    private final TextField durationField = new TextField();
    private final VBox questionsContainer = new VBox(16);
    private final List<QuestionCard> questionCards = new ArrayList<>();

    public ExamDefinitionBuilderDialog(String title, String header, AdminExamDto existingExam, List<AdminQuestionDefinitionDto> existingQuestions) {
        setTitle(title);
        setHeaderText(header);

        ButtonType saveBtnType = new ButtonType("Save Exam Definition", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox content = new VBox(16);
        content.setPadding(new Insets(16));
        content.setPrefWidth(680);
        content.setPrefHeight(560);

        // Metadata grid
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(12);
        metaGrid.setVgap(10);

        if (existingExam != null) {
            titleField.setText(existingExam.title());
            durationField.setText(String.valueOf(existingExam.durationMinutes()));
        } else {
            durationField.setText("60");
        }

        metaGrid.add(new Label("Exam Title:"), 0, 0);
        titleField.setPromptText("Enter examination title");
        titleField.setPrefWidth(380);
        metaGrid.add(titleField, 1, 0);

        metaGrid.add(new Label("Duration (Minutes):"), 0, 1);
        durationField.setPromptText("60");
        durationField.setPrefWidth(120);
        metaGrid.add(durationField, 1, 1);

        // Questions section header
        HBox questionsHeader = new HBox(12);
        questionsHeader.setAlignment(Pos.CENTER_LEFT);
        Label qLabel = new Label("Questions Bank");
        qLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

        Region qSpacer = new Region();
        HBox.setHgrow(qSpacer, Priority.ALWAYS);

        Button addQBtn = new Button("+ Add Question");
        addQBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4px; -fx-cursor: hand;");
        addQBtn.setOnAction(e -> addQuestionCard(null));

        questionsHeader.getChildren().addAll(qLabel, qSpacer, addQBtn);

        ScrollPane scrollPane = new ScrollPane(questionsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f8fafc; -fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 6px;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        content.getChildren().addAll(metaGrid, new Separator(), questionsHeader, scrollPane);
        getDialogPane().setContent(content);

        // Populate existing questions if present
        if (existingQuestions != null && !existingQuestions.isEmpty()) {
            for (AdminQuestionDefinitionDto q : existingQuestions) {
                addQuestionCard(q);
            }
        } else {
            addQuestionCard(null); // At least one empty question
        }

        setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                return validateAndBuild();
            }
            return null;
        });
    }

    private void addQuestionCard(AdminQuestionDefinitionDto existing) {
        QuestionCard card = new QuestionCard(this, questionCards.size() + 1, existing);
        questionCards.add(card);
        questionsContainer.getChildren().add(card);
        refreshQuestionNumbers();
    }

    public void removeQuestionCard(QuestionCard card) {
        if (questionCards.size() <= 1) {
            UiFeedback.showError("Notice", "An examination must have at least one question.");
            return;
        }
        questionCards.remove(card);
        questionsContainer.getChildren().remove(card);
        refreshQuestionNumbers();
    }

    private void refreshQuestionNumbers() {
        for (int i = 0; i < questionCards.size(); i++) {
            questionCards.get(i).updateNumber(i + 1);
        }
    }

    private AdminExamDefinitionRequestDto validateAndBuild() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            UiFeedback.showError("Validation Error", "Exam title is required.");
            return null;
        }
        int duration;
        try {
            duration = Integer.parseInt(durationField.getText().trim());
            if (duration <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            UiFeedback.showError("Validation Error", "Duration must be a positive integer.");
            return null;
        }

        List<AdminQuestionDefinitionDto> questionDtos = new ArrayList<>();
        for (QuestionCard card : questionCards) {
            AdminQuestionDefinitionDto qDto = card.buildDto();
            if (qDto == null) {
                return null; // Card already showed error
            }
            questionDtos.add(qDto);
        }

        if (questionDtos.isEmpty()) {
            UiFeedback.showError("Validation Error", "At least one question is required.");
            return null;
        }

        return new AdminExamDefinitionRequestDto(title, duration, questionDtos);
    }

    // ==========================================
    // Inner QuestionCard UI Component
    // ==========================================

    public static class QuestionCard extends VBox {
        private final ExamDefinitionBuilderDialog parent;
        private final Label numberLabel;
        private final TextField textField = new TextField();
        private final ComboBox<String> typeCombo = new ComboBox<>();
        private final VBox optionsContainer = new VBox(8);
        private final List<OptionRow> optionRows = new ArrayList<>();

        public QuestionCard(ExamDefinitionBuilderDialog parent, int index, AdminQuestionDefinitionDto existing) {
            this.parent = parent;
            setSpacing(10);
            setPadding(new Insets(12));
            getStyleClass().add("theme-card");

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            numberLabel = new Label("Question " + index);
            numberLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

            typeCombo.getItems().addAll("SINGLE", "MULTI");
            typeCombo.setValue(existing != null && "MULTI".equalsIgnoreCase(existing.type()) ? "MULTI" : "SINGLE");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button removeQBtn = new Button("✕ Remove");
            removeQBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-cursor: hand; -fx-font-size: 11px;");
            removeQBtn.setOnAction(e -> parent.removeQuestionCard(this));

            header.getChildren().addAll(numberLabel, new Label("Type:"), typeCombo, spacer, removeQBtn);

            textField.setPromptText("Enter question prompt / text");
            if (existing != null) {
                textField.setText(existing.text());
            }

            HBox optionsHeader = new HBox(8);
            optionsHeader.setAlignment(Pos.CENTER_LEFT);
            Label optLabel = new Label("Options (Check box to mark correct):");
            optLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

            Region optSpacer = new Region();
            HBox.setHgrow(optSpacer, Priority.ALWAYS);

            Button addOptBtn = new Button("+ Add Option");
            addOptBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-font-size: 11px; -fx-cursor: hand;");
            addOptBtn.setOnAction(e -> addOptionRow(null));

            optionsHeader.getChildren().addAll(optLabel, optSpacer, addOptBtn);

            getChildren().addAll(header, textField, optionsHeader, optionsContainer);

            if (existing != null && existing.options() != null && !existing.options().isEmpty()) {
                for (AdminOptionDefinitionDto opt : existing.options()) {
                    addOptionRow(opt);
                }
            } else {
                addOptionRow(new AdminOptionDefinitionDto("Option A", true));
                addOptionRow(new AdminOptionDefinitionDto("Option B", false));
            }
        }

        public void updateNumber(int index) {
            numberLabel.setText("Question " + index);
        }

        private void addOptionRow(AdminOptionDefinitionDto existing) {
            OptionRow row = new OptionRow(this, existing);
            optionRows.add(row);
            optionsContainer.getChildren().add(row);
        }

        public void removeOptionRow(OptionRow row) {
            if (optionRows.size() <= 2) {
                UiFeedback.showError("Notice", "Each question must have at least 2 options.");
                return;
            }
            optionRows.remove(row);
            optionsContainer.getChildren().remove(row);
        }

        public AdminQuestionDefinitionDto buildDto() {
            String text = textField.getText().trim();
            if (text.isEmpty()) {
                UiFeedback.showError("Validation Error", numberLabel.getText() + " text cannot be blank.");
                return null;
            }

            if (optionRows.size() < 2) {
                UiFeedback.showError("Validation Error", numberLabel.getText() + " must have at least 2 options.");
                return null;
            }

            List<AdminOptionDefinitionDto> options = new ArrayList<>();
            int correctCount = 0;
            for (OptionRow row : optionRows) {
                AdminOptionDefinitionDto opt = row.buildDto();
                if (opt == null) return null;
                options.add(opt);
                if (opt.correct()) correctCount++;
            }

            if (correctCount == 0) {
                UiFeedback.showError("Validation Error", numberLabel.getText() + " must have at least one correct option checked.");
                return null;
            }

            return new AdminQuestionDefinitionDto(text, typeCombo.getValue(), options);
        }
    }

    public static class OptionRow extends HBox {
        private final QuestionCard card;
        private final CheckBox correctBox = new CheckBox("Correct");
        private final TextField textField = new TextField();

        public OptionRow(QuestionCard card, AdminOptionDefinitionDto existing) {
            this.card = card;
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);

            textField.setPromptText("Enter option text");
            textField.setPrefWidth(360);
            if (existing != null) {
                textField.setText(existing.text());
                correctBox.setSelected(existing.correct());
            }

            Button removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-cursor: hand;");
            removeBtn.setOnAction(e -> card.removeOptionRow(this));

            getChildren().addAll(correctBox, textField, removeBtn);
        }

        public AdminOptionDefinitionDto buildDto() {
            String text = textField.getText().trim();
            if (text.isEmpty()) {
                UiFeedback.showError("Validation Error", "Option text cannot be blank in " + card.numberLabel.getText());
                return null;
            }
            return new AdminOptionDefinitionDto(text, correctBox.isSelected());
        }
    }
}
