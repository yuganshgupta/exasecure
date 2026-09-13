package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.exam.ExamTimer;
import com.examsystem.client.student.StudentApp;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentOptionDto;
import com.examsystem.contracts.StudentQuestionDto;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.examsystem.client.student.autosave.QuestionAutosaveCoordinator;
import com.examsystem.client.student.autosave.QuestionAutosaveCoordinator.QuestionSaveState;
import com.examsystem.client.student.proctor.ProctorIncidentCoordinator;
import com.examsystem.contracts.StudentProctorEventResponseDto;

public class ExamTakingView {

    static final int MIN_READING_SCALE = 80;
    static final int MAX_READING_SCALE = 160;
    static final int READING_SCALE_STEP = 10;
    static final int DEFAULT_READING_SCALE = 100;

    private final StudentApp app;
    private final ApiClient apiClient;
    private final Stage stage;
    private final StudentExamDto exam;
    private final StudentAttemptDto attempt;
    private final List<StudentQuestionDto> questions;
    private final Map<Integer, List<Integer>> answers;
    private final Set<Integer> flaggedQuestions = new HashSet<>();
    private final QuestionAutosaveCoordinator saveCoordinator;

    private final BorderPane view;
    private final Label timerLabel;
    private final Label questionNumberLabel;
    private final Label questionTypeBadge;
    private final Label questionTextLabel;
    private final VBox optionsBox;
    private final Label autosaveStatusLabel;
    private final Button retryAutosaveBtn;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button clearBtn;
    private final Button flagBtn;
    private final Button submitBtn;
    private final FlowPane palettePane;
    private final Label answeredSummaryLabel;
    private final Label proctorNoticeBanner;
    private final VBox questionCard;
    private final Label readingScaleLabel;
    private final Button decreaseReadingScaleBtn;
    private final Button increaseReadingScaleBtn;

    private int currentIndex = 0;
    private int readingScalePercent = DEFAULT_READING_SCALE;
    private final ExamTimer examTimer;
    private Timeline countdownTimeline;
    private ChangeListener<Boolean> focusListener;
    private ChangeListener<Boolean> iconifiedListener;
    private ChangeListener<Boolean> fullScreenListener;
    private EventHandler<WindowEvent> proctorWindowHiddenHandler;
    private boolean isDialogActive = false;
    private boolean isSubmitted = false;
    private boolean isSubmissionPending = false;
    private ProctorIncidentCoordinator proctorCoordinator;

    public ExamTakingView(StudentApp app, ApiClient apiClient, Stage stage,
                          StudentExamDto exam, StudentAttemptDto attempt,
                          List<StudentQuestionDto> questions,
                          Map<Integer, List<Integer>> initialAnswers) {
        this.app = app;
        this.apiClient = apiClient;
        this.stage = stage;
        this.exam = exam;
        this.attempt = attempt;
        this.questions = questions;
        this.answers = new HashMap<>();
        if (initialAnswers != null) {
            for (var entry : initialAnswers.entrySet()) {
                this.answers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        this.saveCoordinator = new QuestionAutosaveCoordinator(
                questions,
                initialAnswers,
                (qId, snapshot, onSuccess, onError) -> {
                    AsyncRunner.runAsync(
                            () -> {
                                apiClient.saveAnswer(attempt.id(), qId, snapshot);
                                return snapshot;
                            },
                            onSuccess,
                            onError
                    );
                },
                new QuestionAutosaveCoordinator.Listener() {
                    @Override
                    public void onStateChanged(QuestionSaveState state) {
                        updateAutosaveUIForCurrentQuestion();
                        updatePalette();
                        if (isSubmissionPending && saveCoordinator.isAllSynchronized()) {
                            checkAndProceedWithPendingSubmission();
                        }
                    }

                    @Override
                    public void onSaveFailed(QuestionSaveState state, Throwable error) {
                        if (isSubmissionPending) {
                            isSubmissionPending = false;
                            setControlsDisabled(false);
                            submitBtn.setText("Submit Exam");
                            UiFeedback.showError("Submission Aborted",
                                    "Cannot submit exam: Question " + (state.questionIndex() + 1)
                                            + " failed to save (" + (error != null ? error.getMessage() : "unknown error")
                                            + "). Please retry saving before submitting.");
                            currentIndex = state.questionIndex();
                            renderCurrentQuestion();
                            updatePalette();
                        }
                    }
                }
        );

        this.view = new BorderPane();
        this.timerLabel = new Label();
        this.questionNumberLabel = new Label();
        this.questionTypeBadge = new Label();
        this.questionTextLabel = new Label();
        this.optionsBox = new VBox(12);
        this.autosaveStatusLabel = new Label();
        this.retryAutosaveBtn = new Button("🔄 Retry Save");
        this.prevBtn = new Button("← Previous");
        this.nextBtn = new Button("Next →");
        this.clearBtn = new Button("Clear Choice");
        this.flagBtn = new Button("🚩 Flag for Review");
        this.submitBtn = new Button("Submit Exam");
        this.palettePane = new FlowPane();
        this.answeredSummaryLabel = new Label();
        this.proctorNoticeBanner = new Label();
        this.questionCard = new VBox(20);
        this.readingScaleLabel = new Label();
        this.decreaseReadingScaleBtn = new Button("A−");
        this.increaseReadingScaleBtn = new Button("A+");

        Instant start = attempt.startTime() != null ? attempt.startTime() : Instant.now();
        this.examTimer = new ExamTimer(start, exam.durationMinutes());

        buildUI();
        setupTimer();
        setupProctoring();
        renderCurrentQuestion();
        updatePalette();
    }

    public Parent getView() {
        return view;
    }

    private void buildUI() {
        view.getStyleClass().add("app-root");

        // Top Header
        VBox topContainer = new VBox();

        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(12, 24, 12, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("app-header");

        VBox titleBox = new VBox(2);
        Label examTitle = new Label(exam.title());
        examTitle.getStyleClass().add("header-title");

        Label attemptBadge = new Label("Monitored examination session");
        attemptBadge.getStyleClass().addAll("header-meta", "small-text");
        titleBox.getChildren().addAll(examTitle, attemptBadge);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Authoritative Timer Display
        HBox timerBox = new HBox(8);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.getStyleClass().add("timer-panel");

        Label timerIcon = new Label("⏱");
        timerIcon.getStyleClass().add("timer-normal");

        timerLabel.getStyleClass().addAll("timer-value", "timer-normal");
        timerBox.getChildren().addAll(timerIcon, timerLabel);

        HBox readingControls = new HBox(6);
        readingControls.setAlignment(Pos.CENTER);
        readingScaleLabel.getStyleClass().addAll("header-meta", "small-text");
        decreaseReadingScaleBtn.getStyleClass().add("btn-header");
        decreaseReadingScaleBtn.setAccessibleText("Decrease question and answer text size");
        decreaseReadingScaleBtn.setOnAction(e -> changeReadingScale(-1));
        increaseReadingScaleBtn.getStyleClass().add("btn-header");
        increaseReadingScaleBtn.setAccessibleText("Increase question and answer text size");
        increaseReadingScaleBtn.setOnAction(e -> changeReadingScale(1));
        readingControls.getChildren().addAll(
                decreaseReadingScaleBtn, readingScaleLabel, increaseReadingScaleBtn);
        applyReadingScale();

        submitBtn.getStyleClass().add("btn-success");
        submitBtn.setOnAction(e -> confirmAndSubmitExam());

        topBar.getChildren().addAll(titleBox, spacer, readingControls, timerBox, submitBtn);

        // Proctor warning banner (hidden by default)
        proctorNoticeBanner.setVisible(false);
        proctorNoticeBanner.setManaged(false);
        proctorNoticeBanner.setMaxWidth(Double.MAX_VALUE);
        proctorNoticeBanner.setAlignment(Pos.CENTER);
        proctorNoticeBanner.setPadding(new Insets(8));
        proctorNoticeBanner.getStyleClass().addAll("violation-banner", "violation-warning");

        topContainer.getChildren().addAll(topBar, proctorNoticeBanner);
        view.setTop(topContainer);

        // Center: Question Content Card
        questionCard.setMinWidth(0);
        questionCard.setMaxWidth(Double.MAX_VALUE);
        questionCard.getStyleClass().addAll("theme-card", "question-card");

        HBox questionHeader = new HBox(12);
        questionHeader.setAlignment(Pos.CENTER_LEFT);

        questionNumberLabel.getStyleClass().add("question-number");

        questionTypeBadge.getStyleClass().add("question-type");

        Region qSpacer = new Region();
        HBox.setHgrow(qSpacer, Priority.ALWAYS);

        autosaveStatusLabel.getStyleClass().addAll("secondary-text", "small-text");

        retryAutosaveBtn.setVisible(false);
        retryAutosaveBtn.setManaged(false);
        retryAutosaveBtn.setText("Retry Save");
        retryAutosaveBtn.getStyleClass().add("btn-danger");
        retryAutosaveBtn.setOnAction(e -> {
            if (questions.isEmpty()) return;
            StudentQuestionDto q = questions.get(currentIndex);
            retryQuestionSave(q.id());
        });

        questionHeader.getChildren().addAll(questionNumberLabel, questionTypeBadge, qSpacer, autosaveStatusLabel, retryAutosaveBtn);

        questionTextLabel.getStyleClass().add("question-text");
        questionTextLabel.setWrapText(true);
        questionTextLabel.setMaxWidth(Double.MAX_VALUE);

        optionsBox.setPadding(new Insets(10, 0, 10, 0));
        optionsBox.setFillWidth(true);
        optionsBox.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(optionsBox, Priority.ALWAYS);

        // Bottom Navigation
        HBox navBar = new HBox(12);
        navBar.setAlignment(Pos.CENTER_LEFT);

        prevBtn.getStyleClass().add("btn-secondary");
        prevBtn.setOnAction(e -> {
            if (currentIndex > 0) {
                currentIndex--;
                renderCurrentQuestion();
                updatePalette();
            }
        });

        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> clearCurrentAnswer());

        flagBtn.setText("Flag for Review");
        flagBtn.getStyleClass().add("btn-flag");
        flagBtn.setOnAction(e -> toggleCurrentFlag());

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        nextBtn.getStyleClass().add("btn-primary");
        nextBtn.setOnAction(e -> {
            if (currentIndex < questions.size() - 1) {
                currentIndex++;
                renderCurrentQuestion();
                updatePalette();
            }
        });

        navBar.getChildren().addAll(prevBtn, clearBtn, flagBtn, navSpacer, nextBtn);

        questionCard.getChildren().addAll(questionHeader, questionTextLabel, optionsBox, navBar);

        ScrollPane centerScroll = new ScrollPane(questionCard);
        centerScroll.setFitToWidth(true);
        centerScroll.setFitToHeight(true);
        centerScroll.setPadding(new Insets(24));
        view.setCenter(centerScroll);

        // Right Sidebar: Question Palette
        VBox rightSidebar = new VBox(16);
        rightSidebar.setPadding(new Insets(24));
        rightSidebar.setPrefWidth(270);
        rightSidebar.getStyleClass().add("theme-card");

        Label paletteTitle = new Label("Question Navigator");
        paletteTitle.getStyleClass().add("section-title");

        answeredSummaryLabel.getStyleClass().addAll("secondary-text", "small-text");

        palettePane.setHgap(8);
        palettePane.setVgap(8);

        VBox legend = new VBox(6);
        Label currentKey = new Label("▣ Current");
        Label answeredKey = new Label("✓ Answered");
        Label flaggedKey = new Label("⚑ Flagged for review");
        Label failedKey = new Label("! Save failed");
        Label unansweredKey = new Label("○ Unanswered");
        for (Label key : List.of(currentKey, answeredKey, flaggedKey, failedKey, unansweredKey)) {
            key.getStyleClass().addAll("secondary-text", "small-text");
        }
        legend.getChildren().addAll(currentKey, answeredKey, flaggedKey, failedKey, unansweredKey);

        rightSidebar.getChildren().addAll(paletteTitle, answeredSummaryLabel, palettePane, legend);
        view.setRight(rightSidebar);
    }

    private void changeReadingScale(int direction) {
        int adjusted = adjustedReadingScale(readingScalePercent, direction);
        if (adjusted == readingScalePercent) return;
        readingScalePercent = adjusted;
        applyReadingScale();
    }

    private void applyReadingScale() {
        questionCard.getStyleClass().removeIf(style -> style.startsWith("reading-scale-"));
        questionCard.getStyleClass().add("reading-scale-" + readingScalePercent);
        readingScaleLabel.setText(readingScalePercent + "%");
        decreaseReadingScaleBtn.setDisable(readingScalePercent <= MIN_READING_SCALE);
        increaseReadingScaleBtn.setDisable(readingScalePercent >= MAX_READING_SCALE);
    }

    static int adjustedReadingScale(int currentScale, int direction) {
        int stepped = currentScale + Integer.signum(direction) * READING_SCALE_STEP;
        return Math.max(MIN_READING_SCALE, Math.min(MAX_READING_SCALE, stepped));
    }

    private void setupTimer() {
        updateTimerDisplay();
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            updateTimerDisplay();
            if (examTimer.isExpired()) {
                countdownTimeline.stop();
                handleAutoSubmitOnExpiry();
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void updateTimerDisplay() {
        long remaining = examTimer.getRemainingSeconds();
        timerLabel.setText(ExamTimer.formatRemainingTime(remaining));
        timerLabel.getStyleClass().removeAll("timer-normal", "timer-warning", "timer-danger");
        if (remaining <= 60) {
            timerLabel.getStyleClass().add("timer-danger");
        } else if (remaining <= 300) {
            timerLabel.getStyleClass().add("timer-warning");
        } else {
            timerLabel.getStyleClass().add("timer-normal");
        }
    }

    private void setupProctoring() {
        if (stage == null) return;

        proctorCoordinator = new ProctorIncidentCoordinator(
                attempt.id(),
                apiClient,
                this::handleProctorResponseOnFxThread,
                this::handleProctorTerminationOnFxThread
        );
        proctorWindowHiddenHandler = event -> cleanupListeners();
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, proctorWindowHiddenHandler);

        focusListener = (obs, oldVal, newVal) -> {
            if (!newVal && !isDialogActive && !isSubmitted && !isSubmissionPending) {
                proctorCoordinator.onFocusLost();
                Platform.runLater(() -> {
                    setViolationBanner("violation-warning", "Window focus lost. Recording proctoring incident...");
                    proctorNoticeBanner.setVisible(true);
                    proctorNoticeBanner.setManaged(true);
                });
            } else if (newVal && !isSubmitted && !isSubmissionPending) {
                proctorCoordinator.onFocusGained();
            }
        };
        stage.focusedProperty().addListener(focusListener);

        iconifiedListener = (obs, oldVal, newVal) -> {
            if (newVal && !isSubmitted && !isSubmissionPending) {
                Platform.runLater(() -> {
                    if (stage != null && !isSubmitted) {
                        stage.setIconified(false);
                        stage.toFront();
                    }
                });
            }
        };
        stage.iconifiedProperty().addListener(iconifiedListener);

        fullScreenListener = (obs, oldVal, newVal) -> {
            if (!newVal && !isSubmitted && !isSubmissionPending && !isDialogActive) {
                Platform.runLater(() -> {
                    if (stage != null && !isSubmitted && !stage.isFullScreen()) {
                        stage.setFullScreen(true);
                        stage.toFront();
                    }
                });
            }
        };
        stage.fullScreenProperty().addListener(fullScreenListener);
    }

    private void handleProctorResponseOnFxThread(StudentProctorEventResponseDto response) {
        Platform.runLater(() -> {
            int count = response.violationCount();
            boolean flagged = response.flagged();

            if (flagged || count >= 3) {
                isSubmitted = true;
                isSubmissionPending = false;
                cleanupListeners();
                setControlsDisabled(true);
                if (stage != null) stage.setFullScreen(false);

                setViolationBanner("violation-danger",
                        "Violation 3 of 3 — Examination terminated. Attempt flagged for administrative review.");

                isDialogActive = true;
                try {
                    UiFeedback.showModalError(stage, "Exam Terminated",
                            "The examination was terminated after three focus-loss incidents.\n\n"
                                    + "Your attempt was submitted and flagged for administrative review.");
                } finally {
                    isDialogActive = false;
                }
                app.showHistory();
            } else if (count == 2) {
                setViolationBanner("violation-warning",
                        "Violation 2 of 3 — Repeated focus loss. One more violation will terminate the examination.");

                isDialogActive = true;
                try {
                    UiFeedback.showModalWarning(stage, "Proctoring Final Warning",
                            "Repeated focus loss was recorded.\n\n"
                                    + "One more violation will terminate the examination.");
                } finally {
                    isDialogActive = false;
                }
            } else {
                setViolationBanner("violation-warning",
                        "Violation 1 of 3 — Window focus lost. Incident recorded.");

                isDialogActive = true;
                try {
                    UiFeedback.showModalWarning(stage, "Proctoring Warning",
                            "Window focus was lost and the incident was recorded.\n\n"
                                    + "Please remain in the examination window.");
                } finally {
                    isDialogActive = false;
                }
            }
        });
    }

    private void handleProctorTerminationOnFxThread() {
        Platform.runLater(() -> {
            if (!isSubmitted) {
                isSubmitted = true;
                isSubmissionPending = false;
                cleanupListeners();
                setControlsDisabled(true);
                if (stage != null) stage.setFullScreen(false);
            }
        });
    }

    private void renderCurrentQuestion() {
        if (questions.isEmpty()) return;

        StudentQuestionDto q = questions.get(currentIndex);
        questionNumberLabel.setText("Question " + (currentIndex + 1) + " of " + questions.size());

        boolean isMulti = "MULTI".equalsIgnoreCase(q.type());
        questionTypeBadge.setText(isMulti ? "MULTIPLE CHOICE (Select all that apply)" : "SINGLE CHOICE (Select one)");
        questionTypeBadge.getStyleClass().remove("question-type-multi");
        if (isMulti) questionTypeBadge.getStyleClass().add("question-type-multi");

        questionTextLabel.setText(q.text());

        // Update Flag button state
        boolean isFlagged = flaggedQuestions.contains(q.id());
        flagBtn.getStyleClass().removeAll("btn-flag", "btn-flag-selected");
        if (isFlagged) {
            flagBtn.setText("Remove Review Flag");
            flagBtn.getStyleClass().add("btn-flag-selected");
        } else {
            flagBtn.setText("Flag for Review");
            flagBtn.getStyleClass().add("btn-flag");
        }

        prevBtn.setDisable(currentIndex == 0 || isSubmitted || isSubmissionPending);
        nextBtn.setDisable(currentIndex == questions.size() - 1 || isSubmitted || isSubmissionPending);
        clearBtn.setDisable(isSubmitted || isSubmissionPending);
        flagBtn.setDisable(isSubmitted || isSubmissionPending);

        optionsBox.getChildren().clear();
        List<Integer> selectedOptions = answers.getOrDefault(q.id(), List.of());
        ToggleGroup singleGroup = isMulti ? null : new ToggleGroup();

        for (StudentOptionDto opt : q.options()) {
            HBox optRow = new HBox(10);
            optRow.setAlignment(Pos.CENTER_LEFT);
            optRow.getStyleClass().add("option-row");
            optRow.setMaxWidth(Double.MAX_VALUE);

            if (isMulti) {
                CheckBox cb = new CheckBox(opt.optionNumber() + ". " + opt.text());
                cb.getStyleClass().add("option-control");
                cb.setWrapText(true);
                cb.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(cb, Priority.ALWAYS);
                cb.setSelected(selectedOptions.contains(opt.optionNumber()));
                cb.setDisable(isSubmitted || isSubmissionPending);
                cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    List<Integer> current = answers.computeIfAbsent(q.id(), k -> new ArrayList<>());
                    if (newVal && !current.contains(opt.optionNumber())) {
                        current.add(opt.optionNumber());
                    } else if (!newVal) {
                        current.remove(Integer.valueOf(opt.optionNumber()));
                    }
                    onAnswerChanged(q.id(), current);
                });
                optRow.getChildren().add(cb);
            } else {
                RadioButton rb = new RadioButton(opt.optionNumber() + ". " + opt.text());
                rb.getStyleClass().add("option-control");
                rb.setWrapText(true);
                rb.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(rb, Priority.ALWAYS);
                rb.setToggleGroup(singleGroup);
                rb.setSelected(selectedOptions.contains(opt.optionNumber()));
                rb.setDisable(isSubmitted || isSubmissionPending);
                rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        List<Integer> current = List.of(opt.optionNumber());
                        answers.put(q.id(), new ArrayList<>(current));
                        onAnswerChanged(q.id(), current);
                    }
                });
                optRow.getChildren().add(rb);
            }

            optionsBox.getChildren().add(optRow);
        }

        updateAutosaveUIForCurrentQuestion();
    }

    private void clearCurrentAnswer() {
        if (isSubmitted || isSubmissionPending) return;
        StudentQuestionDto q = questions.get(currentIndex);
        answers.remove(q.id());
        onAnswerChanged(q.id(), List.of());
        renderCurrentQuestion();
    }

    private void toggleCurrentFlag() {
        if (isSubmitted || isSubmissionPending) return;
        StudentQuestionDto q = questions.get(currentIndex);
        if (flaggedQuestions.contains(q.id())) {
            flaggedQuestions.remove(q.id());
        } else {
            flaggedQuestions.add(q.id());
        }
        renderCurrentQuestion();
        updatePalette();
    }

    private void onAnswerChanged(int questionId, List<Integer> newSelections) {
        if (isSubmitted || isSubmissionPending) return;
        saveCoordinator.onAnswerChanged(questionId, newSelections);
        updatePalette();
    }

    private void retryQuestionSave(int questionId) {
        if (isSubmitted || isSubmissionPending) return;
        saveCoordinator.retry(questionId);
    }

    private void updateAutosaveUIForCurrentQuestion() {
        if (questions.isEmpty()) return;
        StudentQuestionDto q = questions.get(currentIndex);
        QuestionSaveState state = saveCoordinator.getState(q.id());
        if (state == null) {
            autosaveStatusLabel.setText("");
            retryAutosaveBtn.setVisible(false);
            retryAutosaveBtn.setManaged(false);
            return;
        }

        autosaveStatusLabel.getStyleClass().removeAll("secondary-text", "status-error", "status-warning", "status-success");
        if (state.failed()) {
            autosaveStatusLabel.setText("✗ Autosave failed: " + (state.lastError() != null ? state.lastError() : "unknown error"));
            autosaveStatusLabel.getStyleClass().add("status-error");
            retryAutosaveBtn.setVisible(true);
            retryAutosaveBtn.setManaged(true);
            retryAutosaveBtn.setDisable(isSubmitted || isSubmissionPending);
        } else if (state.inFlight()) {
            autosaveStatusLabel.setText("Saving answer...");
            autosaveStatusLabel.getStyleClass().add("status-warning");
            retryAutosaveBtn.setVisible(false);
            retryAutosaveBtn.setManaged(false);
        } else if (state.acknowledged() != null) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            autosaveStatusLabel.setText("✓ Saved (" + time + ")");
            autosaveStatusLabel.getStyleClass().add("status-success");
            retryAutosaveBtn.setVisible(false);
            retryAutosaveBtn.setManaged(false);
        } else {
            autosaveStatusLabel.setText("");
            retryAutosaveBtn.setVisible(false);
            retryAutosaveBtn.setManaged(false);
        }
    }

    private void updatePalette() {
        palettePane.getChildren().clear();
        int answeredCount = 0;
        int flaggedCount = 0;

        for (int i = 0; i < questions.size(); i++) {
            StudentQuestionDto q = questions.get(i);
            List<Integer> sel = answers.get(q.id());
            boolean answered = sel != null && !sel.isEmpty();
            boolean flagged = flaggedQuestions.contains(q.id());
            QuestionSaveState sState = saveCoordinator.getState(q.id());
            boolean failed = sState != null && sState.failed();

            if (answered) answeredCount++;
            if (flagged) flaggedCount++;

            String marker = failed ? "!" : flagged ? "F" : answered ? "✓" : "";
            Button pBtn = new Button((i + 1) + marker);
            pBtn.getStyleClass().add("navigator-button");
            pBtn.setDisable(isSubmitted || isSubmissionPending);
            pBtn.setAccessibleText("Question " + (i + 1)
                    + (failed ? ", save failed" : flagged ? ", flagged" : answered ? ", answered" : ", unanswered")
                    + (i == currentIndex ? ", current" : ""));

            final int index = i;
            pBtn.setOnAction(e -> {
                currentIndex = index;
                renderCurrentQuestion();
                updatePalette();
            });

            if (flagged) {
                pBtn.getStyleClass().add("navigator-flagged");
            } else if (answered) {
                pBtn.getStyleClass().add("navigator-answered");
            }
            if (failed) pBtn.getStyleClass().add("navigator-failed");
            if (i == currentIndex) pBtn.getStyleClass().add("navigator-current");

            palettePane.getChildren().add(pBtn);
        }

        answeredSummaryLabel.setText(answeredCount + "/" + questions.size() + " Answered"
                + (flaggedCount > 0 ? " • " + flaggedCount + " Flagged" : ""));
    }

    private void confirmAndSubmitExam() {
        if (isSubmitted || isSubmissionPending) return;

        // Check if any question has already failed to save
        Optional<QuestionSaveState> failedOpt = saveCoordinator.getFirstFailed();
        if (failedOpt.isPresent()) {
            QuestionSaveState s = failedOpt.get();
            UiFeedback.showError("Save Failure Pending",
                    "Question " + (s.questionIndex() + 1)
                            + " failed to save to the server. Please review and click '🔄 Retry Save' before submitting.");
            currentIndex = s.questionIndex();
            renderCurrentQuestion();
            updatePalette();
            return;
        }

        isDialogActive = true;
        int answeredCount = 0;
        for (var q : questions) {
            var a = answers.get(q.id());
            if (a != null && !a.isEmpty()) answeredCount++;
        }
        int unanswered = questions.size() - answeredCount;
        int flagged = flaggedQuestions.size();

        StringBuilder content = new StringBuilder();
        content.append("Answered: ").append(answeredCount).append(" of ").append(questions.size()).append(" questions.\n");
        if (unanswered > 0) {
            content.append("⚠️ Warning: You have ").append(unanswered).append(" unanswered question(s)!\n");
        }
        if (flagged > 0) {
            content.append("🚩 Note: You have ").append(flagged).append(" question(s) flagged for review.\n");
        }
        content.append("\nOnce submitted, your answers cannot be modified and your exam attempt will be finalized and graded.");

        boolean confirmed;
        isDialogActive = true;
        try {
            confirmed = UiFeedback.showConfirmation(stage, "Submit Examination", "Are you ready to submit your exam?", content.toString());
        } finally {
            isDialogActive = false;
        }

        if (confirmed) {
            startSubmissionProcess();
        }
    }

    private void startSubmissionProcess() {
        setControlsDisabled(true);

        Optional<QuestionSaveState> failedOpt = saveCoordinator.getFirstFailed();
        if (failedOpt.isPresent()) {
            QuestionSaveState s = failedOpt.get();
            setControlsDisabled(false);
            UiFeedback.showError("Save Failure", "Question " + (s.questionIndex() + 1) + " failed to save. Please retry saving before submitting.");
            currentIndex = s.questionIndex();
            renderCurrentQuestion();
            updatePalette();
            return;
        }

        if (saveCoordinator.isAllSynchronized()) {
            performSubmission();
        } else {
            isSubmissionPending = true;
            submitBtn.setText("Saving answers...");
            autosaveStatusLabel.setText("Synchronizing answers with server before final submit...");
            autosaveStatusLabel.getStyleClass().removeAll("secondary-text", "status-error", "status-success");
            if (!autosaveStatusLabel.getStyleClass().contains("status-warning")) {
                autosaveStatusLabel.getStyleClass().add("status-warning");
            }
        }
    }

    private void checkAndProceedWithPendingSubmission() {
        if (!isSubmissionPending) return;

        Optional<QuestionSaveState> failedOpt = saveCoordinator.getFirstFailed();
        if (failedOpt.isPresent()) {
            QuestionSaveState s = failedOpt.get();
            isSubmissionPending = false;
            setControlsDisabled(false);
            submitBtn.setText("Submit Exam");
            UiFeedback.showError("Save Failure", "Question " + (s.questionIndex() + 1) + " failed to save. Please retry saving before submitting.");
            currentIndex = s.questionIndex();
            renderCurrentQuestion();
            updatePalette();
            return;
        }

        if (saveCoordinator.isAllSynchronized()) {
            isSubmissionPending = false;
            performSubmission();
        }
    }

    private void handleAutoSubmitOnExpiry() {
        cleanupListeners();
        UiFeedback.showError("Exam Time Expired", "The authoritative examination duration has expired. Submitting answers now...");
        startSubmissionProcess();
    }

    private void performSubmission() {
        isSubmitted = true;
        isSubmissionPending = false;
        cleanupListeners();
        setControlsDisabled(true);
        if (stage != null) stage.setFullScreen(false);
        submitBtn.setText("Finalizing...");

        AsyncRunner.runAsync(
                () -> apiClient.submitExam(attempt.id()),
                (Integer score) -> {
                    UiFeedback.showInfo("Examination Completed",
                            "Your exam attempt #" + attempt.id() + " has been successfully submitted and finalized!\n\nFinal Score: " + score + " points.");
                    app.showHistory();
                },
                (Throwable err) -> {
                    setControlsDisabled(false);
                    submitBtn.setText("Submit Exam");
                    if (err instanceof ApiException apiEx && apiEx.isConflict()) {
                        UiFeedback.showInfo("Attempt Finalized", "This exam attempt has already been finalized by the server.");
                        app.showHistory();
                    } else {
                        isSubmitted = false;
                        UiFeedback.showError("Submission Error", "Failed to submit exam: " + err.getMessage());
                    }
                }
        );
    }

    private void setControlsDisabled(boolean disabled) {
        optionsBox.setDisable(disabled);
        clearBtn.setDisable(disabled);
        flagBtn.setDisable(disabled);
        submitBtn.setDisable(disabled);
        retryAutosaveBtn.setDisable(disabled);
        palettePane.setDisable(disabled);
        prevBtn.setDisable(disabled || currentIndex == 0);
        nextBtn.setDisable(disabled || currentIndex == questions.size() - 1);
    }

    private void setViolationBanner(String severityClass, String message) {
        proctorNoticeBanner.getStyleClass().removeAll("violation-warning", "violation-danger");
        proctorNoticeBanner.getStyleClass().add(severityClass);
        proctorNoticeBanner.setText(message);
    }

    private void cleanupListeners() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        if (proctorCoordinator != null) {
            proctorCoordinator.shutdown();
        }
        if (stage != null) {
            if (proctorWindowHiddenHandler != null) {
                stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, proctorWindowHiddenHandler);
            }
            if (focusListener != null) {
                stage.focusedProperty().removeListener(focusListener);
            }
            if (iconifiedListener != null) {
                stage.iconifiedProperty().removeListener(iconifiedListener);
            }
            if (fullScreenListener != null) {
                stage.fullScreenProperty().removeListener(fullScreenListener);
            }
        }
    }
}
