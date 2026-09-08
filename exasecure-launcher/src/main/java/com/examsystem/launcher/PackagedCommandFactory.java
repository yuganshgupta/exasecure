package com.examsystem.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** C04 must supply exasecure.app.dir: Windows image root or macOS bundle Contents.
 * The anchor may be configured relative to a native launcher's $APPDIR, but must
 * expand to an absolute path. Native layout/pipe support still requires C04 proof.
 */
final class PackagedCommandFactory {
    private final Path root;
    private final boolean windows;

    PackagedCommandFactory(Path applicationRoot, String osName) throws IOException {
        if (applicationRoot == null || !applicationRoot.isAbsolute()) {
            throw new IOException("Packaged launch requires an absolute exasecure.app.dir.");
        }
        root = applicationRoot.normalize().toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Packaged application directory is missing.");
        String os = osName.toLowerCase(Locale.ROOT);
        windows = os.startsWith("windows");
        if (!windows && !os.startsWith("mac")) throw new IOException("Unsupported packaged platform.");
    }

    LaunchSpec create(LaunchTarget target) throws IOException {
        Objects.requireNonNull(target, "target");
        String name = switch (target) {
            case SERVER -> "ExaSecure Server";
            case ADMIN -> "ExaSecure Admin";
        };
        Path relative = Path.of(windows ? name + ".exe" : "MacOS/" + name);
        return new LaunchSpec(resolveContained(root, relative), List.of(), root);
    }

    // Also exercised with hostile relative paths; production callers use only the closed map above.
    static Path resolveContained(Path root, Path relative) throws IOException {
        if (relative.isAbsolute() || relative.getNameCount() == 0) throw rejected();
        for (Path part : relative) if (part.toString().equals("..")) throw rejected();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) throw rejected();
        if (!Files.exists(candidate)) throw new IOException("Required packaged component is missing. Repair the Operator installation.");
        Path real = candidate.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real)) throw rejected();
        if (!Files.isExecutable(real)) throw new IOException("Packaged component is not executable.");
        return real;
    }

    private static IOException rejected() { return new IOException("Packaged executable path was rejected."); }
}
