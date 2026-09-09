package com.examsystem.launcher;

import com.examsystem.client.config.ConfigurationMode;
import java.nio.file.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Test-only C03 supervisor against real native children, run using the bundled java.
 * This supplements, but does not replace, the native Control Center UI/parent-loss gate.
 */
public class NativeOwnershipSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("<Operator image> <test config> <Student image>");
        Path operator = Path.of(args[0]).toRealPath(), config = Path.of(args[1]).toRealPath();
        Properties p = new Properties();
        try (var input = Files.newInputStream(config)) { p.load(input); }
        URI db = URI.create(p.getProperty("db.url").substring(5));
        require(db.getPath().equals("/exasecure_test") && Set.of("localhost", "127.0.0.1").contains(db.getHost()), "Only local disposable exasecure_test permitted");
        LaunchSession session = LaunchSession.prepare(ConfigurationMode.INSTALLED, operator.toString(),
                System.getProperty("os.name"), operator, new OperatorSettings(config));
        require(!ProcessSupervisor.probeHealth(session.healthUri()), "Test port already occupied");
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path student = Path.of(args[2]).toRealPath().resolve(windows ? "ExaSecure Student.exe" : "Contents/MacOS/ExaSecure Student");
        Process bystander = new ProcessBuilder(student.toString()).directory(student.getParent().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var tracked = new HashSet<ProcessHandle>();
        tracked.add(bystander.toHandle());
        var owner = new ProcessSupervisor(() -> session);
        var externalObserver = new ProcessSupervisor(() -> session);
        try {
            var ready = owner.startServer().get(30, TimeUnit.SECONDS);
            System.out.println("Owner start: " + ready);
            require(ready.server() == ProcessSupervisor.ServerState.OWNED_READY, "Native child readiness");
            capture(ready.serverPid(), tracked);
            var admin = owner.startAdmin().get(10, TimeUnit.SECONDS);
            require(admin.adminRunning(), "Native Admin start");
            Thread.sleep(3000); capture(admin.adminPid(), tracked);
            System.out.println("Owned Server=" + ready.serverPid() + "; Admin=" + admin.adminPid() + "; unrelated Student=" + bystander.pid());
            var external = externalObserver.startServer().get(10, TimeUnit.SECONDS);
            require(external.server() == ProcessSupervisor.ServerState.EXTERNAL_AVAILABLE && external.serverPid() == null, "External Server must not be adopted");
            externalObserver.stopServer().get(10, TimeUnit.SECONDS);
            externalObserver.closeAsync().get(10, TimeUnit.SECONDS);
            require(alive(ready.serverPid()) && alive(admin.adminPid()) && bystander.isAlive(), "External/unrelated survival");
            System.out.println("External observer stop/close PASS; external Server/Admin and unrelated Student survived");
            var stopped = owner.stopServer().get(15, TimeUnit.SECONDS);
            require(stopped.serverPid() == null && alive(admin.adminPid()) && bystander.isAlive(), "Stop must leave Admin/unrelated Student alive");
            require(stopped.message().contains("Owned SERVER stopped.") && !stopped.message().contains("fallback"), "Graceful native STOP");
            System.out.println("Server Stop PASS; Admin survived; " + stopped.message());
            var restarted = owner.startServer().get(30, TimeUnit.SECONDS);
            require(restarted.server() == ProcessSupervisor.ServerState.OWNED_READY, "Native restart");
            capture(restarted.serverPid(), tracked);
            var closed = owner.closeAsync().get(20, TimeUnit.SECONDS);
            require(closed.serverPid() == null && closed.adminPid() == null && bystander.isAlive(), "Owned close cleanup/unrelated survival");
            require(!ProcessSupervisor.probeHealth(session.healthUri()), "Port freed after close");
            System.out.println("Supervisor close PASS; both roles cleaned, unrelated Student survives; " + closed.message());
        } finally {
            try {
                owner.closeAsync().get(20, TimeUnit.SECONDS);
                externalObserver.closeAsync().get(20, TimeUnit.SECONDS);
            } finally {
                var unrelated = new HashSet<ProcessHandle>();
                unrelated.add(bystander.toHandle()); bystander.descendants().forEach(unrelated::add);
                tracked.addAll(unrelated);
                for (var handle : List.copyOf(tracked)) handle.descendants().forEach(tracked::add);
                var leaks = tracked.stream().filter(ProcessHandle::isAlive).filter(h -> !unrelated.contains(h)).map(ProcessHandle::pid).toList();
                System.out.println("All observed test-owned PIDs=" + tracked.stream().map(ProcessHandle::pid).sorted().toList());
                // Even a failed assertion/cleanup must not leave a test-owned GUI or Server running.
                for (var handle : tracked) if (handle.isAlive()) handle.destroyForcibly();
                bystander.waitFor(5, TimeUnit.SECONDS);
                Thread.sleep(1000);
                var remaining = tracked.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
                require(leaks.isEmpty() && remaining.isEmpty(), "Owned cleanup leaks=" + leaks + "; survivors=" + remaining);
                owner.awaitTermination(Duration.ofSeconds(5));
                externalObserver.awaitTermination(Duration.ofSeconds(5));
            }
        }
        System.out.println("Native-child supervisor smoke PASS (Control Center UI not exercised)");
    }
    static void capture(Long pid, Set<ProcessHandle> handles) {
        ProcessHandle.of(pid).ifPresent(p -> { handles.add(p); p.descendants().forEach(handles::add); });
    }
    static boolean alive(Long pid) { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
