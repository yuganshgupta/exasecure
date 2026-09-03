package com.examsystem.application.usecases;

import com.examsystem.application.commands.AdminExamDefinitionCommand;
import com.examsystem.application.commands.AdminOptionDefinitionCommand;
import com.examsystem.application.commands.AdminQuestionDefinitionCommand;
import com.examsystem.application.ports.AdminExamDefinitionMutationPort;
import com.examsystem.application.query.AdminExamMutationResult;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class AdminExamDefinitionUseCase {
    private final AdminExamDefinitionMutationPort mutationPort;

    public AdminExamDefinitionUseCase(AdminExamDefinitionMutationPort mutationPort) {
        this.mutationPort = mutationPort;
    }

    public AdminExamMutationResult createDefinition(AdminExamDefinitionCommand command, int createdBy) {
        requirePositive(createdBy, "Creator ID");
        return mutationPort.createDefinition(validate(command), createdBy);
    }

    public Optional<AdminExamMutationResult> reviseDefinition(
            int supersededExamId, AdminExamDefinitionCommand command, int createdBy) {
        requirePositive(supersededExamId, "Exam ID");
        requirePositive(createdBy, "Creator ID");
        return mutationPort.reviseDefinition(supersededExamId, validate(command), createdBy);
    }

    public boolean deactivateDefinition(int examId) {
        requirePositive(examId, "Exam ID");
        return mutationPort.deactivateDefinition(examId);
    }

    public boolean reactivateDefinition(int examId) {
        requirePositive(examId, "Exam ID");
        return mutationPort.reactivateDefinition(examId);
    }

    private static AdminExamDefinitionCommand validate(AdminExamDefinitionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Exam definition is required");
        }
        String title = requireText(command.title(), "Exam title", 255);
        if (command.durationMinutes() < 1 || command.durationMinutes() > 1440) {
            throw new IllegalArgumentException("Duration must be between 1 and 1440 minutes");
        }
        if (command.questions() == null || command.questions().isEmpty()) {
            throw new IllegalArgumentException("At least one question is required");
        }
        if (command.questions().size() > 200) {
            throw new IllegalArgumentException("An exam may contain at most 200 questions");
        }
        List<AdminQuestionDefinitionCommand> questions = command.questions().stream()
                .map(AdminExamDefinitionUseCase::validateQuestion)
                .toList();
        return new AdminExamDefinitionCommand(title, command.durationMinutes(), questions);
    }

    private static AdminQuestionDefinitionCommand validateQuestion(AdminQuestionDefinitionCommand question) {
        if (question == null) {
            throw new IllegalArgumentException("Question is required");
        }
        String text = requireText(question.text(), "Question text", 10_000);
        String type = question.type() == null ? "" : question.type().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("SINGLE") && !type.equals("MULTI")) {
            throw new IllegalArgumentException("Question type must be SINGLE or MULTI");
        }
        if (question.options() == null || question.options().size() < 2 || question.options().size() > 10) {
            throw new IllegalArgumentException("Each question must have between 2 and 10 options");
        }
        List<AdminOptionDefinitionCommand> options = question.options().stream()
                .map(option -> {
                    if (option == null) {
                        throw new IllegalArgumentException("Option is required");
                    }
                    return new AdminOptionDefinitionCommand(
                            requireText(option.text(), "Option text", 500), option.correct());
                })
                .toList();
        long correctCount = options.stream().filter(AdminOptionDefinitionCommand::correct).count();
        if (correctCount == 0 || (type.equals("SINGLE") && correctCount != 1)) {
            throw new IllegalArgumentException(
                    type.equals("SINGLE")
                            ? "A SINGLE question must have exactly one correct option"
                            : "A MULTI question must have at least one correct option");
        }
        return new AdminQuestionDefinitionCommand(text, type, options);
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static void requirePositive(int id, String label) {
        if (id <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
