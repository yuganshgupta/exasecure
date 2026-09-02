package com.examsystem.contracts.admin;

import java.util.List;

public record AdminExamDefinitionRequestDto(
        String title,
        int durationMinutes,
        List<AdminQuestionDefinitionDto> questions
) {}
