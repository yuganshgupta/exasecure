package com.examsystem.server.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Timeout(15)
class ManagedServerControlTest {
    @TempDir Path directory;
    private final List<Process> owned = new ArrayList<>();
    private final String nonce = UUID.randomUUID().toString();

    @AfterEach void cleanup() throws Exception {
        for (Process process : owned) {
            process.getOutputStream().close();
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
            assertThat(process.waitFor(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void stopInvokesNormalShutdownAndExitsCleanly() throws Exception {
        Process process = child("normal");
        send(process, "EXASECURE/1 HELLO " + nonce);
        assertThat(line(process)).isEqualTo("EXASECURE/1 READY " + nonce + " 19123");
        send(process, "EXASECURE/1 STOP " + nonce);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(Files.readString(directory.resolve("server.stopped"))).isEqualTo("normal-stop-invoked");
        System.out.println("C03 production control-loop graceful exit PID " + process.pid());
    }

    @Test void eofBeforeHandshakeDoesNotStartServer() throws Exception {
        Process process = child("normal");
        process.getOutputStream().close();
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(Files.exists(directory.resolve("server"))).isFalse();
    }

    @Test void eofAfterAcknowledgmentStopsNormally() throws Exception {
        Process process = child("normal");
        send(process, "EXASECURE/1 HELLO " + nonce);
        assertThat(line(process)).contains(" READY ");
        process.getOutputStream().close();
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(directory.resolve("server.stopped"))).isTrue();
    }

    @Test void eofDuringStartupFinishesCleanupWithoutLeakingServer() throws Exception {
        Process process = child("delayed");
        send(process, "EXASECURE/1 HELLO " + nonce);
        awaitStart();
        process.getOutputStream().close();
        Files.writeString(directory.resolve("server.release"), "release");
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(directory.resolve("server.stopped"))).isTrue();
    }

    @Test void stopDuringStartupCallsNormalStopAfterStartReturns() throws Exception {
        Process process = child("delayed");
        send(process, "EXASECURE/1 HELLO " + nonce);
        awaitStart();
        send(process, "EXASECURE/1 STOP " + nonce);
        Files.writeString(directory.resolve("server.release"), "release");
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(directory.resolve("server.stopped"))).isTrue();
    }

    @Test void ordinaryLogLikeCommandsAndWrongNonceDoNotStopServer() throws Exception {
        Process process = child("normal");
        send(process, "EXASECURE/1 HELLO " + nonce);
        assertThat(line(process)).contains(" READY ");
        send(process, "STOP");
        send(process, "EXASECURE/1 STOP " + UUID.randomUUID());
        send(process, "log: STOP");
        assertThat(process.isAlive()).isTrue();
        send(process, "EXASECURE/1 STOP " + nonce);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
    }

    @Test void failedPartialStartCleansUpAndNeverAcknowledgesOrEchoesCause() throws Exception {
        Process process = child("fail");
        send(process, "EXASECURE/1 HELLO " + nonce);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(output).contains("START_FAILED").doesNotContain("READY", "fixture secret");
        assertThat(error).doesNotContain("fixture secret");
        assertThat(Files.exists(directory.resolve("server.stopped"))).isTrue();
        assertThat(process.exitValue()).isNotZero();
    }

    @Test void malformedAndOversizedHandshakeCannotStartServer() throws Exception {
        for (String hello : List.of("STOP", "EXASECURE/1 HELLO bad", "x".repeat(150))) {
            Process process = child("normal");
            send(process, hello);
            assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isNotZero();
            assertThat(Files.exists(directory.resolve("server"))).isFalse();
        }
    }

    private Process child(String mode) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                ManagedControlChild.class.getName(), directory.resolve("server").toString(), mode).start();
        owned.add(process);
        return process;
    }
    private void send(Process process, String value) throws Exception {
        process.getOutputStream().write((value + "\n").getBytes(StandardCharsets.US_ASCII));
        process.getOutputStream().flush();
    }
    private String line(Process process) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try { return process.inputReader(StandardCharsets.UTF_8).readLine(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).get(3, TimeUnit.SECONDS);
    }
    private void awaitStart() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!Files.exists(directory.resolve("server")) && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
        assertThat(Files.exists(directory.resolve("server"))).isTrue();
    }
}
