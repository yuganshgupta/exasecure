package com.examsystem.contracts.admin;

import java.util.List;

public record AdminQuestionDefinitionDto(
        String text,
        String type,
        List<AdminOptionDefinitionDto> options
) {}
