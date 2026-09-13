package com.examsystem.client.student.proctor;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.proctor.ScreenCaptureService;
import com.examsystem.client.proctor.ScreenCapturer;
import com.examsystem.contracts.StudentProctorEventResponseDto;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Orchestrates delayed supporting evidence capture for proctoring focus-loss incidents.
 *
 * Invariant: One focus departure = ONE incident = ONE strike.
 * Supporting screenshots (Initial ~800ms, Long-Absence ~5000ms) and return telemetry
 * attach to the same incident and never increment the strike count.
 */
public class ProctorIncidentCoordinator {
    private final int attemptId;
    private final ApiClient apiClient;
    private final ScreenCapturer captureService;
    private final ScheduledExecutorService scheduler;
    // Fixed, separate lanes: ordered incident/telemetry HTTP, screen capture, and uploads.
    // No blocking work or future completion may run on the deadline scheduler.
    private final ExecutorService networkExecutor;
    private final ExecutorService captureExecutor;
    private final ExecutorService evidenceExecutor;
    private final boolean ownsWorkers;
    private final Clock clock;
    private final Consumer<StudentProctorEventResponseDto> onViolationCallback;
    private final Runnable onTerminationCallback;
    private final long initialDelayMs;
    private final long longAbsenceDelayMs;
    private final boolean ownsScheduler;

    private boolean isActive = true;
    private boolean closed;
    private boolean isFocused = true;
    private long currentIncidentGeneration = 0;

    private Instant activeLostAt = null;
    private CompletableFuture<Integer> activeEventIdFuture = null;
    private ScheduledFuture<?> initialTask = null;
    private ScheduledFuture<?> longAbsenceTask = null;
    private ScheduledFuture<?> longAbsenceRetryTask = null;

    private static final int MAX_LONG_ABSENCE_RETRIES = 3;
    private static final long LONG_ABSENCE_RETRY_DELAY_MS = 250;

    public ProctorIncidentCoordinator(
            int attemptId,
            ApiClient apiClient,
            ScreenCapturer captureService,
            ScheduledExecutorService scheduler,
            Clock clock,
            Consumer<StudentProctorEventResponseDto> onViolationCallback,
            Runnable onTerminationCallback,
            long initialDelayMs,
            long longAbsenceDelayMs,
            boolean ownsScheduler
    ) {
        this(attemptId, apiClient, captureService, scheduler, clock, onViolationCallback,
                onTerminationCallback, initialDelayMs, longAbsenceDelayMs, ownsScheduler,
                newWorker("proctor-network"), newWorker("proctor-capture"),
                newWorker("proctor-evidence"), true);
    }

    // Tests can supply deterministic workers independently of the deadline scheduler.
    ProctorIncidentCoordinator(
            int attemptId, ApiClient apiClient, ScreenCapturer captureService,
            ScheduledExecutorService scheduler, Clock clock,
            Consumer<StudentProctorEventResponseDto> onViolationCallback, Runnable onTerminationCallback,
            long initialDelayMs, long longAbsenceDelayMs, boolean ownsScheduler,
            ExecutorService networkExecutor, ExecutorService captureExecutor,
            ExecutorService evidenceExecutor, boolean ownsWorkers
    ) {
        this.attemptId = attemptId;
        this.apiClient = apiClient;
        this.captureService = captureService != null ? captureService : new ScreenCaptureService();
        this.scheduler = scheduler;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.onViolationCallback = onViolationCallback;
        this.onTerminationCallback = onTerminationCallback;
        this.initialDelayMs = initialDelayMs;
        this.longAbsenceDelayMs = longAbsenceDelayMs;
        this.ownsScheduler = ownsScheduler;
        this.networkExecutor = networkExecutor;
        this.captureExecutor = captureExecutor;
        this.evidenceExecutor = evidenceExecutor;
        this.ownsWorkers = ownsWorkers;
    }

    private static ExecutorService newWorker(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    private synchronized void dispatch(ExecutorService executor, Runnable work) {
        if (!closed) {
            executor.execute(() -> {
                synchronized (this) {
                    if (closed) return;
                }
                work.run();
            });
        }
    }

    private synchronized boolean isEligible(long generation) {
        return generation == currentIncidentGeneration && !isFocused && isActive;
    }

    public ProctorIncidentCoordinator(
            int attemptId,
            ApiClient apiClient,
            Consumer<StudentProctorEventResponseDto> onViolationCallback,
            Runnable onTerminationCallback
    ) {
        this(
                attemptId,
                apiClient,
                new ScreenCaptureService(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "proctor-incident-coordinator");
                    t.setDaemon(true);
                    return t;
                }),
                Clock.systemUTC(),
                onViolationCallback,
                onTerminationCallback,
                800L,
                5000L,
                true
        );
    }

    public void onFocusLost() {
        long generation;
        Instant lostAt;
        CompletableFuture<Integer> eventIdFuture;

        synchronized (this) {
            if (!isActive || !isFocused) {
                return;
            }
            isFocused = false;
            generation = ++currentIncidentGeneration;
            lostAt = clock.instant();

            eventIdFuture = new CompletableFuture<>();
            activeEventIdFuture = eventIdFuture;
            activeLostAt = lostAt;

            // 1. Schedule Initial Capture at ~800ms from local focus loss
            initialTask = scheduler.schedule(() -> {
                handleInitialDeadline(generation, lostAt, eventIdFuture);
            }, initialDelayMs, TimeUnit.MILLISECONDS);

            // 2. Schedule Long-Absence Deadline at ~5000ms from local focus loss
            longAbsenceTask = scheduler.schedule(() -> {
                handleLongAbsenceDeadline(generation, lostAt, eventIdFuture);
            }, longAbsenceDelayMs, TimeUnit.MILLISECONDS);
            // 3. Enqueue under the state lock to preserve departure order, even across callers.
            String clientEventId = "focus-" + attemptId + "-" + generation + "-" + lostAt.toEpochMilli();
            dispatch(networkExecutor, () -> {
                try {
                    StudentProctorEventResponseDto response = apiClient.recordProctorEvent(attemptId, "FOCUS_LOST", clientEventId);
                    synchronized (ProctorIncidentCoordinator.this) {
                        if (generation == currentIncidentGeneration && isActive) {
                            if (onViolationCallback != null) {
                                onViolationCallback.accept(response);
                            }
                            if (response.flagged() || response.violationCount() >= 3) {
                                cancelPendingTasks();
                                isActive = false;
                                if (onTerminationCallback != null) {
                                    onTerminationCallback.run();
                                }
                            }
                        }
                    }
                    // Continuations only enqueue work; a slow upload cannot delay the strike callback.
                    eventIdFuture.complete(response.eventId());
                } catch (Exception e) {
                    eventIdFuture.completeExceptionally(e);
                }
            });
        }
    }

    public void onFocusGained() {
        Instant lostAt;
        Instant returnedAt;
        long durationMs;
        CompletableFuture<Integer> eventIdFuture;

        synchronized (this) {
            if (!isActive || isFocused) {
                return;
            }
            isFocused = true;
            cancelPendingTasks();

            lostAt = activeLostAt;
            returnedAt = clock.instant();
            durationMs = lostAt != null ? Math.max(0, Duration.between(lostAt, returnedAt).toMillis()) : 0;
            eventIdFuture = activeEventIdFuture;
        }

        if (eventIdFuture != null) {
            eventIdFuture.thenAccept(eventId -> dispatch(networkExecutor, () -> {
                // Return belongs to its original event even if a newer departure has started.
                if (eventId != null && eventId > 0) {
                    try {
                        apiClient.recordProctorReturn(attemptId, eventId, returnedAt, durationMs);
                    } catch (Exception ignored) {}
                }
            }));
        }
    }

    private void handleInitialDeadline(long generation, Instant lostAt, CompletableFuture<Integer> eventIdFuture) {
        if (isEligible(generation)) {
            dispatch(captureExecutor, () -> captureEvidence(generation, eventIdFuture, "INITIAL"));
        }
    }

    private void handleLongAbsenceDeadline(long generation, Instant lostAt, CompletableFuture<Integer> eventIdFuture) {
        if (!isEligible(generation)) return;

        // Authoritative LONG_ABSENCE metadata decoupled from screenshot with bounded retry
        eventIdFuture.thenAccept(eventId -> {
            if (eventId != null && eventId > 0) {
                dispatch(networkExecutor, () -> requestLongAbsenceWithRetry(generation, eventId, 0));
            }
        });
        dispatch(captureExecutor, () -> captureEvidence(generation, eventIdFuture, "LONG_ABSENCE"));
    }

    private void captureEvidence(long generation, CompletableFuture<Integer> eventIdFuture, String type) {
        if (!isEligible(generation)) return;
        byte[] screenshot = null;
        try {
            screenshot = captureService.capture();
        } catch (Exception ignored) {}

        // A return/new incident during physical capture invalidates those bytes.
        if (screenshot != null && screenshot.length > 0 && isEligible(generation)) {
            final byte[] data = screenshot;
            eventIdFuture.thenAccept(eventId -> dispatch(evidenceExecutor, () -> {
                // Cancellation also covers time waiting for an ID or behind an older upload.
                if (eventId != null && eventId > 0 && isEligible(generation)) {
                    try {
                        apiClient.uploadProctorEvidence(attemptId, eventId, data, "image/jpeg", type);
                    } catch (Exception ignored) {}
                }
            }));
        }
    }

    private void requestLongAbsenceWithRetry(long generation, int eventId, int retryCount) {
        synchronized (this) {
            if (generation != currentIncidentGeneration || isFocused || !isActive) {
                return;
            }
        }

        try {
            apiClient.recordProctorLongAbsence(attemptId, eventId);
        } catch (com.examsystem.client.api.ApiException e) {
            if (e.getStatusCode() == 400
                    && "PROCTOR_LONG_ABSENCE_TOO_EARLY".equals(e.getErrorCode())
                    && retryCount < MAX_LONG_ABSENCE_RETRIES) {
                // Server indicated threshold not yet reached (e.g. network latency on event creation).
                // Schedule bounded retry while same incident generation remains active and unfocused.
                synchronized (this) {
                    if (isEligible(generation)) {
                        longAbsenceRetryTask = scheduler.schedule(
                                () -> dispatch(networkExecutor,
                                        () -> requestLongAbsenceWithRetry(generation, eventId, retryCount + 1)),
                                LONG_ABSENCE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private synchronized void cancelPendingTasks() {
        if (initialTask != null) {
            initialTask.cancel(false);
            initialTask = null;
        }
        if (longAbsenceTask != null) {
            longAbsenceTask.cancel(false);
            longAbsenceTask = null;
        }
        if (longAbsenceRetryTask != null) {
            longAbsenceRetryTask.cancel(false);
            longAbsenceRetryTask = null;
        }
    }

    public synchronized void shutdown() {
        closed = true;
        isActive = false;
        cancelPendingTasks();
        if (ownsScheduler && scheduler != null) {
            scheduler.shutdownNow();
        }
        if (ownsWorkers) {
            networkExecutor.shutdownNow();
            captureExecutor.shutdownNow();
            evidenceExecutor.shutdownNow();
        }
    }

    public synchronized boolean isFocused() {
        return isFocused;
    }

    public synchronized boolean isActive() {
        return isActive;
    }

    public synchronized long getCurrentIncidentGeneration() {
        return currentIncidentGeneration;
    }
}
