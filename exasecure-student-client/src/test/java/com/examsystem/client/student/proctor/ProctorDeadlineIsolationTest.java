package com.examsystem.client.student.proctor;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.config.ServerConfig;
import com.examsystem.client.proctor.ScreenCapturer;
import com.examsystem.client.session.SessionState;
import com.examsystem.contracts.StudentProctorEventResponseDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the production constructor's workers and a real, single deadline thread. */
class ProctorDeadlineIsolationTest {
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Network stall was never released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static <T> T take(BlockingQueue<T> queue, long seconds) throws InterruptedException {
        T value = queue.poll(seconds, TimeUnit.SECONDS);
        assertThat(value).as("Work must finish while the network latch is still closed").isNotNull();
        return value;
    }

    private static class RecordingApi extends ApiClient {
        final List<String> keys = new CopyOnWriteArrayList<>();
        final BlockingQueue<String> uploads = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> longAbsences = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> returns = new LinkedBlockingQueue<>();
        final AtomicInteger events = new AtomicInteger();

        RecordingApi() { super(new ServerConfig("http://localhost:8080"), new SessionState(), null); }

        @Override public StudentProctorEventResponseDto recordProctorEvent(int attempt, String type, String key) {
            assertThat(type).isEqualTo("FOCUS_LOST");
            keys.add(key);
            int id = events.incrementAndGet();
            return new StudentProctorEventResponseDto(id, id, false, "ACTIVE");
        }
        @Override public void uploadProctorEvidence(int attempt, int event, byte[] data, String mime, String type) {
            uploads.add(event + ":" + type + ":" + data[0]);
        }
        @Override public void recordProctorLongAbsence(int attempt, int event) { longAbsences.add(event); }
        @Override public void recordProctorReturn(int attempt, int event, Instant time, long duration) { returns.add(event); }
    }

    @Test
    void blockedEventCreationCannotStarveEitherRealDeadline() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var api = new RecordingApi() {
            @Override public StudentProctorEventResponseDto recordProctorEvent(int attempt, String type, String key) {
                var response = super.recordProctorEvent(attempt, type, key);
                entered.countDown();
                await(release);
                return response;
            }
        };
        var captures = new LinkedBlockingQueue<Long>();
        var captureCount = new AtomicInteger();
        ScreenCapturer capture = () -> {
            captures.add(System.nanoTime());
            return new byte[]{(byte) captureCount.incrementAndGet()};
        };
        var responses = new LinkedBlockingQueue<StudentProctorEventResponseDto>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, capture, scheduler, Clock.systemUTC(),
                responses::add, null, 800, 5000, true);
        long start = System.nanoTime();
        try {
            coordinator.onFocusLost();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.onFocusLost(); // Duplicate notification is still one departure.
            long firstMs = TimeUnit.NANOSECONDS.toMillis(take(captures, 3) - start);
            long secondMs = TimeUnit.NANOSECONDS.toMillis(take(captures, 5) - start);
            assertThat(firstMs).isBetween(700L, 3000L);
            assertThat(secondMs).isBetween(4800L, 7500L);
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(api.events.get()).isEqualTo(1);
            assertThat(api.uploads).isEmpty();
            release.countDown();
            assertThat(take(responses, 2).violationCount()).isEqualTo(1);
            assertThat(List.of(take(api.uploads, 2), take(api.uploads, 2)))
                    .containsExactlyInAnyOrder("1:INITIAL:1", "1:LONG_ABSENCE:2");
            assertThat(take(api.longAbsences, 2)).isEqualTo(1);
            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(1);
            scheduler.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(captureCount.get()).isEqualTo(2);
            assertThat(api.keys).hasSize(1);
            assertThat(api.uploads).isEmpty();
            assertThat(api.longAbsences).isEmpty();
            assertThat(responses).isEmpty();
        } finally {
            release.countDown();
            coordinator.shutdown();
            assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void blockedUploadCannotStarveLongDeadlineOrContaminateNextIncident() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var api = new RecordingApi() {
            @Override public void uploadProctorEvidence(int attempt, int event, byte[] data, String mime, String type) {
                super.uploadProctorEvidence(attempt, event, data, mime, type);
                if (event == 1 && type.equals("INITIAL")) {
                    entered.countDown();
                    await(release);
                }
            }
        };
        var captures = new LinkedBlockingQueue<Long>();
        var count = new AtomicInteger();
        ScreenCapturer capture = () -> {
            captures.add(System.nanoTime());
            return new byte[]{(byte) count.incrementAndGet()};
        };
        var responses = new LinkedBlockingQueue<StudentProctorEventResponseDto>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, capture, scheduler, Clock.systemUTC(),
                responses::add, null, 800, 5000, true);
        long start = System.nanoTime();
        try {
            coordinator.onFocusLost();
            assertThat(take(responses, 2).violationCount()).isEqualTo(1);
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(take(captures, 1) - start)).isBetween(700L, 3000L);
            assertThat(TimeUnit.NANOSECONDS.toMillis(take(captures, 6) - start)).isBetween(4800L, 7500L);
            assertThat(take(api.longAbsences, 1)).isEqualTo(1);
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(api.events.get()).isEqualTo(1);
            assertThat(take(api.uploads, 1)).isEqualTo("1:INITIAL:1");

            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(1);
            coordinator.onFocusLost();
            assertThat(take(responses, 2).violationCount()).isEqualTo(2);
            take(captures, 3);
            release.countDown();
            // Old queued long-absence evidence is cancelled; bytes and IDs stay incident-local.
            assertThat(take(api.uploads, 2)).isEqualTo("2:INITIAL:3");
            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(2);
            scheduler.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(count.get()).isEqualTo(3);
            assertThat(api.keys).hasSize(2).doesNotHaveDuplicates();
            assertThat(api.uploads).isEmpty();
            assertThat(api.longAbsences).isEmpty();
            assertThat(responses).isEmpty();
        } finally {
            release.countDown();
            coordinator.shutdown();
            assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void focusReturnDuringCaptureDiscardsBytesBeforeNewIncidentUpload() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        ScreenCapturer capture = () -> {
            int number = count.incrementAndGet();
            if (number == 1) {
                entered.countDown();
                await(release);
            }
            return new byte[]{(byte) number};
        };
        var api = new RecordingApi();
        var responses = new LinkedBlockingQueue<StudentProctorEventResponseDto>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, capture, scheduler, Clock.systemUTC(),
                responses::add, null, 10, 10000, true);
        try {
            coordinator.onFocusLost();
            take(responses, 2);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(1);
            coordinator.onFocusLost();
            assertThat(take(responses, 2).eventId()).isEqualTo(2);
            release.countDown();
            assertThat(take(api.uploads, 2)).isEqualTo("2:INITIAL:2");
            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(2);
            assertThat(api.uploads).isEmpty();
            assertThat(api.longAbsences).isEmpty();
            assertThat(api.keys).hasSize(2).doesNotHaveDuplicates();
        } finally {
            release.countDown();
            coordinator.shutdown();
            assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unresolvedOldEventAfterReturnKeepsReturnButDiscardsStaleEvidenceAndCallback() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var api = new RecordingApi() {
            @Override public StudentProctorEventResponseDto recordProctorEvent(int attempt, String type, String key) {
                var response = super.recordProctorEvent(attempt, type, key);
                if (response.eventId() == 1) {
                    entered.countDown();
                    await(release);
                }
                return response;
            }
        };
        var captures = new LinkedBlockingQueue<Integer>();
        var count = new AtomicInteger();
        ScreenCapturer capture = () -> {
            int number = count.incrementAndGet();
            captures.add(number);
            return new byte[]{(byte) number};
        };
        var responses = new LinkedBlockingQueue<StudentProctorEventResponseDto>();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, capture, scheduler, Clock.systemUTC(),
                responses::add, null, 10, 10000, true);
        try {
            coordinator.onFocusLost();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            take(captures, 2);
            coordinator.onFocusGained();
            coordinator.onFocusLost();
            take(captures, 2);
            release.countDown();
            assertThat(take(responses, 2).eventId()).isEqualTo(2);
            assertThat(take(api.returns, 2)).isEqualTo(1);
            assertThat(take(api.uploads, 2)).isEqualTo("2:INITIAL:2");
            coordinator.onFocusGained();
            assertThat(take(api.returns, 2)).isEqualTo(2);
            assertThat(api.uploads).isEmpty();
            assertThat(responses).isEmpty();
            assertThat(api.longAbsences).isEmpty();
            assertThat(api.keys).hasSize(2).doesNotHaveDuplicates();
        } finally {
            release.countDown();
            coordinator.shutdown();
            assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shutdownInterruptsAllOwnedWorkersAndLateFailureCannotScheduleRetry() throws Exception {
        var entered = new CountDownLatch(3);
        var interrupted = new CountDownLatch(3);
        Runnable stall = () -> {
            entered.countDown();
            try {
                if (!new CountDownLatch(1).await(10, TimeUnit.SECONDS)) throw new AssertionError("Worker leaked");
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        };
        var api = new RecordingApi() {
            @Override public void uploadProctorEvidence(int attempt, int event, byte[] data, String mime, String type) {
                super.uploadProctorEvidence(attempt, event, data, mime, type);
                stall.run();
            }
            @Override public void recordProctorLongAbsence(int attempt, int event) {
                super.recordProctorLongAbsence(attempt, event);
                stall.run();
                throw new com.examsystem.client.api.ApiException(400, "Threshold not yet met", "PROCTOR_LONG_ABSENCE_TOO_EARLY");
            }
        };
        var count = new AtomicInteger();
        ScreenCapturer capture = () -> {
            if (count.incrementAndGet() == 2) stall.run();
            return new byte[]{1};
        };
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var network = Executors.newSingleThreadExecutor();
        var captureWorker = Executors.newSingleThreadExecutor();
        var evidence = Executors.newSingleThreadExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, capture, scheduler, Clock.systemUTC(),
                null, null, 10, 100, true, network, captureWorker, evidence, true);
        try {
            coordinator.onFocusLost();
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            coordinator.shutdown();
            coordinator.shutdown();
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            for (var worker : List.of(scheduler, network, captureWorker, evidence)) {
                assertThat(worker.isShutdown()).isTrue();
                assertThat(worker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            }
            coordinator.onFocusGained();
            coordinator.onFocusLost();
            assertThat(coordinator.isActive()).isFalse();
            assertThat(api.events.get()).isEqualTo(1);
            assertThat(api.uploads).containsExactly("1:INITIAL:1");
            assertThat(api.longAbsences).containsExactly(1);
            assertThat(api.returns).isEmpty();
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void shutdownSuppressesLateEventResponseAndPreservesBorrowedScheduler() throws Exception {
        var entered = new CountDownLatch(1);
        var captured = new CountDownLatch(1);
        var api = new RecordingApi() {
            @Override public StudentProctorEventResponseDto recordProctorEvent(int attempt, String type, String key) {
                super.recordProctorEvent(attempt, type, key);
                entered.countDown();
                try {
                    if (!new CountDownLatch(1).await(10, TimeUnit.SECONDS)) throw new AssertionError("Event worker leaked");
                } catch (InterruptedException expected) {
                    // Model an in-flight request returning a response despite cancellation.
                    Thread.currentThread().interrupt();
                }
                return new StudentProctorEventResponseDto(1, 3, true, "FLAGGED");
            }
        };
        var callbacks = new AtomicInteger();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var network = Executors.newSingleThreadExecutor();
        var captureWorker = Executors.newSingleThreadExecutor();
        var evidence = Executors.newSingleThreadExecutor();
        var coordinator = new ProctorIncidentCoordinator(1, api, () -> {
            captured.countDown();
            return new byte[]{1};
        }, scheduler, Clock.systemUTC(), response -> callbacks.incrementAndGet(), callbacks::incrementAndGet,
                10, 10000, false, network, captureWorker, evidence, true);
        try {
            coordinator.onFocusLost();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(captured.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.onFocusGained();
            coordinator.shutdown();
            for (var worker : List.of(network, captureWorker, evidence)) {
                assertThat(worker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(scheduler.isShutdown()).isFalse();
            scheduler.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertThat(callbacks.get()).isZero();
            assertThat(api.events.get()).isEqualTo(1);
            assertThat(api.uploads).isEmpty();
            assertThat(api.returns).isEmpty();
            assertThat(api.longAbsences).isEmpty();
        } finally {
            coordinator.shutdown();
            scheduler.shutdownNow();
            assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
