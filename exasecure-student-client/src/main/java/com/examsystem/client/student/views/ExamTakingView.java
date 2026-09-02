package com.examsystem.client.student.views;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.api.ApiException;
import com.examsystem.client.async.AsyncRunner;
import com.examsystem.client.exam.ExamTimer;
import com.examsystem.client.proctor.ScreenCaptureService;
import com.examsystem.client.student.StudentApp;
import com.examsystem.client.ui.UiFeedback;
import com.examsystem.contracts.StudentAttemptDto;
import com.examsystem.contracts.StudentExamDto;
import com.examsystem.contracts.StudentOptionDto;
import com.examsystem.contracts.StudentQuestionDto;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExamTakingView {
    private final StudentApp app;
    private final ApiClient apiClient;
    private final Stage stage;
    private final StudentExamDto exam;
    private final StudentAttemptDto attempt;
    private final List<StudentQuestionDto> questions;
    private final Map<Integer, List<Integer>> answers;

    private final BorderPane view;
    private final Label timerLabel;
    private final Label questionNumberLabel;
    private final Label questionTypeBadge;
    private final Label questionTextLabel;
    private final VBox optionsBox;
    private final Label autosaveStatusLabel;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button clearBtn;
    private final Button submitBtn;
    private final FlowPane palettePane;
    private final Label answeredSummaryLabel;
    private final Label proctorNoticeBanner;

    private int currentIndex = 0;
    private final ExamTimer examTimer;
    private Timeline countdownTimeline;
    private ChangeListener<Boolean> focusListener;
    private boolean isDialogActive = false;
    private boolean isSubmitted = false;
    private long lastProctorEventTime = 0;

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

        this.view = new BorderPane();
        this.timerLabel = new Label();
        this.questionNumberLabel = new Label();
        this.questionTypeBadge = new Label();
        this.questionTextLabel = new Label();
        this.optionsBox = new VBox(12);
        this.autosaveStatusLabel = new Label();
        this.prevBtn = new Button("← Previous");
        this.nextBtn = new Button("Next →");
        this.clearBtn = new Button("Clear Choice");
        this.submitBtn = new Button("Submit Exam");
        this.palettePane = new FlowPane();
        this.answeredSummaryLabel = new Label();
        this.proctorNoticeBanner = new Label();

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
        view.setStyle("-fx-background-color: #f8fafc;");

        // Top Header
        VBox topContainer = new VBox();

        HBox topBar = new HBox(16);
        topBar.setPadding(new Insets(12, 24, 12, 24));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #1e293b;");

        VBox titleBox = new VBox(2);
        Label examTitle = new Label(exam.title());
        examTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        examTitle.setStyle("-fx-text-fill: white;");

        Label attemptBadge = new Label("Attempt #" + attempt.id() + " • Monitored Session");
        attemptBadge.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(examTitle, attemptBadge);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Authoritative Timer Display
        HBox timerBox = new HBox(8);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.setStyle("-fx-background-color: #0f172a; -fx-padding: 6 14; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-radius: 6px;");

        Label timerIcon = new Label("⏱");
        timerIcon.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 16px;");

        timerLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));
        timerLabel.setStyle("-fx-text-fill: #38bdf8;");
        timerBox.getChildren().addAll(timerIcon, timerLabel);

        submitBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        submitBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 6px; -fx-padding: 8 16; -fx-cursor: hand;");
        submitBtn.setOnAction(e -> confirmAndSubmitExam());

        topBar.getChildren().addAll(titleBox, spacer, timerBox, submitBtn);

        // Proctor warning banner (hidden by default)
        proctorNoticeBanner.setVisible(false);
        proctorNoticeBanner.setManaged(false);
        proctorNoticeBanner.setMaxWidth(Double.MAX_VALUE);
        proctorNoticeBanner.setAlignment(Pos.CENTER);
        proctorNoticeBanner.setPadding(new Insets(8));
        proctorNoticeBanner.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #b91c1c; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: #fecaca;");

        topContainer.getChildren().addAll(topBar, proctorNoticeBanner);
        view.setTop(topContainer);

        // Center: Question Content Card
        VBox card = new VBox(20);
        card.setPadding(new Insets(28));
        card.setMaxWidth(760);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 8, 0, 0, 2);");

        HBox questionHeader = new HBox(12);
        questionHeader.setAlignment(Pos.CENTER_LEFT);

        questionNumberLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        questionNumberLabel.setStyle("-fx-text-fill: #0f172a;");

        questionTypeBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4px;");

        Region qSpacer = new Region();
        HBox.setHgrow(qSpacer, Priority.ALWAYS);

        autosaveStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        questionHeader.getChildren().addAll(questionNumberLabel, questionTypeBadge, qSpacer, autosaveStatusLabel);

        questionTextLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        questionTextLabel.setStyle("-fx-text-fill: #1e293b; -fx-line-spacing: 4px;");
        questionTextLabel.setWrapText(true);

        optionsBox.setPadding(new Insets(10, 0, 10, 0));

        // Bottom Navigation
        HBox navBar = new HBox(12);
        navBar.setAlignment(Pos.CENTER_LEFT);

        prevBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-background-radius: 6px; -fx-padding: 8 16; -fx-cursor: hand;");
        prevBtn.setOnAction(e -> {
            if (currentIndex > 0) {
                currentIndex--;
                renderCurrentQuestion();
                updatePalette();
            }
        });

        clearBtn.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-text-fill: #64748b; -fx-background-radius: 6px; -fx-border-radius: 6px; -fx-padding: 8 12; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> clearCurrentAnswer());

        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);

        nextBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 6px; -fx-padding: 8 18; -fx-cursor: hand; -fx-font-weight: bold;");
        nextBtn.setOnAction(e -> {
            if (currentIndex < questions.size() - 1) {
                currentIndex++;
                renderCurrentQuestion();
                updatePalette();
            }
        });

        navBar.getChildren().addAll(prevBtn, clearBtn, navSpacer, nextBtn);

        card.getChildren().addAll(questionHeader, questionTextLabel, optionsBox, navBar);

        ScrollPane centerScroll = new ScrollPane(card);
        centerScroll.setFitToWidth(true);
        centerScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 24;");
        view.setCenter(centerScroll);

        // Right Sidebar: Question Palette
        VBox rightSidebar = new VBox(16);
        rightSidebar.setPadding(new Insets(24));
        rightSidebar.setPrefWidth(260);
        rightSidebar.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 0 1;");

        Label paletteTitle = new Label("Question Navigator");
        paletteTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        paletteTitle.setStyle("-fx-text-fill: #0f172a;");

        answeredSummaryLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        palettePane.setHgap(8);
        palettePane.setVgap(8);

        HBox legend = new HBox(12);
        Label greenDot = new Label("● Answered");
        greenDot.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 11px;");
        Label grayDot = new Label("○ Unanswered");
        grayDot.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        legend.getChildren().addAll(greenDot, grayDot);

        rightSidebar.getChildren().addAll(paletteTitle, answeredSummaryLabel, palettePane, legend);
        view.setRight(rightSidebar);
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
        if (remaining <= 60) {
            timerLabel.setStyle("-fx-text-fill: #ef4444;"); // Red critical
        } else if (remaining <= 300) {
            timerLabel.setStyle("-fx-text-fill: #f59e0b;"); // Amber warning
        } else {
            timerLabel.setStyle("-fx-text-fill: #38bdf8;"); // Blue normal
        }
    }

    private void setupProctoring() {
        if (stage == null) return;
        focusListener = (obs, oldVal, newVal) -> {
            if (!newVal && !isDialogActive && !isSubmitted) {
                handleFocusLossViolation();
            }
        };
        stage.focusedProperty().addListener(focusListener);
    }

    private void handleFocusLossViolation() {
        long now = System.currentTimeMillis();
        if (now - lastProctorEventTime < 3000) {
            return; // Debounce
        }
        lastProctorEventTime = now;

        proctorNoticeBanner.setText("⚠ Focus loss detected! An automated evidence screenshot has been captured and submitted.");
        proctorNoticeBanner.setVisible(true);
        proctorNoticeBanner.setManaged(true);

        byte[] screenshot = ScreenCaptureService.captureScreenAsJpeg();
        AsyncRunner.runAsync(
                () -> {
                    int eventId = apiClient.recordProctorEvent(attempt.id(), "FOCUS_LOST");
                    apiClient.uploadProctorEvidence(attempt.id(), eventId, screenshot, "image/jpeg");
                    return eventId;
                },
                (Integer eventId) -> {
                    // Successfully recorded
                },
                (Throwable err) -> {
                    System.err.println("[PROCTOR] Failed to upload focus loss evidence: " + err.getMessage());
                }
        );
    }

    private void renderCurrentQuestion() {
        if (questions == null || questions.isEmpty()) return;

        StudentQuestionDto q = questions.get(currentIndex);
        questionNumberLabel.setText("Question " + (currentIndex + 1) + " of " + questions.size());
        boolean isMulti = "MULTI".equalsIgnoreCase(q.type());
        questionTypeBadge.setText(isMulti ? "MULTIPLE CHOICE (Select all that apply)" : "SINGLE CHOICE");
        questionTextLabel.setText(q.text());

        prevBtn.setDisable(currentIndex == 0);
        nextBtn.setDisable(currentIndex == questions.size() - 1);

        optionsBox.getChildren().clear();
        List<Integer> selectedOptions = answers.getOrDefault(q.id(), new ArrayList<>());

        ToggleGroup singleGroup = isMulti ? null : new ToggleGroup();

        for (StudentOptionDto opt : q.options()) {
            HBox optRow = new HBox(10);
            optRow.setAlignment(Pos.CENTER_LEFT);
            optRow.setPadding(new Insets(10, 14, 10, 14));
            optRow.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 6px; -fx-border-color: #e2e8f0; -fx-border-radius: 6px;");

            if (isMulti) {
                CheckBox cb = new CheckBox(opt.optionNumber() + ". " + opt.text());
                cb.setFont(Font.font("Segoe UI", 14));
                cb.setSelected(selectedOptions.contains(opt.optionNumber()));
                cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    List<Integer> current = answers.computeIfAbsent(q.id(), k -> new ArrayList<>());
                    if (newVal && !current.contains(opt.optionNumber())) {
                        current.add(opt.optionNumber());
                    } else if (!newVal) {
                        current.remove(Integer.valueOf(opt.optionNumber()));
                    }
                    triggerAutosave(q.id(), current);
                    updatePalette();
                });
                optRow.getChildren().add(cb);
            } else {
                RadioButton rb = new RadioButton(opt.optionNumber() + ". " + opt.text());
                rb.setFont(Font.font("Segoe UI", 14));
                rb.setToggleGroup(singleGroup);
                if (selectedOptions.contains(opt.optionNumber())) {
                    rb.setSelected(true);
                }
                rb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        List<Integer> current = List.of(opt.optionNumber());
                        answers.put(q.id(), new ArrayList<>(current));
                        triggerAutosave(q.id(), current);
                        updatePalette();
                    }
                });
                optRow.getChildren().add(rb);
            }

            optionsBox.getChildren().add(optRow);
        }
    }

    private void clearCurrentAnswer() {
        StudentQuestionDto q = questions.get(currentIndex);
        answers.remove(q.id());
        triggerAutosave(q.id(), List.of());
        renderCurrentQuestion();
        updatePalette();
    }

    private void triggerAutosave(int questionId, List<Integer> selected) {
        autosaveStatusLabel.setText("Saving answer...");
        autosaveStatusLabel.setTextFill(Color.web("#d97706"));

        AsyncRunner.runAsync(
                () -> {
                    apiClient.saveAnswer(attempt.id(), questionId, selected);
                    return true;
                },
                (Boolean ok) -> {
                    String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    autosaveStatusLabel.setText("✓ Saved (" + time + ")");
                    autosaveStatusLabel.setTextFill(Color.web("#16a34a"));
                },
                (Throwable err) -> {
                    autosaveStatusLabel.setText("✗ Autosave failed: " + err.getMessage());
                    autosaveStatusLabel.setTextFill(Color.web("#dc2626"));
                }
        );
    }

    private void updatePalette() {
        palettePane.getChildren().clear();
        int answeredCount = 0;

        for (int i = 0; i < questions.size(); i++) {
            StudentQuestionDto q = questions.get(i);
            List<Integer> sel = answers.get(q.id());
            boolean answered = sel != null && !sel.isEmpty();
            if (answered) answeredCount++;

            Button pBtn = new Button(String.valueOf(i + 1));
            pBtn.setPrefSize(38, 38);
            pBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

            final int index = i;
            pBtn.setOnAction(e -> {
                currentIndex = index;
                renderCurrentQuestion();
                updatePalette();
            });

            if (i == currentIndex) {
                pBtn.setStyle("-fx-background-color: " + (answered ? "#16a34a" : "#e2e8f0")
                        + "; -fx-text-fill: " + (answered ? "white" : "#0f172a")
                        + "; -fx-border-color: #2563eb; -fx-border-width: 2.5px; -fx-border-radius: 4px; -fx-cursor: hand;");
            } else {
                pBtn.setStyle("-fx-background-color: " + (answered ? "#16a34a" : "#f1f5f9")
                        + "; -fx-text-fill: " + (answered ? "white" : "#334155")
                        + "; -fx-border-color: #cbd5e1; -fx-border-radius: 4px; -fx-cursor: hand;");
            }

            palettePane.getChildren().add(pBtn);
        }

        answeredSummaryLabel.setText(answeredCount + " of " + questions.size() + " answered");
    }

    private void confirmAndSubmitExam() {
        isDialogActive = true;
        int answeredCount = 0;
        for (StudentQuestionDto q : questions) {
            List<Integer> sel = answers.get(q.id());
            if (sel != null && !sel.isEmpty()) answeredCount++;
        }
        int unanswered = questions.size() - answeredCount;

        String content = "Answered: " + answeredCount + " of " + questions.size() + " questions.\n";
        if (unanswered > 0) {
            content += "Warning: You have " + unanswered + " unanswered question(s).\n\n";
        }
        content += "Once submitted, your answers cannot be modified and your exam attempt will be finalized and graded.";

        boolean confirmed = UiFeedback.showConfirmation("Submit Examination", "Are you ready to submit your exam?", content);
        isDialogActive = false;

        if (confirmed) {
            performSubmission();
        }
    }

    private void handleAutoSubmitOnExpiry() {
        isSubmitted = true;
        cleanupListeners();
        UiFeedback.showError("Exam Time Expired", "The authoritative examination duration has expired. Submitting answers now...");
        performSubmission();
    }

    private void performSubmission() {
        isSubmitted = true;
        cleanupListeners();
        submitBtn.setDisable(true);
        submitBtn.setText("Submitting...");

        AsyncRunner.runAsync(
                () -> apiClient.submitExam(attempt.id()),
                (Integer score) -> {
                    UiFeedback.showInfo("Examination Completed",
                            "Your exam attempt #" + attempt.id() + " has been successfully submitted and finalized!\n\nFinal Score: " + score + " points.");
                    app.showHistory();
                },
                (Throwable err) -> {
                    submitBtn.setDisable(false);
                    submitBtn.setText("Submit Exam");
                    if (err instanceof ApiException apiEx && apiEx.isConflict()) {
                        UiFeedback.showInfo("Attempt Finalized", "This exam attempt has already been finalized by the server.");
                        app.showHistory();
                    } else {
                        UiFeedback.showError("Submission Error", "Failed to submit exam: " + err.getMessage());
                    }
                }
        );
    }

    private void cleanupListeners() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        if (stage != null && focusListener != null) {
            stage.focusedProperty().removeListener(focusListener);
        }
    }
}
