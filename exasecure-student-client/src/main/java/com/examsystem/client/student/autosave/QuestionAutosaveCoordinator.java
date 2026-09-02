package com.examsystem.client.student.autosave;

import com.examsystem.contracts.StudentQuestionDto;

import java.util.*;
import java.util.function.Consumer;

public class QuestionAutosaveCoordinator {

    public static class QuestionSaveState {
        private final int questionId;
        private final int questionIndex;
        private List<Integer> intended = new ArrayList<>();
        private List<Integer> acknowledged = new ArrayList<>();
        private long currentGen = 0;
        private long acknowledgedGen = 0;
        private boolean inFlight = false;
        private boolean failed = false;
        private String lastError = null;

        public QuestionSaveState(int questionId, int questionIndex) {
            this.questionId = questionId;
            this.questionIndex = questionIndex;
        }

        public int questionId() { return questionId; }
        public int questionIndex() { return questionIndex; }
        public List<Integer> intended() { return new ArrayList<>(intended); }
        public List<Integer> acknowledged() { return acknowledged != null ? new ArrayList<>(acknowledged) : null; }
        public long currentGen() { return currentGen; }
        public long acknowledgedGen() { return acknowledgedGen; }
        public boolean inFlight() { return inFlight; }
        public boolean failed() { return failed; }
        public String lastError() { return lastError; }

        public boolean isSynchronized() {
            return !inFlight && !failed && acknowledged != null && acknowledged.equals(intended) && acknowledgedGen == currentGen;
        }
    }

    @FunctionalInterface
    public interface SaveDispatcher {
        void dispatch(int questionId, List<Integer> snapshot, Consumer<List<Integer>> onSuccess, Consumer<Throwable> onError);
    }

    public interface Listener {
        void onStateChanged(QuestionSaveState state);
        void onSaveFailed(QuestionSaveState state, Throwable error);
    }

    private final Map<Integer, QuestionSaveState> states = new LinkedHashMap<>();
    private final SaveDispatcher dispatcher;
    private final Listener listener;

    public QuestionAutosaveCoordinator(List<StudentQuestionDto> questions,
                                       Map<Integer, List<Integer>> initialAnswers,
                                       SaveDispatcher dispatcher,
                                       Listener listener) {
        this.dispatcher = dispatcher;
        this.listener = listener != null ? listener : new Listener() {
            @Override public void onStateChanged(QuestionSaveState state) {}
            @Override public void onSaveFailed(QuestionSaveState state, Throwable error) {}
        };

        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                StudentQuestionDto q = questions.get(i);
                QuestionSaveState s = new QuestionSaveState(q.id(), i);
                if (initialAnswers != null && initialAnswers.containsKey(q.id())) {
                    s.intended = new ArrayList<>(initialAnswers.get(q.id()));
                    s.acknowledged = new ArrayList<>(s.intended);
                } else {
                    s.intended = new ArrayList<>();
                    s.acknowledged = new ArrayList<>();
                }
                states.put(q.id(), s);
            }
        }
    }

    public synchronized QuestionSaveState getState(int questionId) {
        return states.get(questionId);
    }

    public synchronized Collection<QuestionSaveState> getAllStates() {
        return states.values();
    }

    public synchronized boolean isAllSynchronized() {
        for (QuestionSaveState s : states.values()) {
            if (!s.isSynchronized()) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean hasAnyFailed() {
        return states.values().stream().anyMatch(s -> s.failed);
    }

    public synchronized Optional<QuestionSaveState> getFirstFailed() {
        return states.values().stream().filter(s -> s.failed).findFirst();
    }

    public synchronized boolean hasAnyInFlight() {
        return states.values().stream().anyMatch(s -> s.inFlight);
    }

    public synchronized void onAnswerChanged(int questionId, List<Integer> newSelections) {
        QuestionSaveState s = states.get(questionId);
        if (s == null) return;

        s.intended = new ArrayList<>(newSelections);
        s.currentGen++;
        s.failed = false;
        s.lastError = null;

        if (!s.inFlight) {
            dispatchSave(s, s.currentGen, new ArrayList<>(newSelections));
        } else {
            notifyStateChanged(s);
        }
    }

    public synchronized void retry(int questionId) {
        QuestionSaveState s = states.get(questionId);
        if (s == null || s.inFlight) return;

        s.failed = false;
        s.lastError = null;
        s.currentGen++;
        dispatchSave(s, s.currentGen, new ArrayList<>(s.intended));
    }

    private void dispatchSave(QuestionSaveState s, long gen, List<Integer> snapshot) {
        s.inFlight = true;
        notifyStateChanged(s);

        dispatcher.dispatch(
                s.questionId,
                snapshot,
                ack -> handleSuccess(s.questionId, gen, snapshot),
                err -> handleFailure(s.questionId, gen, err)
        );
    }

    private synchronized void handleSuccess(int questionId, long gen, List<Integer> snapshot) {
        QuestionSaveState s = states.get(questionId);
        if (s == null) return;

        s.inFlight = false;

        // If newer selections arrived while this save was in flight, dispatch the latest intended snapshot
        if (s.currentGen > gen) {
            dispatchSave(s, s.currentGen, new ArrayList<>(s.intended));
            return;
        }

        // Latest generation successfully acknowledged
        s.acknowledged = new ArrayList<>(snapshot);
        s.acknowledgedGen = gen;
        s.failed = false;
        s.lastError = null;
        notifyStateChanged(s);
    }

    private synchronized void handleFailure(int questionId, long gen, Throwable error) {
        QuestionSaveState s = states.get(questionId);
        if (s == null) return;

        s.inFlight = false;

        // If newer selections arrived while this save was in flight, discard the stale failure
        // and immediately dispatch the latest intended snapshot
        if (s.currentGen > gen) {
            dispatchSave(s, s.currentGen, new ArrayList<>(s.intended));
            return;
        }

        // Failure on the latest intended generation
        s.failed = true;
        s.lastError = error != null ? error.getMessage() : "Unknown error";
        notifyStateChanged(s);
        listener.onSaveFailed(s, error);
    }

    private void notifyStateChanged(QuestionSaveState s) {
        listener.onStateChanged(s);
    }
}

