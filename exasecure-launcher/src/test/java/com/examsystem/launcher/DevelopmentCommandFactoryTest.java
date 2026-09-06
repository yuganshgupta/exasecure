package com.examsystem.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevelopmentCommandFactoryTest {
    @TempDir
    Path directory;

    @Test
    void serverUsesExactWindowsCommandAndRepositoryWorkingDirectory() throws IOException {
        Path root = repository("server repo", "mvnw.cmd");

        assertCommand(LaunchTarget.SERVER, root, "Windows 11", root, "mvnw.cmd", "exasecure-server");
    }

    @Test
    void adminUsesExactWindowsCommandAndRepositoryWorkingDirectory() throws IOException {
        Path root = repository("admin repo", "mvnw.cmd");

        assertCommand(LaunchTarget.ADMIN, root, "Windows 11", root, "mvnw.cmd", "exasecure-admin-client");
    }

    @Test
    void macOsUsesNonWindowsWrapperForBothTargets() throws IOException {
        Path root = repository("mac repo", "mvnw");

        assertCommand(LaunchTarget.SERVER, root, "Mac OS X", root, "mvnw", "exasecure-server");
        assertCommand(LaunchTarget.ADMIN, root, "Mac OS X", root, "mvnw", "exasecure-admin-client");
    }

    @Test
    void linuxUsesNonWindowsWrapper() throws IOException {
        Path root = repository("linux repo", "mvnw");

        assertCommand(LaunchTarget.SERVER, root, "Linux", root, "mvnw", "exasecure-server");
    }

    @Test
    void immediateChildDiscoversParentWithEitherWrapper() throws IOException {
        for (String wrapper : List.of("mvnw", "mvnw.cmd")) {
            Path root = repository(wrapper + " repo", wrapper);
            Path child = Files.createDirectory(root.resolve("exasecure-launcher"));

            assertCommand(LaunchTarget.SERVER, child, "Windows 11", root, "mvnw.cmd", "exasecure-server");
            assertCommand(LaunchTarget.ADMIN, child, "Mac OS X", root, "mvnw", "exasecure-admin-client");
        }
    }

    @Test
    void currentDirectoryWrapperTakesPrecedenceOverParent() throws IOException {
        Path parent = repository("parent", "mvnw.cmd");
        Path child = Files.createDirectory(parent.resolve("child"));
        Files.createFile(child.resolve("mvnw"));

        assertCommand(LaunchTarget.SERVER, child, "Windows 11", child, "mvnw.cmd", "exasecure-server");
    }

    @Test
    void missingWrapperFallsBackWithoutAddingValidation() throws IOException {
        Path workingDirectory = Files.createDirectory(directory.resolve("no wrapper"));

        assertCommand(LaunchTarget.SERVER, workingDirectory, "Windows 11", workingDirectory,
                "mvnw.cmd", "exasecure-server");
    }

    @Test
    void discoveryDoesNotSearchGrandparents() throws IOException {
        Path root = repository("root", "mvnw.cmd");
        Path grandchild = Files.createDirectories(root.resolve("child").resolve("grandchild"));

        assertCommand(LaunchTarget.ADMIN, grandchild, "Windows 11", grandchild,
                "mvnw.cmd", "exasecure-admin-client");
    }

    @Test
    void pathsWithSpacesRemainSingleUnquotedValues() throws IOException {
        Path root = repository("ExaSecure development checkout", "mvnw.cmd");
        LaunchSpec spec = DevelopmentCommandFactory.create(LaunchTarget.ADMIN, root, "Windows 11");

        ProcessBuilder builder = new ProcessBuilder(spec.command()).directory(spec.workingDirectory().toFile());

        assertThat(builder.command()).containsExactly(root.resolve("mvnw.cmd").toString(),
                "exec:java", "-pl", "exasecure-admin-client");
        assertThat(builder.directory()).isEqualTo(root.toFile());
        assertThat(builder.command().getFirst()).contains(" ").doesNotContain("\"");
    }

    @Test
    void onlyServerAndAdminTargetsAreSupported() {
        assertThat(LaunchTarget.values()).containsExactly(LaunchTarget.SERVER, LaunchTarget.ADMIN);
        for (String unsupported : List.of("STUDENT", "exasecure-server", "arbitrary-module")) {
            assertThatThrownBy(() -> LaunchTarget.valueOf(unsupported))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void nullTargetIsRejectedBeforeCommandConstruction() {
        assertThatThrownBy(() -> DevelopmentCommandFactory.create(null, directory, "Windows 11"))
                .isInstanceOf(NullPointerException.class).hasMessage("target");
    }

    @Test
    void launchSpecDefensivelyCopiesArgumentsAndExposesImmutableLists() {
        List<String> arguments = new ArrayList<>(List.of("exec:java", "-pl", "exasecure-server"));
        LaunchSpec spec = new LaunchSpec(directory.resolve("mvnw.cmd"), arguments, directory);
        arguments.set(2, "unsupported-module");

        assertThat(spec.arguments()).containsExactly("exec:java", "-pl", "exasecure-server");
        assertThatThrownBy(() -> spec.arguments().add("extra")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spec.command().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private Path repository(String name, String wrapper) throws IOException {
        Path root = Files.createDirectory(directory.resolve(name));
        Files.createFile(root.resolve(wrapper));
        return root.toAbsolutePath();
    }

    private void assertCommand(LaunchTarget target, Path workingDirectory, String osName,
                               Path expectedRoot, String wrapper, String module) {
        LaunchSpec spec = DevelopmentCommandFactory.create(target, workingDirectory, osName);

        assertThat(spec.executable()).isAbsolute().isEqualTo(expectedRoot.resolve(wrapper));
        assertThat(spec.arguments()).containsExactly("exec:java", "-pl", module);
        assertThat(spec.workingDirectory()).isEqualTo(expectedRoot);
        assertThat(spec.command()).containsExactly(expectedRoot.resolve(wrapper).toString(), "exec:java", "-pl", module);
    }
}
