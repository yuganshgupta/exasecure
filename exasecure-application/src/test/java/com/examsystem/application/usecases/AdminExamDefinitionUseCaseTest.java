package com.examsystem.application.usecases;

import com.examsystem.application.commands.AdminExamDefinitionCommand;
import com.examsystem.application.commands.AdminOptionDefinitionCommand;
import com.examsystem.application.commands.AdminQuestionDefinitionCommand;
import com.examsystem.application.ports.AdminExamDefinitionMutationPort;
import com.examsystem.application.query.AdminExamMutationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AdminExamDefinitionUseCaseTest {
    private final RecordingPort port = new RecordingPort();
    private final AdminExamDefinitionUseCase useCase = new AdminExamDefinitionUseCase(port);

    @Test
    void validatesNormalizesAndCreatesWholeDefinition() {
        AdminExamMutationResult result = useCase.createDefinition(validDefinition(), 9);

        assertThat(result).isEqualTo(new AdminExamMutationResult(51, 1));
        assertThat(port.definition.title()).isEqualTo("Security Exam");
        assertThat(port.definition.questions().getFirst().type()).isEqualTo("SINGLE");
        assertThat(port.definition.questions().getFirst().options().getFirst().text()).isEqualTo("Yes");
        assertThat(port.createdBy).isEqualTo(9);
    }

    @Test
    void rejectsInvalidCorrectnessMetadataBeforePersistence() {
        AdminExamDefinitionCommand invalid = new AdminExamDefinitionCommand(
                "Exam", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "SINGLE", List.of(
                new AdminOptionDefinitionCommand("A", true),
                new AdminOptionDefinitionCommand("B", true)))));

        assertThatIllegalArgumentException().isThrownBy(() -> useCase.createDefinition(invalid, 9));
        assertThat(port.calls).isZero();
    }

    @Test
    void reviseAndDeactivateRequirePositiveIds() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> useCase.reviseDefinition(0, validDefinition(), 9));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.deactivateDefinition(0));
        assertThat(port.calls).isZero();
    }

    private static AdminExamDefinitionCommand validDefinition() {
        return new AdminExamDefinitionCommand(
                " Security Exam ", 45, List.of(new AdminQuestionDefinitionCommand(
                " Continue? ", "single", List.of(
                new AdminOptionDefinitionCommand(" Yes ", true),
                new AdminOptionDefinitionCommand("No", false)))));
    }

    private static final class RecordingPort implements AdminExamDefinitionMutationPort {
        int calls;
        int createdBy;
        AdminExamDefinitionCommand definition;

        @Override
        public AdminExamMutationResult createDefinition(AdminExamDefinitionCommand definition, int createdBy) {
            calls++;
            this.definition = definition;
            this.createdBy = createdBy;
            return new AdminExamMutationResult(51, 1);
        }

        @Override
        public Optional<AdminExamMutationResult> reviseDefinition(
                int supersededExamId, AdminExamDefinitionCommand definition, int createdBy) {
            calls++;
            return Optional.of(new AdminExamMutationResult(52, 2));
        }

        @Override
        public boolean deactivateDefinition(int examId) {
            calls++;
            return true;
        }
    }
}
