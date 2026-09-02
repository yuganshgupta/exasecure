package com.examsystem.client.student.autosave;

import com.examsystem.contracts.StudentOptionDto;
import com.examsystem.contracts.StudentQuestionDto;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionAutosaveCoordinatorTest {

    private static StudentQuestionDto createQuestion(int id, int number) {
        return new StudentQuestionDto(
                id, number, "Question " + number, "SINGLE",
                List.of(new StudentOptionDto(1, "Option 1"), new StudentOptionDto(2, "Option 2"))
        );
    }

    static class MockDispatcher implements QuestionAutosaveCoordinator.SaveDispatcher {
        final List<List<Integer>> dispatchedSnapshots = new ArrayList<>();
        final List<Consumer<List<Integer>>> pendingSuccessCallbacks = new ArrayList<>();
        final List<Consumer<Throwable>> pendingErrorCallbacks = new ArrayList<>();
        final AtomicInteger activeInFlightCount = new AtomicInteger(0);
        int maxSimultaneousInFlight = 0;

        @Override
        public void dispatch(int questionId, List<Integer> snapshot, Consumer<List<Integer>> onSuccess, Consumer<Throwable> onError) {
            dispatchedSnapshots.add(new ArrayList<>(snapshot));
            int currentInFlight = activeInFlightCount.incrementAndGet();
            if (currentInFlight > maxSimultaneousInFlight) {
                maxSimultaneousInFlight = currentInFlight;
            }
            pendingSuccessCallbacks.add(ack -> {
                activeInFlightCount.decrementAndGet();
                onSuccess.accept(ack);
            });
            pendingErrorCallbacks.add(err -> {
                activeInFlightCount.decrementAndGet();
                onError.accept(err);
            });
        }

        void completeNextSuccess(List<Integer> ack) {
            assertThat(pendingSuccessCallbacks).isNotEmpty();
            var cb = pendingSuccessCallbacks.remove(0);
            pendingErrorCallbacks.remove(0);
            cb.accept(ack);
        }

        void completeNextError(Throwable err) {
            assertThat(pendingErrorCallbacks).isNotEmpty();
            var cb = pendingErrorCallbacks.remove(0);
            pendingSuccessCallbacks.remove(0);
            cb.accept(err);
        }
    }

    @Test
    void rapidChangesAtoB_neverAllowsPersistedAToOverwriteB() {
        MockDispatcher dispatcher = new MockDispatcher();
        List<StudentQuestionDto> questions = List.of(createQuestion(101, 1));
        QuestionAutosaveCoordinator coordinator = new QuestionAutosaveCoordinator(questions, Map.of(), dispatcher, null);

        // Initially synchronized with empty answers
        assertThat(coordinator.isAllSynchronized()).isTrue();
        assertThat(coordinator.getState(101).isSynchronized()).isTrue();

        // 1. User selects A ([1]) -> triggers first in-flight save
        coordinator.onAnswerChanged(101, List.of(1));
        assertThat(dispatcher.dispatchedSnapshots).hasSize(1);
        assertThat(dispatcher.dispatchedSnapshots.get(0)).containsExactly(1);
        assertThat(coordinator.getState(101).inFlight()).isTrue();
        assertThat(coordinator.isAllSynchronized()).isFalse();

        // 2. User rapidly selects B ([2]) while A is still in-flight
        coordinator.onAnswerChanged(101, List.of(2));

        // Invariant: No second request dispatched concurrently! Max simultaneous in flight is strictly 1
        assertThat(dispatcher.dispatchedSnapshots).hasSize(1);
        assertThat(dispatcher.activeInFlightCount.get()).isEqualTo(1);
        assertThat(coordinator.getState(101).intended()).containsExactly(2);
        assertThat(coordinator.getState(101).inFlight()).isTrue();
        assertThat(coordinator.isAllSynchronized()).isFalse();

        // 3. Save A completes on server
        dispatcher.completeNextSuccess(List.of(1));

        // Invariant: As soon as A completed, B was dispatched! Total dispatched is now 2
        assertThat(dispatcher.dispatchedSnapshots).hasSize(2);
        assertThat(dispatcher.dispatchedSnapshots.get(1)).containsExactly(2);
        assertThat(dispatcher.maxSimultaneousInFlight).isEqualTo(1); // Never > 1 simultaneously!
        assertThat(coordinator.getState(101).inFlight()).isTrue();
        assertThat(coordinator.isAllSynchronized()).isFalse();

        // 4. Save B completes on server
        dispatcher.completeNextSuccess(List.of(2));

        // Invariant: Client is now fully synchronized with B, never stale A!
        assertThat(coordinator.getState(101).inFlight()).isFalse();
        assertThat(coordinator.getState(101).isSynchronized()).isTrue();
        assertThat(coordinator.getState(101).acknowledged()).containsExactly(2);
        assertThat(coordinator.isAllSynchronized()).isTrue();

        // Invariant: Dispatched sequence was strictly [1] then [2], never [2] then [1]
        assertThat(dispatcher.dispatchedSnapshots).containsExactly(List.of(1), List.of(2));
    }

    @Test
    void rapidChangesAtoBtoC_coalescesIntermediateSelections() {
        MockDispatcher dispatcher = new MockDispatcher();
        List<StudentQuestionDto> questions = List.of(createQuestion(101, 1));
        QuestionAutosaveCoordinator coordinator = new QuestionAutosaveCoordinator(questions, Map.of(), dispatcher, null);

        // 1. Select A
        coordinator.onAnswerChanged(101, List.of(1));
        assertThat(dispatcher.dispatchedSnapshots).hasSize(1);

        // 2. While A is in flight, rapidly select B then C
        coordinator.onAnswerChanged(101, List.of(2));
        coordinator.onAnswerChanged(101, List.of(1, 2));

        // Still only 1 dispatched
        assertThat(dispatcher.dispatchedSnapshots).hasSize(1);

        // 3. A completes
        dispatcher.completeNextSuccess(List.of(1));

        // Invariant: B was skipped entirely; C was dispatched immediately!
        assertThat(dispatcher.dispatchedSnapshots).hasSize(2);
        assertThat(dispatcher.dispatchedSnapshots.get(1)).containsExactly(1, 2);

        // 4. C completes
        dispatcher.completeNextSuccess(List.of(1, 2));
        assertThat(coordinator.getState(101).isSynchronized()).isTrue();
        assertThat(coordinator.getState(101).acknowledged()).containsExactly(1, 2);
    }

    @Test
    void failureDuringInFlight_discardsOldFailureIfNewerGenerationPending() {
        MockDispatcher dispatcher = new MockDispatcher();
        List<StudentQuestionDto> questions = List.of(createQuestion(101, 1));
        QuestionAutosaveCoordinator coordinator = new QuestionAutosaveCoordinator(questions, Map.of(), dispatcher, null);

        // 1. Select A
        coordinator.onAnswerChanged(101, List.of(1));

        // 2. Select B while A in flight
        coordinator.onAnswerChanged(101, List.of(2));

        // 3. A fails with network exception
        dispatcher.completeNextError(new RuntimeException("Connection reset"));

        // Invariant: Stale failure for A is discarded, B is dispatched immediately!
        assertThat(coordinator.hasAnyFailed()).isFalse();
        assertThat(dispatcher.dispatchedSnapshots).hasSize(2);
        assertThat(dispatcher.dispatchedSnapshots.get(1)).containsExactly(2);

        // 4. B succeeds
        dispatcher.completeNextSuccess(List.of(2));
        assertThat(coordinator.isAllSynchronized()).isTrue();
        assertThat(coordinator.getState(101).acknowledged()).containsExactly(2);
    }

    @Test
    void terminalFailure_allowsRetry() {
        MockDispatcher dispatcher = new MockDispatcher();
        List<StudentQuestionDto> questions = List.of(createQuestion(101, 1));
        QuestionAutosaveCoordinator coordinator = new QuestionAutosaveCoordinator(questions, Map.of(), dispatcher, null);

        // 1. Select A
        coordinator.onAnswerChanged(101, List.of(1));

        // 2. A fails
        dispatcher.completeNextError(new RuntimeException("Timeout"));

        // Invariant: Terminal failure recorded
        assertThat(coordinator.hasAnyFailed()).isTrue();
        assertThat(coordinator.getState(101).failed()).isTrue();
        assertThat(coordinator.getState(101).lastError()).isEqualTo("Timeout");

        // 3. Retry save
        coordinator.retry(101);
        assertThat(dispatcher.dispatchedSnapshots).hasSize(2);
        assertThat(dispatcher.dispatchedSnapshots.get(1)).containsExactly(1);

        // 4. Retry succeeds
        dispatcher.completeNextSuccess(List.of(1));
        assertThat(coordinator.hasAnyFailed()).isFalse();
        assertThat(coordinator.getState(101).isSynchronized()).isTrue();
    }
}
