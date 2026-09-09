import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.net.URLClassLoader;

/** Focused behavioral contract checks; compile together with BuildImages.java. */
public class BuildImagesTest {
    static int passed;
    interface Check { void run() throws Exception; }
    static void test(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    static void rejected(Check check) throws Exception {
        try { check.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) throws Exception {
        var windows = new BuildImages.Target(true, "win");
        var mac = new BuildImages.Target(false, "mac-aarch64");
        Path temp = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "r4-contract-test-");
        test("closed native target layout", () -> {
            BuildImages.require(windows.launcher("ExaSecure Server").equals("ExaSecure Server.exe"), "Windows layout");
            BuildImages.require(mac.launcher("ExaSecure Admin").equals("Contents/MacOS/ExaSecure Admin"), "macOS layout");
        });
        test("platform-specific JavaFX inventory", () -> {
            Set<String> win = BuildImages.allowlist(Path.of("packaging/student-jars.txt"), windows);
            Set<String> apple = BuildImages.allowlist(Path.of("packaging/student-jars.txt"), mac);
            BuildImages.require(win.contains("javafx-graphics-25.0.2-win.jar") && !win.contains("javafx-graphics-25.0.2-mac-aarch64.jar"), "Windows natives");
            BuildImages.require(apple.contains("javafx-graphics-25.0.2-mac-aarch64.jar") && !apple.contains("javafx-graphics-25.0.2-win.jar"), "Apple natives");
        });
        test("allowlist refuses paths and duplicate inputs", () -> {
            Path file = temp.resolve("allowlist.txt"); Files.writeString(file, "../secret.jar\n");
            rejected(() -> BuildImages.allowlist(file, windows));
            Files.writeString(file, "a.jar\na.jar\n"); rejected(() -> BuildImages.allowlist(file, windows));
        });
        test("archive content audit rejects private and configuration entries", () -> {
            for (String name : List.of(".ai/HANDOFF.md", ".git/config", "legacy/Main.class", "db.properties", "src/test/Fixture.class", "debug.log", "config/client.properties", "sources/Main.java", "com/examsystem/server/bootstrap/DevBootstrap.class")) {
                Path zip = temp.resolve("hostile.jar");
                try (var out = new ZipOutputStream(Files.newOutputStream(zip))) { out.putNextEntry(new ZipEntry(name)); out.write(1); out.closeEntry(); }
                rejected(() -> BuildImages.auditJar(zip));
            }
        });
        test("production service descriptors and canonical icon remain allowed", () -> {
            Path zip = temp.resolve("production.jar");
            try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (String name : List.of("META-INF/services/java.sql.Driver", "icons/app_icon.png", "db/migration/schema-v11.sql", "io/javalin/util/legacy/Example.class")) {
                    out.putNextEntry(new ZipEntry(name)); out.write(1); out.closeEntry();
                }
            }
            BuildImages.auditJar(zip);
        });
        test("classpath order is deterministic and app-root relative", () -> {
            var role = BuildImages.ROLES.get(1);
            String a = BuildImages.options(role, List.of("b.jar", "a.jar"), windows);
            String b = BuildImages.options(role, List.of("a.jar", "b.jar"), windows);
            BuildImages.require(a.equals(b) && a.contains("$APPDIR/lib/a.jar;$APPDIR/lib/b.jar") && a.contains("-Dexasecure.app.dir=$APPDIR/.."), "Relocatable options");
        });
        var build = new BuildImages(Path.of(""), BuildImages.Target.current());
        Map<String, SortedMap<String, Path>> resolved = new HashMap<>();
        test("actual production dependency inventory matches exact allowlists", () -> {
            for (var role : BuildImages.ROLES) resolved.put(role.id(), build.resolve(role));
        });
        test("Student Operator Admin cannot load Server JDBC or pool classes", () -> {
            for (String id : List.of("student", "operator", "admin")) {
                var urls = resolved.get(id).values().stream().map(p -> { try { return p.toUri().toURL(); } catch (Exception e) { throw new RuntimeException(e); } }).toArray(java.net.URL[]::new);
                try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                    for (String type : List.of("com.mysql.cj.jdbc.Driver", "com.zaxxer.hikari.HikariDataSource", "com.examsystem.server.ExaSecureServer")) {
                        try { Class.forName(type, false, loader); throw new AssertionError(id + " can load " + type); } catch (ClassNotFoundException expected) { }
                    }
                }
            }
        });
        test("Server bootstrap and JDBC service provider are available", () -> {
            var urls = resolved.get("server").values().stream().map(p -> { try { return p.toUri().toURL(); } catch (Exception e) { throw new RuntimeException(e); } }).toArray(java.net.URL[]::new);
            try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                Class.forName(BuildImages.ROLES.get(3).main(), false, loader);
                try { Class.forName("com.examsystem.server.bootstrap.DevBootstrap", false, loader); throw new AssertionError("Development bootstrap packaged"); }
                catch (ClassNotFoundException expected) { }
                BuildImages.require(ServiceLoader.load(java.sql.Driver.class, loader).stream().anyMatch(p -> p.type().getName().equals("com.mysql.cj.jdbc.Driver")), "JDBC SPI");
            }
        });
        test("native icon conversion is deterministic and has valid headers", () -> {
            Path source = Path.of("exasecure-client-common/src/main/resources/icons/app_icon.png");
            Path a = temp.resolve("a.ico"), b = temp.resolve("b.ico"), apple = temp.resolve("a.icns");
            BuildImages.icon(source, a, true); BuildImages.icon(source, b, true); BuildImages.icon(source, apple, false);
            BuildImages.require(Files.mismatch(a, b) == -1, "Icon reproducibility");
            byte[] ico = Files.readAllBytes(a);
            BuildImages.require(ico[0] == 0 && ico[2] == 1 && ico[4] == 7, "ICO directory");
            BuildImages.require(new String(Files.readAllBytes(apple), 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("icns"), "ICNS header");
        });
        System.out.println("Tests: " + passed + ", failures: 0, errors: 0, skipped: 0");
    }
}
