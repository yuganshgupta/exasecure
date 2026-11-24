package com.examsystem.client.admin.views;

import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.admin.AdminExamDefinitionRequestDto;
import com.examsystem.contracts.admin.AdminExamDto;
import com.examsystem.contracts.admin.AdminQuestionDefinitionDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class ExamDefinitionBuilderDialog extends Dialog<AdminExamDefinitionRequestDto> {
    private final TextField titleField = new TextField();
    private final TextField durationField = new TextField();
    private final VBox questionsContainer = new VBox(16);
    private final List<QuestionCard> questionCards = new ArrayList<>();

    private AdminExamDefinitionRequestDto validatedResult;

    public ExamDefinitionBuilderDialog(String title, String header, AdminExamDto existingExam, List<AdminQuestionDefinitionDto> existingQuestions) {
        setTitle(title);
        setHeaderText(header);

        ButtonType saveBtnType = new ButtonType("Save Exam Definition", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);
        getDialogPane().getStyleClass().add("product-dialog");

        VBox content = new VBox(16);
        content.setPadding(new Insets(16));
        content.setPrefWidth(720);
        content.setPrefHeight(620);

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
        titleField.setMaxWidth(Double.MAX_VALUE);
        metaGrid.add(titleField, 1, 0);

        metaGrid.add(new Label("Duration (Minutes):"), 0, 1);
        durationField.setPromptText("60");
        durationField.setPrefWidth(120);
        metaGrid.add(durationField, 1, 1);
        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        metaGrid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        // Questions section header
        HBox questionsHeader = new HBox(12);
        questionsHeader.setAlignment(Pos.CENTER_LEFT);
        Label qLabel = new Label("Questions Bank");
        qLabel.getStyleClass().add("section-title");

        Region qSpacer = new Region();
        HBox.setHgrow(qSpacer, Priority.ALWAYS);

        Button addQBtn = new Button("+ Add Question");
        addQBtn.getStyleClass().add("btn-primary");
        addQBtn.setOnAction(e -> addQuestionCard(null));

        questionsHeader.getChildren().addAll(qLabel, qSpacer, addQBtn);

        ScrollPane scrollPane = new ScrollPane(questionsContainer);
        scrollPane.setFitToWidth(true);
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

        Button saveButton = (Button) getDialogPane().lookupButton(saveBtnType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            validatedResult = validateAndBuild();
            if (validatedResult == null) {
                event.consume();
            }
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                return validatedResult != null ? validatedResult : validateAndBuild();
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
        private final QuestionEditorState state;
        private final ToggleGroup singleToggleGroup = new ToggleGroup();
        private final Label numberLabel;
        private final TextField textField = new TextField();
        private final ComboBox<String> typeCombo = new ComboBox<>();
        private final Label optLabel = new Label();
        private final Label noticeLabel = new Label();
        private final VBox optionsContainer = new VBox(8);
        private final List<OptionRow> optionRows = new ArrayList<>();
        private boolean syncingControls;

        public QuestionCard(ExamDefinitionBuilderDialog parent, int index, AdminQuestionDefinitionDto existing) {
            this.state = QuestionEditorState.fromExisting(existing);

            setSpacing(10);
            setPadding(new Insets(12));
            getStyleClass().add("theme-card");

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            numberLabel = new Label("Question " + index);
            numberLabel.getStyleClass().add("question-number-small");

            typeCombo.getItems().addAll(QuestionEditorState.TYPE_SINGLE, QuestionEditorState.TYPE_MULTI);
            typeCombo.setValue(state.type());
            typeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (syncingControls || newVal == null) {
                    return;
                }
                state.changeType(newVal);
                syncSelectionControls();
                refreshNotice();
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button removeQBtn = new Button("✕ Remove");
            removeQBtn.getStyleClass().add("btn-danger");
            removeQBtn.setOnAction(e -> parent.removeQuestionCard(this));

            header.getChildren().addAll(numberLabel, new Label("Type:"), typeCombo, spacer, removeQBtn);

            textField.setPromptText("Enter question prompt / text");
            textField.setText(state.text());
            textField.textProperty().addListener((obs, oldVal, newVal) -> state.setText(newVal));

            HBox optionsHeader = new HBox(8);
            optionsHeader.setAlignment(Pos.CENTER_LEFT);
            optLabel.getStyleClass().addAll("secondary-text", "small-text");

            Region optSpacer = new Region();
            HBox.setHgrow(optSpacer, Priority.ALWAYS);

            Button addOptBtn = new Button("+ Add Option");
            addOptBtn.getStyleClass().add("btn-secondary");
            addOptBtn.setOnAction(e -> addNewOptionRow());

            optionsHeader.getChildren().addAll(optLabel, optSpacer, addOptBtn);

            noticeLabel.getStyleClass().addAll("status-warning", "small-text");
            noticeLabel.setWrapText(true);
            noticeLabel.managedProperty().bind(noticeLabel.visibleProperty());

            getChildren().addAll(header, textField, optionsHeader, noticeLabel, optionsContainer);

            for (QuestionEditorState.OptionState optState : state.options()) {
                appendOptionRow(optState);
            }
            syncSelectionControls();
            refreshNotice();
        }

        public void updateNumber(int index) {
            numberLabel.setText("Question " + index);
        }

        private void addNewOptionRow() {
            QuestionEditorState.OptionState optState = state.addOption("");
            appendOptionRow(optState);
            syncSelectionControls();
        }

        private void appendOptionRow(QuestionEditorState.OptionState optState) {
            OptionRow row = new OptionRow(this, optState);
            optionRows.add(row);
            optionsContainer.getChildren().add(row);
        }

        public void removeOptionRow(OptionRow row) {
            if (!state.removeOption(row.optionState)) {
                UiFeedback.showError("Notice", "Each question must have at least 2 options.");
                return;
            }
            row.detachFromToggleGroup();
            optionRows.remove(row);
            optionsContainer.getChildren().remove(row);
            refreshNotice();
        }

        private void onOptionSelectionChanged(OptionRow row, boolean selected) {
            state.setOptionCorrect(row.optionState, selected);
            if (state.isSingle() && selected) {
                syncingControls = true;
                try {
                    for (OptionRow r : optionRows) {
                        r.reflectState(true);
                    }
                } finally {
                    syncingControls = false;
                }
            }
            refreshNotice();
        }

        private void syncSelectionControls() {
            syncingControls = true;
            try {
                optLabel.setText(state.isSingle()
                        ? "Options (Select exactly one correct answer):"
                        : "Options (Check one or more correct answers):");
                for (OptionRow row : optionRows) {
                    row.configureControl(state.isSingle(), singleToggleGroup);
                }
            } finally {
                syncingControls = false;
            }
        }

        private void refreshNotice() {
            String notice = state.notice();
            boolean hasNotice = notice != null && !notice.isBlank();
            noticeLabel.setText(hasNotice ? notice : "");
            noticeLabel.setVisible(hasNotice);
        }

        public AdminQuestionDefinitionDto buildDto() {
            state.setText(textField.getText());
            for (OptionRow row : optionRows) {
                row.flushTextToState();
            }
            QuestionEditorState.ValidationResult result = state.validateAndBuild(numberLabel.getText());
            if (!result.isValid()) {
                UiFeedback.showError("Validation Error", result.errorMessage());
                return null;
            }
            return result.dto();
        }
    }

    public static class OptionRow extends HBox {
        private final QuestionCard card;
        private final QuestionEditorState.OptionState optionState;
        private final StackPane selectorContainer = new StackPane();
        private final TextField textField = new TextField();
        private RadioButton radioButton;
        private CheckBox checkBox;

        public OptionRow(QuestionCard card, QuestionEditorState.OptionState optionState) {
            this.card = card;
            this.optionState = optionState;
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);

            selectorContainer.setAlignment(Pos.CENTER_LEFT);

            textField.setPromptText("Enter option text");
            textField.setMaxWidth(Double.MAX_VALUE);
            textField.setText(optionState.text());
            textField.textProperty().addListener((obs, oldVal, newVal) -> optionState.setText(newVal));
            HBox.setHgrow(textField, Priority.ALWAYS);

            Button removeBtn = new Button("✕");
            removeBtn.getStyleClass().add("btn-secondary");
            removeBtn.setOnAction(e -> card.removeOptionRow(this));

            getChildren().addAll(selectorContainer, textField, removeBtn);
        }

        private void configureControl(boolean singleMode, ToggleGroup toggleGroup) {
            if (singleMode) {
                if (radioButton == null) {
                    radioButton = new RadioButton("Correct");
                    radioButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        if (!card.syncingControls && Boolean.TRUE.equals(newVal)) {
                            card.onOptionSelectionChanged(this, true);
                        }
                    });
                }
                if (radioButton.getToggleGroup() != toggleGroup) {
                    radioButton.setToggleGroup(toggleGroup);
                }
                radioButton.setSelected(optionState.correct());
                selectorContainer.getChildren().setAll(radioButton);
            } else {
                detachFromToggleGroup();
                if (checkBox == null) {
                    checkBox = new CheckBox("Correct");
                    checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                        if (!card.syncingControls) {
                            card.onOptionSelectionChanged(this, Boolean.TRUE.equals(newVal));
                        }
                    });
                }
                checkBox.setSelected(optionState.correct());
                selectorContainer.getChildren().setAll(checkBox);
            }
        }

        private void reflectState(boolean singleMode) {
            if (singleMode && radioButton != null) {
                radioButton.setSelected(optionState.correct());
            } else if (!singleMode && checkBox != null) {
                checkBox.setSelected(optionState.correct());
            }
        }

        private void detachFromToggleGroup() {
            if (radioButton != null) {
                radioButton.setToggleGroup(null);
            }
        }

        private void flushTextToState() {
            optionState.setText(textField.getText());
        }
    }
}
