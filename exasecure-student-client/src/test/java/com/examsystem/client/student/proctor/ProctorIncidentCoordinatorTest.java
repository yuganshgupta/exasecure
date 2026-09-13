package com.examsystem.client.student.proctor;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.config.ServerConfig;
import com.examsystem.client.proctor.ScreenCapturer;
import com.examsystem.client.session.SessionState;
import com.examsystem.contracts.StudentProctorEventResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProctorIncidentCoordinatorTest {

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-04T10:00:00Z");

        void advanceMs(long ms) {
            this.now = this.now.plusMillis(ms);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static class TestVirtualScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        long currentTimeMs = 0;

        static class ScheduledJob implements ScheduledFuture<Void> {
            final Runnable task;
            final long triggerTimeMs;
            boolean cancelled = false;
            boolean done = false;

            ScheduledJob(Runnable task, long triggerTimeMs) {
                this.task = task;
                this.triggerTimeMs = triggerTimeMs;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return unit.convert(triggerTimeMs, TimeUnit.MILLISECONDS);
            }

            @Override
            public int compareTo(Delayed o) {
                if (o instanceof ScheduledJob other) {
                    return Long.compare(this.triggerTimeMs, other.triggerTimeMs);
                }
                return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                this.cancelled = true;
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                return done || cancelled;
            }

            @Override
            public Void get() { return null; }

            @Override
            public Void get(long timeout, TimeUnit unit) { return null; }
        }

        final PriorityQueue<ScheduledJob> queue = new PriorityQueue<>(Comparator.comparingLong(j -> j.triggerTimeMs));

        void advanceTime(long ms) {
            currentTimeMs += ms;
            while (!queue.isEmpty() && queue.peek().triggerTimeMs <= currentTimeMs) {
                ScheduledJob job = queue.poll();
                if (!job.cancelled) {
                    job.done = true;
                    job.task.run();
                }
            }
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            long trigger = currentTimeMs + unit.toMillis(delay);
            ScheduledJob job = new ScheduledJob(command, trigger);
            queue.add(job);
            return job;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() { return List.of(); }

        @Override
        public boolean isShutdown() { return false; }

        @Override
        public boolean isTerminated() { return false; }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    static class MockCaptureService implements ScreenCapturer {
        int captureCount = 0;
        byte[] payload = new byte[]{1, 2, 3, 4};

        @Override
        public byte[] capture() {
            captureCount++;
            return payload;
        }
    }

    record RecordedEventCall(int attemptId, String eventType, String clientEventId) {}
    record RecordedEvidenceCall(int attemptId, int eventId, byte[] bytes, String contentType, String evidenceType) {}
    record RecordedReturnCall(int attemptId, int eventId, Instant returnedAt, long awayDurationMs) {}
    record RecordedLongAbsenceCall(int attemptId, int eventId) {}

    private MutableClock clock;
    private TestVirtualScheduler scheduler;
    private MockCaptureService captureService;
    private List<RecordedEventCall> recordedEvents;
    private List<RecordedEvidenceCall> recordedEvidences;
    private List<RecordedReturnCall> recordedReturns;
    private List<RecordedLongAbsenceCall> recordedLongAbsences;
    private List<StudentProctorEventResponseDto> violationCallbacks;
    private AtomicBoolean terminationCallbackFired;
    private AtomicInteger nextEventId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        scheduler = new TestVirtualScheduler();
        captureService = new MockCaptureService();
        recordedEvents = new ArrayList<>();
        recordedEvidences = new ArrayList<>();
        recordedReturns = new ArrayList<>();
        recordedLongAbsences = new ArrayList<>();
        violationCallbacks = new ArrayList<>();
        terminationCallbackFired = new AtomicBoolean(false);
        nextEventId = new AtomicInteger(100);
    }

    private ProctorIncidentCoordinator createCoordinator(ApiClient mockApiClient) {
        return new ProctorIncidentCoordinator(
                1,
                mockApiClient,
                captureService,
                scheduler,
                clock,
                violationCallbacks::add,
                () -> terminationCallbackFired.set(true),
                800L,
                5000L,
                false,
                scheduler, scheduler, scheduler, false
        );
    }

    private ProctorIncidentCoordinator retryCoordinator(int status, String errorCode) {
        return createCoordinator(new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String type, String key) {
                recordedEvents.add(new RecordedEventCall(attemptId, type, key));
                return new StudentProctorEventResponseDto(nextEventId.getAndIncrement(), recordedEvents.size(), false, "ACTIVE");
            }
            @Override public void uploadProctorEvidence(int attemptId, int eventId, byte[] bytes, String contentType, String type) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, bytes, contentType, type));
            }
            @Override public void recordProctorReturn(int attemptId, int eventId, Instant returnedAt, long duration) {
                recordedReturns.add(new RecordedReturnCall(attemptId, eventId, returnedAt, duration));
            }
            @Override public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
                // Deliberately identical message for every error: messages must not drive retries.
                throw new com.examsystem.client.api.ApiException(status, "Threshold not yet met", errorCode);
            }
        });
    }

    private void advance(long millis) {
        clock.advanceMs(millis);
        scheduler.advanceTime(millis);
    }

    private void reachLongAbsence(ProctorIncidentCoordinator coordinator) {
        coordinator.onFocusLost();
        advance(800);
        advance(4200);
        assertThat(recordedLongAbsences).hasSize(1);
    }

    private void assertOneIncidentAndTwoCaptures() {
        assertThat(recordedEvents).hasSize(1);
        assertThat(violationCallbacks).hasSize(1);
        assertThat(violationCallbacks.getFirst().violationCount()).isEqualTo(1);
        assertThat(captureService.captureCount).isEqualTo(2);
        assertThat(recordedEvidences).hasSize(2);
    }

    private void assertNoThresholdRetry(int status, String code) {
        var coordinator = retryCoordinator(status, code);
        reachLongAbsence(coordinator);
        for (int i = 0; i < 10; i++) advance(250);
        assertThat(recordedLongAbsences).hasSize(1);
        assertOneIncidentAndTwoCaptures();
        coordinator.shutdown();
    }

    @Test void unrelated400DoesNotRetry() { assertNoThresholdRetry(400, "INVALID_REQUEST"); }
    @Test void returnedBelowThresholdDoesNotRetry() { assertNoThresholdRetry(400, "PROCTOR_LONG_ABSENCE_RETURNED_BELOW_THRESHOLD"); }
    @Test void unknownErrorCodeDoesNotRetry() { assertNoThresholdRetry(400, "FUTURE_SERVER_CODE"); }
    @Test void missingErrorCodeDoesNotRetry() { assertNoThresholdRetry(400, null); }
    @Test void forbiddenDoesNotRetryEvenWithTooEarlyCode() { assertNoThresholdRetry(403, "PROCTOR_LONG_ABSENCE_TOO_EARLY"); }
    @Test void notFoundDoesNotRetryEvenWithTooEarlyCode() { assertNoThresholdRetry(404, "PROCTOR_LONG_ABSENCE_TOO_EARLY"); }
    @Test void conflictDoesNotRetryEvenWithTooEarlyCode() { assertNoThresholdRetry(409, "PROCTOR_LONG_ABSENCE_TOO_EARLY"); }

    @Test void tooEarlyRetriesExactlyThreeTimesAt250msWithoutExtraIncidentOrCapture() {
        var coordinator = retryCoordinator(400, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
        reachLongAbsence(coordinator);
        for (int i = 1; i <= 3; i++) {
            advance(249);
            assertThat(recordedLongAbsences).hasSize(i);
            advance(1);
            assertThat(recordedLongAbsences).hasSize(i + 1);
        }
        advance(10000);
        assertThat(recordedLongAbsences).hasSize(4).allSatisfy(call -> assertThat(call.eventId()).isEqualTo(100));
        assertOneIncidentAndTwoCaptures();
        coordinator.shutdown();
    }

    @Test void focusRegainCancelsThresholdRetryIncludingAlreadyDequeuedTask() {
        var coordinator = retryCoordinator(400, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
        reachLongAbsence(coordinator);
        var retry = scheduler.queue.peek();
        coordinator.onFocusGained();
        assertThat(retry.isCancelled()).isTrue();
        retry.task.run(); // Simulate cancellation racing with a task already dequeued.
        advance(10000);
        assertThat(recordedLongAbsences).hasSize(1);
        assertOneIncidentAndTwoCaptures();
        coordinator.shutdown();
    }

    @Test void shutdownCancelsThresholdRetryIncludingAlreadyDequeuedTask() {
        var coordinator = retryCoordinator(400, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
        reachLongAbsence(coordinator);
        var retry = scheduler.queue.peek();
        coordinator.shutdown();
        assertThat(retry.isCancelled()).isTrue();
        retry.task.run();
        advance(10000);
        assertThat(recordedLongAbsences).hasSize(1);
        assertOneIncidentAndTwoCaptures();
    }

    @Test void oldGenerationRetryCannotAffectNewIncident() {
        var coordinator = retryCoordinator(400, "PROCTOR_LONG_ABSENCE_TOO_EARLY");
        reachLongAbsence(coordinator);
        var retry = scheduler.queue.peek();
        coordinator.onFocusGained();
        coordinator.onFocusLost();
        assertThat(coordinator.getCurrentIncidentGeneration()).isEqualTo(2);
        retry.task.run();
        assertThat(recordedLongAbsences).hasSize(1);
        assertThat(captureService.captureCount).isEqualTo(2);
        advance(800);
        advance(4200);
        assertThat(recordedLongAbsences).hasSize(2);
        assertThat(recordedLongAbsences.getLast().eventId()).isEqualTo(101);
        assertThat(recordedEvents).hasSize(2);
        assertThat(violationCallbacks).hasSize(2);
        assertThat(captureService.captureCount).isEqualTo(4);
        coordinator.shutdown();
    }

    @Test
    void quickReturn_recordsOneViolation_cancelsPendingScreenshots() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(nextEventId.getAndIncrement(), 1, false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorReturn(int attemptId, int eventId, Instant returnedAt, long awayDurationMs) {
                recordedReturns.add(new RecordedReturnCall(attemptId, eventId, returnedAt, awayDurationMs));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        // Student leaves
        coordinator.onFocusLost();
        assertThat(recordedEvents).hasSize(1);
        assertThat(recordedEvents.get(0).eventType()).isEqualTo("FOCUS_LOST");
        assertThat(violationCallbacks).hasSize(1);
        assertThat(violationCallbacks.get(0).violationCount()).isEqualTo(1);

        // Student returns 300ms later (before 800ms)
        clock.advanceMs(300);
        scheduler.advanceTime(300);
        coordinator.onFocusGained();

        // Advance past 800ms and 5000ms
        clock.advanceMs(6000);
        scheduler.advanceTime(6000);

        // Invariants: exactly 1 strike, 0 screenshots, away duration recorded
        assertThat(captureService.captureCount).isEqualTo(0);
        assertThat(recordedEvidences).isEmpty();
        assertThat(recordedLongAbsences).isEmpty();
        assertThat(recordedReturns).hasSize(1);
        assertThat(recordedReturns.get(0).awayDurationMs()).isEqualTo(300);
    }

    @Test
    void standardAbsence_capturesInitial_noLongAbsence() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(nextEventId.getAndIncrement(), 1, false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorReturn(int attemptId, int eventId, Instant returnedAt, long awayDurationMs) {
                recordedReturns.add(new RecordedReturnCall(attemptId, eventId, returnedAt, awayDurationMs));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        coordinator.onFocusLost();

        // Advance to 800ms
        clock.advanceMs(800);
        scheduler.advanceTime(800);

        assertThat(captureService.captureCount).isEqualTo(1);
        assertThat(recordedEvidences).hasSize(1);
        assertThat(recordedEvidences.get(0).evidenceType()).isEqualTo("INITIAL");

        // Student returns at 2500ms
        clock.advanceMs(1700);
        scheduler.advanceTime(1700);
        coordinator.onFocusGained();

        // Advance past 5000ms
        clock.advanceMs(4000);
        scheduler.advanceTime(4000);

        assertThat(captureService.captureCount).isEqualTo(1);
        assertThat(recordedEvidences).hasSize(1);
        assertThat(recordedLongAbsences).isEmpty();
        assertThat(recordedReturns).hasSize(1);
        assertThat(recordedReturns.get(0).awayDurationMs()).isEqualTo(2500);
    }

    @Test
    void longAbsence_capturesBothScreenshots_andMarksLongAbsence_stillOneStrike() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(101, 1, false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorReturn(int attemptId, int eventId, Instant returnedAt, long awayDurationMs) {
                recordedReturns.add(new RecordedReturnCall(attemptId, eventId, returnedAt, awayDurationMs));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        coordinator.onFocusLost();

        // T = 800ms
        clock.advanceMs(800);
        scheduler.advanceTime(800);
        assertThat(recordedEvidences).hasSize(1);
        assertThat(recordedEvidences.get(0).evidenceType()).isEqualTo("INITIAL");

        // T = 5000ms
        clock.advanceMs(4200);
        scheduler.advanceTime(4200);
        assertThat(recordedLongAbsences).hasSize(1);
        assertThat(recordedLongAbsences.get(0).eventId()).isEqualTo(101);
        assertThat(recordedEvidences).hasSize(2);
        assertThat(recordedEvidences.get(1).evidenceType()).isEqualTo("LONG_ABSENCE");

        // Student returns at 12000ms
        clock.advanceMs(7000);
        scheduler.advanceTime(7000);
        coordinator.onFocusGained();

        // Authoritative invariant: exactly 1 event recorded, 1 strike!
        assertThat(recordedEvents).hasSize(1);
        assertThat(violationCallbacks).hasSize(1);
        assertThat(violationCallbacks.get(0).violationCount()).isEqualTo(1);
        assertThat(recordedReturns).hasSize(1);
        assertThat(recordedReturns.get(0).awayDurationMs()).isEqualTo(12000);
    }

    @Test
    void networkDelay_screenCapturedAtExactDeadline_uploadedWhenEventIdResolves() {
        CompletableFuture<StudentProctorEventResponseDto> networkFuture = new CompletableFuture<>();

        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                try {
                    return networkFuture.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }
        };

        // Use custom thread pool for async network call
        ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
        try {
            ProctorIncidentCoordinator coordinator = new ProctorIncidentCoordinator(
                    1,
                    mockApi,
                    captureService,
                    scheduler,
                    clock,
                    violationCallbacks::add,
                    null,
                    800L,
                    5000L,
                    false,
                    networkExecutor, scheduler, scheduler, false
            );

            // Trigger focus loss in background so mockApi's blocking network future doesn't block caller
            networkExecutor.submit(coordinator::onFocusLost);

            // Wait a moment for network call to start
            int tries = 0;
            while (recordedEvents.isEmpty() && tries++ < 50) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }

            // At 800ms, eventId is NOT yet resolved because network is slow
            clock.advanceMs(800);
            scheduler.advanceTime(800);

            // Invariant: screen capture still proceeded at 800ms deadline!
            assertThat(captureService.captureCount).isEqualTo(1);
            // But upload waits for eventId
            assertThat(recordedEvidences).isEmpty();

            // At 1500ms, network response arrives
            networkFuture.complete(new StudentProctorEventResponseDto(205, 1, false, "ACTIVE"));

            // Wait a brief moment for thenAccept callback
            tries = 0;
            while (recordedEvidences.isEmpty() && tries++ < 50) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }

            // Upload triggered with the pre-captured bytes
            assertThat(recordedEvidences).hasSize(1);
            assertThat(recordedEvidences.get(0).eventId()).isEqualTo(205);
            assertThat(recordedEvidences.get(0).evidenceType()).isEqualTo("INITIAL");
        } finally {
            networkExecutor.shutdownNow();
        }
    }

    @Test
    void rapidFocusToggle_twoSeparateDepartures_twoStrikes_noGenerationPollution() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                int id = nextEventId.getAndIncrement();
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(id, recordedEvents.size(), false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorReturn(int attemptId, int eventId, Instant returnedAt, long awayDurationMs) {
                recordedReturns.add(new RecordedReturnCall(attemptId, eventId, returnedAt, awayDurationMs));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        // 1. First departure: leaves and returns in 200ms
        coordinator.onFocusLost();
        clock.advanceMs(200);
        scheduler.advanceTime(200);
        coordinator.onFocusGained();

        assertThat(recordedEvents).hasSize(1);
        assertThat(recordedReturns).hasSize(1);
        assertThat(recordedReturns.get(0).awayDurationMs()).isEqualTo(200);
        assertThat(recordedEvidences).isEmpty();

        // 2. Second departure 2 seconds later: stays for 6000ms
        clock.advanceMs(2000);
        scheduler.advanceTime(2000);
        coordinator.onFocusLost();

        assertThat(recordedEvents).hasSize(2); // Exactly 2 strikes!
        assertThat(coordinator.getCurrentIncidentGeneration()).isEqualTo(2);

        clock.advanceMs(6000);
        scheduler.advanceTime(6000);
        coordinator.onFocusGained();

        // Departure 2 has INITIAL and LONG_ABSENCE
        assertThat(recordedEvidences).hasSize(2);
        assertThat(recordedEvidences.get(0).evidenceType()).isEqualTo("INITIAL");
        assertThat(recordedEvidences.get(1).evidenceType()).isEqualTo("LONG_ABSENCE");
        assertThat(recordedLongAbsences).hasSize(1);
        assertThat(recordedReturns).hasSize(2);
        assertThat(recordedReturns.get(1).awayDurationMs()).isEqualTo(6000);
    }

    @Test
    void thirdStrike_terminatesImmediately_cancelsAllPendingCaptures() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(301, 3, true, "FLAGGED");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        coordinator.onFocusLost();

        // Invariants on 3rd strike
        assertThat(terminationCallbackFired.get()).isTrue();
        assertThat(coordinator.isActive()).isFalse();

        // Advance time: pending captures must not run!
        clock.advanceMs(10000);
        scheduler.advanceTime(10000);

        assertThat(captureService.captureCount).isEqualTo(0);
        assertThat(recordedEvidences).isEmpty();
    }

    @Test
    void captureFailure_doesNotUploadFabricatedEvidence_incidentAndStrikesRemainIntact() {
        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(401, 1, false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
            }
        };

        // Screen capture returns null (simulating headless / Robot / OS display failure)
        captureService.payload = null;

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        coordinator.onFocusLost();

        // Advance past initial deadline (800ms) and long absence deadline (5000ms)
        clock.advanceMs(800);
        scheduler.advanceTime(800);
        clock.advanceMs(4200);
        scheduler.advanceTime(4200);

        // Verification:
        // 1. Incident and strike are valid
        assertThat(recordedEvents).hasSize(1);
        assertThat(violationCallbacks).hasSize(1);
        assertThat(violationCallbacks.get(0).violationCount()).isEqualTo(1);

        // 2. Long absence metadata established
        assertThat(recordedLongAbsences).hasSize(1);

        // 3. NO fabricated evidence uploaded
        assertThat(recordedEvidences).isEmpty();
    }

    @Test
    void longAbsence_retriesSafelyWhenServerRejectsEarly_andSucceedsWithoutDuplicateStrike() {
        AtomicInteger longAbsenceAttempts = new AtomicInteger(0);

        ApiClient mockApi = new ApiClient(new ServerConfig("http://localhost:8080"), new SessionState(), null) {
            @Override
            public StudentProctorEventResponseDto recordProctorEvent(int attemptId, String eventType, String clientEventId) {
                recordedEvents.add(new RecordedEventCall(attemptId, eventType, clientEventId));
                return new StudentProctorEventResponseDto(501, 1, false, "ACTIVE");
            }

            @Override
            public void uploadProctorEvidence(int attemptId, int eventId, byte[] screenshotBytes, String contentType, String evidenceType) {
                recordedEvidences.add(new RecordedEvidenceCall(attemptId, eventId, screenshotBytes, contentType, evidenceType));
            }

            @Override
            public void recordProctorLongAbsence(int attemptId, int eventId) {
                int attempt = longAbsenceAttempts.incrementAndGet();
                recordedLongAbsences.add(new RecordedLongAbsenceCall(attemptId, eventId));
                if (attempt == 1) {
                    // First call: server rejects with 400 Bad Request (e.g. server duration was 4980ms due to network delay on event creation)
                    throw new com.examsystem.client.api.ApiException(400, "Threshold not yet met", "PROCTOR_LONG_ABSENCE_TOO_EARLY");
                }
                // Second call: succeeds
            }
        };

        ProctorIncidentCoordinator coordinator = createCoordinator(mockApi);

        coordinator.onFocusLost();

        // Advance to 800ms
        clock.advanceMs(800);
        scheduler.advanceTime(800);

        // Advance to 5000ms: first attempt runs and fails with 400
        clock.advanceMs(4200);
        scheduler.advanceTime(4200);

        assertThat(longAbsenceAttempts.get()).isEqualTo(1);

        // Advance 250ms (the retry delay)
        clock.advanceMs(250);
        scheduler.advanceTime(250);

        // Second attempt succeeded
        assertThat(longAbsenceAttempts.get()).isEqualTo(2);
        assertThat(captureService.captureCount).isEqualTo(2);
        assertThat(recordedEvidences).hasSize(2);

        // Crucial invariant: exactly 1 event recorded, 1 strike!
        assertThat(recordedEvents).hasSize(1);
        assertThat(violationCallbacks).hasSize(1);
        assertThat(violationCallbacks.get(0).violationCount()).isEqualTo(1);
    }
}
