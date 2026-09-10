import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Windows-only EXE wrapper around the C04 builder, always starting with fresh images. */
public class BuildWindowsInstallers {
    static final String WIX_VERSION = "3.14.1.8722";
    // Permanent, distinct upgrade families. Never regenerate these for a new version.
    static final Map<String, String> UPGRADE_IDS = Map.of(
            "student", "57962947-1c83-4ef3-96c5-822d896dab67",
            "operator", "c1d73cd2-d6d2-4825-b7b6-0c2c05745a71");

    public static void main(String[] args) throws Exception {
        BuildImages.require(args.length == 0, "Run from repository root; no image or output overrides accepted");
        BuildImages.Target target = BuildImages.Target.current();
        BuildImages.require(target.windows(), "Windows x64 installers must be built on Windows x64");
        String configured = System.getenv("EXASECURE_WIX_BIN");
        BuildImages.require(configured != null && !configured.isBlank(),
                "Set EXASECURE_WIX_BIN to the pinned WiX 3.14.1 binaries directory (build tooling only)");
        Path wix = Path.of(configured).toRealPath();
        var versions = new ArrayList<String>();
        for (String tool : List.of("candle", "light")) {
            Path executable = wix.resolve(tool + ".exe");
            BuildImages.require(Files.isRegularFile(executable), "Missing WiX " + tool);
            var process = new ProcessBuilder(executable.toString(), "-?").redirectErrorStream(true).start();
            String text = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            BuildImages.require(process.waitFor() == 0 && text.lines().findFirst().orElse("").endsWith(" " + WIX_VERSION),
                    "Pinned WiX " + WIX_VERSION + " required: " + tool);
            versions.add(text.lines().findFirst().orElseThrow());
            versions.add(BuildImages.sha256(executable) + "  " + tool + ".exe");
        }
        var build = new BuildImages(Path.of(""), target);
        // C04 refuses existing output and builds every project JAR in an isolated fresh reactor.
        build.build();
        configureInternalShortcuts(build.output.resolve("images/ExaSecure Operator/app/.jpackage.xml"));
        Files.write(build.output.resolve("wix-toolchain.txt"), versions);
        build.run(List.of("git", "rev-parse", "HEAD"), build.output.resolve("source-head.txt"));
        build.run(List.of("git", "diff", "--binary", "HEAD", "--", "packaging", "pom.xml"), build.output.resolve("source-packaging.diff"));
        var sourceHashes = new ArrayList<String>();
        try (var files = Files.list(build.root.resolve("packaging"))) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                sourceHashes.add(BuildImages.sha256(file) + "  packaging/" + file.getFileName());
            }
        }
        Files.write(build.output.resolve("packaging-source.sha256"), sourceHashes);
        Path checks = build.output.resolve("tool-tests");
        build.run(List.of(build.tool("javac").toString(), "-d", checks.toString(), "packaging/BuildImages.java",
                "packaging/BuildImagesTest.java", "packaging/BuildWindowsInstallers.java", "packaging/BuildWindowsInstallersTest.java"),
                build.output.resolve("contract-compile.log"));
        for (String test : List.of("BuildImagesTest", "BuildWindowsInstallersTest")) {
            build.run(List.of(build.tool("java").toString(), "-cp", checks.toString(), test), build.output.resolve(test + ".log"));
        }
        Path destination = build.output.resolve("installers");
        Files.createDirectory(destination);
        var hashes = new ArrayList<String>();
        for (String product : List.of("student", "operator")) {
            var role = BuildImages.ROLES.stream().filter(r -> r.id().equals(product)).findFirst().orElseThrow();
            Path image = build.output.resolve("images/" + role.name());
            SortedMap<String, String> before = inventory(image);
            Files.write(build.output.resolve(product + "-image.sha256"),
                    before.entrySet().stream().map(e -> e.getValue() + "  " + e.getKey()).toList());
            var command = command(build, product);
            Files.write(build.output.resolve(product + "-installer-command.txt"), command);
            ProcessBuilder process = new ProcessBuilder(command).directory(build.root.toFile()).redirectErrorStream(true)
                    .redirectOutput(build.output.resolve(product + "-installer.log").toFile());
            process.environment().put("PATH", wix + ";" + process.environment().getOrDefault("PATH", ""));
            System.out.println("Building " + role.name() + " EXE installer");
            BuildImages.require(process.start().waitFor() == 0, "Installer failed; inspect " + product + "-installer.log");
            BuildImages.require(before.equals(inventory(image)), "Installer generation changed the verified image");
            auditInstallerProject(build.output.resolve(product + "-installer-work/config/bundle.wxf"), image, role.name());
            Path generated = destination.resolve(role.name() + "-" + BuildImages.NATIVE_VERSION + ".exe");
            BuildImages.require(Files.isRegularFile(generated) && Files.size(generated) > 0, "EXE installer missing");
            Path artifact = destination.resolve(artifactName(product));
            Files.move(generated, artifact);
            hashes.add(BuildImages.sha256(artifact) + "  " + artifact.getFileName());
            System.out.println(artifact.getFileName() + ": " + Files.size(artifact) + " bytes");
        }
        Files.write(destination.resolve("SHA256SUMS.txt"), hashes);
        System.out.println("EXE installers built: " + destination);
        System.out.println("Physical installed-product checks and independent audit remain required.");
    }

    static String artifactName(String product) {
        BuildImages.require(UPGRADE_IDS.containsKey(product), "Unknown installer product");
        return "ExaSecure-" + (product.equals("student") ? "Student" : "Operator")
                + "-" + BuildImages.NATIVE_VERSION + "-windows-x64.exe";
    }

    static List<String> command(BuildImages build, String product) {
        BuildImages.require(UPGRADE_IDS.containsKey(product), "Unknown installer product");
        var role = BuildImages.ROLES.stream().filter(r -> r.id().equals(product)).findFirst().orElseThrow();
        return List.of(build.tool("jpackage").toString(), "--type", "exe", "--name", role.name(),
                "--app-version", BuildImages.NATIVE_VERSION, "--vendor", "ExaSecure",
                "--description", product.equals("student") ? "ExaSecure Student examination client" : "ExaSecure Operator Control Center",
                "--app-image", build.output.resolve("images/" + role.name()).toString(),
                "--icon", build.output.resolve("app.ico").toString(),
                "--dest", build.output.resolve("installers").toString(),
                "--install-dir", role.name(), "--win-dir-chooser", "--win-menu", "--win-menu-group", "ExaSecure",
                "--win-upgrade-uuid", UPGRADE_IDS.get(product),
                "--temp", build.output.resolve(product + "-installer-work").toString(), "--verbose");
    }

    static SortedMap<String, String> inventory(Path directory) throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted().toList()) {
                BuildImages.require(!Files.isSymbolicLink(path), "Image must not contain symbolic links");
                if (Files.isRegularFile(path)) result.put(directory.relativize(path).toString().replace('\\', '/'), BuildImages.sha256(path));
            }
        }
        return result;
    }

    static org.w3c.dom.Document readXml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    static void configureInternalShortcuts(Path metadata) throws Exception {
        // JDK 25.0.1 drops false shortcut hints when saving an app-image. Its
        // predefined-image loader merges the installer's --win-menu into every
        // launcher unless these original parameter keys explicitly override it.
        // Only jpackage build metadata changes; this file is omitted on install.
        var xml = readXml(metadata);
        var root = xml.getDocumentElement();
        BuildImages.require(root.getAttribute("version").equals("25.0.1")
                && root.getAttribute("platform").equals("windows"), "Unexpected jpackage metadata version/platform");
        var launchers = xml.getElementsByTagName("add-launcher");
        var names = new HashSet<String>();
        for (int i = 0; i < launchers.getLength(); i++) {
            var launcher = (org.w3c.dom.Element) launchers.item(i);
            BuildImages.require(names.add(launcher.getAttribute("name")) && launcher.getAttribute("service").equals("false"), "Unexpected internal launcher");
            for (String key : List.of("menu-hint", "shortcut-hint")) {
                BuildImages.require(launcher.getElementsByTagName(key).getLength() == 0, "Shortcut metadata already configured");
                var hint = xml.createElement(key);
                hint.setTextContent("false");
                launcher.appendChild(hint);
            }
        }
        BuildImages.require(names.equals(Set.of("ExaSecure Admin", "ExaSecure Server")), "Unexpected internal launcher inventory");
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(xml), new StreamResult(metadata.toFile()));
    }

    static void auditInstallerProject(Path project, Path image, String productName) throws Exception {
        var xml = readXml(project);
        var shortcuts = xml.getElementsByTagName("Shortcut");
        BuildImages.require(shortcuts.getLength() == 1, "Exactly one product shortcut required");
        var shortcut = (org.w3c.dom.Element) shortcuts.item(0);
        BuildImages.require(shortcut.getAttribute("Name").equals(productName) && shortcut.getAttribute("Arguments").isEmpty(), "Wrong shortcut or unexpected arguments");
        var expected = new TreeSet<>(inventory(image).keySet());
        expected.remove("app/.jpackage.xml"); // jpackage deliberately excludes its build metadata.
        var actual = new TreeSet<String>();
        Path realImage = image.toRealPath();
        var files = xml.getElementsByTagName("File");
        int packageMarkers = 0;
        for (int i = 0; i < files.getLength(); i++) {
            Path source = Path.of(((org.w3c.dom.Element) files.item(i)).getAttribute("Source")).toRealPath();
            if (source.equals(project.resolveSibling(".package").toRealPath())) {
                BuildImages.require(Files.readString(source).equals(productName), "Unexpected jpackage product marker content");
                packageMarkers++;
                continue;
            }
            BuildImages.require(source.startsWith(realImage), "Installer source outside verified image");
            BuildImages.require(actual.add(realImage.relativize(source).toString().replace('\\', '/')), "Duplicate installer source");
        }
        BuildImages.require(actual.equals(expected) && packageMarkers == 1, "Installer payload differs from verified image plus product marker");
        System.out.println(productName + ": verified " + actual.size() + " payload files and one argument-free shortcut");
    }
}
