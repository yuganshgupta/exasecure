package com.examsystem.server.bootstrap;

import com.examsystem.server.ExaSecureServer;
import com.examsystem.server.config.AppConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/** Private process protocol, version 1. Never exposed over HTTP.
 * stdin: EXASECURE/1 HELLO <uuid>, then EXASECURE/1 STOP <same uuid>.
 * stdout: EXASECURE/1 READY <uuid> <actual configured port>, only after start returns.
 * EOF before HELLO does not start a Server. After HELLO, EOF means owner loss:
 * finish/cancel startup and call normal stop. A blocked start is bounded by the parent.
 * Standalone development launches never enter this protocol implicitly.
 */
public final class ManagedServerControl {
    private ManagedServerControl() { }

    interface Lifecycle { void start(); void stop(); }

    public static void run(AppConfig config) {
        PrintStream protocol = System.out;
        // New console appenders and ordinary printlns use stderr; exact nonce parsing
        // also tolerates an already-initialized development logger on stdout.
        System.setOut(System.err);
        ExaSecureServer server = new ExaSecureServer(config);
        try {
            run(new Lifecycle() {
                public void start() { server.start(); }
                public void stop() { server.stop(); }
            }, config.port, System.in, protocol);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Managed Server control channel failed.");
        } finally {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            System.setOut(protocol);
        }
    }

    static void run(Lifecycle server, int port, InputStream input, PrintStream output)
            throws IOException, InterruptedException {
        String hello = readLine(input);
        if (hello == null) return;
        if (!hello.matches("EXASECURE/1 HELLO [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("Invalid owner handshake.");
        }
        String nonce = hello.substring("EXASECURE/1 HELLO ".length());
        CountDownLatch stop = new CountDownLatch(1);
        Thread reader = Thread.ofVirtual().name("exasecure-owner-channel").start(() -> {
            try {
                String command;
                while ((command = readLine(input)) != null) {
                    if (command.equals("EXASECURE/1 STOP " + nonce)) break;
                }
            } catch (IOException ignored) {
                // Pipe failure has the same owner-loss semantics as EOF.
            } finally { stop.countDown(); }
        });
        try {
            if (stop.getCount() == 0) return;
            server.start();
            if (stop.getCount() != 0) {
                output.println("EXASECURE/1 READY " + nonce + " " + port);
                output.flush();
                if (output.checkError()) return;
            }
            stop.await();
        } catch (RuntimeException e) {
            // Do not echo configuration, driver messages, or exception causes into the protocol.
            output.println("EXASECURE/1 ERROR " + nonce + " START_FAILED");
            output.flush();
            throw new IllegalStateException("Managed Server startup/shutdown failed.");
        } finally {
            try { server.stop(); }
            finally {
                reader.interrupt();
                // Native stdin reads may not be interruptible; this virtual reader cannot
                // keep a terminating JVM alive. No lifecycle state is written by it.
            }
        }
    }

    private static String readLine(InputStream input) throws IOException {
        byte[] bytes = new byte[128];
        int length = 0;
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') return new String(bytes, 0, length, StandardCharsets.US_ASCII);
            if (next == '\r') continue;
            if (length == bytes.length) throw new IOException("Owner command too long.");
            bytes[length++] = (byte) next;
        }
        return null; // An unterminated command is never executed.
    }
}
