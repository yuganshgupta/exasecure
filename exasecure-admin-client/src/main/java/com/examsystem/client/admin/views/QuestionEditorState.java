package com.examsystem.client.admin.views;

import com.examsystem.contracts.admin.AdminOptionDefinitionDto;
import com.examsystem.contracts.admin.AdminQuestionDefinitionDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Authoritative editor state for a single question definition in {@link ExamDefinitionBuilderDialog}.
 * Enforces mutual exclusivity for SINGLE questions and independent selection for MULTI questions.
 */
final class QuestionEditorState {
    static final String TYPE_SINGLE = "SINGLE";
    static final String TYPE_MULTI = "MULTI";
    static final int MIN_OPTIONS = 2;

    static final class OptionState {
        private String text;
        private boolean correct;

        OptionState(String text, boolean correct) {
            this.text = text != null ? text : "";
            this.correct = correct;
        }

        String text() {
            return text;
        }

        void setText(String text) {
            this.text = text != null ? text : "";
        }

        boolean correct() {
            return correct;
        }

        void setCorrect(boolean correct) {
            this.correct = correct;
        }
    }

    record ValidationResult(AdminQuestionDefinitionDto dto, String errorMessage) {
        boolean isValid() {
            return dto != null;
        }
    }

    private String text;
    private String type;
    private final List<OptionState> options = new ArrayList<>();
    private String notice;

    private QuestionEditorState(String text, String type, List<OptionState> initialOptions, String notice) {
        this.text = text != null ? text : "";
        this.type = normalizeType(type);
        this.options.addAll(initialOptions);
        this.notice = notice;
    }

    static QuestionEditorState fromExisting(AdminQuestionDefinitionDto existing) {
        if (existing == null) {
            return new QuestionEditorState(
                    "",
                    TYPE_SINGLE,
                    List.of(new OptionState("Option A", true), new OptionState("Option B", false)),
                    null);
        }

        String type = normalizeType(existing.type());
        List<OptionState> loaded = new ArrayList<>();
        if (existing.options() != null && !existing.options().isEmpty()) {
            for (AdminOptionDefinitionDto opt : existing.options()) {
                loaded.add(new OptionState(opt != null ? opt.text() : "", opt != null && opt.correct()));
            }
        } else {
            loaded.add(new OptionState("Option A", true));
            loaded.add(new OptionState("Option B", false));
        }

        long correctCount = loaded.stream().filter(OptionState::correct).count();
        String notice = null;
        if (TYPE_SINGLE.equals(type)) {
            if (correctCount > 1) {
                // Do not silently choose one option for a malformed historical SINGLE definition.
                for (OptionState opt : loaded) {
                    opt.setCorrect(false);
                }
                notice = "Saved SINGLE question had " + correctCount
                        + " correct options. Select exactly one correct option before saving.";
            } else if (correctCount == 0) {
                notice = "Saved SINGLE question has no correct option marked. Select exactly one correct option before saving.";
            }
        } else if (correctCount == 0) {
            notice = "Saved MULTI question has no correct option marked. Check at least one correct option before saving.";
        }

        return new QuestionEditorState(existing.text(), type, loaded, notice);
    }

    String text() {
        return text;
    }

    void setText(String text) {
        this.text = text != null ? text : "";
    }

    String type() {
        return type;
    }

    boolean isSingle() {
        return TYPE_SINGLE.equals(type);
    }

    List<OptionState> options() {
        return List.copyOf(options);
    }

    String notice() {
        return notice;
    }

    OptionState addOption(String optionText) {
        OptionState row = new OptionState(optionText, false);
        options.add(row);
        return row;
    }

    boolean removeOption(OptionState row) {
        if (options.size() <= MIN_OPTIONS) {
            return false;
        }
        boolean wasCorrect = row.correct();
        if (!options.remove(row)) {
            return false;
        }
        if (wasCorrect && options.stream().noneMatch(OptionState::correct)) {
            notice = isSingle()
                    ? "The selected correct option was removed. Select a correct option before saving."
                    : "The last correct option was removed. Check at least one correct option before saving.";
        }
        return true;
    }

    void setOptionCorrect(OptionState target, boolean selected) {
        if (target == null || !options.contains(target)) {
            return;
        }
        if (isSingle()) {
            if (selected) {
                for (OptionState opt : options) {
                    opt.setCorrect(opt == target);
                }
                notice = null;
            } else {
                target.setCorrect(false);
            }
        } else {
            target.setCorrect(selected);
            if (options.stream().anyMatch(OptionState::correct)) {
                notice = null;
            }
        }
    }

    void changeType(String newType) {
        String normalized = normalizeType(newType);
        if (normalized.equals(this.type)) {
            return;
        }
        this.type = normalized;
        if (TYPE_MULTI.equals(normalized)) {
            this.notice = null;
            return;
        }

        // Switching MULTI -> SINGLE: keep deterministic single selection and inform administrator.
        int firstSelectedIndex = -1;
        int selectedCount = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).correct()) {
                selectedCount++;
                if (firstSelectedIndex == -1) {
                    firstSelectedIndex = i;
                }
            }
        }
        if (selectedCount > 1) {
            for (int i = 0; i < options.size(); i++) {
                options.get(i).setCorrect(i == firstSelectedIndex);
            }
            this.notice = "Switched from MULTI to SINGLE: retained Option "
                    + (firstSelectedIndex + 1)
                    + " as the single correct answer.";
        } else if (selectedCount == 0) {
            this.notice = "Switched to SINGLE: select exactly one correct option before saving.";
        } else {
            this.notice = null;
        }
    }

    ValidationResult validateAndBuild(String questionLabel) {
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            return new ValidationResult(null, questionLabel + " text cannot be blank.");
        }
        if (options.size() < MIN_OPTIONS) {
            return new ValidationResult(null, questionLabel + " must have at least " + MIN_OPTIONS + " options.");
        }

        List<AdminOptionDefinitionDto> dtos = new ArrayList<>();
        int correctCount = 0;
        for (OptionState opt : options) {
            String optText = opt.text() == null ? "" : opt.text().trim();
            if (optText.isEmpty()) {
                return new ValidationResult(null, "Option text cannot be blank in " + questionLabel);
            }
            if (opt.correct()) {
                correctCount++;
            }
            dtos.add(new AdminOptionDefinitionDto(optText, opt.correct()));
        }

        if (isSingle() && correctCount != 1) {
            return new ValidationResult(null,
                    questionLabel + " is a SINGLE question and must have exactly one correct option selected.");
        }
        if (!isSingle() && correctCount == 0) {
            return new ValidationResult(null,
                    questionLabel + " is a MULTI question and must have at least one correct option checked.");
        }

        return new ValidationResult(new AdminQuestionDefinitionDto(normalizedText, type, dtos), null);
    }

    private static String normalizeType(String raw) {
        if (raw != null && TYPE_MULTI.equalsIgnoreCase(raw.trim().toUpperCase(Locale.ROOT))) {
            return TYPE_MULTI;
        }
        return TYPE_SINGLE;
    }
}
