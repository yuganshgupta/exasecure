package com.examsystem.application.commands;

import java.util.List;

public record AdminExamDefinitionCommand(
        String title,
        int durationMinutes,
        List<AdminQuestionDefinitionCommand> questions
) {
    public AdminExamDefinitionCommand {
        questions = questions == null ? null : List.copyOf(questions);
    }
}
