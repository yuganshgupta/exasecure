package com.examsystem.launcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static com.examsystem.launcher.ProcessSupervisor.ServerState.*;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(20)
class ProcessSupervisorTest {
    @TempDir Path directory;
    private final List<ProcessSupervisor> supervisors = new ArrayList<>();
    private final List<Process> testOwned = new ArrayList<>();
    private static final ProcessSupervisor.Limits LIMITS = new ProcessSupervisor.Limits(
            Duration.ofSeconds(2), Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(20));

    @AfterEach void cleanup() throws Exception {
        for (var supervisor : supervisors) {
            var closed = supervisor.closeAsync().get(10, TimeUnit.SECONDS);
            assertThat(closed.serverPid()).isNull();
            assertThat(closed.adminPid()).isNull();
            assertThat(supervisor.awaitTermination(Duration.ofSeconds(2))).isTrue();
        }
        for (Process process : testOwned) {
            process.getOutputStream().close();
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            assertThat(process.waitFor(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void acknowledgedHealthyChildIsOwnedAndStopIsGracefulAndRepeatable() throws Exception {
        var supervisor = readySupervisor("ready");
        var ready = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(ready.server()).isEqualTo(OWNED_READY);
        assertThat(ready.serverPid()).isPositive();
        assertThat(supervisor.startServer().get(1, TimeUnit.SECONDS).serverPid()).isEqualTo(ready.serverPid());
        System.out.println("C03 owned Server PID " + ready.serverPid());
        assertThat(supervisor.stopServer().get(5, TimeUnit.SECONDS).serverPid()).isNull();
        assertThat(Files.readString(directory.resolve("server.stopped"))).isEqualTo("normal-stop");
        assertThat(supervisor.stopServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(STOPPED);
    }

    @Test @Timeout(30)
    void externalHealthyServerIsNeverAdoptedAndSurvivesStopAndClose() throws Exception {
        Path marker = directory.resolve("external");
        Process external = external("external", "external", 0);
        long fixtureDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(marker) && external.isAlive() && System.nanoTime() < fixtureDeadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(Files.exists(marker)).as("external Server startup, stderr: %s",
                Files.readString(directory.resolve("external.stderr"))).isTrue();
        int port = Integer.parseInt(Files.readString(marker));
        var supervisor = supervisor(() -> session("ready", port), ProcessSupervisor::probeHealth);
        assertThat(supervisor.startServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(EXTERNAL_AVAILABLE);
        assertThat(supervisor.snapshot().serverPid()).isNull();
        assertThat(Files.exists(directory.resolve("server"))).isFalse();
        assertThat(supervisor.stopServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(EXTERNAL_AVAILABLE);
        supervisor.closeAsync().get(5, TimeUnit.SECONDS);
        assertThat(external.isAlive()).isTrue();
        assertThat(ProcessSupervisor.probeHealth(session("ready", port).healthUri())).isTrue();
        System.out.println("C03 external Server survived stop/close PID " + external.pid());
    }

    @Test void serverAndAdminAreIndependentAndUnrelatedJavaSurvives() throws Exception {
        Process unrelated = external("leaf", "unrelated", 18089);
        var supervisor = readySupervisor("ready");
        supervisor.startServer().get(5, TimeUnit.SECONDS);
        var admin = supervisor.startAdmin().get(5, TimeUnit.SECONDS);
        assertThat(admin.adminPid()).isPositive();
        assertThat(supervisor.startAdmin().get(1, TimeUnit.SECONDS).adminPid()).isEqualTo(admin.adminPid());
        var stopped = supervisor.stopServer().get(5, TimeUnit.SECONDS);
        assertThat(stopped.adminPid()).isEqualTo(admin.adminPid());
        assertThat(unrelated.isAlive()).isTrue();
        supervisor.closeAsync().get(5, TimeUnit.SECONDS);
        assertThat(unrelated.isAlive()).isTrue();
        System.out.println("C03 independent Admin PID " + admin.adminPid() + "; unrelated survivor PID " + unrelated.pid());
    }

    @Test void earlyExitFailsAndClearsStaleOwnership() throws Exception {
        var supervisor = readySupervisor("early");
        var result = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(FAILED);
        assertThat(result.serverPid()).isNull();
        assertThat(result.message()).contains("exited");
    }

    @Test void healthWithoutAcknowledgmentNeverBecomesOwnedReady() throws Exception {
        var supervisor = readySupervisor("noack");
        var result = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(FAILED);
        assertThat(result.serverPid()).isNull();
        assertThat(result.message()).contains("acknowledgment timed out");
    }

    @Test void acknowledgmentWithoutHealthTimesOutAndCleansUp() throws Exception {
        var supervisor = supervisor(() -> session("ready", 18089), uri -> false);
        var result = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(FAILED);
        assertThat(result.message()).contains("health timed out");
        assertThat(result.serverPid()).isNull();
    }

    @Test void logsWrongNonceWrongPortAndStderrCannotFakeAcknowledgment() throws Exception {
        var supervisor = readySupervisor("fake");
        assertThat(supervisor.startServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(FAILED);
    }

    @Test void largeOutputOnBothPipesDoesNotDeadlockOrCorruptValidAcknowledgment() throws Exception {
        var supervisor = readySupervisor("noise");
        assertThat(supervisor.startServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(OWNED_READY);
        assertThat(supervisor.stopServer().get(5, TimeUnit.SECONDS).serverPid()).isNull();
    }

    @Test void ignoringStopUsesBoundedFallback() throws Exception {
        var supervisor = readySupervisor("ignore");
        supervisor.startServer().get(5, TimeUnit.SECONDS);
        var result = supervisor.stopServer().get(5, TimeUnit.SECONDS);
        assertThat(result.serverPid()).isNull();
        assertThat(result.message()).contains("fallback");
    }

    @Test void capturedDescendantIsCleanedEvenAfterRootExitsGracefully() throws Exception {
        var supervisor = readySupervisor("descendant");
        supervisor.startServer().get(5, TimeUnit.SECONDS);
        long leaf = Long.parseLong(Files.readString(directory.resolve("server.leaf")));
        // Lookup here is read-only assertion of a PID emitted by our disposable fixture, never adoption.
        var handle = ProcessHandle.of(leaf).orElseThrow();
        assertThat(handle.isAlive()).isTrue();
        supervisor.stopServer().get(5, TimeUnit.SECONDS);
        assertThat(handle.isAlive()).isFalse();
        System.out.println("C03 captured descendant stopped PID " + leaf);
    }

    @Test void duplicateStartIsRejectedWhilePreparationIsInProgress() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var supervisor = blockedSupervisor(entered, release);
        var first = supervisor.startServer();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        try { assertThat(supervisor.startServer().get(1, TimeUnit.SECONDS).server()).isEqualTo(STARTING); }
        finally { release.countDown(); }
        assertThat(first.get(5, TimeUnit.SECONDS).server()).isEqualTo(OWNED_READY);
    }

    @Test void stopDuringPreparationPreventsProcessCreation() throws Exception {
        raceDuringPreparation(false);
    }
    @Test void closeDuringPreparationPreventsProcessCreationAndNewStarts() throws Exception {
        raceDuringPreparation(true);
    }

    private void raceDuringPreparation(boolean close) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var supervisor = blockedSupervisor(entered, release);
        var starting = supervisor.startServer();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<ProcessSupervisor.Snapshot> stopping;
        try { stopping = close ? supervisor.closeAsync() : supervisor.stopServer(); }
        finally { release.countDown(); }
        starting.get(5, TimeUnit.SECONDS);
        assertThat(stopping.get(5, TimeUnit.SECONDS).serverPid()).isNull();
        assertThat(Files.exists(directory.resolve("server"))).isFalse();
        if (close) assertThat(supervisor.startServer().get(1, TimeUnit.SECONDS).closing()).isTrue();
    }

    @Test void stopDuringAcknowledgmentWaitCleansCreatedChild() throws Exception {
        raceAfterCreation(false);
    }
    @Test void closeDuringAcknowledgmentWaitCleansCreatedChildAndTerminatesWorker() throws Exception {
        raceAfterCreation(true);
    }
    private void raceAfterCreation(boolean close) throws Exception {
        var supervisor = readySupervisor("noack");
        var starting = supervisor.startServer();
        await(() -> Files.exists(directory.resolve("server")));
        var stopping = close ? supervisor.closeAsync() : supervisor.stopServer();
        starting.get(5, TimeUnit.SECONDS);
        var result = stopping.get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(STOPPED);
        assertThat(result.serverPid()).isNull();
        assertThat(Files.exists(directory.resolve("server.stopped"))).isTrue();
    }

    @Test void exitAfterReadinessIsDetectedWithoutStaleOwnership() throws Exception {
        var supervisor = readySupervisor("exit-ready");
        supervisor.startServer().get(5, TimeUnit.SECONDS);
        await(() -> supervisor.snapshot().serverPid() == null);
        assertThat(supervisor.snapshot().server()).isEqualTo(FAILED);
    }

    @Test void noFurtherHealthPollingAfterStopOrClose() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var supervisor = supervisor(() -> session("ready", 18089), uri -> calls.incrementAndGet() > 1);
        supervisor.startServer().get(5, TimeUnit.SECONDS);
        supervisor.stopServer().get(5, TimeUnit.SECONDS);
        int stoppedCalls = calls.get();
        supervisor.closeAsync().get(5, TimeUnit.SECONDS);
        assertThat(supervisor.awaitTermination(Duration.ofSeconds(2))).isTrue();
        assertThat(calls.get()).isEqualTo(stoppedCalls);
    }

    @Test void missingComponentFailsWithoutStartingAnything() throws Exception {
        var supervisor = supervisor(() -> { throw new java.io.IOException("not displayed"); }, uri -> false);
        assertThat(supervisor.startServer().get(2, TimeUnit.SECONDS).server()).isEqualTo(FAILED);
        assertThat(supervisor.snapshot().serverPid()).isNull();
    }

    @Test void machineFailureIsReportedWithoutWaitingForHealthTimeout() throws Exception {
        var supervisor = readySupervisor("reported-failure");
        var result = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(FAILED);
        assertThat(result.message()).contains("reported startup failure");
        assertThat(result.serverPid()).isNull();
    }

    @Test void configurationChangedBetweenPreparationAndChildReadCannotDriftIntoOwnedReadiness() throws Exception {
        Path config = directory.resolve("changed.properties");
        Files.writeString(config, "server.port=19124\n");
        AtomicInteger calls = new AtomicInteger();
        var supervisor = supervisor(() -> new LaunchSession(spec("installed", "server", 19123),
                spec("installed-admin", "admin", 19123), true, config, 19123), uri -> calls.incrementAndGet() > 1);
        var result = supervisor.startServer().get(5, TimeUnit.SECONDS);
        assertThat(result.server()).isEqualTo(FAILED);
        assertThat(result.message()).contains("acknowledgment timed out");
        assertThat(result.serverPid()).isNull();
    }

    @Test void simulatedInstalledSessionUsesPipesAndTheSameConfigurationForBothRoles() throws Exception {
        Path config = directory.resolve("installed.properties");
        Files.writeString(config, "server.port=19123\n");
        AtomicInteger calls = new AtomicInteger();
        var supervisor = supervisor(() -> new LaunchSession(spec("installed", "server", 19123),
                spec("installed-admin", "admin", 19123), true, config, 19123),
                uri -> { assertThat(uri.toString()).isEqualTo("http://localhost:19123/health"); return calls.incrementAndGet() > 1; });
        assertThat(supervisor.startServer().get(5, TimeUnit.SECONDS).server()).isEqualTo(OWNED_READY);
        supervisor.startAdmin().get(5, TimeUnit.SECONDS);
        await(() -> Files.exists(directory.resolve("admin")));
        assertThat(Files.readString(directory.resolve("admin"))).isEqualTo("http://localhost:19123");
        assertThat(supervisor.stopServer().get(5, TimeUnit.SECONDS).adminPid()).isNotNull();
    }

    private ProcessSupervisor blockedSupervisor(CountDownLatch entered, CountDownLatch release) {
        AtomicInteger calls = new AtomicInteger();
        return supervisor(() -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Test release timed out"); }
            catch (InterruptedException e) { throw new java.io.IOException(e); }
            return session("ready", 18089);
        }, uri -> calls.incrementAndGet() > 1);
    }
    private ProcessSupervisor readySupervisor(String mode) {
        AtomicInteger calls = new AtomicInteger();
        return supervisor(() -> session(mode, 18089), uri -> calls.incrementAndGet() > 1);
    }
    private ProcessSupervisor supervisor(LaunchSession.Factory factory, ProcessSupervisor.HealthProbe health) {
        var result = new ProcessSupervisor(factory, health, LIMITS);
        supervisors.add(result);
        return result;
    }
    private LaunchSession session(String mode, int port) {
        return new LaunchSession(spec(mode, "server", port), spec("admin", "admin", port), false, null, port);
    }
    private LaunchSpec spec(String mode, String marker, int port) {
        List<String> command = ControlledChild.command(mode, directory.resolve(marker), port);
        // The marker directory is owned by JUnit. A descendant can keep its cwd open on Windows.
        Path workingDirectory = "descendant".equals(mode) ? Path.of("").toAbsolutePath() : directory;
        return new LaunchSpec(Path.of(command.getFirst()), command.subList(1, command.size()), workingDirectory);
    }
    private Process external(String mode, String marker, int port) throws Exception {
        Process process = new ProcessBuilder(ControlledChild.command(mode, directory.resolve(marker), port))
                .redirectError(directory.resolve(marker + ".stderr").toFile()).start();
        testOwned.add(process);
        return process;
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
