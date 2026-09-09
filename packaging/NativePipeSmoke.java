import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Test-only direct native pipe gate. Does not replace Control Center/UI acceptance. */
public class NativePipeSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("NativePipeSmoke <Operator image> <disposable config path>");
        Path image = Path.of(args[0]).toRealPath(), config = Path.of(args[1]).toRealPath();
        Properties p = new Properties();
        try (var in = Files.newInputStream(config)) { p.load(in); }
        URI db = URI.create(p.getProperty("db.url").substring(5));
        if (!db.getPath().equals("/exasecure_test") || !Set.of("localhost", "127.0.0.1").contains(db.getHost())) {
            throw new IllegalArgumentException("Only local disposable exasecure_test is permitted");
        }
        int port = Integer.parseInt(p.getProperty("server.port"));
        String executable = System.getProperty("os.name").startsWith("Windows") ? "ExaSecure Server.exe" : "Contents/MacOS/ExaSecure Server";
        if (healthy(port)) throw new IllegalStateException("Port is already serving HTTP; refusing test");
        for (boolean eof : List.of(false, true)) {
            ProcessBuilder builder = new ProcessBuilder(image.resolve(executable).toString()).directory(image.toFile());
            Map<String, String> env = builder.environment();
            env.keySet().removeIf(k -> k.startsWith("EXASECURE_") || k.startsWith("DB_") || Set.of("JAVA_HOME", "CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS").contains(k));
            env.put("PATH", System.getenv().getOrDefault("SystemRoot", "/usr") + (System.getProperty("os.name").startsWith("Windows") ? "\\System32" : "/bin"));
            env.put("EXASECURE_SERVER_CONFIG", config.toString());
            Process process = builder.start();
            var descendants = new HashSet<ProcessHandle>();
            var lines = new LinkedBlockingQueue<String>();
            // Avoid retaining raw driver diagnostics, including credentials in exception messages.
            Thread.ofVirtual().start(() -> { try (var in = process.getErrorStream()) { in.transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {} });
            Thread.ofVirtual().start(() -> { try (var in = process.inputReader(StandardCharsets.UTF_8)) { String line; while ((line = in.readLine()) != null) if (line.startsWith("EXASECURE/1 ")) lines.offer(line); } catch (IOException ignored) {} });
            String nonce = UUID.randomUUID().toString();
            try {
                send(process, "EXASECURE/1 HELLO " + nonce);
                System.out.println("HELLO sent; root PID=" + process.pid());
                String ready = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
                while (ready == null && System.nanoTime() < deadline) {
                    process.descendants().forEach(descendants::add);
                    ready = lines.poll(100, TimeUnit.MILLISECONDS);
                    if (!process.isAlive() && ready == null) break;
                }
                process.descendants().forEach(descendants::add);
                System.out.println("Observed descendants=" + descendants.stream().map(ProcessHandle::pid).sorted().toList());
                if (!("EXASECURE/1 READY " + nonce + " " + port).equals(ready)) throw new IllegalStateException("Native READY failed; alive=" + process.isAlive() + "; protocol=" + ready);
                if (!healthy(port)) throw new IllegalStateException("Native health failed");
                System.out.println("READY + health PASS port=" + port);
                if (eof) process.getOutputStream().close(); else send(process, "EXASECURE/1 STOP " + nonce);
                if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException("Native graceful exit failed");
                for (ProcessHandle child : descendants) if (child.isAlive()) throw new IllegalStateException("Orphan PID=" + child.pid());
                if (healthy(port)) throw new IllegalStateException("Port survived native shutdown");
                System.out.println((eof ? "EOF" : "STOP") + " graceful exit PASS; exit=0");
            } finally {
                // Only handles created by this test; never process-name/global termination.
                process.descendants().forEach(descendants::add);
                for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
                if (process.isAlive()) process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
    static void send(Process p, String line) throws IOException {
        p.getOutputStream().write((line + "\n").getBytes(StandardCharsets.US_ASCII)); p.getOutputStream().flush();
    }
    static boolean healthy(int port) {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().equals("OK");
        } catch (Exception e) { return false; }
    }
}
