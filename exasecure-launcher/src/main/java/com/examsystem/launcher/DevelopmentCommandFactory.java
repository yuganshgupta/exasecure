package com.examsystem.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class DevelopmentCommandFactory {
    private DevelopmentCommandFactory() {
    }

    static LaunchSpec create(LaunchTarget target) {
        return create(target, Path.of(""), System.getProperty("os.name"));
    }

    static LaunchSpec create(LaunchTarget target, Path workingDirectory, String osName) {
        Objects.requireNonNull(target, "target");
        Path root = findRepoRoot(workingDirectory);
        boolean isWin = osName.toLowerCase().contains("win");
        Path wrapper = root.resolve(isWin ? "mvnw.cmd" : "mvnw");
        return new LaunchSpec(wrapper, List.of("exec:java", "-pl", target.moduleName()), root);
    }

    static Path findRepoRoot(Path workingDirectory) {
        Path dir = workingDirectory.toAbsolutePath();
        if (Files.exists(dir.resolve("mvnw")) || Files.exists(dir.resolve("mvnw.cmd"))) {
            return dir;
        }
        if (Files.exists(dir.resolve("..").resolve("mvnw")) || Files.exists(dir.resolve("..").resolve("mvnw.cmd"))) {
            return dir.resolve("..").normalize().toAbsolutePath();
        }
        return dir;
    }
}
