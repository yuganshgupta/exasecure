package com.examsystem.client.admin.views;

import com.examsystem.contracts.admin.AdminOptionDefinitionDto;
import com.examsystem.contracts.admin.AdminQuestionDefinitionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionEditorStateTest {

    @Test
    void singleQuestionEnforcesMutuallyExclusiveSelection() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Which protocol operates at the transport layer?");
        var optA = state.options().get(0);
        var optB = state.options().get(1);
        var optC = state.addOption("ICMP");

        assertThat(optA.correct()).isTrue();
        assertThat(optB.correct()).isFalse();
        assertThat(optC.correct()).isFalse();

        state.setOptionCorrect(optB, true);

        assertThat(optA.correct()).isFalse();
        assertThat(optB.correct()).isTrue();
        assertThat(optC.correct()).isFalse();

        var result = state.validateAndBuild("Question 1");
        assertThat(result.isValid()).isTrue();
        assertThat(result.dto().type()).isEqualTo("SINGLE");
        assertThat(result.dto().options()).extracting(AdminOptionDefinitionDto::correct)
                .containsExactly(false, true, false);
    }

    @Test
    void multipleSingleQuestionsMaintainIndependentSelections() {
        QuestionEditorState q1 = QuestionEditorState.fromExisting(null);
        QuestionEditorState q2 = QuestionEditorState.fromExisting(null);
        q1.setText("First question");
        q2.setText("Second question");

        q1.setOptionCorrect(q1.options().get(0), true);
        q2.setOptionCorrect(q2.options().get(1), true);

        assertThat(q1.options()).extracting(QuestionEditorState.OptionState::correct).containsExactly(true, false);
        assertThat(q2.options()).extracting(QuestionEditorState.OptionState::correct).containsExactly(false, true);
    }

    @Test
    void multiQuestionSupportsMultipleIndependentCorrectOptions() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Select all stateless protocols");
        state.changeType("MULTI");
        var optC = state.addOption("HTTP");

        state.setOptionCorrect(state.options().get(0), true);
        state.setOptionCorrect(optC, true);

        var result = state.validateAndBuild("Question 1");
        assertThat(result.isValid()).isTrue();
        assertThat(result.dto().type()).isEqualTo("MULTI");
        assertThat(result.dto().options()).extracting(AdminOptionDefinitionDto::correct)
                .containsExactly(true, false, true);
    }

    @Test
    void switchingSingleToMultiPreservesSelectedOptionAndAllowsAddingMore() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Select network layer protocols");
        state.setOptionCorrect(state.options().get(1), true);

        state.changeType("MULTI");
        assertThat(state.options().get(1).correct()).isTrue();
        assertThat(state.options().get(0).correct()).isFalse();

        state.setOptionCorrect(state.options().get(0), true);
        assertThat(state.options()).extracting(QuestionEditorState.OptionState::correct).containsExactly(true, true);
    }

    @Test
    void switchingMultiToSingleWithMultipleSelectedRetainsFirstAndInformsAdmin() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Routing protocol");
        state.changeType("MULTI");
        var optC = state.addOption("OSPF");
        state.setOptionCorrect(state.options().get(0), false);
        state.setOptionCorrect(state.options().get(1), true);
        state.setOptionCorrect(optC, true);

        state.changeType("SINGLE");

        assertThat(state.isSingle()).isTrue();
        assertThat(state.options()).extracting(QuestionEditorState.OptionState::correct)
                .containsExactly(false, true, false);
        assertThat(state.notice()).contains("Option 2");
        assertThat(state.validateAndBuild("Question 1").isValid()).isTrue();
    }

    @Test
    void switchingMultiToSingleWithZeroSelectedBlocksSaveUntilSelected() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Routing protocol");
        state.changeType("MULTI");
        state.setOptionCorrect(state.options().get(0), false);

        state.changeType("SINGLE");

        assertThat(state.notice()).isNotBlank();
        var invalid = state.validateAndBuild("Question 1");
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.errorMessage()).contains("exactly one correct option");

        state.setOptionCorrect(state.options().get(1), true);
        assertThat(state.notice()).isNull();
        assertThat(state.validateAndBuild("Question 1").isValid()).isTrue();
    }

    @Test
    void removingSelectedCorrectOptionPreventsSilentInvalidSave() {
        QuestionEditorState state = QuestionEditorState.fromExisting(null);
        state.setText("Choose valid port");
        state.addOption("443");
        var optA = state.options().get(0);
        assertThat(optA.correct()).isTrue();

        assertThat(state.removeOption(optA)).isTrue();

        assertThat(state.options()).noneMatch(QuestionEditorState.OptionState::correct);
        assertThat(state.notice()).contains("removed");
        var invalid = state.validateAndBuild("Question 1");
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.errorMessage()).contains("exactly one correct option");

        // Cannot remove below minimum 2 options
        assertThat(state.removeOption(state.options().get(0))).isFalse();
    }

    @Test
    void loadingValidExistingSingleAndMultiDefinitionsPreservesSelections() {
        var existingSingle = new AdminQuestionDefinitionDto(
                "Which OSI layer handles routing?",
                "SINGLE",
                List.of(
                        new AdminOptionDefinitionDto("Data Link", false),
                        new AdminOptionDefinitionDto("Network", true),
                        new AdminOptionDefinitionDto("Transport", false)
                ));
        QuestionEditorState singleState = QuestionEditorState.fromExisting(existingSingle);
        assertThat(singleState.isSingle()).isTrue();
        assertThat(singleState.notice()).isNull();
        assertThat(singleState.options()).extracting(QuestionEditorState.OptionState::correct)
                .containsExactly(false, true, false);
        assertThat(singleState.validateAndBuild("Question 1").isValid()).isTrue();

        var existingMulti = new AdminQuestionDefinitionDto(
                "Select private IPv4 ranges",
                "MULTI",
                List.of(
                        new AdminOptionDefinitionDto("10.0.0.0/8", true),
                        new AdminOptionDefinitionDto("172.16.0.0/12", true),
                        new AdminOptionDefinitionDto("8.8.8.0/24", false)
                ));
        QuestionEditorState multiState = QuestionEditorState.fromExisting(existingMulti);
        assertThat(multiState.isSingle()).isFalse();
        assertThat(multiState.notice()).isNull();
        assertThat(multiState.options()).extracting(QuestionEditorState.OptionState::correct)
                .containsExactly(true, true, false);
        assertThat(multiState.validateAndBuild("Question 2").isValid()).isTrue();
    }

    @Test
    void loadingMalformedHistoricalSingleQuestionFlagsErrorAndRequiresCorrection() {
        var malformed = new AdminQuestionDefinitionDto(
                "Historical broken SINGLE question",
                "SINGLE",
                List.of(
                        new AdminOptionDefinitionDto("Option 1", true),
                        new AdminOptionDefinitionDto("Option 2", true)
                ));

        QuestionEditorState state = QuestionEditorState.fromExisting(malformed);

        assertThat(state.isSingle()).isTrue();
        assertThat(state.options()).noneMatch(QuestionEditorState.OptionState::correct);
        assertThat(state.notice()).contains("2 correct options");
        assertThat(state.validateAndBuild("Question 1").isValid()).isFalse();

        state.setOptionCorrect(state.options().get(0), true);
        assertThat(state.notice()).isNull();
        assertThat(state.validateAndBuild("Question 1").isValid()).isTrue();
    }
}
