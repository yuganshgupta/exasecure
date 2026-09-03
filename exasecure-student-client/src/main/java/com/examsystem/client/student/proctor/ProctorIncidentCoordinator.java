package com.examsystem.client.student.proctor;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.proctor.ScreenCaptureService;
import com.examsystem.client.proctor.ScreenCapturer;
import com.examsystem.contracts.StudentProctorEventResponseDto;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
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
    private final Clock clock;
    private final Consumer<StudentProctorEventResponseDto> onViolationCallback;
    private final Runnable onTerminationCallback;
    private final long initialDelayMs;
    private final long longAbsenceDelayMs;
    private final boolean ownsScheduler;

    private boolean isActive = true;
    private boolean isFocused = true;
    private long currentIncidentGeneration = 0;

    private Instant activeLostAt = null;
    private CompletableFuture<Integer> activeEventIdFuture = null;
    private ScheduledFuture<?> initialTask = null;
    private ScheduledFuture<?> longAbsenceTask = null;

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
        }

        // 3. Dispatch authoritative FOCUS_LOST event at T = 0 outside synchronization
        String clientEventId = "focus-" + attemptId + "-" + generation + "-" + lostAt.toEpochMilli();
        scheduler.execute(() -> {
            try {
                StudentProctorEventResponseDto response = apiClient.recordProctorEvent(attemptId, "FOCUS_LOST", clientEventId);
                eventIdFuture.complete(response.eventId());

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
            } catch (Exception e) {
                eventIdFuture.completeExceptionally(e);
            }
        });
    }

    public void onFocusGained() {
        long generation;
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

            generation = currentIncidentGeneration;
            lostAt = activeLostAt;
            returnedAt = clock.instant();
            durationMs = lostAt != null ? Math.max(0, Duration.between(lostAt, returnedAt).toMillis()) : 0;
            eventIdFuture = activeEventIdFuture;
        }

        if (eventIdFuture != null) {
            eventIdFuture.thenAccept(eventId -> {
                if (eventId != null && eventId > 0) {
                    try {
                        apiClient.recordProctorReturn(attemptId, eventId, returnedAt, durationMs);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void handleInitialDeadline(long generation, Instant lostAt, CompletableFuture<Integer> eventIdFuture) {
        synchronized (this) {
            if (generation != currentIncidentGeneration || isFocused || !isActive) {
                return;
            }
        }

        // Capture physical screen at T ≈ 800ms
        byte[] screenshot = null;
        try {
            screenshot = captureService.capture();
        } catch (Exception ignored) {}

        if (screenshot != null && screenshot.length > 0) {
            final byte[] data = screenshot;
            eventIdFuture.thenAccept(eventId -> {
                if (eventId != null && eventId > 0 && isActive) {
                    try {
                        apiClient.uploadProctorEvidence(attemptId, eventId, data, "image/jpeg", "INITIAL");
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void handleLongAbsenceDeadline(long generation, Instant lostAt, CompletableFuture<Integer> eventIdFuture) {
        synchronized (this) {
            if (generation != currentIncidentGeneration || isFocused || !isActive) {
                return;
            }
        }

        // Authoritative LONG_ABSENCE metadata decoupled from screenshot
        eventIdFuture.thenAccept(eventId -> {
            if (eventId != null && eventId > 0 && isActive) {
                try {
                    apiClient.recordProctorLongAbsence(attemptId, eventId);
                } catch (Exception ignored) {}
            }
        });

        // Capture supporting long-absence screenshot
        byte[] screenshot = null;
        try {
            screenshot = captureService.capture();
        } catch (Exception ignored) {}

        if (screenshot != null && screenshot.length > 0) {
            final byte[] data = screenshot;
            eventIdFuture.thenAccept(eventId -> {
                if (eventId != null && eventId > 0 && isActive) {
                    try {
                        apiClient.uploadProctorEvidence(attemptId, eventId, data, "image/jpeg", "LONG_ABSENCE");
                    } catch (Exception ignored) {}
                }
            });
        }
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
    }

    public synchronized void shutdown() {
        isActive = false;
        cancelPendingTasks();
        if (ownsScheduler && scheduler != null) {
            scheduler.shutdownNow();
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
