import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Java 25 source launcher: run from repository root. No third-party build-driver libraries. */
public class BuildImages {
    static final String VERSION = "2.0.0-SNAPSHOT";
    static final String MODULES = "java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,"
            + "jdk.zipfs,jdk.management,jdk.security.auth,jdk.security.jgss,jdk.httpserver";
    record Role(String id, String module, String name, String main) {
        String jar() { return module + "-" + VERSION + ".jar"; }
    }
    static final List<Role> ROLES = List.of(
            new Role("student", "exasecure-student-client", "ExaSecure Student", "com.examsystem.client.student.StudentAppLauncher"),
            new Role("operator", "exasecure-launcher", "ExaSecure Operator", "com.examsystem.launcher.ExaSecureLauncher"),
            new Role("admin", "exasecure-admin-client", "ExaSecure Admin", "com.examsystem.client.admin.AdminAppLauncher"),
            new Role("server", "exasecure-server", "ExaSecure Server", "com.examsystem.server.bootstrap.PackagedServerBootstrap"));
    record Target(boolean windows, String classifier) {
        static Target current() {
            String os = System.getProperty("os.name"), arch = System.getProperty("os.arch");
            if (os.startsWith("Windows") && Set.of("amd64", "x86_64").contains(arch)) return new Target(true, "win");
            if (os.startsWith("Mac") && arch.equals("aarch64")) return new Target(false, "mac-aarch64");
            throw new IllegalStateException("Build natively on Windows x64 or macOS Apple Silicon only: " + os + "/" + arch);
        }
        String appSuffix() { return windows ? "" : ".app"; }
        String launcher(String name) { return windows ? name + ".exe" : "Contents/MacOS/" + name; }
        String appDirectory() { return windows ? "app" : "Contents/app"; }
    }
    final Path root, output, jdk;
    final Target target;
    final Map<String, SortedMap<String, Path>> artifacts = new TreeMap<>();

    BuildImages(Path root, Target target) throws IOException {
        this.root = root.toRealPath();
        this.output = this.root.resolve("target/r4-package");
        this.jdk = Path.of(System.getProperty("java.home")).toRealPath();
        this.target = target;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Run from repository root; no output/target overrides are accepted.");
        new BuildImages(Path.of(""), Target.current()).build();
    }
    void build() throws Exception {
        require(Runtime.version().version().equals(List.of(25, 0, 1))
                && System.getProperty("java.vendor").equals("Oracle Corporation"), "Pinned Oracle JDK 25.0.1 required");
        require(Files.isRegularFile(root.resolve("pom.xml")) && Files.isDirectory(root.resolve("packaging")), "Run from repository root");
        for (String tool : List.of("java", "jpackage", "jlink", "jdeps")) require(Files.isRegularFile(tool(tool)), "Missing JDK tool " + tool);
        // Never delete an existing tree. Each invocation owns a fresh generated directory.
        require(!Files.exists(output), "target/r4-package already exists; move it aside explicitly before rebuilding");
        Files.createDirectories(output);
        Files.writeString(output.resolve("toolchain.txt"), Files.readString(jdk.resolve("release")));
        run(List.of(tool("jpackage").toString(), "--version"), output.resolve("jpackage-version.txt"));
        var maven = List.of(root.resolve(target.windows ? "mvnw.cmd" : "mvnw").toString(), "-B", "-ntp", "-Pnative-image", "package",
                "org.apache.maven.plugins:maven-dependency-plugin:3.7.0:build-classpath", "-DskipTests", "-DincludeScope=runtime",
                "-Dmdep.regenerateFile=true", "-Dproject.build.outputTimestamp=2026-09-01T00:00:00Z");
        // The profile uses fresh output/maven/<artifactId> targets, leaving development outputs alone.
        var command = new ArrayList<>(maven);
        command.addAll(List.of("-pl", "exasecure-domain,exasecure-application,exasecure-contracts,exasecure-server,exasecure-client-common,exasecure-student-client,exasecure-admin-client,exasecure-launcher"));
        run(command, output.resolve("maven-build.log"));
        for (Role role : ROLES) artifacts.put(role.id, resolve(role));
        Path icon = output.resolve(target.windows ? "app.ico" : "app.icns");
        icon(root.resolve("exasecure-client-common/src/main/resources/icons/app_icon.png"), icon, target.windows);
        Path runtime = output.resolve("runtime");
        // Explicit conservative closure. Classpath service files stay in original JARs.
        // No bind-services: this otherwise pulls in unrelated JDK tool providers.
        run(List.of(tool("jlink").toString(), "--add-modules", MODULES, "--strip-debug", "--no-header-files", "--no-man-pages",
                "--output", runtime.toString()), output.resolve("jlink.log"));
        run(List.of(runtime.resolve("bin/" + (target.windows ? "java.exe" : "java")).toString(), "--list-modules"), output.resolve("runtime-modules.txt"));
        require(Files.readString(output.resolve("runtime-modules.txt")).contains("java.desktop@"), "Desktop runtime missing");
        for (String product : List.of("student", "operator")) image(product, runtime, icon);
        System.out.println("App-images built and structurally audited: " + output.resolve("images"));
        System.out.println("Native lifecycle and UI acceptance must be performed separately before C04 acceptance.");
    }
    SortedMap<String, Path> resolve(Role role) throws Exception {
        var found = new TreeMap<String, Path>();
        String cp = Files.readString(output.resolve("maven/" + role.module + "/r4-classpath.txt")).trim();
        for (String entry : cp.split(Pattern.quote(File.pathSeparator))) {
            Path path = Path.of(entry).toRealPath();
            String name = path.getFileName().toString();
            require(found.put(name, path) == null, "Duplicate dependency filename: " + name);
        }
        Path own = output.resolve("maven/" + role.module + "/" + role.jar()).toRealPath();
        found.put(role.jar(), own);
        Set<String> allowed = allowlist(root.resolve("packaging/" + role.id + "-jars.txt"), target);
        require(found.keySet().equals(allowed), "Dependency inventory changed for " + role.id + ": expected " + allowed + ", resolved " + found.keySet());
        for (var entry : found.entrySet()) {
            if (entry.getKey().startsWith("exasecure-")) {
                String module = entry.getKey().replace("-" + VERSION + ".jar", "");
                require(entry.getValue().equals(output.resolve("maven/" + module + "/" + entry.getKey()).toRealPath()), "Stale local-repository project artifact");
            }
            auditJar(entry.getValue());
        }
        // Maven's unclassified JavaFX JARs are empty metadata carriers. Validate, then omit.
        for (String component : List.of("base", "graphics", "controls")) {
            String name = "javafx-" + component + "-25.0.2.jar";
            Path empty = found.remove(name);
            if (empty != null) try (var zip = new ZipFile(empty.toFile())) {
                require(zip.stream().noneMatch(e -> !e.isDirectory() && !e.getName().startsWith("META-INF/")), "Nonempty JavaFX metadata JAR");
            }
        }
        return found;
    }
    static Set<String> allowlist(Path file, Target target) throws IOException {
        var names = new TreeSet<String>();
        for (String line : Files.readAllLines(file)) {
            String name = line.trim().replace("{platform}", target.classifier);
            if (name.isEmpty() || name.startsWith("#")) continue;
            require(name.matches("[A-Za-z0-9_.-]+\\.jar") && names.add(name), "Invalid/duplicate allowlist entry");
        }
        return names;
    }
    static void auditJar(Path path) throws IOException {
        try (var jar = new ZipFile(path.toFile())) {
            for (var entry : jar.stream().toList()) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                require(!forbidden(name), "Forbidden JAR content: " + path.getFileName() + ": " + name);
            }
        }
    }
    static boolean forbidden(String name) {
        String n = "/" + name.replace('\\', '/').toLowerCase(Locale.ROOT);
        return n.contains("/../") || n.contains("/.ai/") || n.contains("/.git/") || n.startsWith("/legacy/")
                || n.contains("/.setup-local/") || n.contains("/surefire-reports/") || n.contains("/src/test/")
                || n.endsWith("/db.properties") || n.endsWith(".java") || n.endsWith(".log")
                || n.contains("/devbootstrap")
                || n.endsWith("/client.properties") || n.endsWith("/operator.properties") || n.endsWith("/.env");
    }
    static String options(Role role, Collection<String> names, Target target) {
        String cp = String.join(target.windows ? ";" : ":", names.stream().sorted().map(n -> "$APPDIR/lib/" + n).toList());
        return "-Djava.class.path=" + cp + " -Dexasecure.config.mode=installed --enable-native-access=ALL-UNNAMED"
                + (role.id.equals("operator") ? " -Dexasecure.app.dir=$APPDIR/.." : "");
    }
    void image(String product, Path runtime, Path icon) throws Exception {
        List<Role> roles = product.equals("student") ? List.of(ROLES.getFirst()) : ROLES.subList(1, 4);
        Path input = output.resolve("input/" + product), lib = input.resolve("lib");
        Files.createDirectories(lib);
        var union = new TreeMap<String, Path>();
        for (Role role : roles) for (var entry : artifacts.get(role.id).entrySet()) {
            Path previous = union.putIfAbsent(entry.getKey(), entry.getValue());
            require(previous == null || Files.mismatch(previous, entry.getValue()) == -1, "Conflicting shared JAR");
        }
        for (var entry : union.entrySet()) Files.copy(entry.getValue(), lib.resolve(entry.getKey()));
        var manifest = new ArrayList<String>();
        for (var entry : union.entrySet()) manifest.add(sha256(entry.getValue()) + "  lib/" + entry.getKey());
        Files.write(output.resolve(product + "-input.sha256"), manifest);
        for (Role role : roles) {
            Files.write(output.resolve(role.id + "-classpath.txt"), artifacts.get(role.id).keySet());
            run(List.of(tool("jdeps").toString(), "--multi-release", "25", "--ignore-missing-deps", "--print-module-deps", "--class-path",
                    String.join(File.pathSeparator, artifacts.get(role.id).values().stream().map(Path::toString).toList()),
                    artifacts.get(role.id).get(role.jar()).toString()), output.resolve(role.id + "-jdeps.txt"));
        }
        Role main = roles.getFirst();
        var command = new ArrayList<>(List.of(tool("jpackage").toString(), "--type", "app-image", "--name", main.name,
                "--app-version", "2.0.0", "--vendor", "ExaSecure", "--input", input.toString(), "--dest", output.resolve("images").toString(),
                "--runtime-image", runtime.toString(), "--main-jar", "lib/" + main.jar(), "--main-class", main.main, "--icon", icon.toString()));
        // Separate java-options arguments avoid platform shell quoting entirely.
        for (String option : options(main, artifacts.get(main.id).keySet(), target).split(" ")) command.addAll(List.of("--java-options", option));
        for (Role role : roles.subList(1, roles.size())) {
            Path properties = output.resolve(role.id + "-launcher.properties");
            String text = "main-jar=lib/" + role.jar() + "\nmain-class=" + role.main + "\njava-options="
                    + options(role, artifacts.get(role.id).keySet(), target) + "\n";
            if (target.windows) text += "win-console=" + role.id.equals("server") + "\n";
            Files.writeString(properties, text, StandardCharsets.ISO_8859_1);
            command.addAll(List.of("--add-launcher", role.name + "=" + properties));
        }
        run(command, output.resolve(product + "-jpackage.log"));
        Path image = output.resolve("images/" + main.name + target.appSuffix());
        for (Role role : roles) {
            require(Files.isRegularFile(image.resolve(target.launcher(role.name))), "Missing native launcher " + role.name);
            Path cfg = image.resolve(target.appDirectory()).resolve(role.name + ".cfg");
            String text = Files.readString(cfg);
            require(text.contains("app.mainclass=" + role.main), "Wrong packaged main");
            String classpath = options(role, artifacts.get(role.id).keySet(), target).split(" ")[0];
            require(text.contains("java-options=" + classpath), "Missing launcher-specific classpath");
            require(!text.contains(root.toString()) && !text.contains(jdk.toString()), "Build path embedded in launcher");
        }
        Path packagedLib = image.resolve(target.appDirectory()).resolve("lib");
        try (var files = Files.list(packagedLib)) {
            require(files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(union.keySet()), "Packaged JAR inventory mismatch");
        }
        for (var entry : union.entrySet()) require(Files.mismatch(entry.getValue(), packagedLib.resolve(entry.getKey())) == -1, "Packaged bytes changed");
        try (var files = Files.walk(image)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) require(!forbidden(image.relativize(file).toString()), "Forbidden image file");
        }
    }
    Path tool(String name) { return jdk.resolve("bin/" + name + (target.windows ? ".exe" : "")); }
    void run(List<String> command, Path log) throws Exception {
        System.out.println("Running " + Path.of(command.getFirst()).getFileName() + "; evidence: " + log.getFileName());
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("JAVA_HOME", jdk.toString());
        int status = builder.start().waitFor();
        require(status == 0, "Command failed (" + status + "): inspect " + log);
    }
    static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static void icon(Path source, Path destination, boolean windows) throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());
        require(original != null, "Canonical PNG cannot be decoded");
        int[] sizes = windows ? new int[]{16, 24, 32, 48, 64, 128, 256} : new int[]{128, 256, 512, 1024};
        var images = new ArrayList<byte[]>();
        for (int size : sizes) {
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(original, 0, 0, size, size, null); graphics.dispose();
            var bytes = new ByteArrayOutputStream(); ImageIO.write(scaled, "PNG", bytes); images.add(bytes.toByteArray());
        }
        int length = images.stream().mapToInt(b -> b.length).sum();
        ByteBuffer buffer;
        if (windows) {
            int offset = 6 + 16 * sizes.length;
            buffer = ByteBuffer.allocate(offset + length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
            for (int i = 0; i < sizes.length; i++) {
                buffer.put((byte) sizes[i]).put((byte) sizes[i]).putShort((short) 0).putShort((short) 1).putShort((short) 32)
                        .putInt(images.get(i).length).putInt(offset);
                offset += images.get(i).length;
            }
            images.forEach(buffer::put);
        } else {
            buffer = ByteBuffer.allocate(8 + 8 * sizes.length + length);
            buffer.put("icns".getBytes(StandardCharsets.US_ASCII)).putInt(buffer.capacity());
            String[] codes = {"ic07", "ic08", "ic09", "ic10"};
            for (int i = 0; i < sizes.length; i++) buffer.put(codes[i].getBytes(StandardCharsets.US_ASCII)).putInt(images.get(i).length + 8).put(images.get(i));
        }
        Files.write(destination, buffer.array());
    }
}
