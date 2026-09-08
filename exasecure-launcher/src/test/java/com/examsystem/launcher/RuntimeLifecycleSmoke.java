package com.examsystem.launcher;

import com.examsystem.client.config.ConfigurationMode;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit manual smoke entrypoint, not an automatically database-enabled unit test.
 * Requires the guarded exasecure_test environment and freshly installed reactor artifacts.
 * Arguments: repository, Server runtime classpath, Admin runtime classpath.
 * Uses real Maven development commands, then the real packaged Java bootstrap with
 * injected Java executable specs. It does not create or validate native images.
 */
public final class RuntimeLifecycleSmoke {
    public static void main(String[] args) throws Exception {
        String database = System.getenv("DB_URL");
        if (database == null || !URI.create(database.substring(5)).getPath().equals("/exasecure_test")) {
            throw new IllegalArgumentException("Smoke requires the guarded disposable exasecure_test database.");
        }
        Path repo = Path.of(args[0]);
        if (ProcessSupervisor.probeHealth(URI.create("http://localhost:8089/health"))) throw new IllegalStateException("Port 8089 already has an external Server.");
        try (var guard = new ServerSocket(8089)) { /* Do not start over an occupied port. */ }
        Path temporary = Files.createTempDirectory("exasecure-c03-runtime-");
        List<Child> children = new ArrayList<>();
        var development = new ProcessSupervisor(() -> LaunchSession.prepare(ConfigurationMode.DEVELOPMENT,
                null, System.getProperty("os.name"), repo, new OperatorSettings(temporary.resolve("unused"))));
        try {
            var ready = development.startServer().get(30, TimeUnit.SECONDS);
            require(ready.server() == ProcessSupervisor.ServerState.OWNED_READY, "Development Server: " + ready.message());
            System.out.println("C03 DEVELOPMENT owned Server PID " + ready.serverPid() + ": acknowledgment + /health OK");
            var admin = development.startAdmin().get(5, TimeUnit.SECONDS);
            require(admin.adminPid() != null, "Development Admin failed");
            System.out.println("C03 DEVELOPMENT owned Admin PID " + admin.adminPid());
            Path wrapper = DevelopmentCommandFactory.create(LaunchTarget.SERVER, repo, System.getProperty("os.name")).executable();
            for (String module : List.of("exasecure-student-client", "exasecure-launcher")) {
                Child child = new Child(new ProcessBuilder(wrapper.toString(), "exec:java", "-pl", module).directory(repo.toFile()).start());
                children.add(child);
                System.out.println("C03 DEVELOPMENT owned " + module + " PID " + child.process.pid());
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                for (Child child : children) { child.capture(); require(child.process.isAlive() && !child.error.get(), "Development client startup failure"); }
                require(development.snapshot().adminPid() != null, "Development Admin exited");
                TimeUnit.MILLISECONDS.sleep(100);
            }
            var stopped = development.stopServer().get(15, TimeUnit.SECONDS);
            require(stopped.serverPid() == null && stopped.adminPid() != null, "Server stop affected Admin or left Server");
            require(!stopped.message().contains("fallback") && !stopped.message().contains("force"), "Development Server did not stop gracefully");
            System.out.println("C03 DEVELOPMENT graceful Server stop; Admin survived");
        } finally {
            var closed = development.closeAsync().get(25, TimeUnit.SECONDS);
            require(closed.serverPid() == null && closed.adminPid() == null, "Development cleanup incomplete");
            require(development.awaitTermination(Duration.ofSeconds(3)), "Development lifecycle worker still active");
            for (Child child : children) child.stop();
        }
        try (var guard = new ServerSocket(8089)) { System.out.println("C03 DEVELOPMENT port 8089 free after cleanup"); }

        int port;
        try (var guard = new ServerSocket(0)) { port = guard.getLocalPort(); }
        OperatorSettings settings = new OperatorSettings(temporary.resolve("server/db.properties"));
        settings.save(new OperatorSettings.DatabaseSettings(database, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"), Integer.toString(port)));
        var installed = new ProcessSupervisor(() -> new LaunchSession(
                javaSpec(args[1], "com.examsystem.server.bootstrap.PackagedServerBootstrap", temporary),
                javaSpec(args[2], "com.examsystem.client.admin.AdminAppLauncher", temporary), true, settings.file(), port));
        try {
            var ready = installed.startServer().get(30, TimeUnit.SECONDS);
            require(ready.server() == ProcessSupervisor.ServerState.OWNED_READY, "Installed Java Server: " + ready.message());
            System.out.println("C03 INSTALLED JAVA owned Server PID " + ready.serverPid() + " port " + port + ": acknowledgment + /health OK");
            var admin = installed.startAdmin().get(5, TimeUnit.SECONDS);
            require(admin.adminPid() != null, "Installed Java Admin failed");
            System.out.println("C03 INSTALLED JAVA owned Admin PID " + admin.adminPid() + " URL http://localhost:" + port);
            TimeUnit.SECONDS.sleep(5);
            require(installed.snapshot().adminPid() != null, "Installed Java Admin exited");
            var stopped = installed.stopServer().get(15, TimeUnit.SECONDS);
            require(stopped.serverPid() == null && stopped.adminPid() != null, "Installed Server/Admin lifetime failure");
            require(!stopped.message().contains("fallback") && !stopped.message().contains("force"), "Installed Server did not stop gracefully");
            System.out.println("C03 INSTALLED JAVA graceful Server stop; Admin survived");
        } finally {
            var closed = installed.closeAsync().get(25, TimeUnit.SECONDS);
            require(closed.serverPid() == null && closed.adminPid() == null, "Installed cleanup incomplete");
            require(installed.awaitTermination(Duration.ofSeconds(3)), "Installed lifecycle worker still active");
            Files.deleteIfExists(settings.file());
            Files.deleteIfExists(settings.file().getParent());
            Files.deleteIfExists(temporary);
        }
        try (var guard = new ServerSocket(port)) { System.out.println("C03 INSTALLED JAVA port " + port + " free after cleanup"); }
        System.out.println("C03 runtime smoke PASS; no native-image acceptance claimed");
    }

    private static LaunchSpec javaSpec(String classpath, String main, Path cwd) {
        return new LaunchSpec(ControlledChild.javaExecutable(), List.of("-cp", classpath, main), cwd);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static final class Child {
        final Process process;
        final Set<ProcessHandle> descendants = new HashSet<>();
        final AtomicBoolean error = new AtomicBoolean();
        Child(Process process) {
            this.process = process;
            for (var input : List.of(process.getInputStream(), process.getErrorStream())) {
                Thread.ofVirtual().start(() -> {
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("[ERROR]") || line.contains("Exception") || line.contains("Caused by:")) error.set(true);
                        }
                    } catch (java.io.IOException ignored) { }
                });
            }
        }
        void capture() { if (process.isAlive()) process.descendants().forEach(descendants::add); }
        void stop() throws Exception {
            capture();
            descendants.forEach(ProcessHandle::destroy);
            process.destroy();
            process.waitFor(2, TimeUnit.SECONDS);
            for (var child : descendants) if (child.isAlive()) child.destroyForcibly();
            if (process.isAlive()) process.destroyForcibly();
            require(process.waitFor(3, TimeUnit.SECONDS), "Owned smoke child did not exit");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(20);
            require(descendants.stream().noneMatch(ProcessHandle::isAlive), "Owned smoke descendant did not exit");
            System.out.println("C03 DEVELOPMENT stopped owned PID " + process.pid() + " descendants " + descendants.stream().map(ProcessHandle::pid).toList());
        }
    }
}
