package com.examsystem.application.commands;

import java.util.List;

public record AdminQuestionDefinitionCommand(
        String text,
        String type,
        List<AdminOptionDefinitionCommand> options
) {
    public AdminQuestionDefinitionCommand {
        options = options == null ? null : List.copyOf(options);
    }
}
