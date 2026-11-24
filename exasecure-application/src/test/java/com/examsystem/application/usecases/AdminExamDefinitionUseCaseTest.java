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
        AdminExamDefinitionCommand singleMultipleCorrect = new AdminExamDefinitionCommand(
                "Exam", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "SINGLE", List.of(
                new AdminOptionDefinitionCommand("A", true),
                new AdminOptionDefinitionCommand("B", true)))));
        AdminExamDefinitionCommand singleZeroCorrect = new AdminExamDefinitionCommand(
                "Exam", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "SINGLE", List.of(
                new AdminOptionDefinitionCommand("A", false),
                new AdminOptionDefinitionCommand("B", false)))));
        AdminExamDefinitionCommand multiZeroCorrect = new AdminExamDefinitionCommand(
                "Exam", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "MULTI", List.of(
                new AdminOptionDefinitionCommand("A", false),
                new AdminOptionDefinitionCommand("B", false)))));

        assertThatIllegalArgumentException().isThrownBy(() -> useCase.createDefinition(singleMultipleCorrect, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.createDefinition(singleZeroCorrect, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.createDefinition(multiZeroCorrect, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.reviseDefinition(1, singleMultipleCorrect, 9));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.reviseDefinition(1, singleZeroCorrect, 9));
        assertThat(port.calls).isZero();
    }

    @Test
    void acceptsMultiQuestionsWithOneOrMoreCorrectOptions() {
        AdminExamDefinitionCommand multiOneCorrect = new AdminExamDefinitionCommand(
                "Multi One", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "multi", List.of(
                new AdminOptionDefinitionCommand("A", true),
                new AdminOptionDefinitionCommand("B", false)))));
        AdminExamDefinitionCommand multiTwoCorrect = new AdminExamDefinitionCommand(
                "Multi Two", 30, List.of(new AdminQuestionDefinitionCommand(
                "Question", "MULTI", List.of(
                new AdminOptionDefinitionCommand("A", true),
                new AdminOptionDefinitionCommand("B", true),
                new AdminOptionDefinitionCommand("C", false)))));

        assertThat(useCase.createDefinition(multiOneCorrect, 9)).isEqualTo(new AdminExamMutationResult(51, 1));
        assertThat(port.definition.questions().getFirst().type()).isEqualTo("MULTI");
        assertThat(useCase.createDefinition(multiTwoCorrect, 9)).isEqualTo(new AdminExamMutationResult(51, 1));
        assertThat(port.calls).isEqualTo(2);
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

        @Override
        public boolean reactivateDefinition(int examId) {
            calls++;
            return true;
        }
    }
}
