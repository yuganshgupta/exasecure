import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Apple Silicon DMGs around the same fresh C04 images. No signing identity or database required. */
public class BuildMacDmgs {
    static final List<String> PRODUCTS = List.of("student", "operator");

    public static void main(String[] args) throws Exception {
        BuildImages.require(args.length == 0, "Run from repository root; no image/output overrides accepted");
        var target = BuildImages.Target.current();
        BuildImages.require(!target.windows(), "DMGs must be built natively on macOS Apple Silicon");
        var build = new BuildImages(Path.of(""), target);
        build.build();
        finish(build);
    }

    static void finish(BuildImages build) throws Exception {
        build.run(List.of("/usr/bin/git", "rev-parse", "HEAD"), build.output.resolve("source-head.txt"));
        build.run(List.of("/usr/bin/git", "diff", "--binary", "HEAD", "--", "packaging", "pom.xml"),
                build.output.resolve("source-packaging.diff"));
        var hashes = new ArrayList<String>();
        try (var files = Files.list(build.root.resolve("packaging"))) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                hashes.add(BuildImages.sha256(file) + "  packaging/" + file.getFileName());
        }
        Files.write(build.output.resolve("packaging-source.sha256"), hashes);
        Path tests = build.output.resolve("tool-tests");
        build.run(List.of(build.tool("javac").toString(), "-d", tests.toString(), "packaging/BuildImages.java",
                "packaging/BuildImagesTest.java", "packaging/BuildMacDmgs.java", "packaging/BuildMacDmgsTest.java"),
                build.output.resolve("contract-compile.log"));
        for (String test : List.of("BuildImagesTest", "BuildMacDmgsTest"))
            build.run(List.of(build.tool("java").toString(), "-cp", tests.toString(), test), build.output.resolve(test + ".log"));
        Files.createDirectory(build.output.resolve("installers"));
        hashes.clear();
        for (String product : PRODUCTS) {
            Path image = image(build, product);
            var before = inventory(image);
            Files.write(build.output.resolve(product + "-image.sha256"),
                    before.entrySet().stream().map(e -> e.getValue() + "  " + e.getKey()).toList());
            build.run(List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", image.toString()),
                    build.output.resolve(product + "-signature-verify.log"));
            build.run(List.of("/usr/bin/codesign", "--display", "--verbose=4", "--entitlements", ":-", image.toString()),
                    build.output.resolve(product + "-signature.txt"));
            var command = command(build, product);
            Files.write(build.output.resolve(product + "-dmg-command.txt"), command);
            build.run(command, build.output.resolve(product + "-dmg.log"));
            BuildImages.require(before.equals(inventory(image)), "DMG generation modified the audited image");
            Path generated = build.output.resolve("installers/" + role(product).name() + "-" + BuildImages.NATIVE_VERSION + ".dmg");
            BuildImages.require(Files.isRegularFile(generated) && Files.size(generated) > 0, "DMG missing");
            Path artifact = generated.resolveSibling(artifactName(product));
            Files.move(generated, artifact);
            build.run(List.of("/usr/bin/hdiutil", "verify", artifact.toString()), build.output.resolve(product + "-dmg-verify.log"));
            auditDmg(build, product, artifact);
            hashes.add(BuildImages.sha256(artifact) + "  " + artifact.getFileName());
            System.out.println(artifact.getFileName() + ": " + Files.size(artifact) + " bytes");
        }
        Files.write(build.output.resolve("installers/SHA256SUMS.txt"), hashes);
        System.out.println("DMGs built. User physical checks and independent acceptance remain required.");
    }

    /** jpackage replaces build metadata with a product marker and re-signs the main launcher. */
    static void auditDmg(BuildImages build, String product, Path artifact) throws Exception {
        Path work = Files.createTempDirectory(build.output, product + "-distribution-audit-");
        Path mount = Files.createDirectory(work.resolve("mount"));
        build.run(List.of("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse", "-noautoopen", "-mountpoint",
                mount.toString(), artifact.toString()), work.resolve("attach.log"));
        try {
            try (var files = Files.list(mount)) {
                Set<String> names = files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
                BuildImages.require(names.contains(role(product).name() + ".app")
                        && Set.of(role(product).name() + ".app", "Applications", ".DS_Store", ".VolumeIcon.icns", ".background", ".fseventsd").containsAll(names),
                        "Unexpected DMG root content");
            }
            BuildImages.require(Files.isSymbolicLink(mount.resolve("Applications"))
                    && Files.readSymbolicLink(mount.resolve("Applications")).equals(Path.of("/Applications")), "DMG Applications link missing");
            Path source = image(build, product), distributed = mount.resolve(source.getFileName());
            var before = inventory(source);
            var after = inventory(distributed);
            Files.write(build.output.resolve(product + "-distributed-image.sha256"),
                    after.entrySet().stream().map(e -> e.getValue() + "  " + e.getKey()).toList());
            BuildImages.require(before.remove("Contents/app/.jpackage.xml") != null
                    && after.remove("Contents/app/.package") != null
                    && Files.readString(distributed.resolve("Contents/app/.package")).equals(role(product).name()), "Unexpected product marker");
            // Validate the entire new signature, then compare executable code without signature blobs.
            build.run(List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", distributed.toString()), work.resolve("signature-verify.log"));
            BuildImages.require(before.remove("Contents/_CodeSignature/CodeResources") != null
                    && after.remove("Contents/_CodeSignature/CodeResources") != null, "Bundle signature missing");
            List<BuildImages.Role> roles = product.equals("student") ? List.of(role(product)) : BuildImages.ROLES.subList(1, 4);
            for (var role : roles) {
                String relative = "Contents/MacOS/" + role.name();
                BuildImages.require(before.remove(relative) != null && after.remove(relative) != null, "Distributed launcher missing");
                Path a = work.resolve(role.id() + "-source"), b = work.resolve(role.id() + "-distributed");
                Files.copy(source.resolve(relative), a); Files.copy(distributed.resolve(relative), b);
                for (Path copy : List.of(a, b))
                    build.run(List.of("/usr/bin/codesign", "--remove-signature", copy.toString()), work.resolve(copy.getFileName() + ".log"));
                BuildImages.require(Files.mismatch(a, b) == -1, "Distributed launcher code changed");
            }
            BuildImages.require(before.equals(after), "Distributed product payload changed");
            Files.writeString(build.output.resolve(product + "-distribution-audit.txt"),
                    "PASS: one product and Applications link; exact product marker; valid distributed signature; "
                    + "all launcher code identical after signature removal on disposable copies; all other payload bytes/link targets identical.\n");
        } finally {
            build.run(List.of("/usr/bin/hdiutil", "detach", mount.toString()), work.resolve("detach.log"));
        }
    }

    static BuildImages.Role role(String product) {
        BuildImages.require(PRODUCTS.contains(product), "Unknown DMG product");
        return BuildImages.ROLES.stream().filter(r -> r.id().equals(product)).findFirst().orElseThrow();
    }
    static String artifactName(String product) {
        return role(product).name().replace(' ', '-') + "-" + BuildImages.NATIVE_VERSION + "-macos-aarch64.dmg";
    }
    static Path image(BuildImages build, String product) {
        return build.output.resolve("images/" + role(product).name() + ".app");
    }
    static List<String> command(BuildImages build, String product) {
        return List.of(build.tool("jpackage").toString(), "--type", "dmg", "--name", role(product).name(),
                "--app-version", BuildImages.NATIVE_VERSION, "--vendor", "ExaSecure",
                "--app-image", image(build, product).toString(), "--icon", build.output.resolve("app.icns").toString(),
                "--dest", build.output.resolve("installers").toString(),
                "--temp", build.output.resolve(product + "-dmg-work").toString(), "--verbose");
    }

    /** Hash files and link targets without following directory links; reject links escaping the product. */
    static SortedMap<String, String> inventory(Path directory) throws Exception {
        Path root = directory.toRealPath();
        var result = new TreeMap<String, String>();
        try (var files = Files.walk(root)) {
            for (Path path : files.sorted().toList()) {
                String relative = root.relativize(path).toString();
                if (Files.isSymbolicLink(path)) {
                    BuildImages.require(path.toRealPath().startsWith(root), "Bundle link escapes product");
                    result.put(relative, "symlink:" + Files.readSymbolicLink(path));
                } else if (Files.isRegularFile(path)) result.put(relative, BuildImages.sha256(path));
            }
        }
        return result;
    }

    static void auditBundle(BuildImages build, String product) throws Exception {
        Path app = image(build, product), contents = app.resolve("Contents"), lib = contents.resolve("app/lib");
        inventory(app);
        Path bundleId = build.output.resolve(product + "-bundle-identifier.txt");
        build.run(List.of("/usr/bin/plutil", "-extract", "CFBundleIdentifier", "raw", "-o", "-",
                contents.resolve("Info.plist").toString()), bundleId);
        BuildImages.require(Files.readString(bundleId).trim().equals(BuildImages.macBundleIdentifier(product)),
                "macOS bundle identifier changed");
        BuildImages.require(Files.isRegularFile(contents.resolve("app/" + BuildImages.MAC_CAPTURE_LIBRARY))
                == product.equals("student"), "Only Student should bundle the macOS capture library");
        List<BuildImages.Role> roles = product.equals("student") ? List.of(role(product)) : BuildImages.ROLES.subList(1, 4);
        var expected = new TreeSet<String>();
        for (var role : roles) {
            var jars = build.resolve(role);
            expected.addAll(jars.keySet());
            Path launcher = app.resolve(build.target.launcher(role.name()));
            BuildImages.require(Files.isExecutable(launcher), "Missing native launcher " + role.name());
            String config = Files.readString(contents.resolve("app/" + role.name() + ".cfg"));
            for (String option : BuildImages.options(role, jars.keySet(), build.target).split(" "))
                BuildImages.require(config.contains("java-options=" + option + "\n"), "Wrong role options");
            BuildImages.require(config.contains("app.mainclass=" + role.main())
                    && !config.contains(build.root.toString()) && !config.contains(build.jdk.toString()), "Nonrelocatable configuration");
        }
        try (var files = Files.list(lib)) {
            BuildImages.require(files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expected), "Bundle inventory changed");
        }
        var nativeFiles = new ArrayList<Path>();
        try (var files = Files.walk(app)) {
            for (Path file : files.toList()) {
                BuildImages.require(file.equals(app) || !file.toString().endsWith(".app"), "Nested application bundle");
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                BuildImages.require(!BuildImages.forbidden(app.relativize(file).toString())
                        && !name.endsWith(".dll") && !name.endsWith(".exe") && !name.endsWith(".dmg"), "Forbidden bundle content");
                if (Files.isRegularFile(file) && isMachO(file)) nativeFiles.add(file);
            }
        }
        Path extracted = Files.createTempDirectory(build.output, product + "-native-audit-");
        int javafxNatives = 0;
        for (String name : expected) {
            Path jar = lib.resolve(name);
            BuildImages.auditJar(jar);
            BuildImages.require(Files.mismatch(build.output.resolve("input/" + product + "/lib/" + name), jar) == -1, "Staged bytes changed");
            try (var zip = new ZipFile(jar.toFile())) {
                for (var entry : zip.stream().filter(e -> !e.isDirectory()).toList()) {
                    String n = entry.getName().toLowerCase(Locale.ROOT);
                    BuildImages.require(!n.endsWith(".dll") && !n.endsWith(".exe") && !n.endsWith(".so"), "Foreign native payload");
                    if (n.endsWith(".dylib")) {
                        Path file = extracted.resolve(Integer.toString(nativeFiles.size()) + ".dylib");
                        try (var in = zip.getInputStream(entry)) { Files.copy(in, file); }
                        nativeFiles.add(file);
                        if (name.startsWith("javafx-")) javafxNatives++;
                    }
                }
            }
        }
        BuildImages.require(javafxNatives > 0, "No JavaFX native libraries audited");
        Path runtime = contents.resolve("runtime/Contents/Home");
        BuildImages.require(Files.isExecutable(runtime.resolve("bin/java")) && Files.isRegularFile(runtime.resolve("lib/server/libjvm.dylib")), "Shared bundled runtime missing");
        build.run(List.of(runtime.resolve("bin/java").toString(), "-XshowSettings:properties", "-version"), build.output.resolve(product + "-runtime.txt"));
        var proof = new ArrayList<String>();
        for (Path binary : nativeFiles) {
            var process = new ProcessBuilder("/usr/bin/lipo", "-archs", binary.toString()).redirectErrorStream(true).start();
            String arch = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            BuildImages.require(process.waitFor() == 0 && arch.equals("arm64"), "Expected arm64 only: " + binary + ": " + arch);
            proof.add(arch + "  " + build.output.relativize(binary));
        }
        BuildImages.require(nativeFiles.size() > javafxNatives + roles.size(), "Runtime native audit incomplete");
        Files.write(build.output.resolve(product + "-native-architectures.txt"), proof);
        System.out.println(product + ": " + expected.size() + " JARs, " + nativeFiles.size() + " arm64 binaries including " + javafxNatives + " JavaFX libraries");
    }
    static boolean isMachO(Path file) throws Exception {
        try (var input = new java.io.DataInputStream(Files.newInputStream(file))) {
            if (Files.size(file) < 4) return false;
            int magic = input.readInt();
            return magic == 0xcffaedfe || magic == 0xfeedfacf || magic == 0xcafebabe || magic == 0xbebafeca;
        }
    }
}
