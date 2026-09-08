package com.examsystem.launcher;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Disposable process fixture only. Never touches a database or user configuration. */
public final class ControlledChild {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path marker = Path.of(args[1]);
        int port = Integer.parseInt(args[2]);
        if (mode.startsWith("installed")) {
            if (!"installed".equals(System.getenv("EXASECURE_CONFIG_MODE"))) throw new AssertionError("Mode missing");
            for (String key : List.of("DB_USER", "DB_URL", "DB_PASSWORD", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) {
                if (System.getenv(key) != null) throw new AssertionError("Inherited forbidden environment key");
            }
            if (mode.equals("installed-admin")) {
                if (System.getenv("EXASECURE_SERVER_CONFIG") != null) throw new AssertionError("Admin received DB config");
                publish(marker, System.getenv("EXASECURE_SERVER_URL"));
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                return;
            }
            var config = new java.util.Properties();
            try (var input = Files.newInputStream(Path.of(System.getenv("EXASECURE_SERVER_CONFIG")))) { config.load(input); }
            port = Integer.parseInt(config.getProperty("server.port"));
        }
        if (mode.equals("early")) return;
        if (mode.equals("leaf") || mode.equals("admin")) {
            publish(marker, Long.toString(ProcessHandle.current().pid()));
            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
            return;
        }
        if (mode.equals("external")) {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/health", exchange -> {
                byte[] body = "OK".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            publish(marker, "external-ready");
            System.in.read();
            server.stop(0);
            return;
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String hello = input.readLine();
        if (hello == null) return;
        String nonce = hello.substring("EXASECURE/1 HELLO ".length());
        publish(marker, "hello-received");
        if (mode.equals("reported-failure")) {
            System.out.println("EXASECURE/1 ERROR " + nonce + " START_FAILED");
            System.out.flush();
        }
        if (mode.equals("descendant")) {
            new ProcessBuilder(command("leaf", Path.of(marker + ".leaf"), port)).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Files.exists(Path.of(marker + ".leaf")) && System.nanoTime() < deadline) Thread.onSpinWait();
        }
        if (mode.equals("noise") || mode.equals("fake")) {
            System.err.println("EXASECURE/1 READY " + nonce + " " + port);
            System.out.println("log: EXASECURE/1 READY " + nonce + " " + port);
            System.out.println("EXASECURE/1 READY wrong-nonce " + port);
            System.out.println("EXASECURE/1 READY " + nonce + " " + (port + 1));
            byte[] noise = "x".repeat(8192).getBytes();
            for (int i = 0; i < 128; i++) { System.out.write(noise); System.err.write(noise); }
            System.out.println();
            System.err.println();
        }
        if (!mode.equals("noack") && !mode.equals("fake") && !mode.equals("reported-failure")) {
            System.out.println("EXASECURE/1 READY " + nonce + " " + port);
            System.out.flush();
        }
        if (mode.equals("ignore")) { new CountDownLatch(1).await(30, TimeUnit.SECONDS); return; }
        if (mode.equals("exit-ready")) return;
        String line;
        while ((line = input.readLine()) != null) {
            if (line.equals("EXASECURE/1 STOP " + nonce)) {
                Files.writeString(Path.of(marker + ".stopped"), "normal-stop");
                return;
            }
        }
        Files.writeString(Path.of(marker + ".eof"), "owner-lost");
    }

    static List<String> command(String mode, Path marker, int port) {
        return List.of(javaExecutable().toString(), "-cp", System.getProperty("java.class.path"),
                ControlledChild.class.getName(), mode, marker.toString(), Integer.toString(port));
    }
    private static void publish(Path marker, String value) throws Exception {
        Path pending = Path.of(marker + ".pending");
        Files.writeString(pending, value);
        Files.move(pending, marker, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
    static Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    }
}
