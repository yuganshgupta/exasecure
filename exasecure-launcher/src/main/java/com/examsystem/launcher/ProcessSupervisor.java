package com.examsystem.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** In-memory ownership only. All OS work is serialized off the UI thread.
 * Stop/close invalidate an in-flight start immediately, including during process creation.
 * No PID lookup, process-name scan, adoption, recovery or restart supervision.
 */
final class ProcessSupervisor {
    enum ServerState { STOPPED, STARTING, OWNED_READY, EXTERNAL_AVAILABLE, STOPPING, FAILED }
    record Snapshot(ServerState server, boolean adminRunning, boolean busy, boolean closing,
                    Long serverPid, Long adminPid, int port, String message) { }
    record Limits(Duration startup, Duration graceful, Duration terminate, Duration force, Duration poll) {
        static Limits normal() { return new Limits(Duration.ofSeconds(20), Duration.ofSeconds(5),
                Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofMillis(100)); }
    }
    @FunctionalInterface interface HealthProbe { boolean healthy(URI uri); }

    private final LaunchSession.Factory factory;
    private final HealthProbe health;
    private final Limits limits;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("exasecure-lifecycle").factory());
    private final EnumMap<LaunchTarget, OwnedProcess> owned = new EnumMap<>(LaunchTarget.class);
    private LaunchSession session;
    private ServerState state = ServerState.STOPPED;
    private String message = "Ready.";
    private boolean startingAdmin;
    private boolean closing;
    private long generation;
    private CompletableFuture<Snapshot> closeResult;

    ProcessSupervisor(LaunchSession.Factory factory) { this(factory, ProcessSupervisor::probeHealth, Limits.normal()); }
    ProcessSupervisor(LaunchSession.Factory factory, HealthProbe health, Limits limits) {
        this.factory = factory;
        this.health = health;
        this.limits = limits;
        worker.scheduleWithFixedDelay(this::monitor, 250, 250, TimeUnit.MILLISECONDS);
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(state, owned.containsKey(LaunchTarget.ADMIN), startingAdmin
                || state == ServerState.STARTING || state == ServerState.STOPPING, closing,
                pid(LaunchTarget.SERVER), pid(LaunchTarget.ADMIN), session == null ? 8089 : session.port(), message);
    }

    private Long pid(LaunchTarget role) { var process = owned.get(role); return process == null ? null : process.process.pid(); }

    synchronized CompletableFuture<Snapshot> startServer() {
        if (closing || state == ServerState.STARTING || state == ServerState.STOPPING || owned.containsKey(LaunchTarget.SERVER)) {
            return CompletableFuture.completedFuture(snapshot());
        }
        state = ServerState.STARTING;
        message = "Checking Server availability...";
        long ticket = ++generation;
        return submit(() -> startServer(ticket));
    }

    synchronized CompletableFuture<Snapshot> startAdmin() {
        if (closing || startingAdmin || state == ServerState.STOPPING || owned.containsKey(LaunchTarget.ADMIN)) {
            return CompletableFuture.completedFuture(snapshot());
        }
        startingAdmin = true;
        long ticket = generation;
        return submit(() -> {
            try {
                if (cancelled(ticket)) return;
                prepareSession();
                if (!health.healthy(session.healthUri())) {
                    setMessage("Server is unavailable. Start Server before opening Admin.");
                    return;
                }
                if (cancelled(ticket)) return;
                OwnedProcess child = launch(LaunchTarget.ADMIN);
                if (cancelled(ticket)) cleanup(LaunchTarget.ADMIN);
                else if (!child.process.isAlive()) {
                    cleanup(LaunchTarget.ADMIN);
                    setMessage("Admin exited during startup.");
                } else setMessage("Owned Admin launched.");
            } catch (IOException | RuntimeException e) {
                setMessage("Unable to launch Admin. Check installed components and Server configuration.");
            } finally { synchronized (this) { startingAdmin = false; } }
        });
    }

    synchronized CompletableFuture<Snapshot> stopServer() {
        if (closing || state == ServerState.STOPPING) return CompletableFuture.completedFuture(snapshot());
        boolean external = state == ServerState.EXTERNAL_AVAILABLE && !owned.containsKey(LaunchTarget.SERVER);
        ++generation;
        state = ServerState.STOPPING;
        message = "Stopping owned Server...";
        return submit(() -> {
            boolean complete = cleanup(LaunchTarget.SERVER);
            synchronized (this) {
                state = complete ? (external ? ServerState.EXTERNAL_AVAILABLE : ServerState.STOPPED) : ServerState.FAILED;
                if (complete && !closing) message += " External processes are not managed.";
            }
        });
    }

    synchronized CompletableFuture<Snapshot> checkAvailability() {
        if (closing || state == ServerState.STARTING || state == ServerState.STOPPING) return CompletableFuture.completedFuture(snapshot());
        long ticket = generation;
        return submit(() -> {
            try {
                prepareSession();
                boolean available = health.healthy(session.healthUri());
                synchronized (this) {
                    if (!cancelled(ticket) && !owned.containsKey(LaunchTarget.SERVER)) {
                        state = available ? ServerState.EXTERNAL_AVAILABLE : ServerState.STOPPED;
                        message = available ? "External Server available; it is not owned and cannot be stopped here." : "Ready.";
                    }
                }
            } catch (IOException | RuntimeException e) { failConfiguration(); }
        });
    }

    synchronized CompletableFuture<Snapshot> closeAsync() {
        if (closeResult != null) return closeResult;
        closing = true;
        ++generation;
        state = ServerState.STOPPING;
        message = "Closing: stopping owned processes...";
        closeResult = submit(() -> {
            boolean serverStopped = cleanup(LaunchTarget.SERVER);
            boolean adminStopped = cleanup(LaunchTarget.ADMIN);
            synchronized (this) {
                state = serverStopped && adminStopped ? ServerState.STOPPED : ServerState.FAILED;
                message = serverStopped && adminStopped ? "Owned processes stopped." : "Owned process cleanup failed; inspect before exiting.";
            }
            worker.shutdown();
        });
        return closeResult;
    }

    boolean awaitTermination(Duration timeout) throws InterruptedException {
        return worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Snapshot> submit(Runnable action) {
        return CompletableFuture.supplyAsync(() -> { action.run(); return snapshot(); }, worker);
    }

    private synchronized boolean cancelled(long ticket) { return closing || ticket != generation; }
    private synchronized void setMessage(String value) { if (!closing) message = value; }
    private synchronized void failConfiguration() {
        if (!closing && state != ServerState.STOPPING) {
            state = ServerState.FAILED;
            message = "Launch configuration unavailable. Check exasecure.app.dir, packaged components, and installed Server settings.";
        }
    }

    private void prepareSession() throws IOException {
        // Reuse the same port snapshot while either role is owned. UI configuration is disabled then.
        synchronized (this) { if (session != null && !owned.isEmpty()) return; }
        LaunchSession prepared = factory.prepare();
        synchronized (this) { session = prepared; }
    }

    private void startServer(long ticket) {
        try {
            if (cancelled(ticket)) return;
            prepareSession();
            boolean external = health.healthy(session.healthUri());
            if (cancelled(ticket)) return;
            if (external) {
                synchronized (this) {
                    if (!cancelled(ticket)) {
                        state = ServerState.EXTERNAL_AVAILABLE;
                        message = "Port " + session.port() + " has an external Server. No child was started or adopted.";
                    }
                }
                return;
            }
            OwnedProcess child = launch(LaunchTarget.SERVER);
            if (cancelled(ticket)) return;
            child.send("EXASECURE/1 HELLO " + child.nonce);
            child.helloSent = true;
            long deadline = System.nanoTime() + limits.startup.toNanos();
            while (!cancelled(ticket) && System.nanoTime() < deadline) {
                child.captureDescendants();
                if (child.startupFailed) {
                    failStartup(ticket, "Owned Server reported startup failure. Check database availability and port configuration.");
                    return;
                }
                if (!child.process.isAlive()) {
                    failStartup(ticket, "Owned Server exited before readiness (exit " + child.process.exitValue() + ").");
                    return;
                }
                if (child.acknowledged && health.healthy(session.healthUri()) && child.process.isAlive()) {
                    synchronized (this) {
                        if (!cancelled(ticket)) {
                            state = ServerState.OWNED_READY;
                            message = "Owned Server ready on port " + session.port() + ".";
                            return;
                        }
                    }
                }
                child.process.waitFor(limits.poll.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (!cancelled(ticket)) failStartup(ticket, child.acknowledged
                    ? "Server acknowledged startup, but health timed out."
                    : "Server startup acknowledgment timed out; health alone does not grant readiness.");
            // Stop/close is queued on this same worker and owns cancellation cleanup.
        } catch (IOException | RuntimeException e) {
            failStartup(ticket, "Server launch failed. Check component paths and installed configuration; no development fallback is used.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failStartup(ticket, "Server startup interrupted.");
        }
    }

    private void failStartup(long ticket, String reason) {
        cleanup(LaunchTarget.SERVER);
        synchronized (this) {
            if (!cancelled(ticket)) { state = ServerState.FAILED; message = reason + " " + message; }
        }
    }

    private OwnedProcess launch(LaunchTarget role) throws IOException {
        Process process = session.builder(role).start(); // Sole source of ownership.
        OwnedProcess child = new OwnedProcess(role, process, session.port());
        synchronized (this) { owned.put(role, child); }
        // Register even an already-exited child, so cleanup cannot miss its streams or descendants.
        child.captureDescendants();
        child.drain();
        return child;
    }

    private boolean cleanup(LaunchTarget role) {
        OwnedProcess child;
        synchronized (this) { child = owned.get(role); }
        if (child == null) { setMessage("No owned " + role + " to stop."); return true; }
        boolean fallback = false;
        boolean forced = false;
        try {
            child.captureDescendants();
            if (role == LaunchTarget.SERVER && child.process.isAlive()) {
                try {
                    if (child.helloSent) child.send("EXASECURE/1 STOP " + child.nonce);
                    else child.process.getOutputStream().close();
                } catch (IOException ignored) { }
                child.awaitTree(limits.graceful);
            }
            if (child.treeAlive()) {
                fallback = role == LaunchTarget.SERVER;
                child.terminate(false);
                child.awaitTree(limits.terminate);
            }
            if (child.treeAlive()) {
                forced = true;
                child.terminate(true);
                child.awaitTree(limits.force);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        boolean complete = !child.treeAlive();
        if (complete) {
            child.closeStreams();
            synchronized (this) { owned.remove(role, child); }
        }
        setMessage(!complete ? "Owned " + role + " did not terminate; ownership retained."
                : forced ? "Owned " + role + " required force cleanup."
                : fallback ? "Owned Server graceful stop timed out; termination fallback completed."
                : "Owned " + role + " stopped.");
        return complete;
    }

    private void monitor() {
        synchronized (this) {
            if (closing || state == ServerState.STARTING || state == ServerState.STOPPING || startingAdmin) return;
        }
        for (LaunchTarget role : LaunchTarget.values()) {
            OwnedProcess child;
            synchronized (this) { child = owned.get(role); }
            if (child == null) continue;
            child.captureDescendants();
            if (!child.process.isAlive()) {
                int exit = child.process.exitValue();
                cleanup(role);
                synchronized (this) {
                    if (!closing && state != ServerState.STOPPING) {
                        if (role == LaunchTarget.SERVER) state = ServerState.FAILED;
                        message = "Owned " + role + " exited (exit " + exit + ").";
                    }
                }
            }
        }
    }

    static boolean probeHealth(URI uri) {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build()) {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(800)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().equals("OK");
        } catch (IOException | InterruptedException e) { return false; }
    }

    private static final class OwnedProcess {
        final LaunchTarget role;
        final Process process;
        final ProcessHandle root;
        final Set<ProcessHandle> descendants = new HashSet<>();
        final String nonce = UUID.randomUUID().toString();
        final String ready;
        final String failed;
        boolean helloSent;
        volatile boolean acknowledged;
        volatile boolean startupFailed;
        Thread stdout;
        Thread stderr;

        OwnedProcess(LaunchTarget role, Process process, int port) {
            this.role = role;
            this.process = process;
            root = process.toHandle();
            ready = "EXASECURE/1 READY " + nonce + " " + port;
            failed = "EXASECURE/1 ERROR " + nonce + " START_FAILED";
        }

        void captureDescendants() {
            if (root.isAlive()) {
                try (var tree = root.descendants()) { tree.forEach(descendants::add); }
            }
        }
        boolean treeAlive() { return process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive); }
        void send(String line) throws IOException {
            process.getOutputStream().write((line + "\n").getBytes(StandardCharsets.US_ASCII));
            process.getOutputStream().flush();
        }
        void drain() {
            stdout = Thread.ofVirtual().name("exasecure-" + role + "-stdout").start(() -> read(process.getInputStream(), true));
            stderr = Thread.ofVirtual().name("exasecure-" + role + "-stderr").start(() -> read(process.getErrorStream(), false));
        }
        void read(InputStream input, boolean protocol) {
            // Never retain or display raw child output: driver diagnostics may contain credentials.
            // Bounded exact-line parser ignores logs, stderr, oversized lines, and wrong nonces/ports.
            try (input) {
                StringBuilder line = new StringBuilder();
                boolean oversized = false;
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (!protocol) continue;
                    for (int i = 0; i < count; i++) {
                        char c = (char) (buffer[i] & 255);
                        if (c == '\n') {
                            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') line.setLength(line.length() - 1);
                            if (!oversized) {
                                if (line.toString().equals(ready)) acknowledged = true;
                                if (line.toString().equals(failed)) startupFailed = true;
                            }
                            line.setLength(0);
                            oversized = false;
                        } else if (!oversized) {
                            if (line.length() == 256) oversized = true;
                            else line.append(c);
                        }
                    }
                }
            } catch (IOException ignored) { }
        }
        void awaitTree(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (treeAlive() && System.nanoTime() < deadline) {
                captureDescendants();
                TimeUnit.MILLISECONDS.sleep(20);
            }
        }
        void terminate(boolean force) {
            captureDescendants();
            for (ProcessHandle child : descendants) if (child.isAlive()) {
                if (force) child.destroyForcibly(); else child.destroy();
            }
            if (process.isAlive()) { if (force) process.destroyForcibly(); else process.destroy(); }
        }
        void closeStreams() {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            try {
                if (stdout != null) stdout.join(1000);
                if (stderr != null) stderr.join(1000);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
