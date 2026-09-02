package com.examsystem.application.ports;

import com.examsystem.application.commands.AdminExamDefinitionCommand;
import com.examsystem.application.query.AdminExamMutationResult;

import java.util.Optional;

public interface AdminExamDefinitionMutationPort {
    AdminExamMutationResult createDefinition(AdminExamDefinitionCommand definition, int createdBy);

    Optional<AdminExamMutationResult> reviseDefinition(
            int supersededExamId, AdminExamDefinitionCommand definition, int createdBy);

    boolean deactivateDefinition(int examId);
}
